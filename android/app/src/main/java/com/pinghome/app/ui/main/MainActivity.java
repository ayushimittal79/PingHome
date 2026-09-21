package com.pinghome.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;
import com.pinghome.app.R;
import com.pinghome.app.data.repository.AuthRepository;
import com.pinghome.app.ui.auth.LoginActivity;
import com.pinghome.app.ui.contacts.ContactsFragment;
import com.pinghome.app.ui.trip.CreateTripActivity;
import com.pinghome.app.ui.trip.TripsFragment;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> { /* no-op: each screen re-checks before using a permission-gated feature */ });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthRepository authRepository = new AuthRepository(this);
        if (!authRepository.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        requestEssentialPermissions();
        registerFcmToken();

        View newTripFab = findViewById(R.id.newTripFab);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = fragmentFor(item.getItemId());
            if (fragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .commit();
                // Only the Trips tab has "start a new trip" as an action - on every other tab
                // this FAB would sit on top of (and swallow taps meant for) that fragment's own
                // controls, e.g. the Circle tab's "add contact" FAB in the same corner.
                newTripFab.setVisibility(item.getItemId() == R.id.nav_trips ? View.VISIBLE : View.GONE);
                return true;
            }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_home);

        newTripFab.setOnClickListener(v ->
                startActivity(new Intent(this, CreateTripActivity.class)));
    }

    /** Lets a fragment (e.g. Home's avatar button) switch tabs through the bottom bar. */
    public void showTab(int navItemId) {
        bottomNav.setSelectedItemId(navItemId);
    }

    private Fragment fragmentFor(int itemId) {
        if (itemId == R.id.nav_home) return new HomeFragment();
        if (itemId == R.id.nav_trips) return new TripsFragment();
        if (itemId == R.id.nav_circle) return new ContactsFragment();
        if (itemId == R.id.nav_profile) return new ProfileFragment();
        return null;
    }

    private void requestEssentialPermissions() {
        List<String> toRequest = new ArrayList<>();

        addIfMissing(toRequest, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(toRequest, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(toRequest, Manifest.permission.POST_NOTIFICATIONS);
            addIfMissing(toRequest, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(toRequest, Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (!toRequest.isEmpty()) {
            permissionLauncher.launch(toRequest.toArray(new String[0]));
        }
        // Background location is intentionally NOT requested here - Android requires it to be a
        // separate, second prompt after foreground location is granted, and PingHome only needs
        // it once a trip actually starts (CreateTripActivity requests it just before starting).
    }

    private void addIfMissing(List<String> list, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    private void registerFcmToken() {
        AuthRepository authRepository = new AuthRepository(this);
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(authRepository::registerFcmToken);
    }
}
