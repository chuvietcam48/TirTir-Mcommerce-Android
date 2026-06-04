package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;

/**
 * Màn hình Đăng ký.
 * Áp dụng MVVM Pattern - mọi validation và logic đều ở AuthViewModel.
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilFullName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etFullName, etEmail, etPassword, etConfirmPassword;
    private MaterialButton btnRegister;
    private ProgressBar progressRegister;
    private TextView tvGoLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();
        setListeners();
    }

    private void bindViews() {
        tilFullName = findViewById(R.id.tilFullName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        progressRegister = findViewById(R.id.progressRegister);
        tvGoLogin = findViewById(R.id.tvGoLogin);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
    }

    private void setListeners() {
        btnRegister.setOnClickListener(v -> handleMockRegister());

        tvGoLogin.setOnClickListener(v -> {
            onBackPressed(); // Quay lại màn LoginActivity
        });
    }

    private void handleMockRegister() {
        // Mock loading
        progressRegister.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        progressRegister.postDelayed(() -> {
            progressRegister.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
            
            Toast.makeText(this, getString(R.string.toast_register_success), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }, 1000);
    }
}
