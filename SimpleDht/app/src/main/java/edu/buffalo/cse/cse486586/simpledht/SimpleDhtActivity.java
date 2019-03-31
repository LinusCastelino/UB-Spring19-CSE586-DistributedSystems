package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SimpleDhtActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));

        final Uri CONTENT_PROVIDER_URI = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider");
        final String LOCAL_PAIRS_QUERY = "@";
        final String ALL_PAIRS_QUERY = "*";

        Button lDump = (Button)findViewById(R.id.button1);
        Button gDump = (Button)findViewById(R.id.button2);

        lDump.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Cursor cursor = getContentResolver().query(CONTENT_PROVIDER_URI, null,
                                                LOCAL_PAIRS_QUERY, null, null);
                        displayOnAVD(tv, cursor);
                    }
                }
        );

        gDump.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Cursor cursor = getContentResolver().query(CONTENT_PROVIDER_URI, null,
                                ALL_PAIRS_QUERY, null, null);
                        displayOnAVD(tv, cursor);
                    }
                }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

    public void displayOnAVD(TextView tv, Cursor cursor){
        tv.setText("");

        String cursorToStr = "";
        final String KEY_COLUMN_NAME = "key";
        final String VALUE_COLUMN_NAME = "value";

        int keyColumnIndex = cursor.getColumnIndex(KEY_COLUMN_NAME);
        int valueColumnIndex = cursor.getColumnIndex(VALUE_COLUMN_NAME);

        if(cursor.moveToFirst()) {
            do {
                cursorToStr += cursor.getString(keyColumnIndex);
                cursorToStr += ":";
                cursorToStr += cursor.getString(valueColumnIndex);
                cursorToStr += "\n";
            } while (cursor.moveToNext());
        }

        tv.setText(cursorToStr);
    }    //displayOnAVD()

}
