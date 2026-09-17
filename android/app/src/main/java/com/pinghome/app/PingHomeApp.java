package com.pinghome.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class PingHomeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
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
