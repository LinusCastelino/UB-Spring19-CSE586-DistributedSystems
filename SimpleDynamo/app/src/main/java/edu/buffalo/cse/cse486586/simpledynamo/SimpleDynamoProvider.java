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
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDynamoProvider extends ContentProvider {
	private final String TAG = SimpleDynamoProvider.class.getSimpleName();

	private final Integer SERVER_PORT = 10000;
	private final String KEY = "key";
	private final String VALUE = "value";
	private final String MSG_DELIMETER = ":";
	private final String CV_DELIMETER = "/";
	private final String INSERT_TAG = "I";

	private String selfPort = null;
	private String selfId = null;


	private SimpleDynamoHelper dynamoHelper = null;

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
		return true;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.d(TAG, "Insert - " + values.toString());

		String insertKey = null;
		try{
			insertKey = (String)values.get(KEY);
			String keyHash = genHash(insertKey);
			DHTNode partitionCoordinator = getPartitionCoordinator(keyHash);
			if(partitionCoordinator.getHash().equals(selfId)){
				dynamoHelper.insert(values);

				// TODO : create replicas in next two successors
			}
			else{
				String msgToSend = INSERT_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + insertKey +
						CV_DELIMETER + values.get(VALUE);
				sendMessage(partitionCoordinator.getPort(), msgToSend);    //TODO successor port
			}

		}
		catch (NoSuchAlgorithmException nsae){
			Log.e(TAG, "Exception encountered while generating hash for key " + insertKey);
			nsae.printStackTrace();
		}

		return uri;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
						String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		return null;
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

	public DHTNode getPartitionCoordinator(String keyHash){

		return null; // TODO - Return coordinator node
	}

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

		@Override
		protected Void doInBackground(ServerSocket... serverSockets) {
			return null;
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
}
