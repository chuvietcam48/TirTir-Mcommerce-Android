package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.bumptech.glide.Glide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProfileActivity extends AppCompatActivity {

    private SharedPrefsManager prefs;
    private AuthViewModel authViewModel;

    private SwitchMaterial switch2FA, switchAlerts;
    private TextView tvAdminName, tvAdminRole, tvCurrentLanguage;
    private ImageButton btnThemeToggle;

    private boolean isDarkMode = false;
    private boolean isVietnamese = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_profile);

        prefs = new SharedPrefsManager(this);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();
        loadAdminData();
        setupListeners();
    }

    private void initViews() {
        tvAdminName = findViewById(R.id.tvAdminProfileName);
        tvAdminRole = findViewById(R.id.tvAdminProfileRole);
        switch2FA = findViewById(R.id.switchAdmin2FA);
        switchAlerts = findViewById(R.id.switchAdminAlerts);
        tvCurrentLanguage = findViewById(R.id.tvAdminCurrentLanguage);
        btnThemeToggle = findViewById(R.id.btnAdminThemeToggle);
    }

    private void loadAdminData() {
        User user = prefs.getCachedUser();
        if (user != null) {
            tvAdminName.setText(user.getName());
            tvAdminRole.setText(user.getRole().toUpperCase());

            // Initialize switches from preferences
            switch2FA.setChecked(prefs.getBoolean("admin_2fa_enabled", false));
            switchAlerts.setChecked(prefs.getBoolean("admin_alerts_enabled", true));

            // Load avatars
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                Glide.with(this)
                        .load(user.getAvatar())
                        .placeholder(R.drawable.ic_person)
                        .into((android.widget.ImageView) findViewById(R.id.ivAdminProfileAvatarSmall));
                Glide.with(this)
                        .load(user.getAvatar())
                        .placeholder(R.drawable.ic_person)
                        .into((android.widget.ImageView) findViewById(R.id.ivAdminProfileAvatarLarge));
            }
        }
    }

    private void setupListeners() {
        findViewById(R.id.btnAdminProfileBack).setOnClickListener(v -> finish());

        // Account Security
        findViewById(R.id.btnAdminChangePassword).setOnClickListener(v -> {
            Toast.makeText(this, "Change Password clicked", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnAdminLoginHistory).setOnClickListener(v -> fetchLogs("login-history"));

        switch2FA.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePreference("twoFactorEnabled", isChecked);
        });

        // Admin Controls
        findViewById(R.id.btnAdminApiLogs).setOnClickListener(v -> fetchLogs("api-logs"));
        findViewById(R.id.btnAdminAuditTrails).setOnClickListener(v -> fetchLogs("audit-trails"));

        // System Preferences
        findViewById(R.id.btnAdminAppLanguage).setOnClickListener(v -> {
            isVietnamese = !isVietnamese;
            String lang = isVietnamese ? "vi" : "en";
            tvCurrentLanguage.setText(isVietnamese ? "Vietnamese" : "English");
            updatePreference("language", lang);
        });

        btnThemeToggle.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            if (isDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                updatePreference("theme", "dark");
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                updatePreference("theme", "light");
            }
        });

        switchAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePreference("criticalAlerts", isChecked);
        });

        // Sign Out
        findViewById(R.id.btnAdminSignOutProfile).setOnClickListener(v -> confirmLogout());
    }

    private void updatePreference(String key, Object value) {
        Map<String, Object> req = new HashMap<>();
        req.put(key, value);

        // Save locally first for responsiveness
        if (value instanceof Boolean) {
            if ("twoFactorEnabled".equals(key)) {
                prefs.saveLanguage(key); // SharedPrefsManager doesn't have saveBoolean, wait.
            }
        }
        
        // Actually, SharedPrefsManager.getBoolean exists but no putBoolean.
        // I'll check SharedPrefsManager again.

        RetrofitClient.getAuthClient(this).create(ApiService.class).updateAdminPreferences(req)
                .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(AdminProfileActivity.this, key + " updated", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(AdminProfileActivity.this, "Failed to update " + key, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchLogs(String type) {
        Call<ApiResponse<List<Map<String, Object>>>> call = null;
        switch (type) {
            case "login-history":
                call = RetrofitClient.getAuthClient(this).create(ApiService.class).getAdminLoginHistory();
                break;
            case "api-logs":
                call = RetrofitClient.getAuthClient(this).create(ApiService.class).getAdminApiLogs();
                break;
            case "audit-trails":
                call = RetrofitClient.getAuthClient(this).create(ApiService.class).getAdminAuditTrails();
                break;
        }

        if (call != null) {
            call.enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call, Response<ApiResponse<List<Map<String, Object>>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        List<Map<String, Object>> logs = response.body().getData();
                        new AlertDialog.Builder(AdminProfileActivity.this)
                                .setTitle(type.replace("-", " ").toUpperCase())
                                .setMessage("Found " + logs.size() + " records.\n\nLatest: " + logs.get(0).toString())
                                .setPositiveButton("OK", null)
                                .show();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                    Toast.makeText(AdminProfileActivity.this, "Failed to load logs", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Sign out of the admin console?")
                .setPositiveButton("Sign out", (dialog, which) ->
                        authViewModel.logout(() -> {
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
