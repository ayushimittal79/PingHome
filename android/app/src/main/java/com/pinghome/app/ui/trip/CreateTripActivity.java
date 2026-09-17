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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.dto.CreateTripRequest;
import com.pinghome.app.data.api.dto.WatcherAssignment;
import com.pinghome.app.data.repository.ContactRepository;
import com.pinghome.app.data.repository.TripRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

public class CreateTripActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private GoogleMap map;
    private LatLng selectedDestination;

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

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void loadContacts() {
        contactRepository.list(result -> {
            if (result.success) {
                watcherAdapter.submitList(result.data);
                noContactsText.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        // Reasonable default camera (world view) until the traveller taps a destination.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(20, 0), 2));
        map.setOnMapClickListener(latLng -> {
            selectedDestination = latLng;
            map.clear();
            map.addMarker(new MarkerOptions().position(latLng).title("Destination"));
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        });
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
                destinationName, selectedDestination.latitude, selectedDestination.longitude,
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
