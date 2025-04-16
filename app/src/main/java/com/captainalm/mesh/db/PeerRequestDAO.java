package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PeerRequestDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addPeerRequest(PeerRequest node);
    @Delete
    void removePeerRequest(PeerRequest node);

    @Query("select * from peer_requests")
    List<PeerRequest> getAllPeerRequests();

    @Query("select * from peer_requests where id == :ID")
    PeerRequest getPeerRequest(String ID);
}
