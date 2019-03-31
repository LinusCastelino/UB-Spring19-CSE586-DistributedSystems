package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

public class SimpleDhtProvider extends ContentProvider {
    private final String TAG = SimpleDhtProvider.class.getSimpleName();
    private final Integer SERVER_PORT = 10000;

    private final String NODE_0 = "5554";
    private final String KEY = "key";
    private final String VALUE = "value";

    private final String LOCAL_PAIRS_QUERY = "@";
    private final String ALL_PAIRS_QUERY = "*";

    private final String MSG_DELIMETER = ":";
    private final String CV_DELIMETER = "/";
    private final String DELETE_TAG = "D";
    private final String INSERT_TAG = "I";
    private final String QUERY_TAG = "Q";
    private final String JOIN_TAG = "J";
    private final String SUCCESSOR_UPDATE_TAG = "S";
    private final String PREDECESSOR_UPDATE_TAG = "P";

    private String selfPort = null;
    private String selfId = null;
    private String successorPort = null;
    private String predecessorPort = null;
    private String predecessorId = null;

    private SimpleDHTHelper dhtHelper;

    @Override
    public boolean onCreate() {
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        selfPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

        dhtHelper = new SimpleDHTHelper(this.getContext());

        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException ioe){
            Log.e(TAG, "Error encountered while creating server socket");
            Log.e(TAG, ioe.getMessage());
            return false;
        }

        try {
            selfId = genHash(selfPort);
        }
        catch (NoSuchAlgorithmException nsae){
            Log.e(TAG, "Exception encountered while generating selfId");
            nsae.printStackTrace();
        }

        if(!selfPort.equals(NODE_0)){
            // new node other than "5554" joining the chord
            String msgToSend = JOIN_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selfPort;
            sendMessage(convertToPort(NODE_0), msgToSend);    // send a new join request to NODE_0
        }

        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v(TAG, "AVD " + selfPort + " : Insert - " + values.toString());

        String key = (String)values.get(KEY);

        if(belongsToSelfPartition(key)){
            dhtHelper.insert(values);
        }
        else{
            String msgToSend = INSERT_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + values.get(KEY) + CV_DELIMETER + values.get(VALUE);
            sendMessage(successorPort, msgToSend);
        }

        return uri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.v(TAG, "AVD " + selfPort + " : Query - " + selection);

        String msgToSend = null;
        if(selection.equals(ALL_PAIRS_QUERY)){
            Cursor retCursor = dhtHelper.query(LOCAL_PAIRS_QUERY);

            msgToSend = QUERY_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + ALL_PAIRS_QUERY;
            sendMessage(successorPort, msgToSend);

            return retCursor;
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            return dhtHelper.query(LOCAL_PAIRS_QUERY);
        }
        else{
            if(belongsToSelfPartition(selection)){
                return dhtHelper.query(selection);
            }
            else{
                // query does not belong to current partition, send request to successor
                msgToSend = QUERY_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selection;
                sendMessage(successorPort, msgToSend);
            }
        }

        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "AVD " + selfPort + " Delete - " + selection);

        int deletedRows = 0;
        String msgToSend = null;
        if(selection.equals(ALL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);

            msgToSend = DELETE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + ALL_PAIRS_QUERY;
            sendMessage(successorPort, msgToSend);

            return deletedRows;
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);
        }
        else{
            if(belongsToSelfPartition(selection)){
                return dhtHelper.delete(selection);
            }
            else{
                // query does not belong to current partition, send request to successor
                msgToSend = DELETE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selection;
                sendMessage(successorPort, msgToSend);
            }
        }
        return deletedRows; // returns the number of rows deleted in the local partition
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public String convertToPort(String avdID){
        return (Integer.parseInt(avdID) *2) + "";
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

    public void sendMessage(String destination, String message){
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, destination, message);
    }    //sendMessage()

    public boolean belongsToSelfPartition(String key){

        if(predecessorId == null && successorPort == null){
            return true;
        }

        String hashedKey = null;
        try{
            hashedKey = genHash(key);
        }
        catch (NoSuchAlgorithmException nsae){
            Log.e(TAG, "Exception occurred while generated hash of query key");
            nsae.printStackTrace();
        }

//        Log.v(TAG, "predecessor id - " + predecessorId + " || hashed key - " + hashedKey + " || selfId - " + selfId);

        if(hashedKey != null) {
            if (predecessorId.compareTo(hashedKey) < 0 && hashedKey.compareTo(selfId) <= 0)
                return true;
            if (predecessorId.compareTo(selfId) > 0    // last partition check
                    && predecessorId.compareTo(hashedKey) < 0 || hashedKey.compareTo(selfId) <= 0)
                return true;
        }

        return false;
    }    //belongsToSelfPartition()

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];

            while(true){
                Socket socket = null;
                BufferedReader br = null;
                PrintWriter pw = null;
                try{
                    socket = serverSocket.accept();
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    String inputMsg = br.readLine();
                    if(inputMsg != null){
                        Log.d(TAG, "ServerTask - Input msg received : " + inputMsg);
                        pw = new PrintWriter( socket.getOutputStream(), true);
                        pw.println(inputMsg);

                        String msgArr[] = inputMsg.split(MSG_DELIMETER);
                        String msgTag = msgArr[0];
                        String msgSrc = msgArr[1];
                        String msg = msgArr[2];

                        if(!msgSrc.equals(selfPort)){
                            // stopping condition if the msg goes around the chord and comes back
                            if(msgTag.equals(JOIN_TAG)){
                                String succUpdateMsg = null;
                                if(predecessorId == null && successorPort == null){
                                    // first node join
                                    Log.d(TAG, "AVD " + selfPort + " : First node join request received from " + msg);
                                    successorPort = convertToPort(msg);

                                    succUpdateMsg = SUCCESSOR_UPDATE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selfPort;
                                    sendMessage(successorPort, succUpdateMsg);

                                    String predUpdateMsg = PREDECESSOR_UPDATE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selfPort;
                                    sendMessage(successorPort, predUpdateMsg);
                                }
                                else {
                                    if (belongsToSelfPartition(msg)) {
                                        Log.d(TAG, "AVD " + selfPort + " : Node join request received from "
                                                + msg + ". Belongs to current partition");

                                        // update the successor of the current node's predecessor to be the newly joined node
                                        succUpdateMsg = SUCCESSOR_UPDATE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + msg;
                                        sendMessage(predecessorPort, succUpdateMsg);

                                        // udpate the successor of the newly joined node to be the current node
                                        succUpdateMsg = SUCCESSOR_UPDATE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selfPort;
                                        sendMessage(convertToPort(msg), succUpdateMsg);

                                        // update current node's predecessor
                                        predecessorId = genHash(msg);
                                        predecessorPort = convertToPort(msg);
                                    } else {
                                        Log.d(TAG, "AVD " + selfPort + " : Node join request received from " + msg
                                                + ". Does not belong to current partition. Forwarded request to " + successorPort);
                                        sendMessage(successorPort, inputMsg);
                                    }
                                }
                            }
                            else if(msgTag.equals(SUCCESSOR_UPDATE_TAG)){
                                successorPort = convertToPort(msg);

                                Log.d(TAG, "AVD " + selfPort + " : New successor - " + successorPort);

                                // inform the successor that this node is its predecessor
                                String predUpdateMsg = PREDECESSOR_UPDATE_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + selfPort;
                                sendMessage(successorPort, predUpdateMsg);
                            }
                            else if(msgTag.equals(PREDECESSOR_UPDATE_TAG)){
                                // update current node's predecessor
                                predecessorId = genHash(msg);
                                predecessorPort = convertToPort(msg);

                                Log.d(TAG, "AVD " + selfPort + " : New predecessor - " + predecessorPort);
                            }
                            else if(msgTag.equals(INSERT_TAG)){
                                String[] record = msg.split(CV_DELIMETER);

                                if(belongsToSelfPartition(record[0])){
                                    ContentValues contentValues = new ContentValues();
                                    contentValues.put(KEY, record[0]);
                                    contentValues.put(VALUE, record[1]);
                                    insert(null, contentValues);
                                }
                                else
                                    sendMessage(successorPort, inputMsg);
                            }
                            else if(msgTag.equals(QUERY_TAG)){
                                if(msg.equals(ALL_PAIRS_QUERY)){
                                    query(null, null, LOCAL_PAIRS_QUERY, null, null);
                                    sendMessage(successorPort, inputMsg);

                                    // TODO : send search result back to source
                                }
                                else{
                                    if(belongsToSelfPartition(msg)) {
                                        Cursor cursor = query(null, null, msg, null, null);
                                    }
                                    else{
                                        sendMessage(successorPort, inputMsg);
                                    }

                                }
                            }
                            else if(msgTag.equals(DELETE_TAG)){
                                if(msg.equals(ALL_PAIRS_QUERY)){
                                    delete(null, LOCAL_PAIRS_QUERY, null);
                                    sendMessage(successorPort, inputMsg);

                                    // TODO : send search result back to source
                                }
                                else{
                                    if(belongsToSelfPartition(msg))
                                        delete(null, msg, null);
                                    else
                                        sendMessage(successorPort, inputMsg);
                                }
                            }
                        }
                    }
                }
                catch(Exception e){
                    Log.e(TAG, "Exception occured in ServerTask");
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

            }

        }    //doInBackground()
    }    //ServerTask

    private class ClientTask extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... msgs) {

            String port = (String)msgs[0];
            String msgToSend = msgs[1];

            Socket socket = null;
            BufferedReader br = null;
            PrintWriter pw = null;

            try{
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.valueOf(port));

                pw = new PrintWriter(socket.getOutputStream(), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                pw.println(msgToSend);
                Log.d(TAG, "AVD " + selfPort + " : ClientTask - \"" + msgToSend + "\" msg sent to "+ port);

                while(true){
                    if(br.readLine().equals(msgToSend))
                        break;
                }

            }
            catch(Exception e){
                Log.d(TAG, "Exception encountered in ClientTask");
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
    }    //ClientTask
}    //SimpleDhtProvider
