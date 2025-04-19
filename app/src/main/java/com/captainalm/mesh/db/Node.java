package com.captainalm.mesh.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

import com.captainalm.lib.mesh.routing.graphing.GraphNode;
import com.captainalm.lib.mesh.utils.BytesToHex;

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
        this.ID = BytesToHex.bytesToHex(nodeIn.ID);
        isGateway = (nodeIn.isGateway ? 1 : 0);
        hasEncryption = (nodeIn.getEncryptionKey() == null ? 0 : 1);
        siblings = "";
        ethereal = "";
        for (GraphNode cNode : nodeIn.siblings)
            siblings += BytesToHex.bytesToHex(cNode.ID) + ":";
        for (GraphNode cNode : nodeIn.etherealNodes)
            ethereal += BytesToHex.bytesToHex(cNode.ID) + ":";
        if (!siblings.isEmpty())
            siblings = siblings.substring(0, siblings.length() - 1);
        if (!ethereal.isEmpty())
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

    @Ignore
    @Override
    public String extraData() {
        return ((isGateway == 1) ? "G" : "") + ((hasEncryption == 1 ? "E" : ""));
    }

    @Ignore
    public GraphNode getGraphNodeCopy() {
        return new GraphNode(BytesToHex.hexToBytes(this.ID));
    }
}
