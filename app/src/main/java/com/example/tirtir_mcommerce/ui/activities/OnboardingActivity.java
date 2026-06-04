package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;

/**
 * SCR-01 OnboardingActivity
 *
 * Shown only on first launch (controlled by SplashActivity).
 * → Get Started → LoginActivity
 * → Log in text → LoginActivity
 *
 * Sprint S0.1
 */
public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        TextView tvLogin = findViewById(R.id.tvOnboardingLogin);

        btnGetStarted.setOnClickListener(v -> goToLogin());
        tvLogin.setOnClickListener(v -> goToLogin());
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
