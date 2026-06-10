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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * SCR-10 LoginActivity — Màn hình Đăng nhập.
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────────────────
 * Structured for easy PM handoff to real backend:
 *
 * 1. loginWithApi()   → calls AuthRepository.login() → POST /api/v1/auth/login
 *                       Saves JWT via SharedPrefsManager.saveToken()
 *                       On success → MainActivity
 *                       On HTTP 401 → "Email or password incorrect"
 *
 * 2. handleMockLogin() → bypass auth for demo/dev only
 *                        TODO: Remove or gate behind BuildConfig.DEBUG when backend ready
 *
 * MVVM Pattern: Activity → AuthRepository (direct, no ViewModel for simplicity in Phase 1)
 * TODO Phase 2: Migrate to AuthViewModel for proper LiveData observation.
 *
 * Sprint 1.1 — Task B
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
        // Primary login button: try real API, fall back to mock
        btnLogin.setOnClickListener(v -> {
            if (!validateForm()) return;
            String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            loginWithApi(email, password);
        });

        // Google login: mock for Phase 1
        btnGoogleLogin.setOnClickListener(v -> Toast.makeText(this, "Tính năng đăng nhập Google chưa có", Toast.LENGTH_SHORT).show());

        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show());
    }

    // ===========================
    // REAL API LOGIN (TASK 9)
    // ===========================

    /**
     * Calls POST /api/v1/auth/login via AuthRepository.
     * On success: JWT saved to SharedPrefsManager → navigate to MainActivity.
     * On failure (404 endpoint / backend not ready): falls back to mock login.
     *
     * TODO: Remove mock fallback in handleMockLogin() when backend is confirmed stable.
     */
    private void loginWithApi(String email, String password) {
        showLoading(true);

        authRepository.login(email, password,
                user -> {
                    // SUCCESS: JWT saved by AuthRepository.login()
                    showLoading(false);
                    goToMain();
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
            tilEmail.setError("Vui lòng nhập email hợp lệ");
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Vui lòng nhập mật khẩu");
            valid = false;
        }
        return valid;
    }

    private void showLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnGoogleLogin.setEnabled(!loading);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
