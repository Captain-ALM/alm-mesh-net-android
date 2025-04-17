package com.captainalm.mesh.db;

import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.utils.BytesToHex;

/**
 * Provides a Blocked Node.
 *
 * @author Alfred Manville
 */
@Entity(primaryKeys = "id", tableName = "blocked_nodes")
public class BlockedNode extends BaseIDEntity {

    public BlockedNode() {}

    @Ignore
    public BlockedNode(byte[] ID){
        this.ID = BytesToHex.bytesToHex(ID);
    }
}

