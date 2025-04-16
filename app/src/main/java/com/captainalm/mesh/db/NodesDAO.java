package com.captainalm.mesh.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NodesDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addNode(Node node);

    @Update
    void updateNode(Node node);

    @Delete
    void removeNode(Node node);

    @Query("select * from nodes")
    List<Node> getAllNodes();

    @Query("select * from nodes where id == :ID")
    Node getNode(String ID);

    @Query("select * from nodes where id IN(:nodeIDs)")
    List<Node> getNodes(String[] nodeIDs);
}
