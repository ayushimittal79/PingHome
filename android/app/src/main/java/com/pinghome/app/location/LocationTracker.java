package com.pinghome.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;

import com.google.android.gms.location.*;
import com.pinghome.app.data.repository.TripRepository;

/**
 * Pushes periodic position samples to the backend while a trip's Active Trip screen is open, so
 * LOCATION_DURING_TRIP watchers have something recent to see. Deliberately NOT a background
 * foreground-tracking service: arrival detection - the thing that must keep working when the
 * app isn't open - is owned by GeofenceManager/ArrivalGeofenceReceiver instead. This keeps the
 * "no unnecessary continuous GPS polling" rule in SPEC.md: coarse periodic samples only while
 * the traveller has the trip screen open, nothing tracked before or after the trip.
 */
public class LocationTracker {

    private static final long UPDATE_INTERVAL_MS = 30_000;
    private static final long FASTEST_INTERVAL_MS = 15_000;

    private final FusedLocationProviderClient client;
    private final TripRepository tripRepository;
    private LocationCallback activeCallback;
    private long trackedTripId = -1;

    public LocationTracker(Context context) {
        this.client = LocationServices.getFusedLocationProviderClient(context.getApplicationContext());
        this.tripRepository = new TripRepository(context.getApplicationContext());
    }

    @SuppressLint("MissingPermission") // Caller verifies ACCESS_FINE_LOCATION before starting.
    public void start(long tripId) {
        stop();
        trackedTripId = tripId;

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .build();

        activeCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result.getLastLocation() == null) return;
                tripRepository.pushLocationBestEffort(tripId,
                        result.getLastLocation().getLatitude(), result.getLastLocation().getLongitude());
            }
        };

        client.requestLocationUpdates(request, activeCallback, Looper.getMainLooper());
    }

    public void stop() {
        if (activeCallback != null) {
            client.removeLocationUpdates(activeCallback);
            activeCallback = null;
        }
        trackedTripId = -1;
    }
}
