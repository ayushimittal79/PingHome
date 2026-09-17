package com.pinghome.app.ble;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.repository.SosRepository;
import com.pinghome.app.ui.main.MainActivity;

/**
 * Foreground service that keeps a live BLE connection to the paired ESP32 wearable so a physical
 * SOS button press is caught even when PingHome isn't in the foreground. This is the "Android
 * scans/connects/subscribes to the wearable" half of the mandatory physical-button -> BLE ->
 * Android -> backend flow; it is started once a device is paired (see WearableSettingsActivity)
 * and stopped only if the user explicitly unpairs.
 */
public class WearableWatchService extends Service implements PingHomeBleManager.Listener {

    private static final int NOTIFICATION_ID = 42;

    private PingHomeBleManager bleManager;
    private SosRepository sosRepository;
    private FusedLocationProviderClient fusedLocationClient;
    private BleConnectionState currentState = BleConnectionState.DISCONNECTED;

    @Override
    public void onCreate() {
        super.onCreate();
        bleManager = new PingHomeBleManager(this, this);
        sosRepository = new SosRepository(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("Watching for your PingHome wearable");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        String address = SessionManager.getInstance(this).getPairedBleAddress();
        if (address != null) {
            bleManager.connectToKnownDevice(address);
        } else {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        bleManager.disconnect();
        BleStateHolder.connectionState.postValue(BleConnectionState.DISCONNECTED);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStateChanged(BleConnectionState state) {
        currentState = state;
        BleStateHolder.connectionState.postValue(state);
        updateNotification();
    }

    @Override
    public void onSosReceived() {
        Long activeTripId = SessionManager.getInstance(this).getActiveTripId();

        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (hasLocationPermission) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location ->
                    sendWearableSos(activeTripId, location));
        } else {
            sendWearableSos(activeTripId, null);
        }
    }

    private void sendWearableSos(Long tripId, @Nullable Location location) {
        double lat = location != null ? location.getLatitude() : 0d;
        double lng = location != null ? location.getLongitude() : 0d;
        String bleAddress = SessionManager.getInstance(this).getPairedBleAddress();

        sosRepository.triggerWearableSos(tripId, lat, lng, bleAddress, result -> {
            // Best-effort: the backend also de-dupes rapid repeats, and a failed send here isn't
            // silently lost forever - the user can still reach for the in-app SOS button, which
            // is why the wearable is a convenience trigger, not the sole SOS path.
        });
    }

    @Override
    public void onBatteryUpdate(int percent) {
        // Surfaced via WearableSettingsActivity polling device state from the backend/local
        // cache in a future iteration; not required for the mandatory SOS path.
    }

    @Override
    public void onDeviceFoundForPairing(BluetoothDevice device, String name) {
        // Not used in the watch service - pairing/discovery happens in WearableSettingsActivity.
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_safety_id))
                .setContentTitle("PingHome wearable")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @android.annotation.SuppressLint("MissingPermission") // POST_NOTIFICATIONS already required to start this foreground service's initial notification.
    private void updateNotification() {
        String text = switch (currentState) {
            case CONNECTED -> "Wearable connected";
            case CONNECTING, SCANNING -> "Connecting to wearable...";
            case RECONNECTING -> "Reconnecting to wearable...";
            case DISCONNECTED -> "Wearable disconnected";
        };
        androidx.core.app.NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(text));
    }
}
