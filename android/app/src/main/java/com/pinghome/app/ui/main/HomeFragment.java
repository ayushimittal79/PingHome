package com.pinghome.app.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.api.dto.TripResponse;
import com.pinghome.app.data.api.dto.TripStatus;
import com.pinghome.app.data.repository.SosRepository;
import com.pinghome.app.data.repository.TripRepository;
import com.pinghome.app.ui.components.LiveBadgeView;
import com.pinghome.app.ui.components.SosButtonView;
import com.pinghome.app.ui.fakecall.FakeCallService;
import com.pinghome.app.ui.trip.ActiveTripActivity;
import com.pinghome.app.ui.trip.CreateTripActivity;
import com.pinghome.app.util.StatusPillHelper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The SOS-first landing screen: pulsing SOS button, Fake Call / trip quick actions, and the
 * current trip (if any). SOS here works with or without an active trip - the backend accepts a
 * standalone SOS and notifies the Circle either way.
 */
public class HomeFragment extends Fragment {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private TripRepository tripRepository;
    private SosRepository sosRepository;
    private FusedLocationProviderClient fusedLocationClient;

    private LiveBadgeView liveBadge;
    private SosButtonView sosButton;
    private MaterialButton tripPill;
    private TextView tripTitle, tripDetail, tripStatusPill;
    private View tripCard;

    @Nullable
    private TripResponse currentTrip;
    private boolean sosInFlight = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tripRepository = new TripRepository(requireContext());
        sosRepository = new SosRepository(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        liveBadge = view.findViewById(R.id.liveBadge);
        sosButton = view.findViewById(R.id.sosButton);
        tripPill = view.findViewById(R.id.tripPill);
        tripCard = view.findViewById(R.id.tripCard);
        tripTitle = view.findViewById(R.id.tripTitle);
        tripDetail = view.findViewById(R.id.tripDetail);
        tripStatusPill = view.findViewById(R.id.tripStatusPill);

        ImageButton themeToggle = view.findViewById(R.id.themeToggle);
        themeToggle.setImageResource(ThemePrefs.isDark(requireContext()) ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        themeToggle.setOnClickListener(v -> ThemePrefs.toggle(requireContext()));

        view.findViewById(R.id.profileAvatar).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showTab(R.id.nav_profile);
        });

        sosButton.setOnClickListener(v -> confirmSos());
        view.findViewById(R.id.fakeCallPill).setOnClickListener(v -> startFakeCall());
        tripPill.setOnClickListener(v -> openTrip());
        tripCard.setOnClickListener(v -> openTrip());

        renderTrip(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCurrentTrip();
    }

    private void loadCurrentTrip() {
        tripRepository.myTrips(result -> {
            if (!isAdded()) return;
            if (!result.success) return;
            TripResponse live = null;
            for (TripResponse trip : result.data) {
                if (trip.status == TripStatus.ACTIVE || trip.status == TripStatus.ESCALATED) {
                    live = trip;
                    break;
                }
            }
            SessionManager.getInstance(requireContext()).setActiveTripId(live == null ? null : live.id);
            renderTrip(live);
        });
    }

    private void renderTrip(@Nullable TripResponse trip) {
        currentTrip = trip;
        boolean live = trip != null && trip.status == TripStatus.ACTIVE;
        liveBadge.setLive(live);
        if (trip == null) {
            tripTitle.setText(R.string.home_no_trip_title);
            tripDetail.setText(R.string.home_no_trip_body);
            tripStatusPill.setVisibility(View.GONE);
            tripPill.setText(R.string.home_start_trip);
        } else {
            tripTitle.setText(trip.destinationName);
            tripDetail.setText("Expected by " + DISPLAY_FORMAT.format(trip.expectedArrival));
            tripStatusPill.setVisibility(View.VISIBLE);
            StatusPillHelper.apply(tripStatusPill, requireContext(), trip.status);
            tripPill.setText(R.string.home_open_trip);
        }
    }

    private void openTrip() {
        if (currentTrip != null) {
            Intent intent = new Intent(requireContext(), ActiveTripActivity.class);
            intent.putExtra(ActiveTripActivity.EXTRA_TRIP_ID, currentTrip.id);
            startActivity(intent);
        } else {
            startActivity(new Intent(requireContext(), CreateTripActivity.class));
        }
    }

    private void startFakeCall() {
        ContextCompat.startForegroundService(requireContext(), new Intent(requireContext(), FakeCallService.class));
        Toast.makeText(requireContext(), "Simulated call incoming shortly...", Toast.LENGTH_SHORT).show();
    }

    private void confirmSos() {
        if (sosInFlight) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.home_sos_confirm_title)
                .setMessage(R.string.home_sos_confirm_body)
                .setPositiveButton(R.string.home_sos_send, (d, w) -> triggerSos())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void triggerSos() {
        if (sosInFlight) return; // Client-side guard against a double tap, on top of the backend's dedup window.
        sosInFlight = true;
        sosButton.setEnabled(false);

        Long tripId = currentTrip == null ? null : currentTrip.id;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> sendSos(tripId,
                            location != null ? location.getLatitude() : 0d,
                            location != null ? location.getLongitude() : 0d))
                    .addOnFailureListener(e -> sendSos(tripId, 0d, 0d));
        } else {
            sendSos(tripId, 0d, 0d);
        }
    }

    private void sendSos(@Nullable Long tripId, double lat, double lng) {
        sosRepository.triggerManualSos(tripId, lat, lng, result -> {
            sosInFlight = false;
            if (!isAdded()) return;
            sosButton.setEnabled(true);
            if (result.success) {
                Toast.makeText(requireContext(), "SOS sent. Your Circle is being notified.", Toast.LENGTH_LONG).show();
                loadCurrentTrip();
            } else {
                Toast.makeText(requireContext(), "SOS failed to send: " + result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}
