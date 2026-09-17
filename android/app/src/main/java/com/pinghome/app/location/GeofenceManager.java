package com.pinghome.app.location;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Registers/removes the single destination geofence for the current ACTIVE trip. Using the
 * Android Geofencing API (backed by Google Play services' passive location tracking) instead of
 * an app-driven GPS polling loop is deliberate - it is dramatically cheaper on battery and is
 * exactly what SPEC.md calls for ("use Android geofencing rather than unnecessary continuous
 * GPS polling").
 */
public class GeofenceManager {

    private static final String TAG = "GeofenceManager";
    public static final String GEOFENCE_REQUEST_ID_PREFIX = "pinghome-trip-";

    private final Context appContext;
    private final GeofencingClient geofencingClient;

    public GeofenceManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(appContext);
    }

    @SuppressLint("MissingPermission") // Caller verifies ACCESS_FINE_LOCATION (+ background on API 29+) first.
    public void registerArrivalGeofence(long tripId, double latitude, double longitude, int radiusMeters) {
        Geofence geofence = new Geofence.Builder()
                .setRequestId(GEOFENCE_REQUEST_ID_PREFIX + tripId)
                .setCircularRegion(latitude, longitude, radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                // Reduces flapping/duplicate ENTER events from GPS noise right at the boundary.
                .setLoiteringDelay(10_000)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        geofencingClient.addGeofences(request, geofencePendingIntent(tripId))
                .addOnSuccessListener(unused -> Log.i(TAG, "Registered arrival geofence for trip " + tripId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to register geofence for trip " + tripId, e));
    }

    public void removeArrivalGeofence(long tripId) {
        geofencingClient.removeGeofences(geofencePendingIntent(tripId))
                .addOnCompleteListener(task -> Log.i(TAG, "Removed arrival geofence for trip " + tripId));
    }

    private PendingIntent geofencePendingIntent(long tripId) {
        Intent intent = new Intent(appContext, ArrivalGeofenceReceiver.class);
        intent.putExtra(ArrivalGeofenceReceiver.EXTRA_TRIP_ID, tripId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(appContext, (int) tripId, intent, flags);
    }
}
