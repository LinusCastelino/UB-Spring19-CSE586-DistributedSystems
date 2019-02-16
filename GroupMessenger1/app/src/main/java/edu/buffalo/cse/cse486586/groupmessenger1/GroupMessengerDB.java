package edu.buffalo.cse.cse486586.groupmessenger1;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {GroupMessengerEntity.class}, version = 1)
public abstract class GroupMessengerDB extends RoomDatabase {

    public abstract GroupMessengerDAO groupMessengerDAO();

    private static GroupMessengerDB groupMessengerDBInstance;

    public static GroupMessengerDB getGroupMessengerDBInstance(final Context context){
        if(groupMessengerDBInstance == null){
            synchronized (GroupMessengerDB.class){
                if(groupMessengerDBInstance == null){
                    groupMessengerDBInstance = Room.databaseBuilder(context.getApplicationContext(), GroupMessengerDB.class,
                                        "GroupMessenger_Database").allowMainThreadQueries().build();
                }
            }
        }

        return groupMessengerDBInstance;
    }
}
