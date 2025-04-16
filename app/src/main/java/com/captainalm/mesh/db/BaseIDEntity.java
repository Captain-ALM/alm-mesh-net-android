package com.captainalm.mesh.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.captainalm.lib.mesh.crypto.Provider;

/**
 * Provides an abstract class for storing an ID.
 *
 * @author Alfred Manville
 */
public abstract class BaseIDEntity {
    @ColumnInfo(name = "id")
    @NonNull
    public String ID;

    @Ignore
    public void setID(byte[] newID) {
        ID = Provider.base64Encode(newID);
    }

    @Ignore
    public byte[] getID() {
        return Provider.base64Decode(ID);
    }

    /**
     * Gets an easy to use check code.
     *
     * @return The check code created from ID.
     */
    @Ignore
    public int getCheckCode() {
        byte code = 0;
        byte[] cID = getID();
        for (byte b : cID) code += b;
        return (code < 0) ? code + 128: code;
    }
}
