package com.pinghome.app.ui.contacts;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.pinghome.app.R;
import com.pinghome.app.data.repository.ContactRepository;

public class EditContactActivity extends AppCompatActivity {

    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_PHONE = "phone";
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_RELATIONSHIP = "relationship";

    private ContactRepository contactRepository;
    private EditText nameInput, phoneInput, emailInput, relationshipInput;
    private long contactId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_contact);

        contactRepository = new ContactRepository(this);

        nameInput = findViewById(R.id.nameInput);
        phoneInput = findViewById(R.id.phoneInput);
        emailInput = findViewById(R.id.emailInput);
        relationshipInput = findViewById(R.id.relationshipInput);

        contactId = getIntent().getLongExtra(EXTRA_CONTACT_ID, -1);
        if (contactId != -1) {
            nameInput.setText(getIntent().getStringExtra(EXTRA_NAME));
            phoneInput.setText(getIntent().getStringExtra(EXTRA_PHONE));
            emailInput.setText(getIntent().getStringExtra(EXTRA_EMAIL));
            relationshipInput.setText(getIntent().getStringExtra(EXTRA_RELATIONSHIP));
            findViewById(R.id.deleteButton).setVisibility(android.view.View.VISIBLE);
        }

        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        findViewById(R.id.deleteButton).setOnClickListener(v -> confirmDelete());
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        String phone = emptyToNull(phoneInput.getText().toString().trim());
        String email = emptyToNull(emailInput.getText().toString().trim());
        String relationship = emptyToNull(relationshipInput.getText().toString().trim());

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (contactId == -1) {
            contactRepository.create(name, phone, email, relationship, result -> {
                if (result.success) finish();
                else Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            });
        } else {
            contactRepository.update(contactId, name, phone, email, relationship, result -> {
                if (result.success) finish();
                else Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Remove contact?")
                .setMessage("They'll no longer be able to watch any of your trips.")
                .setPositiveButton("Remove", (dialog, which) ->
                        contactRepository.delete(contactId, result -> {
                            if (result.success) finish();
                            else Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show();
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
