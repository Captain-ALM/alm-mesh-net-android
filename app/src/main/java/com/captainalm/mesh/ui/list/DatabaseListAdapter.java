package com.captainalm.mesh.ui.list;

import android.content.Context;

import com.captainalm.mesh.FragmentIndicator;

/**
 * Provides a database list for {@link androidx.recyclerview.widget.RecyclerView}
 *
 * @author Alfred Manville
 */
public class DatabaseListAdapter extends ListAdapter {
    private FragmentIndicator dbID;

    public DatabaseListAdapter(Context context, IListClickHandler cHandler, FragmentIndicator dbID) {
        super(context, cHandler);
        this.dbID = dbID;
        refresh();
    }

    public void refresh() {
        if (app != null) {
            items.clear();
            switch (dbID) {
                case AllowedNodes -> items.addAll(app.database.getAllowedNodeDAO().getAllAllowedNodes());
                case BlockedNodes -> items.addAll(app.database.getBlockedNodeDAO().getAllBlockedNodes());
                case AllowedNodeSignatureKeys -> items.addAll(app.database.getAllowedSignatureDAO().getAllAllowedSignatures());
                case PeeringRequests -> items.addAll(app.database.getPeerRequestDAO().getAllPeerRequests());
            }
            notifyDataSetChanged();
        }
    }
}
