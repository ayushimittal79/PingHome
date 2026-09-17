package com.pinghome.app.notification;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pinghome.app.R;
import com.pinghome.app.data.repository.AuthRepository;
import com.pinghome.app.ui.main.MainActivity;
import com.pinghome.app.ui.trip.ActiveTripActivity;

import java.util.Map;

/**
 * Receives all PingHome push notifications: safety nudges, escalation/watcher alerts, and trip
 * completion. FCM is the primary channel per SPEC.md; Twilio SMS is a backend-side fallback
 * this service never needs to know about. Delivery is best-effort - if the process is dead and
 * Play services can't wake it, or the device has no connectivity, the push simply doesn't arrive,
 * which is why escalation state itself lives on the backend rather than depending on this class.
 */
public class PingHomeFirebaseMessagingService extends FirebaseMessagingService {

    private static final int NOTIFICATION_ID_GENERAL = 100;

    @Override
    public void onNewToken(String token) {
        new AuthRepository(this).registerFcmToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        RemoteMessage.Notification notification = message.getNotification();
        Map<String, String> data = message.getData();
        String type = data.getOrDefault("type", "");

        String title = notification != null ? notification.getTitle() : titleForType(type);
        String body = notification != null ? notification.getBody() : "";

        boolean critical = type.equals("ESCALATION") || type.equals("SOS") || type.equals("WATCHER_ALERT");
        String channel = critical
                ? getString(R.string.notification_channel_escalation_id)
                : getString(R.string.notification_channel_safety_id);

        Intent target = new Intent(this, data.containsKey("tripId") ? ActiveTripActivity.class : MainActivity.class);
        target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (data.containsKey("tripId")) {
            target.putExtra(ActiveTripActivity.EXTRA_TRIP_ID, Long.parseLong(data.get("tripId")));
        }

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, target,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(critical ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent);

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_GENERAL, builder.build());
        }
    }

    private String titleForType(String type) {
        return switch (type) {
            case "NUDGE" -> "Still safe?";
            case "ESCALATION" -> "Safety alert";
            case "WATCHER_ALERT" -> "Watcher alert";
            case "TRIP_COMPLETE" -> "Trip completed";
            default -> "PingHome";
        };
    }
}
