package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;

/**
 * AdminActivity - Màn hình dành riêng cho tài khoản có role "admin".
 *
 * Điều kiện vào màn hình này:
 * - Đăng nhập thành công với user.role == "admin"
 * - LoginActivity điều hướng tự động theo role
 *
 * Sprint 1.1 - Task: Logic Auth & Role
 */
public class AdminActivity extends AppCompatActivity {

    private AuthViewModel authViewModel;
    private MaterialButton btnAdminLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        btnAdminLogout = findViewById(R.id.btnAdminLogout);

        btnAdminLogout.setOnClickListener(v -> confirmLogout());
    }

    /**
     * Hiển thị AlertDialog xác nhận trước khi logout.
     */
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất khỏi tài khoản Admin không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> performLogout())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performLogout() {
        authViewModel.logout(() -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}
