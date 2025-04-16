package com.captainalm.mesh;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.captainalm.lib.mesh.crypto.Provider;
import com.captainalm.mesh.db.Authorizer;
import com.captainalm.mesh.db.TheDatabase;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Security;
import java.util.Random;
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
    private String errorNotifID;

     // Adapted from:
     // https://stackoverflow.com/questions/2584401/how-to-add-bouncy-castle-algorithm-to-android/66323575#66323575
     // (satur9nine and Mendhak)
    static {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void onCreate() {
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
        authorizer = new Authorizer(database);
        super.onCreate();
        errorNotifID = makeErrorChannel(errorNotifID);
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

    public void showException(Exception e) {
        if (errorNotifID == null || e == null)
            return;
        StringWriter esw = new StringWriter();
        e.printStackTrace(new PrintWriter(esw));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, errorNotifID)
                .setContentTitle(e.getClass().toString()).setContentText(e.getClass() + "\n"
                + e.getMessage()).setStyle(new NotificationCompat.BigTextStyle().bigText(
                        e.getClass() + "\n"  + e.getMessage() + "\n" + esw)).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(new Random().nextInt(8) + 100, builder.build());
    }
}
