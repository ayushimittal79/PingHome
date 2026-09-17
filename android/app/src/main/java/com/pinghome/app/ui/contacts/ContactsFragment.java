package com.pinghome.app.ui.contacts;

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
import com.pinghome.app.data.repository.ContactRepository;

public class ContactsFragment extends Fragment {

    private ContactRepository contactRepository;
    private ContactListAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        contactRepository = new ContactRepository(requireContext());

        view.findViewById(R.id.addContactFab).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditContactActivity.class)));

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactListAdapter(contact -> {
            Intent intent = new Intent(requireContext(), EditContactActivity.class);
            intent.putExtra(EditContactActivity.EXTRA_CONTACT_ID, contact.id);
            intent.putExtra(EditContactActivity.EXTRA_NAME, contact.name);
            intent.putExtra(EditContactActivity.EXTRA_PHONE, contact.phone);
            intent.putExtra(EditContactActivity.EXTRA_EMAIL, contact.email);
            intent.putExtra(EditContactActivity.EXTRA_RELATIONSHIP, contact.relationship);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::loadContacts);
        emptyText = view.findViewById(R.id.emptyText);
        emptyText.setText("No trusted contacts yet. Add people who should watch over your trips.");

        loadContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadContacts();
    }

    private void loadContacts() {
        swipeRefresh.setRefreshing(true);
        contactRepository.list(result -> {
            swipeRefresh.setRefreshing(false);
            if (result.success) {
                adapter.submitList(result.data);
                emptyText.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }
}
