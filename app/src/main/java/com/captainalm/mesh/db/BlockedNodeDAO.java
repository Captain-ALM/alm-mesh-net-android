package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BlockedNodeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addBlockedNode(BlockedNode node);
    @Delete
    void removeBlockedNode(BlockedNode node);

    @Query("select * from blocked_nodes")
    List<BlockedNode> getAllBlockedNodes();

    @Query("select * from blocked_nodes where id == :ID")
    BlockedNode getBlockedNode(String ID);
}
