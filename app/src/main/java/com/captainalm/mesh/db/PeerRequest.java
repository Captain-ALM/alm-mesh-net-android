package com.captainalm.mesh.db;

import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.crypto.Provider;

/**
 * Provides peering requests.
 *
 * @author Alfred Manville
 */
@Entity(primaryKeys = "id", tableName = "peer_requests")
public class PeerRequest extends BaseIDEntity {

    public PeerRequest() {}

    @Ignore
    public PeerRequest(byte[] ID){
        this.ID = Provider.base64Encode(ID);
    }
}

