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

public class LoginActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView errorText;
    private MaterialButton loginButton;
    private ProgressBar loadingSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new AuthRepository(this);

        if (authRepository.isLoggedIn()) {
            goToMain();
            return;
        }

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText = findViewById(R.id.errorText);
        loginButton = findViewById(R.id.loginButton);
        loadingSpinner = findViewById(R.id.loadingSpinner);

        loginButton.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.goToRegisterButton).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Enter your email and password.");
            return;
        }

        setLoading(true);
        authRepository.login(email, password, result -> {
            setLoading(false);
            if (result.success) {
                goToMain();
            } else {
                showError(result.errorMessage);
            }
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        loadingSpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
    }
}
