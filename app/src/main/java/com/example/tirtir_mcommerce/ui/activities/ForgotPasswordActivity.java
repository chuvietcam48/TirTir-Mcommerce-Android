package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.tirtir_mcommerce.repository.AuthRepository;

public class ForgotPasswordActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private MaterialButton btnSendCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authRepository = new AuthRepository(this);

        ((Toolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        btnSendCode = findViewById(R.id.btnSendCode);
        EditText etEmail = findViewById(R.id.etEmail);

        btnSendCode.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }


            btnSendCode.setEnabled(false);

            authRepository.forgotPassword(email, 
                message -> {

                    btnSendCode.setEnabled(true);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(this, VerifyEmailActivity.class);
                    intent.putExtra("EMAIL", email);
                    startActivity(intent);
                },
                error -> {

                    btnSendCode.setEnabled(true);
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                });
        });
    }
}
