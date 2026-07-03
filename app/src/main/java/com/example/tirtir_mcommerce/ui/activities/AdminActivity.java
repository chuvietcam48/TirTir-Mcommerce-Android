package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.fragments.AdminDashboardFragment;
import com.example.tirtir_mcommerce.ui.fragments.AdminMarketingFragment;
import com.example.tirtir_mcommerce.ui.fragments.AdminOrdersFragment;
import com.example.tirtir_mcommerce.ui.fragments.AdminProductsFragment;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminActivity extends AppCompatActivity {
    private AuthViewModel authViewModel;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        bottomNav = findViewById(R.id.bottomNavAdmin);
        setupNavigation();

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_admin_dashboard);
        }
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_admin_dashboard) {
                selectedFragment = new AdminDashboardFragment();
            } else if (itemId == R.id.nav_admin_products) {
                selectedFragment = new AdminProductsFragment();
            } else if (itemId == R.id.nav_admin_orders) {
                selectedFragment = new AdminOrdersFragment();
            } else if (itemId == R.id.nav_admin_marketing) {
                selectedFragment = new AdminMarketingFragment();
            } else if (itemId == R.id.nav_admin_logout) {
                confirmLogout();
                return false;
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainerAdmin, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });

        // Top bar more menu (logout could go here)
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarAdmin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        android.view.View btnProfile = findViewById(R.id.btnAdminProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> showAdminProfile());
        }

    }

    private void showAdminProfile() {
        com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs =
                new com.example.tirtir_mcommerce.utils.SharedPrefsManager(this);
        com.example.tirtir_mcommerce.model.User user = prefs.getCachedUser();
        String name = user != null ? user.getName() : "Admin";
        String email = user != null ? user.getEmail() : "";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Admin Profile")
                .setMessage("Name: " + name + "\nEmail: " + email)
                .setPositiveButton("Logout", (dialog, which) -> confirmLogout())
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmLogout() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
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

    @Override
    protected void onStart() {
        super.onStart();
        checkTimeout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkTimeout();
    }

    private void checkTimeout() {
        com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs =
                new com.example.tirtir_mcommerce.utils.SharedPrefsManager(this);
        com.example.tirtir_mcommerce.model.User user = prefs.getCachedUser();
        if (!prefs.isLoggedIn() || user == null || !user.isAdmin()) {
            prefs.clearAuthSession();
            Toast.makeText(this, "Your admin session has expired.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}
