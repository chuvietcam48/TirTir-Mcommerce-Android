package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;

public class NewPasswordActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private String email;
    private String resetToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_password);

        authRepository = new AuthRepository(this);
        email = getIntent().getStringExtra("EMAIL");
        resetToken = getIntent().getStringExtra("RESET_TOKEN");

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        EditText etNewPassword = findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = findViewById(R.id.etConfirmPassword);
        MaterialButton btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        btnUpdatePassword.setOnClickListener(v -> {
            String newPassword = etNewPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            if (newPassword.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            btnUpdatePassword.setEnabled(false);

            authRepository.resetPassword(email, resetToken, newPassword,
                success -> {
                    Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                },
                error -> {
                    btnUpdatePassword.setEnabled(true);
                    if ("401".equals(error)) {
                        new AlertDialog.Builder(this)
                            .setTitle("Token Expired")
                            .setMessage("Mã xác thực đã hết hạn, vui lòng yêu cầu lại.")
                            .setCancelable(false)
                            .setPositiveButton("OK", (dialog, id) -> {
                                Intent intent = new Intent(this, ForgotPasswordActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .create()
                            .show();
                    } else {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    }
                });
        });
    }
}
