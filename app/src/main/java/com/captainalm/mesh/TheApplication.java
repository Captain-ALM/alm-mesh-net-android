package com.captainalm.mesh;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.lib.mesh.routing.graphing.GraphNode;
import com.captainalm.mesh.db.Authorizer;
import com.captainalm.mesh.db.Node;
import com.captainalm.mesh.db.PeerRequest;
import com.captainalm.mesh.db.Settings;
import com.captainalm.mesh.db.TheDatabase;

import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey;
import org.bouncycastle.jcajce.interfaces.MLKEMPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.Security;
import java.util.List;
import java.util.Random;

/**
 * Provides a way of overriding the security provider.
 *
 * @author Alfred Manville
 */
public class TheApplication extends Application {
    public Provider cryptographyProvider;
    public Authorizer authorizer;
    public TheDatabase database;
    public Settings settings;
    private String errorNotifID;
    private String peerNotifID;
    private String nodeInfoNotifID;
    private String vpnNotifID;
    private PendingIntent settingsPIntent;
    public GraphNode thisNode;
    private GraphNode thisEtherealNode;
    private final Object slockEtherealNode = new Object();
    public boolean firstStart = true;
    public boolean serviceActive;

     // Adapted from:
     // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android/66323575#66323575
     // (satur9nine and Mendhak)
    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

    // My code
    @Override
    public void onCreate() {
        super.onCreate();
        database = Room.databaseBuilder(getApplicationContext(), TheDatabase.class, "Datacore").allowMainThreadQueries().addCallback(
                new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                    }

                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                    }
                }
        ).build();
        cryptographyProvider = new Provider(this);
        authorizer = new Authorizer(database);
        errorNotifID = makeChannel(errorNotifID, NotificationManager.IMPORTANCE_MIN, getString(R.string.error_channel), getString(R.string.error_channel_desc));
        peerNotifID = makeChannel(peerNotifID, NotificationManager.IMPORTANCE_HIGH, getString(R.string.peer_channel), getString(R.string.peer_channel_desc));
        nodeInfoNotifID = makeChannel(nodeInfoNotifID, NotificationManager.IMPORTANCE_DEFAULT, getString(R.string.node_info_channel), getString(R.string.peer_channel_desc));
        vpnNotifID = makeChannel(vpnNotifID, NotificationManager.IMPORTANCE_DEFAULT, getString(R.string.vpn_channel), getString(R.string.vpn_channel_desc));
        obtainSettings();
    }

    private void obtainSettings() {
        List<Settings> settings = database.getSettingsDAO().getSettings();
        if (settings == null || settings.isEmpty()) {
            this.settings = new Settings();
            this.settings.packetChargeSize = 100000;
            this.settings.maxTTL = 64;
            KeyPair kem = Provider.generateMLKemKeyPair();
            if (kem != null)
                this.settings.setPrivateKeyKEM((MLKEMPrivateKey) kem.getPrivate());
            KeyPair dsa = Provider.generateMLDsaKeyPair();
            if (dsa != null)
                this.settings.setPrivateKeyDSA((MLDSAPrivateKey) dsa.getPrivate());
            database.getSettingsDAO().addSettings(this.settings);
        } else
            this.settings = settings.get(0);
        //cryptographyProvider.selfTest(this.settings.getPrivateKeyKEM(),
        //        this.settings.getPrivateKeyDSA()); // Self test
        setThisNodeFromSettings();
    }

    private void setThisNodeFromSettings() {
        if (settings == null)
            return;
        byte[] kemKey = this.settings.getPrivateKeyKEM().getPublicKey().getPublicData();
        byte[] dsaKey = this.settings.getPrivateKeyDSA().getPublicKey().getPublicData();
        if (kemKey != null && dsaKey != null)
            this.thisNode = new GraphNode(kemKey, dsaKey, cryptographyProvider.GetHasherInstance());
        else
            this.thisNode = new GraphNode(new byte[32]);
        database.getNodesDAO().clear();
        database.getNodesDAO().addNode(new Node(this.thisNode));
    }

    public void regenerateKeys() {
        if (settings == null)
            return;
        KeyPair kem = Provider.generateMLKemKeyPair();
        if (kem != null)
            this.settings.setPrivateKeyKEM((MLKEMPrivateKey) kem.getPrivate());
        KeyPair dsa = Provider.generateMLDsaKeyPair();
        if (dsa != null)
            this.settings.setPrivateKeyDSA((MLDSAPrivateKey) dsa.getPrivate());
        database.getSettingsDAO().updateSettings(this.settings);
        setThisNodeFromSettings();
    }

    public void onionSetup(boolean onion) {
        synchronized (slockEtherealNode) {
            if (onion) {
                KeyPair kem = Provider.generateMLKemKeyPair();
                if (kem != null)
                    this.settings.setEtherealPrivateKeyKEM((MLKEMPrivateKey) kem.getPrivate());
                KeyPair dsa = Provider.generateMLDsaKeyPair();
                if (dsa != null)
                    this.settings.setEtherealPrivateKeyDSA((MLDSAPrivateKey) dsa.getPrivate());
                database.getSettingsDAO().updateSettings(this.settings);
                byte[] kemKey = this.settings.getEtherealPrivateKeyKEM().getPublicKey().getPublicData();
                byte[] dsaKey = this.settings.getEtherealPrivateKeyDSA().getPublicKey().getPublicData();
                if (kemKey != null && dsaKey != null)
                    this.thisEtherealNode = new GraphNode(kemKey, dsaKey, cryptographyProvider.GetHasherInstance());
                else
                    this.thisEtherealNode = new GraphNode(new byte[32]);
            } else {
                thisEtherealNode = null;
                this.settings.etherealPrivateKeyKEM = null;
                this.settings.etherealPrivateKeyDSA = null;
                database.getSettingsDAO().updateSettings(this.settings);
            }
        }
    }

    public GraphNode getThisEtherealNode() {
        synchronized (slockEtherealNode) {
            return thisEtherealNode;
        }
    }

    public void invokeService(Context context) {
        serviceActive = true;
        if (settings == null) {
            serviceActive = false;
            return;
        }
        startForegroundService(new Intent(context, MeshVpnService.class).setAction(IntentActions.START_VPN).putExtra("onion", true));
    }

    public void stopService(Context context) {
        startForegroundService(new Intent(context, MeshVpnService.class).setAction(IntentActions.STOP_VPN));
    }

    public void regenerateCircuit() {
        sendBroadcast(new Intent(IntentActions.NEW_CIRCUIT));
    }

    private String makeChannel(String ID, int importance, String name, String desc) {
        if (ID == null)
            ID = name.replace(" ", "-");
        //    ID = UUID.randomUUID().toString();
        NotificationChannel channel = new NotificationChannel(ID,
                name, importance);
        channel.setDescription(desc);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return ID;
    }

    public void showException(Exception e) {
        if (errorNotifID == null || e == null)
            return;
        StringWriter esw = new StringWriter();
        e.printStackTrace(new PrintWriter(esw));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, errorNotifID)
                .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(e.getClass().toString()).setContentText(e.getClass() + "\n"
                + e.getMessage()).setStyle(new NotificationCompat.BigTextStyle().bigText(
                        e.getClass() + "\n"  + e.getMessage() + "\n" + esw)).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(new Random().nextInt(8) + 100, builder.build());
    }

    public void showPeeringOperation(Context context, PeerRequest req) {
        if (peerNotifID == null || req == null)
            return;
        PendingIntent pIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).putExtra("frag", FragmentIndicator.PeeringRequests.getID())
                , PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, peerNotifID)
                .setSmallIcon(R.drawable.network_peering).setContentTitle("Peering Request " + req.getCheckCode()).setContentText(req.ID).setAutoCancel(true).setContentIntent(pIntent);
        if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(99, builder.build());
    }

    public void showNodeInfo(Node node) {
        if (nodeInfoNotifID == null || node == null)
            return;
        GraphNode copy = node.getGraphNodeCopy();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, nodeInfoNotifID)
                .setSmallIcon(R.drawable.network).setContentTitle(node.ID).setContentText(ipv4ToIP(copy.getIPv4Address()) + "\n" + ipv6HexToIP(copy.getIPv6AddressString()))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(node.ID + "\n" + node.getCheckCode() + "\n" +
                        ipv4ToIP(copy.getIPv4Address()) + "\n" + ipv6HexToIP(copy.getIPv6AddressString()))).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(98, builder.build());
    }

    private Intent getLaunchEditorIntent(Context context, FragmentIndicator frag, boolean adding, String id) {
        return new Intent(context, EditorActivity.class).putExtra("frag", frag.getID()).putExtra("adder", adding).putExtra("edit_id", id);
    }

    public void launchEditor(Context context, FragmentIndicator frag, boolean adding, String id) {
        context.startActivity(getLaunchEditorIntent(context, frag, adding, id));
    }

    public NotificationCompat.Builder getVPNNotification(Context context, int resString, String extra) {
        if (vpnNotifID == null)
            return null;
        if (settingsPIntent == null)
            settingsPIntent = PendingIntent.getActivity(context, 0,
                    new Intent(this, MainActivity.class).putExtra("frag",
                            FragmentIndicator.Unknown.getID()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, vpnNotifID).setSmallIcon(R.drawable.network_accept).setContentTitle(getString(R.string.vpn_channel))
                .setContentText(getString(resString)).setContentIntent(settingsPIntent).setAutoCancel(true);
        if (extra != null)
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(getString(resString) + "\n" + extra));
        return builder;
    }

    public String ipv4ToIP(byte[] addr) {
        return  ((addr[0] < 0) ? (int) addr[0] + 128 : addr[0]) + "." +
                ((addr[1] < 0) ? (int) addr[1] + 128 : addr[1]) + "." +
                ((addr[2] < 0) ? (int) addr[2] + 128 : addr[2]) + "." +
                ((addr[3] < 0) ? (int) addr[3] + 128 : addr[3]);
    }

    public String ipv6HexToIP(String hex) {
        String address = "[";
        int remaining = hex.length();
        int pos = 0;
        int remainder = 0;
        while (remaining > 0) {
            if (remaining > 3) {
                address += hex.substring(pos, pos + 4) + ":";
                pos += 4;
                remaining -= 4;
            } else {
                address += hex.substring(pos, pos + remaining);
                pos += remaining;
                remainder = 4 - remaining;
                if (remainder == 0)
                    address += ":";
                remaining = 0;
            }
        }
        if (remainder > 0) {
            while (remainder > 0) {
                remainder--;
                address += "0";
            }
            address += ":";
        }
        return address.substring(0, address.length() - 1) + "]";
    }

    public String ipv6HexToIPPure(String hex) {
        String addr = ipv6HexToIP(hex);
        if (addr.length() > 2)
            return addr.substring(1, addr.length() - 1);
        return addr;
    }
}
