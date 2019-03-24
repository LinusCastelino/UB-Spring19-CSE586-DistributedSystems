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
    private String selfPort = null;
    private String predecessorPort = null;
    private String sucessorPort = null;
    private String selfId = null;
    private String predecessorId = null;
    private String successorId = null;

    private final String LOCAL_PAIRS_QUERY = "@";
    private final String ALL_PAIRS_QUERY = "*";

    private final String MSG_DELIMETER = ":";
    private final String DELETE_TAG = "D";
    private final String INSERT_TAG = "I";
    private final String QUERY_TAG = "Q";
    private final String JOIN_TAG = "J";

    private boolean lastPartition = false;

    private final SimpleDHTHelper dhtHelper = new SimpleDHTHelper(this.getContext());

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "Delete - " + selection);

        int deletedRows = 0;
        if(selection.equals(ALL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);

            // send msg to other avds to delete from their local partitions
            String msgToSend = DELETE_TAG + MSG_DELIMETER + selfPort;
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);
        }
        else{
            deletedRows = dhtHelper.delete(selection);
        }

        return deletedRows; // returns the number of rows deleted in the local partition
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v(TAG, "Insert - " + values.toString());

        dhtHelper.insert(values);

        return uri;
    }

    @Override
    public boolean onCreate() {

        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        selfPort = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

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

        if(selfPort.equals(NODE_0)){

        }
        else{
            // new node other than "5554" joining the chord
            String msgToSend = JOIN_TAG + MSG_DELIMETER + selfPort;
            sendMessage(NODE_0, msgToSend);    // send a new join request to NODE_0
        }

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.v(TAG, "Query - " + selection);

        String msgToSend = null;
        if(selection.equals(ALL_PAIRS_QUERY)){
            Cursor retCursor = dhtHelper.query(LOCAL_PAIRS_QUERY);

            msgToSend = QUERY_TAG + MSG_DELIMETER + selfPort + MSG_DELIMETER + ALL_PAIRS_QUERY;
            sendMessage(sucessorPort, msgToSend);

            return retCursor;
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            return dhtHelper.query(LOCAL_PAIRS_QUERY);
        }
        else{
            String hashedQuery = null;
            try{
                hashedQuery = genHash(selection);
            }
            catch (NoSuchAlgorithmException nsae){
                Log.e(TAG, "Exception occurred while generated hash of qery key");
                nsae.printStackTrace();
            }

            if(belongsToSefPartition(hashedQuery)){
                return dhtHelper.query(selection);
            }
            else{
                // query does not belong to current partition, send request to successor
                return null;
            }
        }

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
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

    public void sendMessage(String destination, String msgToSend){

    }    //sendMessage

    public boolean belongsToSefPartition(String hashedKey){
        if(predecessorId.compareTo(hashedKey) < 0 && hashedKey.compareTo(selfId) <= 0)
            return true;
        if(predecessorId.compareTo(selfId) > 0    // last partition check
            && predecessorId.compareTo(hashedKey) < 0)
            return true;

        return false;
    }

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

                        String msgArr[] = inputMsg.split(MSG_DELIMETER);
                        String msgTag = msgArr[0];
                        String msgSrc = msgArr[1];
                        String msg = msgArr[2];

                        if(msgTag.equals(JOIN_TAG)){

                        }
                        else if(msgTag.equals(INSERT_TAG)){

                        }
                        else if(msgTag.equals(QUERY_TAG)){
                            if(!msgSrc.equals(selfPort)){
                                if(msg.equals(ALL_PAIRS_QUERY)){
                                    query(null, null, LOCAL_PAIRS_QUERY, null, null);
                                    sendMessage(sucessorPort, inputMsg);

                                    // TODO : send search result back to source
                                }
                                else{
                                    Cursor cursor = query(null, null, msg, null, null);

                                    if(cursor == null){
                                        // does not belong to current partition
                                        // send request to successor
                                        sendMessage(sucessorPort, inputMsg);

                                    }
                                }
                            }
                        }
                        else if(msgTag.equals(DELETE_TAG)){
                            if(!msgSrc.equals(selfPort)){

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
        protected Void doInBackground(String... strings) {

            String msgToSend = strings[0];
            Integer port = Integer.valueOf(strings[1]);
            Socket socket = null;
            BufferedReader br = null;
            PrintWriter pw = null;

            try{
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), port);

                pw = new PrintWriter(socket.getOutputStream(), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                pw.println(msgToSend);
                Log.d(TAG, "ClientTask - \"" + msgToSend + "\" msg sent to "+ port);

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
    }
}
