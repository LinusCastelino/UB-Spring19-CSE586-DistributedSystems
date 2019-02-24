package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GroupMessengerHelper extends SQLiteOpenHelper {

    public static final Integer DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "GroupMessenger.db";

    public static final String GM_TABLE_NAME = "GM_MSGS_TABLE";
    public static final String KEY_COLUMN_NAME = "key";
    public static final String VALUE_COLUMN_NAME = "value";

    public static final String CREATE_TABLE_QUERY = "CREATE TABLE " + GM_TABLE_NAME
                                                    + "(" + KEY_COLUMN_NAME + " text PRIMARY KEY"+
                                                    VALUE_COLUMN_NAME + " text)";
    public static final String DROP_TABLE_NAME = "DROP TABLE IF EXISTS " + GM_TABLE_NAME;

    public GroupMessengerHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL(DROP_TABLE_NAME);
        onCreate(sqLiteDatabase);
    }

    public void insert(ContentValues contentValues){
        SQLiteDatabase db = this.getWritableDatabase();
        db.insertWithOnConflict(GM_TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor query(String key){
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = KEY_COLUMN_NAME + " = ?";

        return db.query(GM_TABLE_NAME, new String[]{KEY_COLUMN_NAME, VALUE_COLUMN_NAME}, selection,
                        new String[]{key}, null, null, null );
    }
}
