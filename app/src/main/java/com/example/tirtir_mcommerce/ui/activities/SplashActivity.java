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

/**
 * SCR-00 SplashActivity
 *
 * Startup logic:
 * - First launch → OnboardingActivity
 * - Already logged in (token saved) → MainActivity
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
        SharedPreferences prefs = getSharedPreferences("tirtir_prefs", MODE_PRIVATE);
        boolean firstLaunchDone = prefs.getBoolean(PREF_FIRST_LAUNCH, false);

        Intent intent;
        if (!firstLaunchDone) {
            // First launch — show onboarding
            prefs.edit().putBoolean(PREF_FIRST_LAUNCH, true).apply();
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            // Check if user has a saved token
            SharedPrefsManager prefsManager = new SharedPrefsManager(this);
            if (prefsManager.isLoggedIn()) {
                com.example.tirtir_mcommerce.model.User user = prefsManager.getCachedUser();
                intent = new Intent(this,
                        user != null && user.isAdmin() ? AdminActivity.class : MainActivity.class);
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
        }

        startActivity(intent);
        finish();
    }
}
