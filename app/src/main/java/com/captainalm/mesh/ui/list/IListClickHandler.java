package com.captainalm.mesh.ui.list;

/**
 * Provides an interface for handling an item click event.
 *
 * @author Alfred Manville
 */
public interface IListClickHandler {
    void onItemClicked(int position);
    boolean onItemLongClicked(int position);
}
