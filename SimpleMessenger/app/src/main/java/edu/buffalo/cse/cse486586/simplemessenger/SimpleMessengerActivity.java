package edu.buffalo.cse.cse486586.simplemessenger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import edu.buffalo.cse.cse486586.simplemessenger.R;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.TextView;

/**
 * SimpleMessengerActivity creates an Activity (i.e., a screen) that has an input box and a display
 * box. This is almost like main() for a typical C or Java program.
 * 
 * Please read http://developer.android.com/training/basics/activity-lifecycle/index.html first
 * to understand what an Activity is.
 * 
 * Please also take look at how this Activity is declared as the main Activity in
 * AndroidManifest.xml file in the root of the project directory (that is, using an intent filter).
 * 
 * @author stevko
 *
 */
public class SimpleMessengerActivity extends Activity {
    static final String TAG = SimpleMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final int SERVER_PORT = 10000;

    /** Called when the Activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /*
         * Allow this Activity to use a layout file that defines what UI elements to use.
         * Please take a look at res/layout/main.xml to see how the UI elements are defined.
         * 
         * R is an automatically generated class that contains pointers to statically declared
         * "resources" such as UI elements and strings. For example, R.layout.main refers to the
         * entire UI screen declared in res/layout/main.xml file. You can find other examples of R
         * class variables below.
         */
        setContentView(R.layout.main);
        
        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             * 
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             * 
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * Retrieve a pointer to the input box (EditText) defined in the layout
         * XML file (res/layout/main.xml).
         * 
         * This is another example of R class variables. R.id.edit_text refers to the EditText UI
         * element declared in res/layout/main.xml. The id of "edit_text" is given in that file by
         * the use of "android:id="@+id/edit_text""
         */
        final EditText editText = (EditText) findViewById(R.id.edit_text);
        
        /*
         * Register an OnKeyListener for the input box. OnKeyListener is an event handler that
         * processes each key event. The purpose of the following code is to detect an enter key
         * press event, and create a client thread so that the client thread can send the string
         * in the input box over the network.
         */
        editText.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    /*
                     * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                     * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
                     * an AsyncTask that sends the string to the remote AVD.
                     */
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); // This is one way to reset the input box.
                    TextView localTextView = (TextView) findViewById(R.id.local_text_display);
                    localTextView.append("\t" + msg); // This is one way to display a string.
                    TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
                    remoteTextView.append("\n");

                    /*
                     * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                     * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                     * the difference, please take a look at
                     * http://developer.android.com/reference/android/os/AsyncTask.html
                     */
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    return true;
                }
                return false;
            }
        });
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     * 
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     * 
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

            while(true){
                Socket socket = null;
                BufferedReader br = null;
                PrintWriter pw = null;
                try{
                    socket = serverSocket.accept();
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String inputMsg = br.readLine();
                    if(inputMsg != null){
                        Log.d(TAG, "Server : Input msg received - " + inputMsg);
                        pw = new PrintWriter(socket.getOutputStream(), true);
                        pw.println(inputMsg);
                        publishProgress(inputMsg);
                    }
                    else{
                        Log.d(TAG, "Received a null message");
                    }

                }
                catch(Exception e){
                    Log.e(TAG, "Exception encountered while accepting socket input");
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
                    catch(Exception e){
                        Log.e(TAG,"Exception encountered while closing server socket");
                    }

                }
            }


//            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.local_text_display);
            localTextView.append("\n");
            
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             * 
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            
            String filename = "SimpleMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;

            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     * 
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String remotePort = REMOTE_PORT0;
                if (msgs[1].equals(REMOTE_PORT0))
                    remotePort = REMOTE_PORT1;


                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                
                String msgToSend = msgs[0];
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                pw.println(msgToSend);
                Log.d(TAG, "Client: Sent msg - " + msgToSend);

                //waiting until server sends an acknowledgement
                while(true){
                    if(br.readLine().equals(msgToSend))
                        break;
                }

                pw.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            catch(Exception e){
                Log.e(TAG, "ClientTask socket general Exception");
            }

            return null;
        }
    }
}