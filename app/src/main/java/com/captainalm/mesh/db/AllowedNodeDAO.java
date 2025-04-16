package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AllowedNodeDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addAllowedNode(AllowedNode node);
    @Delete
    void removeAllowedNode(AllowedNode node);

    @Query("select * from allowed_nodes")
    List<AllowedNode> getAllAllowedNodes();

    @Query("select * from allowed_nodes where id == :ID")
    AllowedNode getAllowedNode(String ID);
}
