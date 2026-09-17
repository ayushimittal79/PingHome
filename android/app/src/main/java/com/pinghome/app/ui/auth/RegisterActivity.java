package com.pinghome.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.pinghome.app.R;
import com.pinghome.app.data.repository.AuthRepository;
import com.pinghome.app.ui.main.MainActivity;

public class RegisterActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private EditText nameInput, emailInput, phoneInput, passwordInput;
    private TextView errorText;
    private MaterialButton registerButton;
    private ProgressBar loadingSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authRepository = new AuthRepository(this);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);
        registerButton = findViewById(R.id.registerButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        registerButton.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (name.isEmpty() || email.isEmpty() || password.length() < 8) {
            showError("Enter your name, a valid email, and a password of at least 8 characters.");
            return;
        }

        setLoading(true);
        authRepository.register(name, email, phone.isEmpty() ? null : phone, password, result -> {
            setLoading(false);
            if (result.success) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                showError(result.errorMessage);
            }
        });
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
    }
}
