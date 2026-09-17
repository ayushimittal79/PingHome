package com.pinghome.app.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pinghome.app.R;
import com.pinghome.app.data.api.dto.ContactResponse;

import java.util.ArrayList;
import java.util.List;

public class ContactListAdapter extends RecyclerView.Adapter<ContactListAdapter.ContactViewHolder> {

    public interface OnContactClickListener {
        void onContactClicked(ContactResponse contact);
    }

    private final List<ContactResponse> contacts = new ArrayList<>();
    private final OnContactClickListener listener;

    public ContactListAdapter(OnContactClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ContactResponse> newContacts) {
        contacts.clear();
        contacts.addAll(newContacts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactResponse contact = contacts.get(position);
        holder.nameText.setText(contact.name);

        StringBuilder detail = new StringBuilder();
        if (contact.relationship != null && !contact.relationship.isEmpty()) detail.append(contact.relationship);
        if (contact.email != null && !contact.email.isEmpty()) {
            if (detail.length() > 0) detail.append(" · ");
            detail.append(contact.email);
        }
        holder.detailText.setText(detail.toString());
        holder.itemView.setOnClickListener(v -> listener.onContactClicked(contact));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView detailText;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            detailText = itemView.findViewById(R.id.detailText);
        }
    }
}
