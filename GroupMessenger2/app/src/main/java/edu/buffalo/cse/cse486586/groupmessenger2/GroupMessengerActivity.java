package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.Buffer;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    private static final String TAG = GroupMessengerActivity.class.getSimpleName();

    private static final Integer[] REMOTE_PORTS = new Integer[]{11108, 11112, 11116, 11120, 11124};
    private static final Integer SERVER_PORT = 10000;
    public static  final Integer SO_TIMEOUT = 500;    // milliseconds

    private Integer msgCounter = 0;

    private static final Uri CONTENT_PROVIDER_URI = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch(IOException ioe){
            Log.e(TAG, "Error occurred while creating a server socket");
            Log.e(TAG, ioe.getMessage());

        }

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        EditText editText = (EditText) findViewById(R.id.editText1);
                        String msgToSend = editText.getText().toString();
                        editText.setText("");

                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
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
                        Log.d(TAG, "Server : Input msg received : " + inputMsg);
                        pw = new PrintWriter(socket.getOutputStream(), true);
                        pw.println(inputMsg);
                        publishProgress(inputMsg);
                    }
                }
                catch(IOException ioe){
                    Log.e(TAG, "Exception encountered during socket operations");
                    Log.e(TAG, ioe.getMessage());
                }
                catch(Exception e){
                    Log.e(TAG, "General exception in ServerTask");
                    Log.e(TAG, e.getMessage());
                }
                finally{
                    try{
                        if(br != null)
                            br.close();
                        if(pw != null)
                            pw.close();
                        if(socket != null)
                            socket.close();
                    }
                    catch(Exception e){
                        Log.e(TAG,"Server : Exception encountered while closing streams or socket");
                        Log.e(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onProgressUpdate(String ...receivedMsgs){
            String receivedMsg = receivedMsgs[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(receivedMsg + "\n\n");

            //saving to database
            ContentValues contentToInsert = new ContentValues();
            contentToInsert.put("key", msgCounter++);
            contentToInsert.put("value", receivedMsg);
            Uri uri = getContentResolver().insert(CONTENT_PROVIDER_URI, contentToInsert);
        }
    }    //ServerTask

    private class ClientTask extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];
            Socket socket = null;
            PrintWriter pw = null;
            BufferedReader br= null;

            for(Integer remotePort : REMOTE_PORTS){
                try{
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    socket.setSoTimeout(SO_TIMEOUT);

                    pw = new PrintWriter(socket.getOutputStream(), true);
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    pw.println(msgToSend);
                    Log.d(TAG, "Client: Msg Sent - " + msgToSend);

                    while(true){
                        if(br.readLine().equals(msgToSend))
                            break;
                    }
                }
                catch(UnknownHostException uhe){
                    Log.e(TAG, "Error encountered while connecting to remote socket");
                    Log.e(TAG, uhe.getMessage());
                }
                catch (SocketTimeoutException ste){
                    Log.e(TAG, "Socket timeout occurred at remote port - " + remotePort);
                    Log.e(TAG, ste.getMessage());
                }
                catch (IOException ioe){
                    Log.e(TAG, "Exception encountered while creating a socket");
                    Log.e(TAG, ioe.getMessage());
                }
                catch(Exception e){
                    Log.e(TAG, "General exception in ClientTask");
                    Log.e(TAG, e.getMessage());
                }
                finally{
                    try{
                        if(pw != null)
                            pw.close();
                        if(br != null)
                            br.close();
                        if(socket != null)
                            socket.close();
                    }
                    catch (IOException ioe){
                        Log.e(TAG, "Client : Exception thrown while closing streams and socket");
                        Log.e(TAG, ioe.getMessage());
                        ioe.printStackTrace();
                    }
                }
            }
            return null;
        }
    }
}    //GroupMessengerActivity
