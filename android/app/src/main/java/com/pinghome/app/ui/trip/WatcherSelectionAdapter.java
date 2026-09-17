package com.pinghome.app.ui.trip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.ContactResponse;
import com.pinghome.app.data.api.dto.WatcherAssignment;
import com.pinghome.app.data.api.dto.WatcherPermission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lets the traveller pick which trusted contacts watch this trip and at what permission level -
 * defaults to ALERT_ONLY (SPEC.md: unrestricted location access must never be the default). */
public class WatcherSelectionAdapter extends RecyclerView.Adapter<WatcherSelectionAdapter.ViewHolder> {

    private final List<ContactResponse> contacts = new ArrayList<>();
    private final Map<Long, WatcherPermission> selected = new LinkedHashMap<>();
    private static final String[] PERMISSION_LABELS = {"Alert only", "Full location"};

    public void submitList(List<ContactResponse> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
        notifyDataSetChanged();
    }

    public List<WatcherAssignment> getSelectedWatchers() {
        List<WatcherAssignment> result = new ArrayList<>();
        for (Map.Entry<Long, WatcherPermission> entry : selected.entrySet()) {
            result.add(new WatcherAssignment(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_watcher_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactResponse contact = contacts.get(position);
        holder.nameText.setText(contact.name);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(holder.itemView.getContext(),
                android.R.layout.simple_spinner_item, PERMISSION_LABELS);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.permissionSpinner.setAdapter(spinnerAdapter);

        boolean isSelected = selected.containsKey(contact.id);
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(isSelected);
        holder.permissionSpinner.setEnabled(isSelected);
        holder.permissionSpinner.setSelection(
                selected.get(contact.id) == WatcherPermission.LOCATION_DURING_TRIP ? 1 : 0);

        holder.checkbox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                selected.put(contact.id, WatcherPermission.ALERT_ONLY);
                holder.permissionSpinner.setSelection(0);
            } else {
                selected.remove(contact.id);
            }
            holder.permissionSpinner.setEnabled(checked);
        });

        holder.permissionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (selected.containsKey(contact.id)) {
                    selected.put(contact.id, position == 1 ? WatcherPermission.LOCATION_DURING_TRIP : WatcherPermission.ALERT_ONLY);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView nameText;
        final Spinner permissionSpinner;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.selectedCheckbox);
            nameText = itemView.findViewById(R.id.contactNameText);
            permissionSpinner = itemView.findViewById(R.id.permissionSpinner);
        }
    }
}
