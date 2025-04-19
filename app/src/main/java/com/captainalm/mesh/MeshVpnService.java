package com.captainalm.mesh;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.captainalm.lib.mesh.routing.IPacketProcessor;
import com.captainalm.lib.mesh.routing.Router;

import java.util.Objects;


/**
 * Provides a VPN Service.
 *
 * @author Alfred Manville
 */
public class MeshVpnService extends VpnService implements Handler.Callback {
    private RemoteIntentReceiver intentReceiver;
    private boolean intentReceiverRegistered;

    private Handler messenger;
    private TheApplication app;
    private String extra;

    private Router router;
    private IPacketProcessor packetProcessor;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what != R.string.vpn_stopped)
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //TODO: Forced disconnect and cleanup
        app = null;
        if (intentReceiverRegistered) {
            unregisterReceiver(intentReceiver);
            intentReceiverRegistered = false;
        }
        intentReceiver = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && IntentActions.STOP_VPN.equals(intent.getAction())) {
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

    }

    private void stopVPN() {
        router.deactivate(true);
        router = null;
        packetProcessor = null;
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
            if (Objects.equals(intent.getAction(), IntentActions.ACTIVITY_UP)) {

            } else if (Objects.equals(intent.getAction(), IntentActions.NEW_CIRCUIT)) {

            } else if (Objects.equals(intent.getAction(), IntentActions.PURGE_BLOCKED)) {

            }
        }
    }
}