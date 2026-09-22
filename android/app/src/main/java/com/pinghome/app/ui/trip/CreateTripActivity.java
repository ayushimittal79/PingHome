package com.pinghome.app.ui.trip;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.dto.CreateTripRequest;
import com.pinghome.app.data.api.dto.WatcherAssignment;
import com.pinghome.app.data.geo.OsmGeoService;
import com.pinghome.app.data.geo.Place;
import com.pinghome.app.data.geo.RouteEstimate;
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

    /** Added on top of the routed travel time so a normal delay doesn't trigger a nudge. */
    private static final int ETA_BUFFER_MINUTES = 5;

    private MapView mapView;
    private Marker destinationMarker;
    private GeoPoint selectedDestination;

    private ContactRepository contactRepository;
    private TripRepository tripRepository;
    private OsmGeoService geoService;
    private FusedLocationProviderClient fusedLocationClient;
    private WatcherSelectionAdapter watcherAdapter;

    private EditText destinationNameInput;
    private EditText searchInput;
    private EditText gracePeriodInput;
    private MaterialButton pickTimeButton;
    private MaterialButtonToggleGroup modeToggle;
    private TextView estimateText;
    private TextView errorText;
    private TextView noContactsText;

    private ZonedDateTime expectedArrival;
    /** True once the traveller has picked a time by hand; auto-estimates then stop overriding it. */
    private boolean arrivalSetManually = false;
    private boolean searchInFlight = false;
    private int estimateRequestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_trip);
        setTitle("New trip");

        contactRepository = new ContactRepository(this);
        tripRepository = new TripRepository(this);
        geoService = new OsmGeoService(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        destinationNameInput = findViewById(R.id.destinationNameInput);
        searchInput = findViewById(R.id.searchInput);
        gracePeriodInput = findViewById(R.id.gracePeriodInput);
        pickTimeButton = findViewById(R.id.pickTimeButton);
        modeToggle = findViewById(R.id.modeToggle);
        estimateText = findViewById(R.id.estimateText);
        errorText = findViewById(R.id.errorText);
        noContactsText = findViewById(R.id.noContactsText);

        pickTimeButton.setOnClickListener(v -> pickExpectedArrival());
        findViewById(R.id.startTripButton).setOnClickListener(v -> validateAndPreview());

        TextInputLayout searchLayout = findViewById(R.id.searchLayout);
        searchLayout.setEndIconOnClickListener(v -> searchPlace());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterUp = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterUp) searchPlace();
            return true; // consume both DOWN and UP so a hardware Enter can't double-fire
        });
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && selectedDestination != null) estimateTravelTime();
        });

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

    /** Map tap: place the marker, then look up what's there to pre-fill the name. */
    private void selectDestination(GeoPoint point) {
        placeDestination(point);
        geoService.reverse(point.getLatitude(), point.getLongitude(), result -> {
            if (result.success && destinationNameInput.getText().toString().trim().isEmpty()) {
                destinationNameInput.setText(result.data.shortName);
            }
        });
    }

    private void placeDestination(GeoPoint point) {
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
        estimateTravelTime();
    }

    /** Typed destination: geocode it and let the traveller confirm which real place they meant. */
    private void searchPlace() {
        String query = searchInput.getText().toString().trim();
        // The Enter key can deliver the editor action twice; never run two searches at once.
        if (query.isEmpty() || searchInFlight) return;
        searchInFlight = true;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
        estimateText.setText("Searching for \"" + query + "\"...");
        withLastLocation(location -> geoService.search(query,
                location == null ? null : location.getLatitude(),
                location == null ? null : location.getLongitude(),
                result -> {
                    searchInFlight = false;
                    if (!result.success) {
                        estimateText.setText(result.errorMessage);
                        return;
                    }
                    List<Place> places = result.data;
                    String[] labels = new String[places.size()];
                    for (int i = 0; i < places.size(); i++) labels[i] = places.get(i).displayName;
                    new AlertDialog.Builder(this)
                            .setTitle("Which place?")
                            .setItems(labels, (dialog, which) -> {
                                Place place = places.get(which);
                                destinationNameInput.setText(place.shortName);
                                searchInput.setText(place.shortName);
                                placeDestination(new GeoPoint(place.latitude, place.longitude));
                            })
                            .setNegativeButton(android.R.string.cancel, (d, w) ->
                                    estimateText.setText("Pick a destination to estimate travel time."))
                            .show();
                }));
    }

    /** Routes from the current position to the destination and proposes an arrival time. */
    private void estimateTravelTime() {
        if (selectedDestination == null) return;
        int requestId = ++estimateRequestId;
        RouteEstimate.Mode mode = modeToggle.getCheckedButtonId() == R.id.modeWalking
                ? RouteEstimate.Mode.WALKING : RouteEstimate.Mode.DRIVING;
        estimateText.setText("Estimating travel time...");
        withLastLocation(location -> {
            if (location == null) {
                estimateText.setText("Couldn't get your current location - set the arrival time manually.");
                return;
            }
            geoService.estimateRoute(location.getLatitude(), location.getLongitude(),
                    selectedDestination.getLatitude(), selectedDestination.getLongitude(), mode, result -> {
                        if (requestId != estimateRequestId) return; // a newer estimate superseded this one
                        if (!result.success) {
                            estimateText.setText(result.errorMessage);
                            return;
                        }
                        RouteEstimate estimate = result.data;
                        int minutes = Math.max(1, (int) Math.ceil(estimate.durationSeconds / 60.0));
                        ZonedDateTime suggested = ZonedDateTime.now().plusMinutes(minutes + ETA_BUFFER_MINUTES);
                        String modeLabel = mode == RouteEstimate.Mode.WALKING ? "walking" : "driving";
                        String summary = String.format(java.util.Locale.getDefault(),
                                "About %d min %s (%.1f km)", minutes, modeLabel, estimate.distanceMeters / 1000.0);
                        if (arrivalSetManually) {
                            estimateText.setText(summary + " - keeping the time you set.");
                        } else {
                            expectedArrival = suggested;
                            pickTimeButton.setText("Arrive by " + DISPLAY_FORMAT.format(expectedArrival) + " (suggested)");
                            estimateText.setText(summary + " + " + ETA_BUFFER_MINUTES + " min buffer. Tap the time to change it.");
                        }
                    });
        });
    }

    private interface LocationConsumer { void accept(Location location); }

    @SuppressLint("MissingPermission")
    private void withLastLocation(LocationConsumer consumer) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            consumer.accept(null);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(consumer::accept)
                .addOnFailureListener(e -> consumer.accept(null));
    }

    private void pickExpectedArrival() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                expectedArrival = ZonedDateTime.ofInstant(Instant.ofEpochMilli(picked.getTimeInMillis()), ZoneId.systemDefault());
                arrivalSetManually = true;
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
