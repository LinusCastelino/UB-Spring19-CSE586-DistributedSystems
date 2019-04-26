package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {
	private final String TAG = SimpleDynamoProvider.class.getSimpleName();

	private final String[] DHT_NODE_PORTS = new String[]{"5554", "5556", "5558", "5560", "5562"};

	private final Integer SERVER_PORT = 10000;
	private final Integer MIN_WRITE_COUNT = 2;
	private final Integer MIN_READ_COUNT = 2;
	private final String KEY = "key";
	private final String VALUE = "value";
	private final String VERSION = "version";
	private final String LOCAL_PAIRS_QUERY = "@";
	private final String ALL_PAIRS_QUERY = "*";
	private final String MSG_DELIMETER = ":";
	private final String CV_DELIMETER = "/";
	private final String CURSOR_REC_DELIMETER = "#";
	private final String INSERT_TAG = "I";
	private final String INSERT_REPLICA_TAG = "IR";
	private final String INSERT_ACK_TAG = "IA";
	private final String DELETE_TAG = "D";
	private final String DELETE_REPLICA_TAG = "DR";
	private final String DELETE_ACK_TAG = "DA";
	private final String QUERY_TAG = "Q";
	private final String QUERY_RESPONSE_TAG = "QR";
	private final String NULL_STR = "null";

	private String selfPort = null;
	private String selfId = null;
	private Integer selfDhtPosition = null;
	private List<DHTNode> dhtNodes = null;

	private SimpleDynamoHelper dynamoHelper = null;

	private Map<String, List<String>> queryMap;
	private Map<String, Integer> quorumWriteCheck;

	@Override
	public boolean onCreate() {
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		selfPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

		dynamoHelper = new SimpleDynamoHelper(this.getContext());

		try{
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		}
		catch(IOException ioe){
			Log.e(TAG, "Exception encountered while creating server socket : " + ioe.getMessage());
			ioe.printStackTrace();
			return false;
		}

		try {
			selfId = genHash(selfPort);
		}
		catch (NoSuchAlgorithmException nsae){
			Log.e(TAG, "Exception encountered while generating selfId");
			nsae.printStackTrace();
		}

		queryMap = new HashMap<String, List<String>>();
		quorumWriteCheck = new HashMap<String, Integer>();

		dhtNodes = new ArrayList<DHTNode>();
		for(String nodePort : DHT_NODE_PORTS){
			DHTNode node = new DHTNode();
			node.setPort(nodePort);
			try {
				node.setHash(genHash(nodePort));
			}
			catch (NoSuchAlgorithmException nsae){
				Log.e(TAG, "Exception encountered while generating hash for node " + nodePort);
				nsae.printStackTrace();
			}
			dhtNodes.add(node);
		}

		Collections.sort(dhtNodes, new DHTNodesComparator());

		for(int i=0; i<dhtNodes.size(); i++) {
			if (dhtNodes.get(i).equals(selfPort)) {
				selfDhtPosition = i;
				break;
			}
		}

		return true;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.d(TAG, "Insert - " + values.toString());

		String insertKey = null;
		try{
			insertKey = (String)values.get(KEY);
			String keyHash = genHash(insertKey);
			List<Object> partitionCoordinatorInfo = getPartitionCoordinatorInfo(keyHash);
			DHTNode partitionCoordinatorNode = (DHTNode)(partitionCoordinatorInfo.get(1));
			if(partitionCoordinatorNode.getHash().equals(selfId)){
				// created a new CV object so that the insert does not override a higher version
				// number with an older one in replicas (in case this node experiences a failure)
				insertVersionedEntry(values);
				quorumWriteCheck.put(insertKey, 1);
				sendMessageToReplicas(selfDhtPosition, INSERT_REPLICA_TAG + MSG_DELIMETER
						+ selfPort + MSG_DELIMETER + insertKey + CV_DELIMETER + values.get(VALUE));
			}
			else{
				String msgToSendPartial = MSG_DELIMETER + selfPort + MSG_DELIMETER + insertKey +
									CV_DELIMETER + values.get(VALUE);
				quorumWriteCheck.put(insertKey, 0);
				sendMessage(convertToPort(partitionCoordinatorNode.getPort()), INSERT_TAG + msgToSendPartial);
				sendMessageToReplicas((Integer)partitionCoordinatorInfo.get(0) , INSERT_REPLICA_TAG + msgToSendPartial);
			}

			synchronized (insertKey){
				try {
					insertKey.wait();
				}
				catch(InterruptedException ie){
					Log.e(TAG, "InterruptedException encountered while waiting on quorum write condition" +
							" (insert) for key " + insertKey);
					ie.printStackTrace();
				}
			}
		}
		catch (NoSuchAlgorithmException nsae){
			Log.e(TAG, "Exception encountered while generating hash for key " + insertKey);
			nsae.printStackTrace();
		}
		return uri;
	}

	public void insertVersionedEntry(ContentValues values) {
		Log.d(TAG, "Inserting Versioned Entry of - " + values.toString());

		String insertKey = (String)values.get(KEY);
		Cursor cursor = dynamoHelper.query(insertKey);
		if(cursor.getCount() == 0){
			// first insert for the key
			values.put(VERSION, 0);   // 0 versioning used
		}
		else{
			cursor.moveToFirst();
			Integer oldVersionNum = cursor.getInt(cursor.getColumnIndex(VERSION));
			values.put(VERSION, ++oldVersionNum);
		}
		dynamoHelper.insert(values);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {

		if(selection.equals(ALL_PAIRS_QUERY)){
			Cursor cursor = dynamoHelper.query(LOCAL_PAIRS_QUERY);

			String cursorToStr = convertCursorToString(cursor);
			List<String> queryResults = new ArrayList<String>();
			queryResults.add(cursorToStr);
			queryMap.put(selection, queryResults);

			String msgToSend = QUERY_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + ALL_PAIRS_QUERY;
			// send delete request to all other nodes
			for(int i=0; i<dhtNodes.size(); i++){
				if(i != selfDhtPosition){
					// to avoid infinite loop
					sendMessage(convertToPort(dhtNodes.get(i).getPort()), msgToSend);
				}
			}
		}
		else if(selection.equals(LOCAL_PAIRS_QUERY)){
			return dynamoHelper.query(LOCAL_PAIRS_QUERY);
		}
		else{    // key query
			try{
				String keyHash = genHash(selection);
				List<Object> partitionCoordinatorInfo = getPartitionCoordinatorInfo(keyHash);
				DHTNode partitionCoordinatorNode = (DHTNode)(partitionCoordinatorInfo.get(1));
				List<String> queryResults = new ArrayList<String>();
				if(partitionCoordinatorNode.getPort().equals(selfPort)){
					Cursor selfQueryResults = dynamoHelper.query(selection);
					queryResults.add(convertCursorToString(selfQueryResults));
				}
				else{
					String msgToSend = QUERY_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selection;
					sendMessage(convertToPort(partitionCoordinatorNode.getPort()), msgToSend);
					sendMessageToReplicas((Integer)partitionCoordinatorInfo.get(0), msgToSend);
				}
				queryMap.put(selection, queryResults);
			}
			catch(NoSuchAlgorithmException nsae){
				Log.e(TAG, "Error occured while generating hash of the query key " + selection);
				nsae.printStackTrace();
			}
		}

		synchronized (selection){
			try{
				selection.wait();
			}
			catch (InterruptedException ie){
				Log.e(TAG, "InterruptedException encountered while waiting on quorum read condition" +
						" for key " + selection);
				ie.printStackTrace();
			}
		}

		List<String> results = null;
		synchronized (queryMap){
			// synchronizing this operation so that no updates to a key are made once MIN_READ_COUNT
			// responses are received
			results = queryMap.get(selection);
			queryMap.remove(selection);
		}
		Cursor resultCursor = convertQueryResultsToCursor(results);

		return resultCursor;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.d(TAG, "Delete - " + selection);

		if(selection.equals(ALL_PAIRS_QUERY)){
			dynamoHelper.delete(LOCAL_PAIRS_QUERY);

			String msgToSend = DELETE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + LOCAL_PAIRS_QUERY;
			// send delete request to all other nodes
			for(int i=0; i<dhtNodes.size(); i++){
				if(i != selfDhtPosition){
					// to avoid infinite loop
					sendMessage(convertToPort(dhtNodes.get(i).getPort()), msgToSend);
				}
			}
		}
		else if(selection.equals(LOCAL_PAIRS_QUERY)){
			dynamoHelper.delete(LOCAL_PAIRS_QUERY);
		}
		else{
			try{
				String keyHash = genHash(selection);
				List<Object> partitionCoordinatorInfo = getPartitionCoordinatorInfo(keyHash);
				DHTNode partitionCoordinatorNode = (DHTNode)(partitionCoordinatorInfo.get(1));
				if(partitionCoordinatorNode.getPort().equals(selfPort)){
					dynamoHelper.delete(selection);
					quorumWriteCheck.put(selection, 1);
					sendMessageToReplicas(selfDhtPosition, DELETE_REPLICA_TAG +
							MSG_DELIMETER + selfPort + MSG_DELIMETER + selection);
				}
				else{
					String msgToSendPartial = MSG_DELIMETER + selfPort + MSG_DELIMETER + selection;
					quorumWriteCheck.put(selection, 0);
					sendMessage(convertToPort(partitionCoordinatorNode.getPort()), DELETE_TAG + msgToSendPartial);
					sendMessageToReplicas((Integer)partitionCoordinatorInfo.get(0), DELETE_REPLICA_TAG + msgToSendPartial);
				}

				synchronized (selection){
					try{
						selection.wait();
					}
					catch(InterruptedException ie){
						Log.e(TAG, "InterruptedException encountered while waiting on quorum " +
								"write condition (delete) for key " + selection);
						ie.printStackTrace();
					}
				}
			}
			catch (NoSuchAlgorithmException nsae){
				Log.e(TAG, "Error occurred while generating hash for key - " + selection);
				nsae.printStackTrace();
			}

		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    public void sendMessage(String port, String msg){
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, port, msg);
	}

	public void sendMessageToReplicas(Integer dhtPosition, String msgToSend){
		for(int i=1; i<3; i++){
			DHTNode replicaSuccessor = dhtNodes.get((dhtPosition + i) % dhtNodes.size());
			sendMessage(convertToPort(replicaSuccessor.getPort()), msgToSend);
		}
	}

	public String convertCursorToString(Cursor cursor){

		if(cursor.moveToFirst()){
			String cursorToStr = "";
			int keyColumnIndex = cursor.getColumnIndex(KEY);
			int valueColumnIndex = cursor.getColumnIndex(VALUE);
			int versionColumnIndex = cursor.getColumnIndex(VERSION);

			do{
				cursorToStr += cursor.getString(keyColumnIndex);
				cursorToStr += CV_DELIMETER;
				cursorToStr += cursor.getString(valueColumnIndex);
				cursorToStr += CV_DELIMETER;
				cursorToStr += versionColumnIndex;
				cursorToStr += CURSOR_REC_DELIMETER;
			}while(cursor.moveToNext());

			cursorToStr = cursorToStr.substring(0, cursorToStr.length()-1);    // removing the delimeter at the end
			return cursorToStr;
		}

		return null;
	}    //convertCursorToSting()

	public Cursor convertQueryResultsToCursor(List<String> results){
		MatrixCursor cursor = new MatrixCursor(new String[]{KEY, VALUE});

		for(String result : results){
			if(!result.equals(NULL_STR)) {
				String records[] = result.split(CURSOR_REC_DELIMETER);
				for (int i = 0; i < records.length; i++) {
					String record[] = records[i].split(CV_DELIMETER);
					cursor.newRow().add(KEY, record[0])
							.add(VALUE, record[1]);
				}
			}
		}

		return cursor;
	}    //convertStringToCursor()

	public List<Object> getPartitionCoordinatorInfo(String keyHash){

		Integer limit = dhtNodes.size();
		for(int c=0, p = limit-1 ; c < limit; c++, p++){
			DHTNode coord = dhtNodes.get(c);
			DHTNode pred = dhtNodes.get(p%limit);

			boolean flag = false;
			if (pred.getHash().compareTo(keyHash) < 0 && keyHash.compareTo(coord.getHash()) <= 0)
				flag = true;
			if (pred.getHash().compareTo(coord.getHash()) > 0    // last partition check
					&& (pred.getHash().compareTo(keyHash) < 0 || keyHash.compareTo(coord.getHash()) <= 0))
				flag = true;

			if(flag)
				return Arrays.asList(c, coord);
		}
		return null;
	}    //getPartitionCoordinatorInfo()

	public void checkToNotifyWriteOnKey(String key){
		Integer writeCount = quorumWriteCheck.get(key);
		// if writecount is null, there is no need to capture this write acknowledgement
		// since the insert method call by the thread will have already returned
		// since the MIN_WRITE_COUNT condition will have been satisfied due to previous responses.
		if(writeCount != null){
			writeCount++;
			if(writeCount == MIN_WRITE_COUNT){
				for(String waitKey : quorumWriteCheck.keySet()){
					if(waitKey.equals(key)){
						synchronized (waitKey){
							waitKey.notify();
							quorumWriteCheck.remove(waitKey);
						}
						break;
					}
				}
			}
			else{
				quorumWriteCheck.put(key,writeCount);
			}
		}
	}

	public String convertToPort(String avdID){
		return (Integer.parseInt(avdID) *2) + "";
	}

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

		@Override
		protected Void doInBackground(ServerSocket... serverSockets) {
			ServerSocket serverSocket = serverSockets[0];

			while(true){
				Socket socket = null;
				BufferedReader br = null;
				PrintWriter pw = null;

				try{
					socket = serverSocket.accept();
					br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

					String inputMsg = br.readLine();
					if(inputMsg != null){
						Log.d(TAG, "Server Task - Input msg received : " + inputMsg);
						pw = new PrintWriter(socket.getOutputStream(), true);
						pw.println(inputMsg);

						String msgArr[] = inputMsg.split(MSG_DELIMETER);
						String msgTag = msgArr[0];
						String msgSrc = msgArr[1];
						String msg = msgArr[2];

						if(msgTag.equals(INSERT_TAG)){
							String[] record = msg.split(CV_DELIMETER);

							ContentValues contentValues = new ContentValues();
							contentValues.put(KEY, record[0]);
							contentValues.put(VALUE, record[1]);
							insertVersionedEntry(contentValues);
							sendMessage(msgSrc, INSERT_ACK_TAG + MSG_DELIMETER + selfPort +
									MSG_DELIMETER+ record[0]);
//							sendMessageToReplicas(selfDhtPosition, INSERT_REPLICA_TAG +
//									MSG_DELIMETER + msgSrc + MSG_DELIMETER + msg);
						}
						else if(msgTag.equals(INSERT_REPLICA_TAG)){
							String[] record = msg.split(CV_DELIMETER);

							ContentValues contentValues = new ContentValues();
							contentValues.put(KEY, record[0]);
							contentValues.put(VALUE, record[1]);
							insertVersionedEntry(contentValues);
							sendMessage(msgSrc, INSERT_ACK_TAG + MSG_DELIMETER + selfPort +
									MSG_DELIMETER + record[0]);
						}
						else if(msgTag.equals(DELETE_TAG)){
							dynamoHelper.delete(msg);
							sendMessage(msgSrc, DELETE_ACK_TAG + MSG_DELIMETER + selfPort +
									MSG_DELIMETER + msg);
//							sendMessageToReplicas(selfDhtPosition, DELETE_REPLICA_TAG +
//									MSG_DELIMETER + msgSrc + MSG_DELIMETER + msg);
						}
						else if(msgTag.equals(DELETE_REPLICA_TAG)){
							dynamoHelper.delete(msg);
							sendMessage(msgSrc, DELETE_ACK_TAG + MSG_DELIMETER + selfPort +
									MSG_DELIMETER + msg);
						}
						else if(msgTag.equals(INSERT_ACK_TAG) || msgTag.equals(DELETE_ACK_TAG)){
							checkToNotifyWriteOnKey(msg);
						}
						else if(msgTag.equals(QUERY_TAG)){
							if(msg.equals(ALL_PAIRS_QUERY)) {
								// ALL_PAIRS_QUERY received in ServerTask should return all local pairs to msgSrc
								Cursor cursor = dynamoHelper.query(LOCAL_PAIRS_QUERY);

								String cursorToStr = convertCursorToString(cursor);
								String msgToSend = QUERY_RESPONSE_TAG + MSG_DELIMETER + selfPort +
										MSG_DELIMETER + msg + MSG_DELIMETER + cursorToStr;
								sendMessage(msgSrc, msgToSend);
							}
							else{
								// key query


							}
						}
						else if(msgTag.equals(QUERY_RESPONSE_TAG)){
							List<String> queryResults = queryMap.get(msg);
							queryResults.add(msgArr[3]);

							if(msg.equals(ALL_PAIRS_QUERY) && queryResults.size() == dhtNodes.size()){
								// all records received from every node
								for(String key : queryMap.keySet()){
									if(key.equals(msg)){
										synchronized (key){
											key.notify();
										}
									}
								}
							}
							else{
								// key query response
							}
						}
					}
				}
				catch(Exception e){
					Log.e(TAG, "General exception occurred in ServerTask - " + e.getMessage());
					e.printStackTrace();
				}
				finally{
					try{
						if(br != null)
							br.close();
						if(pw != null)
							pw.close();
						if(socket !=null && !socket.isClosed())
							socket.close();
					}
					catch (IOException ioe){
						Log.e(TAG, "Error occurred while trying to close socket/streams - " + ioe.getMessage());
						ioe.printStackTrace();
					}
				}
			}
		}
	}

	private class ClientTask extends AsyncTask<String, Void, Void>{

		@Override
		protected Void doInBackground(String... msgs) {
			String port = msgs[0];
			String msgToSend = msgs[1];

			Socket socket = null;
			BufferedReader br = null;
			PrintWriter pw = null;

			try{
				socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.valueOf(port));

				pw = new PrintWriter(socket.getOutputStream(), true);
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				pw.println(msgToSend);
				Log.d(TAG, "ClientTask - Sent msg " + msgToSend + " to port " + port);

				while(true){
					if(br.readLine().equals(msgToSend))
						break;
				}
			}
			catch(SocketTimeoutException ste){
				Log.e(TAG, "Socket timeout exception encountered in ClientTask - " + ste.getMessage());
				ste.printStackTrace();
			}
			catch (IOException ioe){
				Log.e(TAG, "IO exception encountered in ClientTask - " + ioe.getMessage());
				ioe.printStackTrace();
			}
			catch(Exception e){
				Log.e(TAG, "General exception encountered in ClientTask - " + e.getMessage());
				e.printStackTrace();
			}
			finally {
				try{
					if(br != null)
						br.close();
					if(pw != null)
						pw.close();
					if(socket != null)
						socket.close();
				}
				catch (Exception e){
					Log.e(TAG, "Exception encountered while closing streams");
					e.printStackTrace();
				}
			}

			return null;
		}
	}

	private class DHTNode{
		private String port;
		private String hash;

		public void setPort(String port){
			this.port = port;
		}

		public String getPort(){
			return this.port;
		}

		public void setHash(String hash){
			this.hash = hash;
		}

		public String getHash(){
			return this.hash;
		}
	}

	private class DHTNodesComparator implements Comparator<DHTNode> {
		@Override
		public int compare(DHTNode dhtNode1, DHTNode dhtNode2) {
			return dhtNode1.getHash().compareTo(dhtNode2.getHash());
		}

	}
}