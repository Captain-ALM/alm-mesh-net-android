package com.captainalm.mesh.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.routing.graphing.GraphNode;

/**
 * Provides an Entity to store a connected node.
 *
 * @author Alfred Manville
 */
@Entity(primaryKeys = "id", tableName = "nodes")
public class Node extends BaseIDEntity {
    @ColumnInfo(name = "siblings")
    public String siblings;
    @ColumnInfo(name = "children")
    public String ethereal;
    @ColumnInfo(name = "gateway")
    public int isGateway;
    @ColumnInfo(name = "encryption")
    public int hasEncryption;

    public Node() {
    }

    @Ignore
    public Node(GraphNode nodeIn) {
        ID = Provider.base64Encode(nodeIn.ID);
        isGateway = (nodeIn.isGateway ? 1 : 0);
        hasEncryption = (nodeIn.getEncryptionKey() == null ? 0 : 1);
        siblings = "";
        ethereal = "";
        for (GraphNode cNode : nodeIn.siblings)
            siblings += Provider.base64Encode(cNode.ID) + ":";
        for (GraphNode cNode : nodeIn.etherealNodes)
            ethereal += Provider.base64Encode(cNode.ID) + ":";
        siblings = siblings.substring(0, siblings.length() - 1);
        ethereal = ethereal.substring(0, ethereal.length() - 1);
    }

    @Ignore
    public String[] getSiblings() {
        if (siblings == null)
            return new String[0];
        return siblings.split(":");
    }

    @Ignore
    public String[] getEthereal() {
        if (ethereal == null)
            return new String[0];
        return ethereal.split(":");
    }
}
