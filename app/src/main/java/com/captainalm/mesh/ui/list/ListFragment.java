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

import com.captainalm.mesh.FragmentIndicator;
import com.captainalm.mesh.IRefreshable;
import com.captainalm.mesh.MainActivity;
import com.captainalm.mesh.TheApplication;
import com.captainalm.mesh.databinding.FragmentBaseListingBinding;

/**
 * Provides a base list fragment for {@link androidx.recyclerview.widget.RecyclerView}.
 *
 * @author Alfred Manville
 */
public abstract class ListFragment extends Fragment implements IRefreshable {
    protected TheApplication app;
    protected Context container;
    protected FragmentBaseListingBinding binding;
    protected ListAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity();
        if (context != null && context.getApplicationContext() instanceof TheApplication ta)
            app = ta;
        container = context;
        if (container instanceof MainActivity ma)
            ma.addRefreshable(this);
        adapter = getAdapter(context);
    }

    protected abstract ListAdapter getAdapter(Context context);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBaseListingBinding.inflate(inflater, container, false);
        binding.recyclerViewBase.setAdapter(adapter);
        binding.recyclerViewBase.setLayoutManager(new LinearLayoutManager(this.container));
        refresh(getRelatedFragment());
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        if (container instanceof MainActivity ma)
            ma.removeRefreshable(this);
        super.onDestroy();
        app = null;
        container = null;
    }

    protected abstract FragmentIndicator getRelatedFragment();

    @Override
    public void refresh(FragmentIndicator fragRefreshing) {
        if (getRelatedFragment() == fragRefreshing)
            adapter.refresh(true);
    }
}
