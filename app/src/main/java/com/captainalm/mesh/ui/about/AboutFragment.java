package com.captainalm.mesh.ui.about;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.captainalm.mesh.databinding.FragmentAboutBinding;

/**
 * Provides an About fragment.
 *
 * @author Alfred Manville
 */
public class AboutFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //return inflater.inflate(R.layout.fragment_about, container, false);
        return FragmentAboutBinding.inflate(inflater, container, false).getRoot();
    }
}