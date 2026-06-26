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

import com.example.tirtir_mcommerce.network.FirebaseAuthManager;
import com.google.firebase.auth.FirebaseUser;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.model.LoginResponse;
import android.util.Log;

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

        // Enable Google sign-in button
        btnGoogleLogin.setVisibility(View.VISIBLE);
        btnGoogleLogin.setOnClickListener(v -> {
            FirebaseAuthManager authManager = new FirebaseAuthManager(this);
            authManager.startGoogleSignIn(this);
        });

        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FirebaseAuthManager.RC_SIGN_IN) {
            FirebaseAuthManager authManager = new FirebaseAuthManager(this);
            authManager.handleGoogleSignInResult(data, new FirebaseAuthManager.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    user.getIdToken(true).addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            String idToken = task.getResult().getToken();
                            loginWithGoogle(idToken);
                        } else {
                            Toast.makeText(LoginActivity.this, "Failed to get ID token from Firebase", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(Exception exception) {
                    Toast.makeText(LoginActivity.this, "Google Sign-In failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void loginWithGoogle(String idToken) {
        showLoading(true);
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("idToken", idToken);
        
        apiService.googleLogin(body).enqueue(new retrofit2.Callback<LoginResponse>() {
            @Override
            public void onResponse(retrofit2.Call<LoginResponse> call, retrofit2.Response<LoginResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    if (loginResponse.getToken() != null) {
                        com.example.tirtir_mcommerce.utils.SharedPrefsManager prefsManager = new com.example.tirtir_mcommerce.utils.SharedPrefsManager(LoginActivity.this);
                        if (loginResponse.getUser() != null) {
                            prefsManager.saveUser(loginResponse.getUser());
                        }
                        prefsManager.saveSession(loginResponse.getToken(), loginResponse.getRefreshToken());
                        
                        // Sync FCM token
                        try {
                            com.example.tirtir_mcommerce.data.repository.CloudRepository cloudRepository = new com.example.tirtir_mcommerce.data.repository.CloudRepository(LoginActivity.this);
                            cloudRepository.syncFcmToken();
                        } catch (Exception e) {
                            Log.e("LoginActivity", "Firebase FCM token sync failed", e);
                        }
                        
                        goToDestination(loginResponse.getUser());
                    } else {
                        Toast.makeText(LoginActivity.this, "Google sign-in failed: No token returned", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Google sign-in failed on server", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<LoginResponse> call, Throwable t) {
                showLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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
        startActivity(new Intent(this, ForgotPasswordActivity.class));
    }

    private void goToDestination(User user) {
        Intent intent = new Intent(this,
                user != null && user.isAdmin() ? AdminActivity.class : MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
