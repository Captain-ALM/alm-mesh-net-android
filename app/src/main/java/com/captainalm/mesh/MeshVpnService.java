package com.captainalm.mesh;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.IpPrefix;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.routing.IPacketProcessor;
import com.captainalm.lib.mesh.routing.NetTransportProcessor;
import com.captainalm.lib.mesh.routing.Router;
import com.captainalm.lib.mesh.routing.graphing.GraphNode;
import com.captainalm.mesh.db.Node;
import com.captainalm.mesh.db.Settings;
import com.captainalm.mesh.service.MeshVPN;
import com.captainalm.mesh.service.TransportManager;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Provides a VPN Service.
 *
 * @author Alfred Manville
 */
public class MeshVpnService extends VpnService implements Handler.Callback {
    public static final int MTU = 1280;
    private static final int DISCON_NOTIF_ID = 97;
    private RemoteIntentReceiver intentReceiver;
    private boolean intentReceiverRegistered;
    private LongRemoteIntentReceiver longIntentReceiver;
    private boolean longIntentReceiverRegistered;

    private Handler messenger;
    private TheApplication app;
    private String extra;

    private Router router;
    private IPacketProcessor packetProcessor;
    private MeshVPN vpnTransport;
    private final List<TransportManager> managers = new ArrayList<>();
    private final Object slockVPN = new Object();
    private PendingIntent settingsPIntent;
    private Thread exceptionThread;
    private Thread nodeThread;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        notificationUpdate(msg.what);
        return true;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        if (messenger == null)
            messenger = new Handler(this);

        if (getApplicationContext() instanceof TheApplication ta)
            app = ta;

        if (app == null)
            return;

        app.authorizer.setContext(this);

        intentReceiver = new RemoteIntentReceiver();
        longIntentReceiver = new LongRemoteIntentReceiver();

        if (!intentReceiverRegistered) {
            IntentFilter filter = new IntentFilter(IntentActions.PURGE_BLOCKED);
            filter.addAction(IntentActions.ACTIVITY_UP);
            filter.addAction(IntentActions.NEW_CIRCUIT);
            filter.addAction(IntentActions.DISCOVERY);
            intentReceiver.register(filter);
            intentReceiverRegistered = true;
        }

        if (!longIntentReceiverRegistered) {
            IntentFilter filter = new IntentFilter("");
            // TODO:
            longIntentReceiver.register(filter);
            longIntentReceiverRegistered = true;
        }

        if (settingsPIntent == null)
            settingsPIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class).putExtra("frag",
                            FragmentIndicator.Unknown.getID()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVPN();
        app.authorizer.setContext(null);
        app = null;
        if (intentReceiverRegistered) {
            intentReceiver.unregister();
            intentReceiverRegistered = false;
        }
        if (longIntentReceiverRegistered) {
            longIntentReceiver.unregister();
            longIntentReceiverRegistered = false;
        }
        intentReceiver = null;
        longIntentReceiver = null;
        for (TransportManager manager : managers)
            manager.terminate();
        managers.clear();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && IntentActions.START_VPN.equals(intent.getAction())) {
            notificationUpdate(R.string.vpn_starting);
            startVPN(intent.getBooleanExtra("onion", false));
            refreshApp();
            return START_STICKY;
        } else {
            extra = "";
            notificationUpdate(R.string.vpn_stopping);
            stopVPN();
            refreshApp();
            return START_NOT_STICKY;
        }
    }

    private Settings loadSettings() {
        List<Settings> settingsList = app.database.getSettingsDAO().getSettings();
        if (settingsList == null || settingsList.isEmpty())
            return null;
        return settingsList.get(0);
    }

    private void startVPN(boolean onion) {
        if (app == null)
            return;
        try {
            synchronized (slockVPN) {
                if (router != null)
                    return;
                Settings settings = loadSettings();
                if (settings == null)
                    return;
                VpnService.Builder builder = new Builder().setBlocking(true).setMtu(MTU);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    builder.setMetered(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
                builder.addAddress(app.ipv4ToIP(app.thisNode.getIPv4Address()), 32);
                builder.addAddress(app.ipv6HexToIPPure(app.thisNode.getIPv6AddressString()).toLowerCase(), 126);
                GraphNode ce = app.getThisEtherealNode();
                extra = app.ipv4ToIP((ce == null) ? app.thisNode.getIPv4Address() : ce.getIPv4Address()) + "\n"
                        + app.ipv6HexToIPPure((ce == null) ? app.thisNode.getIPv6AddressString() : ce.getIPv6AddressString()).toLowerCase();
                if (settings.gatewayMode()) {
                    builder.addRoute("::", 0);
                    builder.addRoute("0.0.0.0", 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        builder.excludeRoute(new IpPrefix(InetAddress.getByName("192.168.49.0"), 24)); // Wi-Fi Direct
                } else {
                    builder.allowBypass();
                    builder.addRoute("fd0a::", 16);
                    builder.addRoute("10.0.0.0", 8);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    for (String excluded : settings.getExcludedAddresses())
                        try {
                            if (excluded.contains("."))
                                builder.excludeRoute(new IpPrefix(InetAddress.getByName(excluded), 32));
                            else if (excluded.contains(":"))
                                builder.excludeRoute(new IpPrefix(Inet6Address.getByName(excluded), 128));
                        } catch (IllegalArgumentException e) {
                            if (app != null)
                                app.showException(e);
                        }
                }
                if (settingsPIntent != null)
                    builder.setConfigureIntent(settingsPIntent);
                ParcelFileDescriptor desc = builder.establish();
                if (desc == null) {
                    app.serviceActive = false;
                    app.showException(new Exception("VPN Start Failed!"));
                    return;
                }
                vpnTransport = new MeshVPN(app, desc);
                packetProcessor = new NetTransportProcessor(vpnTransport);
                router = new Router(app.thisNode, Provider.getMLKemPrivateKeyBytes(settings.getPrivateKeyKEM()),
                        Provider.getMLDsaPrivateKeyBytes(settings.getPrivateKeyDSA()), app.cryptographyProvider,
                        packetProcessor, settings.packetChargeSize, (byte) settings.maxTTL, settings.e2eEnabled(),
                        settings.e2eRequired(), settings.e2eIgnoreNonEncryptedPackets());
                exceptionThread = new Thread(() -> {
                    while (app.serviceActive) {
                        try {
                            Exception e = router.getFirstException();
                            app.showException(e);
                        } catch (InterruptedException ex) {
                            return;
                        }
                    }
                });
                exceptionThread.start();
                nodeThread = new Thread(() -> {
                    while (app.serviceActive) {
                        try {
                            Router.NodeUpdate update = router.getFirstUpdate();
                            if (update.removed)
                                app.database.getNodesDAO().removeNode(new Node(update.node));
                            else
                                app.database.getNodesDAO().addNode(new Node(update.node));
                            refreshApp();
                        } catch (InterruptedException ex) {
                            return;
                        }
                    }
                });
                nodeThread.start();
                // TODO: Modify transport mangers list based on settings
                for (TransportManager manager : managers)
                    manager.setRouter(router);
                messenger.sendEmptyMessage(R.string.vpn_running);
            }
        } catch (SecurityException | UnknownHostException ignored) {
            if (app != null)
                app.serviceActive = false;
        }
    }

    private void stopVPN() {
        synchronized (slockVPN) {
            if (router != null) {
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
                    manager.clearRouter();
                router.deactivate(true);
                if (exceptionThread != null && exceptionThread.isAlive())
                    exceptionThread.interrupt();
                exceptionThread = null;
                if (nodeThread != null && nodeThread.isAlive())
                    nodeThread.interrupt();
                nodeThread = null;
                router = null;
                packetProcessor = null;
                vpnTransport.close();
                vpnTransport = null;
            }
            if (app != null)
                app.serviceActive = false;
            messenger.sendEmptyMessage(R.string.vpn_stopped);
        }
    }

    private void refreshApp() {
        if (app != null)
            app.sendBroadcast(new Intent(IntentActions.REFRESH));
    }

    private void notificationUpdate(int stringRes) {
        if (app != null)
            if (stringRes == R.string.vpn_stopped) {
                stopForeground(true);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    getSystemService(NotificationManager.class).notify(DISCON_NOTIF_ID,
                            app.getVPNNotification(this, stringRes, extra).build());
            } else {
                startForeground(1, app.getVPNNotification(this, stringRes, extra).build());
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    getSystemService(NotificationManager.class).cancel(DISCON_NOTIF_ID);
            }
    }

    private class RemoteIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), IntentActions.ACTIVITY_UP) && app != null) {
                refreshApp();
            } else if (Objects.equals(intent.getAction(), IntentActions.DISCOVERY)) {
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
                    manager.discover();
            } else if (Objects.equals(intent.getAction(), IntentActions.PURGE_BLOCKED)) {
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
                    manager.purgeBlockCache();
            } /*else if (Objects.equals(intent.getAction(), IntentActions.NEW_CIRCUIT)) {
            }*/ else {
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
                    manager.receiveBroadcast(context, intent);
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
                MeshVpnService.this.registerReceiver(this, filter, RECEIVER_EXPORTED);
            else
                MeshVpnService.this.registerReceiver(this, filter);
        }

        public void unregister() {
            MeshVpnService.this.unregisterReceiver(this);
        }
    }

    private class LongRemoteIntentReceiver extends RemoteIntentReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
            for (TransportManager manager : lManagers)
                manager.receiveBroadcast(context, intent);
        }
    }
}