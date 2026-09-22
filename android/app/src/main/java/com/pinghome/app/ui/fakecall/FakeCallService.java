package com.pinghome.app.ui.fakecall;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
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

    private static final int PREPARING_NOTIFICATION_ID = 77;
    // Must differ from the foreground-service id: stopForeground(STOP_FOREGROUND_REMOVE) removes
    // that one, and would otherwise cancel the incoming-call notification the moment it's posted.
    private static final int CALL_NOTIFICATION_ID = 78;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int delaySeconds = SessionManager.getInstance(this).getFakeCallDelaySeconds();

        Notification quiet = buildQuietNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(PREPARING_NOTIFICATION_ID, quiet, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(PREPARING_NOTIFICATION_ID, quiet);
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
            NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, notification);
        }

        // While the app is on screen a full-screen intent only appears as a heads-up banner, and on
        // Android 14+ it is blocked entirely until the user grants "full-screen notifications" - so
        // open the call screen directly in that case (allowed because the app is in the foreground).
        // The notification above still covers the locked / backgrounded case.
        if (isAppInForeground() || !canUseFullScreenIntent()) {
            startActivity(fullScreenIntent);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true;
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.canUseFullScreenIntent();
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
