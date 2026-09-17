package com.pinghome.app.ui.watcher;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.TripResponse;
import com.pinghome.app.data.repository.TripRepository;
import com.pinghome.app.util.StatusPillHelper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WatcherTripDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "trip_id";
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private TripRepository tripRepository;
    private long tripId;
    private GoogleMap map;

    private TextView statusPill, destinationText, expectedArrivalText, locationHintText, noAlertsText;
    private AlertListAdapter alertAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watcher_trip_detail);
        setTitle("Trip detail");

        tripId = getIntent().getLongExtra(EXTRA_TRIP_ID, -1);
        tripRepository = new TripRepository(this);

        statusPill = findViewById(R.id.statusPill);
        destinationText = findViewById(R.id.destinationText);
        expectedArrivalText = findViewById(R.id.expectedArrivalText);
        locationHintText = findViewById(R.id.locationHintText);
        noAlertsText = findViewById(R.id.noAlertsText);

        RecyclerView alertsList = findViewById(R.id.alertsRecyclerView);
        alertsList.setLayoutManager(new LinearLayoutManager(this));
        alertAdapter = new AlertListAdapter(alert -> tripRepository.resolveAlert(alert.id, result -> {
            if (result.success) {
                Toast.makeText(this, "Alert marked resolved.", Toast.LENGTH_SHORT).show();
                loadAlerts();
            } else {
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            }
        }));
        alertsList.setAdapter(alertAdapter);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                map = googleMap;
                loadLocation();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrip();
        loadAlerts();
        loadLocation();
    }

    private void loadTrip() {
        tripRepository.getTrip(tripId, result -> {
            if (!result.success) {
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            TripResponse trip = result.data;
            StatusPillHelper.apply(statusPill, this, trip.status);
            destinationText.setText(trip.destinationName);
            expectedArrivalText.setText("Expected by " + DISPLAY_FORMAT.format(trip.expectedArrival));
        });
    }

    private void loadAlerts() {
        tripRepository.alertsForTrip(tripId, result -> {
            if (result.success) {
                alertAdapter.submitList(result.data);
                noAlertsText.setVisibility(result.data.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });
    }

    private void loadLocation() {
        if (map == null) return;
        tripRepository.latestLocation(tripId, result -> {
            if (result.success && result.data != null) {
                LatLng position = new LatLng(result.data.latitude, result.data.longitude);
                map.clear();
                map.addMarker(new MarkerOptions().position(position).title("Last known location"));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15));
                locationHintText.setText("Last updated " + DISPLAY_FORMAT.format(result.data.timestamp));
            } else {
                locationHintText.setText("Location isn't shared for this trip right now.");
            }
        });
    }
}
