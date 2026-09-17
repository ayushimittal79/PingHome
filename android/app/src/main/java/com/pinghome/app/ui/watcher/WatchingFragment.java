package com.pinghome.app.ui.watcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.pinghome.app.R;
import com.pinghome.app.data.repository.TripRepository;
import com.pinghome.app.ui.trip.TripListAdapter;

public class WatchingFragment extends Fragment {

    private TripRepository tripRepository;
    private TripListAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tripRepository = new TripRepository(requireContext());

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TripListAdapter(trip -> {
            Intent intent = new Intent(requireContext(), WatcherTripDetailActivity.class);
            intent.putExtra(WatcherTripDetailActivity.EXTRA_TRIP_ID, trip.id);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::loadTrips);
        emptyText = view.findViewById(R.id.emptyText);
        emptyText.setText("You're not watching any trips right now.");

        loadTrips();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTrips();
    }

    private void loadTrips() {
        swipeRefresh.setRefreshing(true);
        tripRepository.watchingTrips(result -> {
            swipeRefresh.setRefreshing(false);
            if (result.success) {
                adapter.submitList(result.data);
                emptyText.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }
}
