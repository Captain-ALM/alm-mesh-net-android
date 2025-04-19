package com.captainalm.mesh.ui.list;

import android.content.Context;

import com.captainalm.mesh.db.Node;

/**
 * Provides an adapter to browse node data.
 *
 * @author Alfred Manville
 */
public class NodeListAdapter extends ListAdapter implements IListClickHandler {
    Node current;
    Node parent;

    public NodeListAdapter(Context context) {
        super(context, null);
        refresh(true);
    }

    @Override
    protected IListClickHandler getClickHandler(IListClickHandler handler) {
        return this;
    }

    @Override
    protected String noItemsText() {
        return "No Nodes?";
    }

    @Override
    public void refresh(boolean reset) {
        if (reset)
            if (app == null)
                current = null;
            else
                current = app.database.getNodesDAO().getNode(new Node(app.thisNode).ID);

        if (current != null && app != null) {
            items.clear();
            items.add(current);
            items.addAll(app.database.getNodesDAO().getNodes(current.getSiblings()));
            items.addAll(app.database.getNodesDAO().getNodes(current.getEthereal()));
            if (items.size() == 1 && parent != null && !parent.ID.equals(current.ID))
                items.add(0, parent);
            notifyDataSetChanged();
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (items.isEmpty())
            return;
        parent = current;
        try {
            current = (Node) items.get(position);
            refresh(false);
        } catch (IndexOutOfBoundsException e) {
            if (app != null)
                app.showException(e);
            parent = null;
            refresh(true);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (items.isEmpty())
            return false;
        try {
            if (app != null)
                app.showNodeInfo((Node) items.get(position));
            return true;
        } catch (IndexOutOfBoundsException e) {
            app.showException(e);
        }
        return false;
    }
}
