package com.captainalm.mesh.db;

import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.crypto.Provider;

/**
 * Provides an Allowed Signature Public Key.
 *
 * @author Alfred Manville
 */
@Entity(primaryKeys = "id", tableName = "allowed_signature_keys")
public class AllowedSignature extends BaseIDEntity {

    public AllowedSignature() {}

    @Ignore
    public AllowedSignature(byte[] ID){
        this.ID = Provider.base64Encode(ID);
    }

    @Ignore
    @Override
    public void setID(byte[] newID) {
        ID = Provider.base64Encode(newID);
    }

    @Ignore
    @Override
    public byte[] getID() {
        return Provider.base64Decode(ID);
    }
}

