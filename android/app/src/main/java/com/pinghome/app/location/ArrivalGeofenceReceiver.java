package com.pinghome.app.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.repository.TripRepository;

import java.util.List;

/**
 * Fires on destination-geofence ENTER for an ACTIVE trip. Calls the backend's idempotent
 * complete-arrival endpoint - duplicate ENTER events (GPS jitter re-triggering at the boundary,
 * Play services redelivering) are handled there, not with ad-hoc client-side debouncing, so the
 * single source of truth for "did this trip complete" stays the server.
 */
public class ArrivalGeofenceReceiver extends BroadcastReceiver {

    private static final String TAG = "ArrivalGeofenceReceiver";
    public static final String EXTRA_TRIP_ID = "trip_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) return;

        if (event.hasError()) {
            Log.e(TAG, "Geofence event error code: " + event.getErrorCode());
            return;
        }
        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) {
            return;
        }

        List<Geofence> triggering = event.getTriggeringGeofences();
        if (triggering == null || triggering.isEmpty()) return;

        for (Geofence geofence : triggering) {
            Long tripId = parseTripId(geofence.getRequestId());
            if (tripId == null) continue;

            PendingResult pendingResult = goAsync();
            TripRepository tripRepository = new TripRepository(context);
            tripRepository.completeArrival(tripId, result -> {
                if (result.success) {
                    Log.i(TAG, "Trip " + tripId + " auto-completed via arrival geofence");
                    SessionManager.getInstance(context).setActiveTripId(null);
                    new GeofenceManager(context).removeArrivalGeofence(tripId);
                } else {
                    Log.w(TAG, "Failed to complete arrival for trip " + tripId + ": " + result.errorMessage);
                }
                pendingResult.finish();
            });
        }
    }

    private Long parseTripId(String requestId) {
        if (requestId == null || !requestId.startsWith(GeofenceManager.GEOFENCE_REQUEST_ID_PREFIX)) return null;
        try {
            return Long.parseLong(requestId.substring(GeofenceManager.GEOFENCE_REQUEST_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
