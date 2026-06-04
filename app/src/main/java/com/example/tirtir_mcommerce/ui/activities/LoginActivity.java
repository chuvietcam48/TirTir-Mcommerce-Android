package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;

/**
 * Màn hình Đăng nhập.
 *
 * Áp dụng MVVM Pattern:
 * - Không chứa logic nghiệp vụ hay gọi API trực tiếp
 * - Observe LiveData từ AuthViewModel để cập nhật UI
 * - Uỷ quyền xử lý logic cho AuthViewModel
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoogleLogin;
    private ProgressBar progressLogin;
    private TextView tvGoRegister, tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        bindViews();
        setListeners();
    }

    private void bindViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        progressLogin = findViewById(R.id.progressLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setListeners() {
        btnLogin.setOnClickListener(v -> handleMockLogin());
        btnGoogleLogin.setOnClickListener(v -> handleMockLogin());

        tvGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show();
        });
    }

    private void handleMockLogin() {
        // Mock loading
        progressLogin.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        
        // Mock success after short delay
        progressLogin.postDelayed(() -> {
            progressLogin.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
            goToMain();
        }, 1000);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
