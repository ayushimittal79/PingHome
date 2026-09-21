package com.pinghome.app.ui.trip;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.dto.CreateTripRequest;
import com.pinghome.app.data.api.dto.WatcherAssignment;
import com.pinghome.app.data.repository.ContactRepository;
import com.pinghome.app.data.repository.TripRepository;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

public class CreateTripActivity extends AppCompatActivity {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private MapView mapView;
    private Marker destinationMarker;
    private GeoPoint selectedDestination;

    private ContactRepository contactRepository;
    private TripRepository tripRepository;
    private WatcherSelectionAdapter watcherAdapter;

    private EditText destinationNameInput;
    private EditText gracePeriodInput;
    private MaterialButton pickTimeButton;
    private TextView errorText;
    private TextView noContactsText;

    private ZonedDateTime expectedArrival;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_trip);
        setTitle("New trip");

        contactRepository = new ContactRepository(this);
        tripRepository = new TripRepository(this);

        destinationNameInput = findViewById(R.id.destinationNameInput);
        gracePeriodInput = findViewById(R.id.gracePeriodInput);
        pickTimeButton = findViewById(R.id.pickTimeButton);
        errorText = findViewById(R.id.errorText);
        noContactsText = findViewById(R.id.noContactsText);

        pickTimeButton.setOnClickListener(v -> pickExpectedArrival());
        findViewById(R.id.startTripButton).setOnClickListener(v -> validateAndPreview());

        RecyclerView watcherList = findViewById(R.id.watcherRecyclerView);
        watcherList.setLayoutManager(new LinearLayoutManager(this));
        watcherAdapter = new WatcherSelectionAdapter();
        watcherList.setAdapter(watcherAdapter);
        loadContacts();

        setUpMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    private void loadContacts() {
        contactRepository.list(result -> {
            if (result.success) {
                watcherAdapter.submitList(result.data);
                noContactsText.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setUpMap() {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        // The map sits inside a NestedScrollView; without this, vertical drags scroll the page
        // instead of panning the map.
        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        // World view until the traveller taps a destination.
        mapView.getController().setZoom(2.5);
        mapView.getController().setCenter(new GeoPoint(20.0, 0.0));

        mapView.getOverlays().add(new CopyrightOverlay(this));
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                selectDestination(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));
    }

    private void selectDestination(GeoPoint point) {
        selectedDestination = point;
        if (destinationMarker == null) {
            destinationMarker = new Marker(mapView);
            destinationMarker.setTitle("Destination");
            destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(destinationMarker);
        }
        destinationMarker.setPosition(point);
        mapView.getController().setZoom(15.0);
        mapView.getController().animateTo(point);
        mapView.invalidate();
    }

    private void pickExpectedArrival() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                expectedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(picked.getTimeInMillis()), ZoneId.systemDefault());
                pickTimeButton.setText("Arrive by " + DISPLAY_FORMAT.format(expectedArrival));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void validateAndPreview() {
        String destinationName = destinationNameInput.getText().toString().trim();
        String gracePeriodText = gracePeriodInput.getText().toString().trim();
        List<WatcherAssignment> watchers = watcherAdapter.getSelectedWatchers();

        if (selectedDestination == null) {
            showError("Tap the map to choose a destination.");
            return;
        }
        if (destinationName.isEmpty()) {
            showError("Give your destination a name.");
            return;
        }
        if (expectedArrival == null || expectedArrival.isBefore(ZonedDateTime.now())) {
            showError("Pick an expected arrival time in the future.");
            return;
        }
        if (gracePeriodText.isEmpty()) {
            showError("Set a grace period.");
            return;
        }
        int gracePeriod;
        try {
            gracePeriod = Integer.parseInt(gracePeriodText);
        } catch (NumberFormatException e) {
            showError("Grace period must be a number.");
            return;
        }
        if (watchers.isEmpty()) {
            showError("Select at least one watcher for this trip.");
            return;
        }
        errorText.setVisibility(View.GONE);

        String summary = "Destination: " + destinationName
                + "\nArrive by: " + DISPLAY_FORMAT.format(expectedArrival)
                + "\nGrace period: " + gracePeriod + " min"
                + "\nWatchers: " + watchers.size();

        new AlertDialog.Builder(this)
                .setTitle("Confirm trip")
                .setMessage(summary)
                .setPositiveButton("Start trip", (dialog, which) ->
                        createTrip(destinationName, gracePeriod, watchers))
                .setNegativeButton("Edit", null)
                .show();
    }

    private void createTrip(String destinationName, int gracePeriod, List<WatcherAssignment> watchers) {
        CreateTripRequest request = new CreateTripRequest(
                destinationName, selectedDestination.getLatitude(), selectedDestination.getLongitude(),
                expectedArrival.toInstant(), gracePeriod, null, watchers);

        tripRepository.createTrip(request, result -> {
            if (result.success) {
                SessionManager.getInstance(this).setActiveTripId(result.data.id);
                Intent intent = new Intent(this, ActiveTripActivity.class);
                intent.putExtra(ActiveTripActivity.EXTRA_TRIP_ID, result.data.id);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
