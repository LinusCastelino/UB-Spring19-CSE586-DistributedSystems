package edu.buffalo.cse.cse486586.groupmessenger1;

import android.arch.persistence.db.SupportSQLiteStatement;
import android.arch.persistence.room.EntityInsertionAdapter;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.RoomSQLiteQuery;
import android.database.Cursor;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;

@SuppressWarnings("unchecked")
public class GroupMessengerDAO_Impl implements GroupMessengerDAO {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter __insertionAdapterOfGroupMessengerEntity;

  public GroupMessengerDAO_Impl(RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGroupMessengerEntity = new EntityInsertionAdapter<GroupMessengerEntity>(__db) {
      @Override
      public String createQuery() {
        return "INSERT OR REPLACE INTO `GM_Storage_Table`(`key`,`value`) VALUES (?,?)";
      }

      @Override
      public void bind(SupportSQLiteStatement stmt, GroupMessengerEntity value) {
        if (value.getKey() == null) {
          stmt.bindNull(1);
        } else {
          stmt.bindString(1, value.getKey());
        }
        if (value.getValue() == null) {
          stmt.bindNull(2);
        } else {
          stmt.bindString(2, value.getValue());
        }
      }
    };
  }

  @Override
  public void addEntity(GroupMessengerEntity entity) {
    __db.beginTransaction();
    try {
      __insertionAdapterOfGroupMessengerEntity.insert(entity);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public GroupMessengerEntity getEntityByKey(String searchKey) {
    final String _sql = "SELECT * FROM GM_Storage_Table WHERE [key] = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (searchKey == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchKey);
    }
    final Cursor _cursor = __db.query(_statement);
    try {
      final int _cursorIndexOfKey = _cursor.getColumnIndexOrThrow("key");
      final int _cursorIndexOfValue = _cursor.getColumnIndexOrThrow("value");
      final GroupMessengerEntity _result;
      if(_cursor.moveToFirst()) {
        final String _tmpKey;
        _tmpKey = _cursor.getString(_cursorIndexOfKey);
        final String _tmpValue;
        _tmpValue = _cursor.getString(_cursorIndexOfValue);
        _result = new GroupMessengerEntity(_tmpKey,_tmpValue);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }
}
