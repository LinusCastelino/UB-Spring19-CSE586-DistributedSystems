package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SimpleDHTHelper extends SQLiteOpenHelper {

    private static final Integer DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SimpleDHT.db";

    private static final String TABLE_NAME = "DHT_TABLE";
    private static final String KEY_COLUMN_NAME = "key";
    private static final String VALUE_COLUMN_NAME = "value";

    private static final String CREATE_TABLE_QUERY = "CREATE TABLE " + TABLE_NAME +
                                                     " (" + KEY_COLUMN_NAME + " text PRIMARY KEY, " +
                                                     VALUE_COLUMN_NAME + " text)";
    private static final String DROP_TABLE_QUERY = "DROP TABLE IF EXISTS " + TABLE_NAME;

    private static final String LOCAL_PAIRS_QUERY = "@";

    public SimpleDHTHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE_QUERY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL(DROP_TABLE_QUERY);
        onCreate(sqLiteDatabase);
    }

    public void insert(ContentValues contentValues){
        SQLiteDatabase db = this.getWritableDatabase();
        db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }    //insert()

    public Cursor query(String queryKey){
        SQLiteDatabase db = this.getReadableDatabase();

        if(queryKey.equals(LOCAL_PAIRS_QUERY)){
            return db.query(TABLE_NAME, new String[]{KEY_COLUMN_NAME, VALUE_COLUMN_NAME}, null,
                    null, null, null, null);
        }
        else{
            String selection = KEY_COLUMN_NAME + " = ?";

            return db.query(TABLE_NAME, new String[]{KEY_COLUMN_NAME, VALUE_COLUMN_NAME}, selection,
                    new String[]{queryKey}, null, null, null);
        }
    }    //query()

    public int delete(String deleteKey){
        SQLiteDatabase db = this.getWritableDatabase();

        if(deleteKey.equals(LOCAL_PAIRS_QUERY)){
            return db.delete(TABLE_NAME, null, null);
        }
        else{
            String whereClause = KEY_COLUMN_NAME + " = ?";
            return db.delete(TABLE_NAME, whereClause, new String[]{deleteKey});
        }
    }    //delete()

}    //SimpleDHTHelper
