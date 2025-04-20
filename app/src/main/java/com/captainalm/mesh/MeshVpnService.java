package com.captainalm.mesh;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.IpPrefix;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.routing.IPacketProcessor;
import com.captainalm.lib.mesh.routing.NetTransportProcessor;
import com.captainalm.lib.mesh.routing.Router;
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
    private RemoteIntentReceiver intentReceiver;
    private boolean intentReceiverRegistered;

    private Handler messenger;
    private TheApplication app;
    private String extra;

    private Router router;
    private IPacketProcessor packetProcessor;
    private MeshVPN vpnTransport;
    private final List<TransportManager> managers = new ArrayList<>();
    private Settings settings;
    private final Object slockVPN = new Object();
    private PendingIntent settingsPIntent;
    private Thread exceptionThread;
    private Thread nodeThread;

    private Thread autoStop;

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

        intentReceiver = new RemoteIntentReceiver();

        if (!intentReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(intentReceiver, new IntentFilter( IntentActions.REFRESH), RECEIVER_NOT_EXPORTED);
            else
                registerReceiver(intentReceiver, new IntentFilter( IntentActions.REFRESH));
            intentReceiverRegistered = true;
        }

        if (settingsPIntent == null)
            settingsPIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class).putExtra("frag",
                            FragmentIndicator.Unknown.getID()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // TODO: Create transport managers
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVPN();
        app = null;
        if (intentReceiverRegistered) {
            unregisterReceiver(intentReceiver);
            intentReceiverRegistered = false;
        }
        intentReceiver = null;
        for (TransportManager manager : managers)
            manager.terminate();
        managers.clear();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (autoStop != null) {
            autoStop.interrupt();
            autoStop = null;
        }
        if (intent != null && IntentActions.STOP_VPN.equals(intent.getAction())) {
            extra = "";
            messenger.sendEmptyMessage(R.string.vpn_stopping);
            stopVPN();
            refreshApp();
            return START_NOT_STICKY;
        } else {
            messenger.sendEmptyMessage(R.string.vpn_starting);
            startVPN(intent != null && intent.getBooleanExtra("onion", false));
            refreshApp();
            return START_STICKY;
        }
    }

    private void startVPN(boolean onion) {
        if (app == null)
            return;
        try {
            synchronized (slockVPN) {
                if (router != null)
                    return;
                List<Settings> settingsList = app.database.getSettingsDAO().getSettings();
                if (settingsList == null || settingsList.isEmpty())
                    return;
                settings = settingsList.get(0);
                VpnService.Builder builder = new Builder().setBlocking(true).setMtu(MTU);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    builder.setMetered(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
                builder.addAddress(app.ipv4ToIP(app.thisNode.getIPv4Address()), 32);
                builder.addAddress(app.ipv6HexToIPPure(app.thisNode.getIPv6AddressString()).toLowerCase(), 126);
                extra = app.ipv4ToIP(app.thisNode.getIPv4Address()) + "\n" + app.ipv6HexToIPPure(app.thisNode.getIPv6AddressString()).toLowerCase();
                if (settings.gatewayMode()) {
                    builder.addRoute("::", 0);
                    builder.addRoute("0.0.0.0", 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        builder.excludeRoute(new IpPrefix(InetAddress.getByName("192.168.49.0"), 24));
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
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
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
            if (router == null)
                return;
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
            if (app != null)
                app.serviceActive = false;
            messenger.sendEmptyMessage(R.string.vpn_stopped);
            autoStop = new Thread(() -> {
               try {
                   Thread.sleep(15000);
                   synchronized (slockVPN) {
                       stopForeground(true);
                   }
               } catch (InterruptedException ignored) {
               }
            });
            autoStop.start();
        }
    }

    private void refreshApp() {
        if (app != null)
            app.sendBroadcast(new Intent(IntentActions.REFRESH));
    }

    private void notificationUpdate(int stringRes) {
        if (app != null)
            startForeground(1, app.getVPNNotification(this, stringRes, extra).build());
    }

    private class RemoteIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), IntentActions.ACTIVITY_UP) && app != null) {
                refreshApp();
            } else if (Objects.equals(intent.getAction(), IntentActions.PURGE_BLOCKED)) {
                TransportManager[] lManagers = managers.toArray(new TransportManager[0]);
                for (TransportManager manager : lManagers)
                    manager.purgeBlockCache();
            } /*else if (Objects.equals(intent.getAction(), IntentActions.NEW_CIRCUIT)) {
            }*/
        }
    }
}