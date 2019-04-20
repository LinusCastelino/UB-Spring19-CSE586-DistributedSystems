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

	private String selfPort = null;

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
		// TODO Auto-generated method stub
		return null;
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

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

		@Override
		protected Void doInBackground(ServerSocket... serverSockets) {
			return null;
		}
	}

	private class ClientTask extends AsyncTask<String, Void, Void>{

		@Override
		protected Void doInBackground(String... strings) {
			String port = null;				// #########################3
			String msgToSend = null;      // ###########################3

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

			}

			return null;
		}
	}
}
