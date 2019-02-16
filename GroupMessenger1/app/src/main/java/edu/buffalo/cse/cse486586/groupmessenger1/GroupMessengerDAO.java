package edu.buffalo.cse.cse486586.groupmessenger1;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

@Dao
public interface GroupMessengerDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addEntity(GroupMessengerEntity entity);

    // add [] because key is a reserved keyword
    @Query("SELECT * FROM GM_Storage_Table WHERE [key] = :searchKey")
    GroupMessengerEntity getEntityByKey(String searchKey);
}
