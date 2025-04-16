package com.captainalm.mesh.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {AllowedNode.class, BlockedNode.class, AllowedSignature.class, Node.class, PeerRequest.class, Settings.class}, version = 1, exportSchema = false)
public abstract class TheDatabase extends RoomDatabase {
    public abstract AllowedNodeDAO getAllowedNodeDAO();
    public abstract BlockedNodeDAO getBlockedNodeDAO();

    public abstract AllowedSignatureDAO getAllowedSignatureDAO();

    public abstract NodesDAO getNodesDAO();

    public abstract PeerRequestDAO getPeerRequestDAO();

    public abstract SettingsDAO getSettingsDAO();
}
