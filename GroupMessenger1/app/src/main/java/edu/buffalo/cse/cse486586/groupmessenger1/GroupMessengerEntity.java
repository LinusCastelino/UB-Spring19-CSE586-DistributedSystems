package edu.buffalo.cse.cse486586.groupmessenger1;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

@Entity(tableName = "GM_Storage_Table")
public class GroupMessengerEntity {

    @PrimaryKey
    @NonNull
    private String key;
    private String value;

    public GroupMessengerEntity(String key, String value){
        this.key = key;
        this.value = value;
    }

    public String getKey(){
        return this.key;
    }

    public void setKey(String key){
        this.key = key;
    }

    public String getValue(){
        return this.value;
    }

    public void setValue(String value){
        this.value = value;
    }

}
