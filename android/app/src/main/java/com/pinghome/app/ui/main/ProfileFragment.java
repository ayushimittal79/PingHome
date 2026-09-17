package com.pinghome.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pinghome.app.R;
import com.pinghome.app.data.SessionManager;
import com.pinghome.app.data.repository.AuthRepository;
import com.pinghome.app.ui.auth.LoginActivity;
import com.pinghome.app.ui.fakecall.FakeCallSettingsActivity;
import com.pinghome.app.ui.wearable.WearableSettingsActivity;

public class ProfileFragment extends Fragment {

    private AuthRepository authRepository;
    private EditText nameInput, phoneInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        authRepository = new AuthRepository(requireContext());
        SessionManager session = SessionManager.getInstance(requireContext());

        ((TextView) view.findViewById(R.id.nameText)).setText(session.getUserName());
        ((TextView) view.findViewById(R.id.emailText)).setText(session.getUserEmail());

        nameInput = view.findViewById(R.id.nameInput);
        phoneInput = view.findViewById(R.id.phoneInput);
        nameInput.setText(session.getUserName());

        view.findViewById(R.id.saveProfileButton).setOnClickListener(v -> saveProfile());
        view.findViewById(R.id.wearableSettingsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WearableSettingsActivity.class)));
        view.findViewById(R.id.fakeCallSettingsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FakeCallSettingsActivity.class)));
        view.findViewById(R.id.logoutButton).setOnClickListener(v -> logout());
    }

    private void saveProfile() {
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name can't be empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        authRepository.updateProfile(name, phone.isEmpty() ? null : phone, result -> {
            if (result.success) {
                Toast.makeText(requireContext(), "Profile updated.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), result.errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void logout() {
        authRepository.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
