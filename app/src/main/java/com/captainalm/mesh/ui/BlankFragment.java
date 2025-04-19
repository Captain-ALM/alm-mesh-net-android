package com.captainalm.mesh.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.captainalm.mesh.IntentActions;
import com.captainalm.mesh.databinding.BlankBinding;

/**
 * Provides a dummy fragment to avoid a mobile navigation quirk with the home element.
 *
 * @author Alfred Manville
 */
public class BlankFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        BlankBinding binding = BlankBinding.inflate(inflater, container, false);
        Context context = getContext();
        if (context != null)
            context.getApplicationContext().sendBroadcast(new Intent(IntentActions.REFRESH).putExtra("tescos", true));
        return binding.getRoot();
    }
}
