package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SCR-00 SplashActivity
 *
 * Startup logic:
 * - First launch → OnboardingActivity
 * - Already logged in (Firebase non-anonymous) → MainActivity
 * - Otherwise → LoginActivity
 *
 * Sprint S0.1
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 350;
    private static final String PREF_FIRST_LAUNCH = "first_launch_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, SPLASH_DELAY_MS);
    }

    private void navigate() {
        SharedPrefsManager prefsManager = new SharedPrefsManager(this);
        SharedPreferences prefs = getSharedPreferences("tirtir_prefs", MODE_PRIVATE);
        boolean firstLaunchDone = prefs.getBoolean(PREF_FIRST_LAUNCH, false);

        Intent intent;
        if (!firstLaunchDone) {
            // First launch — show onboarding
            prefs.edit().putBoolean(PREF_FIRST_LAUNCH, true).apply();
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            // Check if user has a valid JWT token
            String token = prefsManager.getToken();
            if (token != null && !token.isEmpty()) {
                // Has token -> Route to Main or Admin
                com.example.tirtir_mcommerce.model.User user = prefsManager.getCachedUser();
                boolean isAdmin = (user != null && user.isAdmin());
                intent = new Intent(this, isAdmin ? AdminActivity.class : MainActivity.class);
            } else {
                // No token -> Force Login
                intent = new Intent(this, LoginActivity.class);
            }
        }

        startActivity(intent);
        finish();
    }
}
