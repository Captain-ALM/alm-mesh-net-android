package com.captainalm.mesh;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.captainalm.mesh.databinding.ActivityMainBinding;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private TheApplication app;

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private FragmentIndicator indicator = FragmentIndicator.Unknown;
    private RemoteIntentReceiver intentReceiver;
    private NavController navController;
    private boolean intentReceiverRegistered;

    private final Object refreshableLocker = new Object();
    private final List<IRefreshable> refreshables = new LinkedList<>();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (app != null && isFinishing())
            app.firstStart = true;
        app = null;
        binding = null;
        mAppBarConfiguration = null;
        navController = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // My get the application instance code

        if (getApplicationContext() instanceof TheApplication ta)
            app = ta;

        intentReceiver = new RemoteIntentReceiver();

        if (app == null)
            return;

        // Template defined
        getLayoutInflater();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // My code:
        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(view -> {
            if (indicator == FragmentIndicator.AllowedNodes ||
            indicator == FragmentIndicator.AllowedNodeSignatureKeys ||
            indicator == FragmentIndicator.BlockedNodes) {
                app.launchEditor(this, indicator, true, "");
            }
        });

        // Template defined
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        // Modified with my fragments
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_settings, R.id.nav_peering, R.id.nav_allowed, R.id.nav_allowed_keys, R.id.nav_blocked, R.id.nav_topology, R.id.nav_about)
                .setOpenableLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
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
        // Template defined
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // My code to navigate activity if returned to from editor
        triggerNavigation(getIntent().getIntExtra("frag", (app != null && app.firstStart) ? 0 : -1));
        getIntent().removeExtra("frag");
        app.firstStart = false;
        if (getIntent().getBooleanExtra("refresh", false)) {
            refresh(indicator);
            getIntent().removeExtra("refresh");
        }
    }

    private void triggerNavigation(int frag) {
        if (navController == null || frag < 0)
            return;
        FragmentIndicator user = FragmentIndicator.getIndicator(frag);
        indicator = user;
        switch (user) {
            case NodeNavigator -> navController.navigate(R.id.nav_topology);
            case AllowedNodes -> navController.navigate(R.id.nav_allowed);
            case AllowedNodeSignatureKeys -> navController.navigate(R.id.nav_allowed_keys);
            case BlockedNodes -> navController.navigate(R.id.nav_blocked);
            case PeeringRequests -> navController.navigate(R.id.nav_peering);
            case Unknown -> navController.navigate(R.id.nav_settings);
        }
    }

    private void refresh(FragmentIndicator frag) {
        synchronized (refreshableLocker) {
            for (IRefreshable fragment : refreshables)
                fragment.refresh(frag);
        }
    }

    public void addRefreshable(IRefreshable fragment) {
        if (fragment == null)
            return;
        synchronized (refreshableLocker) {
            refreshables.add(fragment);
        }
    }

    public void removeRefreshable(IRefreshable fragment) {
        if (fragment == null)
            return;
        synchronized (refreshableLocker) {
            refreshables.remove(fragment);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    // Below Based on:
    // https://stackoverflow.com/questions/7276537/using-a-broadcast-intent-broadcast-receiver-to-send-messages-from-a-service-to-a/7276808#7276808
    // Squonk; micha
    // And
    // https://stackoverflow.com/questions/77235063/one-of-receiver-exported-or-receiver-not-exported-should-be-specified-when-a-rec/77529595#77529595
    // Mohd. Jafar Iqbal khan; Pritesh Patel
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        if (!intentReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(intentReceiver, new IntentFilter( IntentActions.REFRESH), RECEIVER_NOT_EXPORTED);
            else
                registerReceiver(intentReceiver, new IntentFilter( IntentActions.REFRESH));
            intentReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (intentReceiverRegistered) {
            unregisterReceiver(intentReceiver);
            intentReceiverRegistered = false;
        }
    }

    private class RemoteIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), IntentActions.REFRESH)) {
                /// Humorous named intent extra
                if (intent.getBooleanExtra("tescos", false))
                    triggerNavigation(indicator.getID());
                else
                    triggerNavigation(intent.getIntExtra("frag", -1));
                refresh(indicator);
            }
        }
    }
}