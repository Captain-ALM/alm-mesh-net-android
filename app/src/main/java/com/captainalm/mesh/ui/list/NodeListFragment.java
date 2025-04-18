package com.captainalm.mesh.ui.list;

import android.content.Context;

/**
 * Provides a NodeList Fragment.
 *
 * @author Alfred Manville
 */
public class NodeListFragment extends ListFragment {

    @Override
    protected ListAdapter getAdapter(Context context) {
        return new NodeListAdapter(context);
    }
}
