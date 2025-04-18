package com.captainalm.mesh;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import java.util.Set;
import java.util.UUID;

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
    public GraphNode thisNode;
    public boolean serviceActive;

     // Adapted from:
     // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android/66323575#66323575
     // (satur9nine and Mendhak)
    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

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
        cryptographyProvider = new Provider(getApplicationContext());
        authorizer = new Authorizer(database);
        errorNotifID = makeErrorChannel(errorNotifID);
        peerNotifID = makePeeringChannel(peerNotifID);
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

    public void invokeService(boolean onion) {
        serviceActive = true;
    }

    public void stopService() {
        serviceActive = false;
        sendBroadcast(new Intent(IntentActions.REFRESH));
        // TODO: ^ Should be in the service
    }

    public void regenerateCircuit() {

    }

    private String makeErrorChannel(String ID) {
        if (ID == null)
            ID = UUID.randomUUID().toString();
        NotificationChannel channel = new NotificationChannel(ID,
                getString(R.string.error_channel), NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(getString(R.string.error_channel_desc));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        return ID;
    }
    private String makePeeringChannel(String ID) {
        if (ID == null)
            ID = UUID.randomUUID().toString();
        NotificationChannel channel = new NotificationChannel(ID,
                getString(R.string.peer_channel), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.peer_channel_desc));
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
    public void showPeeringOperation(PeerRequest req) {
        if (peerNotifID == null || req == null)
            return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, peerNotifID)
                .setSmallIcon(R.drawable.network_peering).setContentTitle("Peering Request " + req.getCheckCode()).setContentText(req.ID).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(99, builder.build());
    }

    public void launchEditor(Context context, FragmentIndicator frag, boolean adding, String id) {
        context.startActivity(new Intent(context, EditorActivity.class).putExtra("frag", frag.getID()).putExtra("adder", adding).putExtra("edit_id", id));
    }
}
