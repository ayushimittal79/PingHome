package com.pinghome.app.ui.fakecall;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;

/**
 * Schedules the simulated incoming call. A background service cannot reliably launch an Activity
 * directly on Android 10+ (background-activity-start restrictions), so this posts a high-priority
 * notification with a full-screen intent instead - the same mechanism real dialer/VoIP apps use
 * for incoming calls, which is exactly the effect PingHome wants here. Purely local: no network
 * call is made and no safety alert is ever created by this feature.
 */
public class FakeCallService extends Service {

    private static final int NOTIFICATION_ID = 77;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int delaySeconds = SessionManager.getInstance(this).getFakeCallDelaySeconds();

        Notification quiet = buildQuietNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, quiet, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, quiet);
        }
        handler.postDelayed(this::showIncomingCall, delaySeconds * 1000L);
        return START_NOT_STICKY;
    }

    private void showIncomingCall() {
        String callerName = SessionManager.getInstance(this).getFakeCallCallerName();

        Intent fullScreenIntent = new Intent(this, FakeCallActivity.class);
        fullScreenIntent.putExtra(FakeCallActivity.EXTRA_CALLER_NAME, callerName);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, getString(R.string.notification_channel_fakecall_id))
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Incoming call")
                .setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .build();

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification buildQuietNotification() {
        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_fakecall_id))
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("PingHome")
                .setContentText("Preparing a simulated call...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
