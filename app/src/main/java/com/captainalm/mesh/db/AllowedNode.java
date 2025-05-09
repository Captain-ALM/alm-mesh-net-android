package com.captainalm.mesh.db;

import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.utils.BytesToHex;

/**
 * Provides an Allowed Node.
 *
 * @author Alfred Manville
 */
@Entity(primaryKeys = "id", tableName = "allowed_nodes")
public class AllowedNode extends BaseIDEntity {

    public AllowedNode() {}

    @Ignore
    public AllowedNode(byte[] ID){
        this.ID = BytesToHex.bytesToHex(ID);
    }
}

