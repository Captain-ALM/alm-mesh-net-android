package com.captainalm.mesh;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.navigation.NavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

    private ActivityResultLauncher<Intent> vpnLauncher;
    private ActivityResultLauncher<Intent> bluetoothEnable;
    private ActivityResultLauncher<Intent> bluetoothDiscover;
    private boolean discoveringBluetooth = false;
    private boolean discoveringP2P = false;
    private boolean canRegisterBluetoothProtected;
    private boolean intentReceiverBluetoothRegistered;
    private RemoteIntentReceiver intentReceiverBluetooth;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (app != null && isFinishing())
            app.firstStart = true;
        app = null;
        binding = null;
        mAppBarConfiguration = null;
        navController = null;
        intentReceiver = null;
        intentReceiverBluetooth = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // My get the application instance code

        if (getApplicationContext() instanceof TheApplication ta)
            app = ta;

        intentReceiver = new RemoteIntentReceiver();
        intentReceiverBluetooth = new RemoteIntentReceiver();

        if (app == null)
            return;

        if (vpnLauncher == null)
            vpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                o -> {
                    if (o.getResultCode() == MainActivity.RESULT_OK && app != null)
                        app.invokeService(MainActivity.this);
                    else
                        refresh(FragmentIndicator.Unknown);
                });
        if (bluetoothEnable == null)
            bluetoothEnable = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {
                if (o.getResultCode() == MainActivity.RESULT_CANCELED && app != null) {
                    app.settings.setBluetooth(false);
                    refresh(FragmentIndicator.Unknown);
                }
                    });
        if (bluetoothDiscover == null)
            bluetoothDiscover = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {
                if (o.getResultCode() == MainActivity.RESULT_CANCELED) {
                    discoveringBluetooth = false;
                    refresh(FragmentIndicator.Unknown);
                }
                    });

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
                app.launchEditor(MainActivity.this, indicator, true, "");
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
        sendBroadcast(new Intent(IntentActions.ACTIVITY_UP));
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


    @Override
    protected void onResume() {
        super.onResume();
        if (!intentReceiverRegistered) {
            IntentFilter filter = new IntentFilter(IntentActions.REFRESH);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            intentReceiver.register(filter);
            intentReceiverRegistered = true;
        }
        if (!intentReceiverBluetoothRegistered && canRegisterBluetoothProtected) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            intentReceiverBluetooth.register(filter);
            intentReceiverBluetoothRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (intentReceiverRegistered) {
            intentReceiver.unregister();
            intentReceiverRegistered = false;
        }
        if (intentReceiverBluetoothRegistered) {
            intentReceiverBluetooth.unregister();
            intentReceiverBluetoothRegistered = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 8080) { // Bluetooth
            boolean fine = grantResults.length > 0;
            for (int c : grantResults)
                fine = fine && (c == PackageManager.PERMISSION_GRANTED);
            if (fine)
                triggerBluetoothEnable();
            else {
                app.settings.setBluetooth(false);
                refresh(FragmentIndicator.Unknown);
            }
        } else if (requestCode == 8081) { // Wi-Fi Direct

        }
    }

    public void startVPN(boolean onion) {
        Intent vpnIntent = VpnService.prepare(this);
        if (app != null)
            app.onionSetup(onion);
        if (vpnIntent == null && app != null)
            app.invokeService(this);
        else if (vpnIntent != null)
            vpnLauncher.launch(vpnIntent);
    }

    public void triggerBluetoothEnable() {
        List<String> perms = new LinkedList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!perms.isEmpty())
            requestPermissions(perms.toArray(new String[0]), 8080);
        else
            bluetoothEnable.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    }

    public void triggerBluetoothDiscoverable() {
        if (!app.getBluetoothEnabled()) {
            refresh(FragmentIndicator.Unknown);
            return;
        } else {
            discoveringBluetooth = true;
            bluetoothDiscover.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60));
        }
    }

    public boolean isDiscovering() {
        return discoveringBluetooth || discoveringP2P;
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
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                refresh(FragmentIndicator.Unknown);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                refresh(FragmentIndicator.Unknown);
            }
        }


        // Below Based on:
        // https://stackoverflow.com/questions/7276537/using-a-broadcast-intent-broadcast-receiver-to-send-messages-from-a-service-to-a/7276808#7276808
        // Squonk; micha
        // And
        // https://stackoverflow.com/questions/77235063/one-of-receiver-exported-or-receiver-not-exported-should-be-specified-when-a-rec/77529595#77529595
        // Mohd. Jafar Iqbal khan; Pritesh Patel
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        public void register(IntentFilter filter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                MainActivity.this.registerReceiver(intentReceiver, filter, RECEIVER_EXPORTED);
            else
                MainActivity.this.registerReceiver(intentReceiver, filter);
        }

        public void unregister() {
            MainActivity.this.unregisterReceiver(this);
        }
    }
}