package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.repository.AuthRepository;
import com.example.tirtir_mcommerce.model.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * SCR-10 LoginActivity — Màn hình Đăng nhập.
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────────────────
 * Authenticates against POST /api/v1/auth/login through AuthRepository.
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoogleLogin;
    private ProgressBar progressLogin;
    private TextView tvGoRegister, tvForgotPassword;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        authRepository = new AuthRepository(this);
        bindViews();
        setListeners();
        String registeredEmail = getIntent().getStringExtra("REGISTERED_EMAIL");
        if (registeredEmail != null && !registeredEmail.isEmpty()) {
            etEmail.setText(registeredEmail);
        }
    }

    private void bindViews() {
        tilEmail         = findViewById(R.id.tilEmail);
        tilPassword      = findViewById(R.id.tilPassword);
        etEmail          = findViewById(R.id.etEmail);
        etPassword       = findViewById(R.id.etPassword);
        btnLogin         = findViewById(R.id.btnLogin);
        btnGoogleLogin   = findViewById(R.id.btnGoogleLogin);
        progressLogin    = findViewById(R.id.progressLogin);
        tvGoRegister     = findViewById(R.id.tvGoRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setListeners() {
        // Primary login button uses the backend API.
        btnLogin.setOnClickListener(v -> {
            if (!validateForm()) return;
            String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            loginWithApi(email, password);
        });

        // The current Firebase project has no OAuth client configured. Hiding the
        // dead action is safer than exposing a button that can never complete.
        btnGoogleLogin.setVisibility(View.GONE);

        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    // ===========================
    // REAL API LOGIN (TASK 9)
    // ===========================

    /**
     * Calls POST /api/v1/auth/login via AuthRepository.
     * On success: JWT saved to SharedPrefsManager → navigate to MainActivity.
     * API errors are shown inline so invalid credentials are never bypassed.
     */
    private void loginWithApi(String email, String password) {
        showLoading(true);

        authRepository.login(email, password,
                user -> {
                    // SUCCESS: JWT saved by AuthRepository.login()
                    showLoading(false);
                    goToDestination(user);
                },
                errorMessage -> {
                    showLoading(false);

                        // Real credential error (401, 403): show to user
                        if (tilPassword != null) {
                            tilPassword.setError(errorMessage);
                        } else {
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                }
        );
    }

    // ===========================
    // HELPERS
    // ===========================

    private boolean validateForm() {
        boolean valid = true;
        tilEmail.setError(null);
        tilPassword.setError(null);

        String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email");
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Please enter your password");
            valid = false;
        }
        return valid;
    }

    private void showLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnGoogleLogin.setEnabled(!loading);
    }

    private void showForgotPasswordDialog() {
        TextInputEditText emailInput = new TextInputEditText(this);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setHint("Email address");
        if (etEmail.getText() != null) {
            emailInput.setText(etEmail.getText().toString().trim());
        }
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(emailInput);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Reset password")
                .setMessage("We will email you a secure reset link.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send link", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String email = emailInput.getText() == null
                            ? ""
                            : emailInput.getText().toString().trim();
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailInput.setError("Enter a valid email");
                        return;
                    }
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    authRepository.forgotPassword(email,
                            message -> runOnUiThread(() -> {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            }),
                            error -> runOnUiThread(() -> {
                                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                emailInput.setError(error);
                            }));
                }));
        dialog.show();
    }

    private void goToDestination(User user) {
        Intent intent = new Intent(this,
                user != null && user.isAdmin() ? AdminActivity.class : MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
