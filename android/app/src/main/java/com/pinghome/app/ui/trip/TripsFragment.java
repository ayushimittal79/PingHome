package com.pinghome.app.ui.trip;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.pinghome.app.R;
import com.pinghome.app.ui.watcher.WatchingFragment;

/** Hosts "My trips" and "Watching" under one tab with a segmented toggle. */
public class TripsFragment extends Fragment {

    private static final String TAG_MINE = "trips_mine";
    private static final String TAG_WATCHING = "trips_watching";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trips, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.tripsToggle);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) show(checkedId == R.id.toggleWatching);
        });
        if (savedInstanceState == null) show(false);
    }

    private void show(boolean watching) {
        String tag = watching ? TAG_WATCHING : TAG_MINE;
        Fragment fragment = getChildFragmentManager().findFragmentByTag(tag);
        if (fragment == null) fragment = watching ? new WatchingFragment() : new MyTripsFragment();
        getChildFragmentManager().beginTransaction()
                .replace(R.id.tripsContainer, fragment, tag)
                .commit();
    }
}
