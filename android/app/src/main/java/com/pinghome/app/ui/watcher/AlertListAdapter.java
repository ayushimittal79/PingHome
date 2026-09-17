package com.pinghome.app.ui.watcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.AlertResponse;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AlertListAdapter extends RecyclerView.Adapter<AlertListAdapter.ViewHolder> {

    public interface OnResolveListener {
        void onResolve(AlertResponse alert);
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private final List<AlertResponse> alerts = new ArrayList<>();
    private final OnResolveListener listener;

    public AlertListAdapter(OnResolveListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AlertResponse> newAlerts) {
        alerts.clear();
        alerts.addAll(newAlerts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlertResponse alert = alerts.get(position);
        holder.title.setText(alert.alertType + " · " + alert.source);
        holder.detail.setText(TIME_FORMAT.format(alert.triggeredAt) + (alert.resolved ? " · Resolved" : ""));
        holder.resolveButton.setVisibility(alert.resolved ? View.GONE : View.VISIBLE);
        holder.resolveButton.setOnClickListener(v -> listener.onResolve(alert));
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView detail;
        final MaterialButton resolveButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.alertTitleText);
            detail = itemView.findViewById(R.id.alertDetailText);
            resolveButton = itemView.findViewById(R.id.resolveButton);
        }
    }
}
