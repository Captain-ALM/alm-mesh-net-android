package com.captainalm.mesh.ui.list;

import android.content.Context;

import com.captainalm.mesh.FragmentIndicator;

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

    @Override
    protected FragmentIndicator getRelatedFragment() {
        return FragmentIndicator.NodeNavigator;
    }
}
