package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * SCR-11 RegisterActivity — Màn hình Đăng ký.
 *
 * Wire với BE: POST /api/v1/auth/register
 * Body: { firstName, lastName, email, password }
 *
 * Lưu ý BE nhận "fullName" field thông qua firstName + lastName tách riêng.
 * Form layout chỉ có 1 field "Họ và tên" → tách chuỗi lấy lastName = từ đầu tiên,
 * firstName = phần còn lại (quy ước đủ cho MVP).
 *
 * Flow:
 * 1. User điền form → btnRegister → AuthViewModel.register()
 * 2. AuthViewModel → AuthRepository.register() → POST /api/v1/auth/register
 * 3. Success → Toast + navigate to LoginActivity
 * 4. Error  → hiển thị lỗi trên field tương ứng
 *
 * MVVM: Activity observe AuthViewModel.registerSuccess + errorMessage + isLoading
 *
 * Sprint 1.1 — SCR-11 (wired to real API)
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilFullName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etFullName, etEmail, etPassword, etConfirmPassword;
    private MaterialButton btnRegister;
    private ProgressBar progressRegister;
    private TextView tvGoLogin;

    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        bindViews();
        observeViewModel();
        setListeners();
    }

    // ===========================
    // VIEW BINDING
    // ===========================

    private void bindViews() {
        tilFullName        = findViewById(R.id.tilFullName);
        tilEmail           = findViewById(R.id.tilEmail);
        tilPassword        = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etFullName        = findViewById(R.id.etFullName);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnRegister      = findViewById(R.id.btnRegister);
        progressRegister = findViewById(R.id.progressRegister);
        tvGoLogin        = findViewById(R.id.tvGoLogin);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
    }

    // ===========================
    // VIEWMODEL OBSERVERS
    // ===========================

    private void observeViewModel() {
        // Loading state → toggle ProgressBar + disable button
        authViewModel.isLoading.observe(this, isLoading -> {
            progressRegister.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnRegister.setEnabled(!isLoading);
        });

        // Register success → toast + navigate to Login
        authViewModel.registerSuccess.observe(this, message -> {
            String displayMsg = (message != null && !message.isEmpty())
                    ? message
                    : getString(R.string.toast_register_success);
            Toast.makeText(this, displayMsg, Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // Error → show on relevant field or Toast
        authViewModel.errorMessage.observe(this, error -> {
            if (error == null || error.isEmpty()) return;

            // Map error message to the appropriate field
            if (error.contains("tên") || error.contains("Tên") || error.contains("Họ")) {
                tilFullName.setError(error);
            } else if (error.contains("email") || error.contains("Email")) {
                tilEmail.setError(error);
            } else if (error.contains("khớp")) {
                tilConfirmPassword.setError(error);
            } else if (error.contains("mật khẩu") || error.contains("Mật khẩu")) {
                tilPassword.setError(error);
            } else {
                // Generic error (server/network)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===========================
    // LISTENERS
    // ===========================

    private void setListeners() {
        btnRegister.setOnClickListener(v -> {
            clearErrors();
            registerWithApi();
        });

        tvGoLogin.setOnClickListener(v -> onBackPressed());
    }

    // ===========================
    // REAL API REGISTER
    // ===========================

    /**
     * Đọc form → gọi AuthViewModel.register() → POST /api/v1/auth/register
     *
     * BE nhận: { firstName, lastName, email, password }
     * Form có 1 field "Họ và tên" → tách:
     *   - Nếu chỉ 1 từ: firstName = chuỗi đó, lastName = ""
     *   - Nếu nhiều từ: firstName = từ cuối, lastName = phần còn lại
     *   (phù hợp tên tiếng Việt: "Nguyễn Văn An" → lastName="Nguyễn Văn", firstName="An")
     */
    private void registerWithApi() {
        String fullName    = getText(etFullName);
        String email       = getText(etEmail);
        String password    = getText(etPassword);
        String confirmPass = getText(etConfirmPassword);

        // Split fullName into firstName + lastName for BE
        String firstName, lastName;
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            firstName = parts[0];
            lastName  = "";
        } else {
            // Last token = firstName (given name), rest = lastName (family name)
            firstName = parts[parts.length - 1];
            lastName  = fullName.substring(0, fullName.lastIndexOf(parts[parts.length - 1])).trim();
        }

        // Delegate to AuthViewModel (which handles validation + API call)
        authViewModel.register(firstName, lastName, email, password, confirmPass);
    }

    // ===========================
    // HELPERS
    // ===========================

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void clearErrors() {
        tilFullName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
    }
}
