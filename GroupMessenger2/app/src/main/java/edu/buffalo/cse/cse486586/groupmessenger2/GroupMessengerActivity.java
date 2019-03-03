package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    private final String TAG = GroupMessengerActivity.class.getSimpleName();

    private final List<Integer> CLIENT_PORTS = new ArrayList<Integer>();
    private final Integer SERVER_PORT = 10000;
    private final Integer SO_TIMEOUT = 500;    // milliseconds
    private final String DECISION_TAG = "decision";

    private Integer selfPId;
    private Integer selfClientPort;

    private Map<String, Float> receivedMsgStorage;    // stores the received msgs generated by other AVDs
    private Map<String, List<Float>> sentMsgsStorage;    // stores the msg generated by this AVD and the suggested sequence numbers by all AVDs in the pool

    private Map<Float, String> deliveryQueue;    // stores the msgs waiting to be delviered along with their decided seq numbers

    private Integer cur_msg_seq;
    private Integer msgCounter;

    private final Uri CONTENT_PROVIDER_URI = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        receivedMsgStorage = new HashMap<String, Float>();
        sentMsgsStorage = new HashMap<String, List<Float>>();
        deliveryQueue = new TreeMap<Float, String>();

        CLIENT_PORTS.add(11108);
        CLIENT_PORTS.add(11112);
        CLIENT_PORTS.add(11116);
        CLIENT_PORTS.add(11120);
        CLIENT_PORTS.add(11124);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        selfClientPort = (Integer.parseInt(portStr) * 2);
        selfPId = (selfClientPort - 11108) / 4;

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        // Initializing msg counters
        msgCounter = 0;
        cur_msg_seq = 0;

        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch(IOException ioe){
            Log.e(TAG, "AVD" + selfPId + " : Error occurred while creating a server socket");
            ioe.printStackTrace();

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

                        synchronized (cur_msg_seq){
                            msgToSend = selfPId + ":" + msgToSend;
                            sentMsgsStorage.put(msgToSend, null);
                        }
                        sendMessage(msgToSend);
                    }
                }
        );
    }

    public void sendMessage(String msgToSend){
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
    }

    public void sendMessageWithAdditionalParam(String msgToSend, String additionalParam){
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, additionalParam);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {
            ServerSocket serverSocket = serverSockets[0];

            while(true){
                Socket socket = null;
                BufferedReader br = null;
                PrintWriter pw = null;

                String msgSrcID = null;
                try{
                    socket = serverSocket.accept();

                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String inputMsg = br.readLine();

                    if(inputMsg != null){
                        Log.d(TAG, "Server " + selfPId + " : Input msg received - " + inputMsg);
                        pw = new PrintWriter(socket.getOutputStream(), true);
                        pw.println(inputMsg);

                        int firstDelimeterIndex = inputMsg.indexOf(":");
                        msgSrcID = inputMsg.substring(0, firstDelimeterIndex);
                        String receivedMsg = inputMsg.substring(firstDelimeterIndex + 1);

                        int lastDelimeterIndex = inputMsg.lastIndexOf(":");

                        if( lastDelimeterIndex > firstDelimeterIndex){
                            // there is a sequence number appended with the message
                            receivedMsg = inputMsg.substring(firstDelimeterIndex+1, lastDelimeterIndex);
                            Float receivedMsgSeq = Float.parseFloat(inputMsg.substring( lastDelimeterIndex + 1));

                            if(Integer.parseInt(msgSrcID) == selfPId){
                                // the msg was sent by this AVD
                                processOwnMsg(receivedMsg, receivedMsgSeq);
                            }
                            else{
                                // msg not generated by this AVD
                                if(receivedMsgStorage.keySet().contains(msgSrcID + ":" + receivedMsg)){
                                    // decided sequence number for remote client's msg received
                                    receivedMsgStorage.remove(msgSrcID + ":" + receivedMsg);
                                    deliveryQueue.put(receivedMsgSeq, receivedMsg);
                                    checkForMsgDelivery();
                                }
                            }
                        }
                        else{
                            // Msg from a client received first time
                            // there is no sequence number associated with the message
                            // generate one and send it to the source
                            Float suggestedSeqNum;
                            synchronized (cur_msg_seq){
                                 suggestedSeqNum = Float.parseFloat((cur_msg_seq++) + "." +selfPId);
                            }
                            String msgToSend= inputMsg + ":" + suggestedSeqNum;

                            // Received msg storage map will only keep a track of msgs and corresponding seq #s of remote hosts
//                            if(!(Integer.parseInt(msgSrcID) == selfPId))
                            receivedMsgStorage.put(inputMsg, suggestedSeqNum);

                            Integer returnPort = (Integer.parseInt(msgSrcID) * 4) + 11108;
                            sendMessageWithAdditionalParam(msgToSend, returnPort+"");
                        }

                    }
                }
                catch(IOException ioe){
                    Log.e(TAG, "AVD" + selfPId + " : Exception encountered during socket operations");
                    if(msgSrcID != null)
                        updateDecisions(msgSrcID);
                    ioe.printStackTrace();
                }
                catch(Exception e){
                    Log.e(TAG, "AVD" + selfPId + " : General exception in ServerTask");
                    e.printStackTrace();
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
                        Log.e(TAG,"AVD" + selfPId + " : Server : Exception encountered while closing streams or socket");
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void onProgressUpdate(String ...receivedMsgs){
            String receivedMsg = receivedMsgs[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(receivedMsg + "\n");

            // Saving to database
            ContentValues contentToInsert = new ContentValues();
            contentToInsert.put("key", msgCounter++);
            contentToInsert.put("value", receivedMsg);
            Uri uri = getContentResolver().insert(CONTENT_PROVIDER_URI, contentToInsert);
        }

        public void processOwnMsg(String receivedMsg, Float receivedMsgSeq){
            List<Float> msgSeqNums = sentMsgsStorage.get(selfPId + ":" + receivedMsg);
            if(msgSeqNums == null)
            {
                msgSeqNums = new ArrayList<Float>();
                msgSeqNums.add(receivedMsgSeq);
                sentMsgsStorage.put(selfPId + ":" + receivedMsg, msgSeqNums);
            }
            else{
                // checked if the input msg was duplicated by network in transit
                if(!msgSeqNums.contains(receivedMsgSeq))
                    msgSeqNums.add(receivedMsgSeq);
            }

            if(msgSeqNums.size() >= CLIENT_PORTS.size()){
                // all AVDs suggested a possible sequence number for the msg generated by this AVD
                // time to decide the final sequence number and inform others in the network
                Float finalMsgSeqNum = findMax(msgSeqNums);

                sentMsgsStorage.remove(selfPId + ":" + receivedMsg);
                receivedMsgStorage.remove(selfPId + ":" + receivedMsg);
                deliveryQueue.put(finalMsgSeqNum, receivedMsg);

                String msgToSend = selfPId + ":" + receivedMsg + ":" + finalMsgSeqNum;
                sendMessageWithAdditionalParam(msgToSend, DECISION_TAG);

                checkForMsgDelivery();
            }
        }

        public void checkForMsgDelivery(){
            List<Float> deliveredMsgs = new ArrayList<Float>();
            System.out.println("checkForMsgDelivery - msgsCount" + deliveryQueue.entrySet().size());
            for(Map.Entry<Float, String> entry : deliveryQueue.entrySet())    // TreeMap.entrySet() returns keys in sorted order
            {
                if(isKeySmallerThanPendingDecisions(entry.getKey())){
                    deliveredMsgs.add(entry.getKey());
                    publishProgress(entry.getValue());
                }
                else{
                    // need to wait until all decisions having smaller seq number than current key are resolved
                    break;
                }
            }

            for(Float key : deliveredMsgs){
                Log.d(TAG, "Server " + selfPId + " : Delievered msg " + deliveryQueue.get(key));
                deliveryQueue.remove(key);
            }
        }

        public boolean isKeySmallerThanPendingDecisions(Float key){
            Collection<Float> pendingDecisions = receivedMsgStorage.values();
            for(Float pendingDecision : pendingDecisions){
                if(pendingDecision < key)
                    return false;
            }

            return true;
        }
    }    //ServerTask

    private class ClientTask extends AsyncTask<String, Void, Void>{

        @Override
        protected Void doInBackground(String... msgs) {
            String msgToSend = msgs[0];

            List<Integer> remoteList = null;

            if(msgs.length == 2){
                if(msgs[1].equals(DECISION_TAG)) {
                    remoteList = new ArrayList<Integer>();
                    remoteList.addAll(CLIENT_PORTS);
                    remoteList.remove(Integer.valueOf(selfClientPort));
                }
                else {
                    // response to an AVD with suggested sequence number
                    remoteList = new ArrayList<Integer>();
                    remoteList.add(Integer.parseInt(msgs[1]));
                }
            }
            else{
                // send msg to all AVDs
                remoteList = new ArrayList<Integer>();    // created a new array because of concurrent update exceptions
                remoteList.addAll(CLIENT_PORTS);
            }

            Socket socket = null;
            PrintWriter pw = null;
            BufferedReader br= null;

            List<Integer> portDeletionList = new ArrayList<Integer>();

            for(Integer remotePort : remoteList){
                try{
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    socket.setSoTimeout(SO_TIMEOUT);

                    pw = new PrintWriter(socket.getOutputStream(), true);
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    pw.println(msgToSend);
                    Log.d(TAG, "Client " + selfPId + " : Msg Sent to " + remotePort + "- " + msgToSend);

                    while(true){
                        if(br.readLine().equals(msgToSend))
                            break;
                    }
                }
                catch(UnknownHostException uhe){
                    Log.e(TAG, "AVD" + selfPId + " : Error encountered while connecting to remote socket");
                    Log.i(TAG, "AVD" + selfPId + " : Excluding device at port " + remotePort + " from recipient list" );
//                    CLIENT_PORTS.remove(remotePort);
                    portDeletionList.add(remotePort);
                    uhe.printStackTrace();
                }
                catch (SocketTimeoutException ste){
                    Log.e(TAG, "AVD" + selfPId + " : Socket timeout occurred at remote port - " + remotePort);
                    Log.i(TAG, "AVD" + selfPId + " : Excluding device at port " + remotePort + " from recipient list" );
//                    CLIENT_PORTS.remove(remotePort);
                    portDeletionList.add(remotePort);
                    ste.printStackTrace();
                }
                catch (IOException ioe){
                    Log.e(TAG, "AVD" + selfPId + " : Exception encountered while creating a socket");
                    Log.i(TAG, "AVD" + selfPId + " : Excluding device at port " + remotePort + " from recipient list" );
//                    CLIENT_PORTS.remove(remotePort);
                    portDeletionList.add(remotePort);
                    ioe.printStackTrace();
                }
                catch(Exception e){
                    Log.e(TAG, "AVD" + selfPId + " : General exception in ClientTask");
                    Log.i(TAG, "AVD" + selfPId + " : Excluding device at port " + remotePort + " from recipient list" );
//                    CLIENT_PORTS.remove(remotePort);
                    portDeletionList.add(remotePort);
                    e.printStackTrace();
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
                        Log.e(TAG, "AVD" + selfPId + " : Exception thrown while closing streams and socket");
                        Log.e(TAG, ioe.getMessage());
                        ioe.printStackTrace();
                    }
                }
            }

            if(portDeletionList.size() > 0) {
                updateClientPorts(portDeletionList);
            }

            return null;
        }
    }

    public void updateClientPorts(List<Integer> portDeletionList){
        // remove all pending msgs from the deleted port waiting for final decisions
        synchronized (CLIENT_PORTS) {
            for (Integer portToDelete : portDeletionList) {
                CLIENT_PORTS.remove(portToDelete);
                updateDecisions(((portToDelete - 11108) / 4) + "");
            }
        }

        //check if exisiting sent msgs satisfy for delivery
        for(Map.Entry<String, List<Float>> entry : sentMsgsStorage.entrySet()){
            List<Float> msgSeqNums = entry.getValue();
            if(msgSeqNums != null && msgSeqNums.size() >= CLIENT_PORTS.size()){
                Float finalMsgSeqNum = findMax(msgSeqNums);

                sentMsgsStorage.remove(entry.getKey());
                receivedMsgStorage.remove(entry.getKey());
                deliveryQueue.put(finalMsgSeqNum, entry.getKey().substring(entry.getKey().indexOf(":") + 1));

                String msgToSend = entry.getKey() + ":" + finalMsgSeqNum;
                sendMessageWithAdditionalParam(msgToSend, DECISION_TAG);
            }

        }
    }

    public Float findMax(List<Float> msgSeqNums){
        Float max = -1.0f;
        for(Float seq : msgSeqNums) {
            if (max < seq)
                max = seq;
        }
        return max;
    }    // findMax()

    public void updateDecisions(String pId){
        List<String> entriesToDelete = new ArrayList<String>();

        for(String key : receivedMsgStorage.keySet()){
            if(key.startsWith(pId + ":"))
                entriesToDelete.add(key);
        }

        for(String key : entriesToDelete)
            receivedMsgStorage.remove(key);
    }    //updatePendingDecisions


}    //GroupMessengerActivity
