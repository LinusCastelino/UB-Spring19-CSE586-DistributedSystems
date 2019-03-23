package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.net.ServerSocket;
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
    private static final String TAG = SimpleDhtProvider.class.getSimpleName();
    private static final Integer SERVER_PORT = 10000;

    private static final String LOCAL_PAIRS_QUERY = "@";
    private static final String ALL_PAIRS_QUERY = "*";

    private String selfPort;

    private final SimpleDHTHelper dhtHelper = new SimpleDHTHelper(this.getContext());

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "Delete - " + selection);

        int deletedRows = 0;
        if(selection.equals(ALL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);
            // send msg to other avds to delete from their local partitions
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            deletedRows = dhtHelper.delete(LOCAL_PAIRS_QUERY);
        }
        else{
            deletedRows = dhtHelper.delete(selection);
        }

        return deletedRows;
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
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.v(TAG, "Query - " + selection);

        if(selection.equals(ALL_PAIRS_QUERY)){
            Cursor retCursor = dhtHelper.query(LOCAL_PAIRS_QUERY);
            return null;
        }
        else if(selection.equals(LOCAL_PAIRS_QUERY)){
            return dhtHelper.query(LOCAL_PAIRS_QUERY);
        }
        else{
            return dhtHelper.query(selection);
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


    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            return null;
        }
    }    //ServerTask

    private class ClientTask extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... strings) {
            return null;
        }
    }
}
