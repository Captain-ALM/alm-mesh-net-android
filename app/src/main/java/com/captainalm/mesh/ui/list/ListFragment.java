package com.captainalm.mesh.ui.list;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.captainalm.mesh.TheApplication;
import com.captainalm.mesh.databinding.FragmentBaseListingBinding;

/**
 * Provides a base list fragment for {@link androidx.recyclerview.widget.RecyclerView}.
 *
 * @author Alfred Manville
 */
public abstract class ListFragment extends Fragment {
    protected TheApplication app;
    protected Context container;
    protected FragmentBaseListingBinding binding;
    protected ListAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext();
        if (context != null && context.getApplicationContext() instanceof TheApplication ta)
            app = ta;
        container = context;
        adapter = getAdapter(context);
    }

    protected abstract ListAdapter getAdapter(Context context);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBaseListingBinding.inflate(inflater, container, false);
        binding.recyclerViewBase.setAdapter(adapter);
        binding.recyclerViewBase.setLayoutManager(new LinearLayoutManager(this.container));
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        app = null;
    }

    public void refresh() {
        adapter.refresh(true);
    }
}
