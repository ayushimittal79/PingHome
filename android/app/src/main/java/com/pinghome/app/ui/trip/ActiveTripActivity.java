package com.pinghome.app.ui.trip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.ble.BleConnectionState;
import com.pinghome.app.ble.BleStateHolder;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.dto.TripResponse;
import com.pinghome.app.data.api.dto.TripStatus;
import com.pinghome.app.data.repository.SosRepository;
import com.pinghome.app.data.repository.TripRepository;
import com.pinghome.app.location.GeofenceManager;
import com.pinghome.app.location.LocationTracker;
import com.pinghome.app.ui.components.LiveBadgeView;
import com.pinghome.app.ui.components.SosButtonView;
import com.pinghome.app.ui.fakecall.FakeCallService;
import com.pinghome.app.util.StatusPillHelper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The traveller's mission-control screen for an ACTIVE trip: status, manual "I'm Safe", manual
 * SOS, fake incoming call, and wearable connection status. Arrival is detected passively via
 * GeofenceManager/ArrivalGeofenceReceiver even if this screen (or the app) is closed - this
 * activity's job is registering that geofence once, not re-registering it on every open.
 */
public class ActiveTripActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "trip_id";
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private TripRepository tripRepository;
    private SosRepository sosRepository;
    private GeofenceManager geofenceManager;
    private LocationTracker locationTracker;
    private FusedLocationProviderClient fusedLocationClient;

    private long tripId;
    private TripResponse currentTrip;
    private boolean sosInFlight = false;

    private TextView statusPill, destinationText, expectedArrivalText, wearableStatusText;
    private View wearableDot;
    private LiveBadgeView liveBadge;
    private MaterialButton imSafeButton, cancelTripButton;
    private SosButtonView sosButton;

    private boolean backgroundLocationAsked = false;

    private final ActivityResultLauncher<String> backgroundLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    registerGeofenceIfPossible();
                } else {
                    // Ask once per screen; re-requesting from inside the result callback would loop.
                    Toast.makeText(this, "Automatic arrival detection needs location set to \"Allow all the time\". "
                            + "You can still tap I'm Safe when you arrive.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripId = getIntent().getLongExtra(EXTRA_TRIP_ID, -1);
        if (tripId == -1) {
            finish();
            return;
        }

        tripRepository = new TripRepository(this);
        sosRepository = new SosRepository(this);
        geofenceManager = new GeofenceManager(this);
        locationTracker = new LocationTracker(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        statusPill = findViewById(R.id.statusPill);
        destinationText = findViewById(R.id.destinationText);
        expectedArrivalText = findViewById(R.id.expectedArrivalText);
        wearableStatusText = findViewById(R.id.wearableStatusText);
        wearableDot = findViewById(R.id.wearableDot);
        liveBadge = findViewById(R.id.liveBadge);
        imSafeButton = findViewById(R.id.imSafeButton);
        sosButton = findViewById(R.id.sosButton);
        cancelTripButton = findViewById(R.id.cancelTripButton);

        imSafeButton.setOnClickListener(v -> confirmSafe());
        sosButton.setOnClickListener(v -> triggerSos());
        cancelTripButton.setOnClickListener(v -> confirmCancel());
        findViewById(R.id.fakeCallButton).setOnClickListener(v -> startFakeCall());

        BleStateHolder.connectionState.observe(this, this::renderWearableState);

        loadTrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrip();
    }

    private void loadTrip() {
        tripRepository.getTrip(tripId, result -> {
            if (!result.success) {
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
                return;
            }
            currentTrip = result.data;
            render(currentTrip);

            if (currentTrip.status == TripStatus.ACTIVE) {
                registerGeofenceIfPossible();
                if (hasFineLocation()) locationTracker.start(tripId);
            } else {
                locationTracker.stop();
                geofenceManager.removeArrivalGeofence(tripId);
                if (SessionManager.getInstance(this).getActiveTripId() != null
                        && SessionManager.getInstance(this).getActiveTripId() == tripId) {
                    SessionManager.getInstance(this).setActiveTripId(null);
                }
            }
        });
    }

    private void render(TripResponse trip) {
        StatusPillHelper.apply(statusPill, this, trip.status);
        destinationText.setText(trip.destinationName);
        expectedArrivalText.setText("Expected by " + DISPLAY_FORMAT.format(trip.expectedArrival));

        boolean active = trip.status == TripStatus.ACTIVE;
        liveBadge.setLive(active);
        imSafeButton.setEnabled(active);
        cancelTripButton.setEnabled(active);
        sosButton.setEnabled(active || trip.status == TripStatus.ESCALATED);
    }

    private void renderWearableState(BleConnectionState state) {
        int colorRes;
        String text;
        switch (state) {
            case CONNECTED -> { colorRes = R.color.safe_green; text = "Wearable connected"; }
            case CONNECTING, SCANNING -> { colorRes = R.color.warning_amber; text = "Connecting to wearable..."; }
            case RECONNECTING -> { colorRes = R.color.warning_amber; text = "Reconnecting to wearable..."; }
            default -> { colorRes = R.color.neutral_pill; text = "Wearable not connected"; }
        }
        wearableDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(colorRes)));
        wearableStatusText.setText(text);
    }

    private void confirmSafe() {
        imSafeButton.setEnabled(false);
        tripRepository.confirmSafe(tripId, result -> {
            if (result.success) {
                currentTrip = result.data;
                render(currentTrip);
                locationTracker.stop();
                geofenceManager.removeArrivalGeofence(tripId);
                SessionManager.getInstance(this).setActiveTripId(null);
                Toast.makeText(this, "Marked safe. Trip completed.", Toast.LENGTH_SHORT).show();
            } else {
                imSafeButton.setEnabled(true);
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void triggerSos() {
        if (sosInFlight) return; // Client-side guard against accidental double-tap, on top of server-side dedup.
        sosInFlight = true;
        sosButton.setEnabled(false);

        Runnable resetButton = () -> {
            sosInFlight = false;
            if (currentTrip != null) sosButton.setEnabled(currentTrip.status == TripStatus.ACTIVE || currentTrip.status == TripStatus.ESCALATED);
        };

        if (hasFineLocation()) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                double lat = location != null ? location.getLatitude() : 0d;
                double lng = location != null ? location.getLongitude() : 0d;
                sendSos(lat, lng, resetButton);
            }).addOnFailureListener(e -> sendSos(0, 0, resetButton));
        } else {
            sendSos(0, 0, resetButton);
        }
    }

    private void sendSos(double lat, double lng, Runnable onDone) {
        sosRepository.triggerManualSos(tripId, lat, lng, result -> {
            onDone.run();
            if (result.success) {
                Toast.makeText(this, "SOS sent. Your watchers are being notified.", Toast.LENGTH_LONG).show();
                loadTrip();
            } else {
                Toast.makeText(this, "SOS failed to send: " + result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel this trip?")
                .setMessage("Your watchers will no longer be notified for this trip.")
                .setPositiveButton("Cancel trip", (dialog, which) ->
                        tripRepository.cancelTrip(tripId, result -> {
                            if (result.success) {
                                locationTracker.stop();
                                geofenceManager.removeArrivalGeofence(tripId);
                                SessionManager.getInstance(this).setActiveTripId(null);
                                finish();
                            } else {
                                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Keep trip", null)
                .show();
    }

    private void startFakeCall() {
        Intent intent = new Intent(this, FakeCallService.class);
        ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, "Simulated call incoming shortly...", Toast.LENGTH_SHORT).show();
    }

    private boolean hasFineLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void registerGeofenceIfPossible() {
        if (currentTrip == null || currentTrip.status != TripStatus.ACTIVE) return;
        if (!hasFineLocation()) return;

        boolean hasBackground = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasBackground) {
            // Two-step permission flow required on Android 10+: foreground location must already
            // be granted (checked above) before requesting background location separately.
            if (!backgroundLocationAsked) {
                backgroundLocationAsked = true;
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
            return;
        }

        geofenceManager.registerArrivalGeofence(tripId, currentTrip.latitude, currentTrip.longitude, currentTrip.geofenceRadiusMeters);
    }
}
