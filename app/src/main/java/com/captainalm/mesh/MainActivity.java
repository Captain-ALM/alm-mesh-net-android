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
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

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
    private ActivityResultLauncher<Intent> wifiDirectEnable;
    private ActivityResultLauncher<Intent> bluetoothDiscover;
    private ActivityResultLauncher<String> wifiDirectPermissionLauncher;
    private ActivityResultLauncher<String[]> bluetoothPermissionLauncher;
    private ActivityResultLauncher<String> locationWifiDirectPermissionLauncher;
    private ActivityResultLauncher<String> blocationWifiDirectPermissionLauncher;
    private ActivityResultLauncher<String> locationBluetoothPermissionLauncher;
    private ActivityResultLauncher<String> blocationBluetoothPermissionLauncher;

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

        if (vpnLauncher == null)
            vpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                o -> {
                    if (o.getResultCode() == MainActivity.RESULT_OK && app != null)
                        app.invokeService(MainActivity.this);
                    else
                        refresh(FragmentIndicator.Unknown);
                });

        if (wifiDirectPermissionLauncher == null)
            wifiDirectPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    o -> {
                            if (o)
                                triggerLocationPermission(8081, true);
                            else
                                triggerPostPermissionsFailure(8081);
                    });
        if (bluetoothPermissionLauncher == null)
            bluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
        o -> {
                for (boolean b : o.values())
                    if (!b) {
                        triggerPostPermissionsFailure(8080);
                        return;
                    }
                triggerLocationPermission(8080, true);
        });

        if (locationWifiDirectPermissionLauncher == null)
            locationWifiDirectPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    o -> {
                        if (o)
                            triggerBLocationPermission(8081, true);
                        else
                            triggerPostPermissionsFailure(8081);
                    });
        if (locationBluetoothPermissionLauncher == null)
            locationBluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    o -> {
                        if (o)
                            triggerBLocationPermission(8080, true);
                        else
                            triggerPostPermissionsFailure(8080);
                    });

        if (blocationWifiDirectPermissionLauncher == null)
            blocationWifiDirectPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    o -> {
                        if (o)
                            triggerPostPermissions(8081, true);
                        else
                            triggerPostPermissionsFailure(8081);
                    });
        if (blocationBluetoothPermissionLauncher == null)
            blocationBluetoothPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    o -> {
                        if (o)
                            triggerPostPermissions(8080, true);
                        else
                            triggerPostPermissionsFailure(8080);
                    });

        if (bluetoothEnable == null)
            bluetoothEnable = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {
                if (o.getResultCode() == MainActivity.RESULT_CANCELED && app != null) {
                    triggerPostPermissionsFailure(8080);
                }
                    });
        if (bluetoothDiscover == null)
            bluetoothDiscover = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {});

        if (wifiDirectEnable == null)
            wifiDirectEnable = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    o -> {
                        if (app != null && !app.getWiFiEnabled())
                            triggerPostPermissionsFailure(8081);
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
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            intentReceiver.register(filter);
            intentReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (intentReceiverRegistered) {
            intentReceiver.unregister();
            intentReceiverRegistered = false;
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

    private boolean triggerBluetoothPermissions() {
        List<String> perms = new LinkedList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!perms.isEmpty()) {
            bluetoothPermissionLauncher.launch(perms.toArray(new String[0]));
            return false;
        }
        else
            return triggerLocationPermission(8080, false);
    }

    private boolean triggerWiFiDirectPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                wifiDirectPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES);
                return false;
            }
        }
        return triggerLocationPermission(8081, false);
    }

    private boolean triggerLocationPermission(int mode, boolean trigger) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ((mode == 8080) ? locationBluetoothPermissionLauncher : locationWifiDirectPermissionLauncher).launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return false;
            }
        }
        return triggerBLocationPermission(mode, trigger);
    }

    private boolean triggerBLocationPermission(int mode, boolean trigger) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ((mode == 8080) ? blocationBluetoothPermissionLauncher : blocationWifiDirectPermissionLauncher).launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                return false;
            }
        }
        triggerPostPermissions(mode, trigger);
        return true;
    }

    private void triggerPostPermissionsFailure(int mode) {
        if (mode == 8080)
            app.settings.setBluetooth(false);
        else
            app.settings.setWiFiDirect(false);
        refresh(FragmentIndicator.Unknown);
    }

    private void triggerPostPermissions(int mode ,boolean trigger) {
        if (mode == 8080) {
            app.bluetoothAuthority = true;
            if (trigger)
                triggerBluetoothEnable();
        } else {
            app.wifiDirectAuthority = true;
            if (trigger)
                triggerWiFiDirectEnable();
        }
    }

    public void triggerWiFiDirectEnable() {
        if (triggerWiFiDirectPermissions()) {
            if (!app.getWiFiEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    wifiDirectEnable.launch(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
                else {
                    try {
                        if (getSystemService(Context.WIFI_SERVICE) instanceof WifiManager wm)
                            wm.setWifiEnabled(true);
                    } catch (RuntimeException e) {
                        app.showException(e);
                    }
                }
            }
        } else {
            triggerPostPermissionsFailure(8081);
        }
    }

    public void triggerBluetoothEnable() {
        if (triggerBluetoothPermissions()) {
            if (!app.getBluetoothEnabled())
                bluetoothEnable.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else
            triggerPostPermissionsFailure(8080);
    }

    public void triggerBluetoothDiscoverable() {
        if (!app.getBluetoothEnabled() || !app.bluetoothAuthority)
            refresh(FragmentIndicator.Unknown);
        else
            bluetoothDiscover.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60));
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
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
            || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
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
                MainActivity.this.registerReceiver(this, filter, RECEIVER_EXPORTED);
            else
                MainActivity.this.registerReceiver(this, filter);
        }

        public void unregister() {
            MainActivity.this.unregisterReceiver(this);
        }
    }
}