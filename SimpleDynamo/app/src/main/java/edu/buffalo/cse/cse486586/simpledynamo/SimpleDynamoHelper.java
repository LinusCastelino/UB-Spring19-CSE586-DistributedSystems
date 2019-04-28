package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SimpleDynamoHelper extends SQLiteOpenHelper {

    private static final Integer DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SimpleDynamo.db";

    private final String TABLE_NAME = "DYNAMO_TABLE";
    private final String KEY_COLUMN_NAME = "key";
    private final String VALUE_COLUMN_NAME = "value";
    private final String VERSION_COLUMN_NAME = "version";

    private final String CREATE_TABLE_QUERY = "CREATE TABLE " + TABLE_NAME + "( " +
                                               KEY_COLUMN_NAME + " TEXT PRIMARY KEY, " +
                                               VALUE_COLUMN_NAME + " TEXT, " +
                                               VERSION_COLUMN_NAME + " INTEGER)";
    private final String DROP_TABLE_QUERY = "DROP TABLE IF EXISTS " + TABLE_NAME;

    private final String LOCAL_PAIRS_QUERY = "@";
    private final String WHERE_CLAUSE = KEY_COLUMN_NAME + " = ?";
    private final String[] RESULT_COLUMNS = new String[]{KEY_COLUMN_NAME, VALUE_COLUMN_NAME, VERSION_COLUMN_NAME};

    public SimpleDynamoHelper(Context context){
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
    }

    public int delete(String deleteKey){
        SQLiteDatabase db = this.getWritableDatabase();

        if(deleteKey.equals(LOCAL_PAIRS_QUERY))
            return db.delete(TABLE_NAME, null, null);
        else
            return db.delete(TABLE_NAME, WHERE_CLAUSE, new String[]{deleteKey});
    }

    public Cursor query(String queryKey){
        SQLiteDatabase db = this.getReadableDatabase();

        if(queryKey.equals(LOCAL_PAIRS_QUERY))
            return db.query(TABLE_NAME, RESULT_COLUMNS, null,
                    null, null, null, null);
        else
            return db.query(TABLE_NAME, RESULT_COLUMNS, WHERE_CLAUSE, new String[]{queryKey},
                            null, null, null);
    }
}
