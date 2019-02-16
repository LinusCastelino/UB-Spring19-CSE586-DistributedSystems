package edu.buffalo.cse.cse486586.groupmessenger1;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Callback;
import android.arch.persistence.db.SupportSQLiteOpenHelper.Configuration;
import android.arch.persistence.room.DatabaseConfiguration;
import android.arch.persistence.room.InvalidationTracker;
import android.arch.persistence.room.RoomOpenHelper;
import android.arch.persistence.room.RoomOpenHelper.Delegate;
import android.arch.persistence.room.util.TableInfo;
import android.arch.persistence.room.util.TableInfo.Column;
import android.arch.persistence.room.util.TableInfo.ForeignKey;
import android.arch.persistence.room.util.TableInfo.Index;
import java.lang.IllegalStateException;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.HashMap;
import java.util.HashSet;

@SuppressWarnings("unchecked")
public class GroupMessengerDB_Impl extends GroupMessengerDB {
  private volatile GroupMessengerDAO _groupMessengerDAO;

  @Override
  protected SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration configuration) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(configuration, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(SupportSQLiteDatabase _db) {
        _db.execSQL("CREATE TABLE IF NOT EXISTS `GM_Storage_Table` (`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`))");
        _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, \"284e3505879cb5fa2a9db9a48d317f81\")");
      }

      @Override
      public void dropAllTables(SupportSQLiteDatabase _db) {
        _db.execSQL("DROP TABLE IF EXISTS `GM_Storage_Table`");
      }

      @Override
      protected void onCreate(SupportSQLiteDatabase _db) {
        if (mCallbacks != null) {
          for (int _i = 0, _size = mCallbacks.size(); _i < _size; _i++) {
            mCallbacks.get(_i).onCreate(_db);
          }
        }
      }

      @Override
      public void onOpen(SupportSQLiteDatabase _db) {
        mDatabase = _db;
        internalInitInvalidationTracker(_db);
        if (mCallbacks != null) {
          for (int _i = 0, _size = mCallbacks.size(); _i < _size; _i++) {
            mCallbacks.get(_i).onOpen(_db);
          }
        }
      }

      @Override
      protected void validateMigration(SupportSQLiteDatabase _db) {
        final HashMap<String, TableInfo.Column> _columnsGMStorageTable = new HashMap<String, TableInfo.Column>(2);
        _columnsGMStorageTable.put("key", new TableInfo.Column("key", "TEXT", true, 1));
        _columnsGMStorageTable.put("value", new TableInfo.Column("value", "TEXT", false, 0));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGMStorageTable = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGMStorageTable = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoGMStorageTable = new TableInfo("GM_Storage_Table", _columnsGMStorageTable, _foreignKeysGMStorageTable, _indicesGMStorageTable);
        final TableInfo _existingGMStorageTable = TableInfo.read(_db, "GM_Storage_Table");
        if (! _infoGMStorageTable.equals(_existingGMStorageTable)) {
          throw new IllegalStateException("Migration didn't properly handle GM_Storage_Table(edu.buffalo.cse.cse486586.groupmessenger1.GroupMessengerEntity).\n"
                  + " Expected:\n" + _infoGMStorageTable + "\n"
                  + " Found:\n" + _existingGMStorageTable);
        }
      }
    }, "284e3505879cb5fa2a9db9a48d317f81", "ec17cc507a18c6adcc31888a64f7e391");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
        .name(configuration.name)
        .callback(_openCallback)
        .build();
    final SupportSQLiteOpenHelper _helper = configuration.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  protected InvalidationTracker createInvalidationTracker() {
    return new InvalidationTracker(this, "GM_Storage_Table");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `GM_Storage_Table`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  public GroupMessengerDAO groupMessengerDAO() {
    if (_groupMessengerDAO != null) {
      return _groupMessengerDAO;
    } else {
      synchronized(this) {
        if(_groupMessengerDAO == null) {
          _groupMessengerDAO = new GroupMessengerDAO_Impl(this);
        }
        return _groupMessengerDAO;
      }
    }
  }
}
