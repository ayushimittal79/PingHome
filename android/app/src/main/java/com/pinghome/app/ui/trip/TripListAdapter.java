package com.pinghome.app.ui.trip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.TripResponse;
import com.pinghome.app.util.StatusPillHelper;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TripListAdapter extends RecyclerView.Adapter<TripListAdapter.TripViewHolder> {

    public interface OnTripClickListener {
        void onTripClicked(TripResponse trip);
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private final List<TripResponse> trips = new ArrayList<>();
    private final OnTripClickListener listener;

    public TripListAdapter(OnTripClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TripResponse> newTrips) {
        trips.clear();
        trips.addAll(newTrips);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        TripResponse trip = trips.get(position);
        holder.destinationText.setText(trip.destinationName);
        holder.detailText.setText("Expected " + TIME_FORMAT.format(trip.expectedArrival));
        StatusPillHelper.apply(holder.statusPill, holder.itemView.getContext(), trip.status);
        holder.itemView.setOnClickListener(v -> listener.onTripClicked(trip));
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        final TextView destinationText;
        final TextView detailText;
        final TextView statusPill;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            destinationText = itemView.findViewById(R.id.destinationText);
            detailText = itemView.findViewById(R.id.detailText);
            statusPill = itemView.findViewById(R.id.statusPill);
        }
    }
}
