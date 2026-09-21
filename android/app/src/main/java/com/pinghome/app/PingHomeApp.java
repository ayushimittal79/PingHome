package com.pinghome.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.pinghome.app.ui.main.ThemePrefs;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public class PingHomeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.applySaved(this);
        configureMapTiles();
        createNotificationChannels();
    }

    private void configureMapTiles() {
        IConfigurationProvider config = Configuration.getInstance();
        // OSM's tile usage policy requires an identifying User-Agent; the default one gets blocked.
        config.setUserAgentValue(BuildConfig.APPLICATION_ID);
        // Keep the tile cache in app-private storage so no storage permission is ever needed.
        File osmDir = new File(getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(osmDir);
        config.setOsmdroidTileCache(new File(osmDir, "tiles"));
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel safety = new NotificationChannel(
                getString(R.string.notification_channel_safety_id),
                getString(R.string.notification_channel_safety_name),
                NotificationManager.IMPORTANCE_DEFAULT);

        NotificationChannel escalation = new NotificationChannel(
                getString(R.string.notification_channel_escalation_id),
                getString(R.string.notification_channel_escalation_name),
                NotificationManager.IMPORTANCE_HIGH);

        NotificationChannel fakeCall = new NotificationChannel(
                getString(R.string.notification_channel_fakecall_id),
                getString(R.string.notification_channel_fakecall_name),
                NotificationManager.IMPORTANCE_HIGH);

        manager.createNotificationChannel(safety);
        manager.createNotificationChannel(escalation);
        manager.createNotificationChannel(fakeCall);
    }
}
