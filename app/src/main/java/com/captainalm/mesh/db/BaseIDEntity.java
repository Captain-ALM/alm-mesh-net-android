package com.captainalm.mesh.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.utils.BytesToHex;

/**
 * Provides an abstract class for storing an ID.
 *
 * @author Alfred Manville
 */
public abstract class BaseIDEntity {
    @ColumnInfo(name = "id")
    @NonNull
    public String ID = "";

    @Ignore
    public void setID(byte[] newID) {
        ID = BytesToHex.bytesToHex(newID);
    }

    @Ignore
    public byte[] getID() {
        return BytesToHex.hexToBytes(ID);
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

    @Ignore
    public String extraData() {
        return Integer.toString(getCheckCode());
    }

    @Ignore
    public boolean valid() {
        return !ID.isEmpty();
    }
}
