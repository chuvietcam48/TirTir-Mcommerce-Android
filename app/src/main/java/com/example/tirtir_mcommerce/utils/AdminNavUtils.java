package com.example.tirtir_mcommerce.utils;

import android.app.Activity;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.activities.AdminActivity;
import com.example.tirtir_mcommerce.ui.activities.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminNavUtils {

    public static void setupBottomNav(Activity activity, BottomNavigationView bottomNav, int currentItemId) {
        // Force white background and no tint for Admin Bottom Nav
        bottomNav.setBackgroundResource(R.drawable.bg_bottom_nav_admin);
        bottomNav.setBackgroundTintList(null);

        bottomNav.setSelectedItemId(currentItemId);
        bottomNav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_SELECTED);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentItemId) {
                return true;
            }

            if (itemId == R.id.nav_admin_logout) {
                confirmLogout(activity);
                return false;
            }

            Intent intent = new Intent(activity, AdminActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("navigate_to", itemId);
            activity.startActivity(intent);
            
            // Finish the child activity if we are navigating away
            if (!(activity instanceof AdminActivity)) {
                activity.finish();
            }
            return true;
        });
    }

    private static void confirmLogout(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Sign out")
                .setMessage("Sign out of the admin console?")
                .setPositiveButton("Sign out", (dialog, which) -> {
                    SharedPrefsManager prefs = new SharedPrefsManager(activity);
                    prefs.clearAuthSession();
                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
