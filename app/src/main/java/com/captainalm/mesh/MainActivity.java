package com.captainalm.mesh;

import android.os.Bundle;

import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.captainalm.mesh.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private TheApplication app;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private FragmentIndicator indicator = FragmentIndicator.Unknown;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        app = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // My get the application instance code

        if (getApplicationContext() instanceof TheApplication ta)
            app = ta;

        if (app == null)
            return;

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(view -> {
            if (indicator == FragmentIndicator.AllowedNodes ||
            indicator == FragmentIndicator.AllowedNodeSignatureKeys ||
            indicator == FragmentIndicator.BlockedNodes) {
                app.launchEditor(this, indicator, true, "");
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        // Modified with my fragments
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_settings, R.id.nav_peering, R.id.nav_allowed, R.id.nav_allowed_keys, R.id.nav_blocked, R.id.nav_topology, R.id.nav_about)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        // Added by me to control the fab visibility
        navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
            if (navDestination.getId() == R.id.nav_peering) {
                indicator = FragmentIndicator.PeeringRequests;
                binding.appBarMain.fab.hide();
            } else if (navDestination.getId() == R.id.nav_allowed) {
                indicator = FragmentIndicator.AllowedNodes;
                binding.appBarMain.fab.show();
            } else if (navDestination.getId() == R.id.nav_allowed_keys) {
                indicator = FragmentIndicator.AllowedNodeSignatureKeys;
                binding.appBarMain.fab.show();
            } else if (navDestination.getId() == R.id.nav_blocked) {
                indicator = FragmentIndicator.BlockedNodes;
                binding.appBarMain.fab.show();
            } else if (navDestination.getId() == R.id.nav_topology) {
                indicator = FragmentIndicator.NodeNavigator;
                binding.appBarMain.fab.hide();
            } else {
                indicator = FragmentIndicator.Unknown;
                binding.appBarMain.fab.hide();
            }
        });
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // My code to navigate activity if returned to from editor
        FragmentIndicator user = FragmentIndicator.getIndicator(getIntent().getIntExtra("frag", 0));
        switch (user) {
            case NodeNavigator -> navController.navigate(R.id.nav_topology);
            case AllowedNodes -> navController.navigate(R.id.nav_allowed);
            case AllowedNodeSignatureKeys -> navController.navigate(R.id.nav_allowed_keys);
            case BlockedNodes -> navController.navigate(R.id.nav_blocked);
            case PeeringRequests -> navController.navigate(R.id.nav_peering);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}