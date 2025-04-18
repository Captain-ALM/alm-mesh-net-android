package com.captainalm.mesh.ui.list;

import android.content.Context;

import com.captainalm.mesh.FragmentIndicator;

/**
 * Provides a base database list fragment.
 *
 * @author Alfred Manville
 */
public abstract class DatabaselistFragment extends ListFragment implements IListClickHandler {
    protected final FragmentIndicator fragmentIndicator;

    public DatabaselistFragment(FragmentIndicator frag) {
        fragmentIndicator = frag;
    }

    @Override
    public void onItemClicked(int position) {
        String id = adapter.itemIDAt(position);
        if (id != null && app != null)
            app.launchEditor(container, fragmentIndicator, false, id);
    }

    @Override
    protected ListAdapter getAdapter(Context context) {
        return new DatabaseListAdapter(context, this, fragmentIndicator);
    }
}
