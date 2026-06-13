package com.example.tirtir_mcommerce.ui.activities;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.adapters.NotificationSettingAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "tirtir_notification_settings";
    private static final String KEY_ROUTINE_TIME = "routine_reminder_time";

    private SharedPreferences prefs;
    private NotificationSettingAdapter adapter;
    private final List<NotificationSettingAdapter.NotificationSetting> settings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbarNotificationSettings);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rvSettings = findViewById(R.id.rvNotificationSettings);
        rvSettings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationSettingAdapter(new NotificationSettingAdapter.Listener() {
            @Override
            public void onSwitchChanged(NotificationSettingAdapter.NotificationSetting item, boolean enabled) {
                prefs.edit().putBoolean(item.key, enabled).apply();
                Toast.makeText(NotificationSettingsActivity.this,
                        enabled ? "Da bat " + item.name : "Da tat " + item.name,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeClick(NotificationSettingAdapter.NotificationSetting item) {
                showRoutineTimePicker(item);
            }
        });
        rvSettings.setAdapter(adapter);

        loadSettings();
    }

    private void loadSettings() {
        settings.clear();
        settings.add(createSetting("restock_alert", R.drawable.ic_cart,
                "Restock Alert", "Notify when a saved cushion or shade is back in stock.", false));
        settings.add(createSetting("routine_reminder", R.drawable.ic_routine,
                "Routine Reminder", "Remind your AM/PM skincare routine on time.", true));
        settings.add(createSetting("loyalty_milestone", R.drawable.ic_tirtir_logo,
                "Loyalty Milestone", "Track points, tiers, and new rewards.", false));
        settings.add(createSetting("ingredient_conflict", R.drawable.ic_scan,
                "Ingredient Conflict Alert", "Warn when routine ingredients may conflict.", false));
        settings.add(createSetting("skin_tip", R.drawable.ic_skin,
                "Skin-aware Tip", "Personalized tips from skin profile, scans, and orders.", false));
        adapter.submitList(settings);
    }

    private NotificationSettingAdapter.NotificationSetting createSetting(
            String key,
            int icon,
            String name,
            String description,
            boolean supportsTimePicker
    ) {
        NotificationSettingAdapter.NotificationSetting item =
                new NotificationSettingAdapter.NotificationSetting(key, icon, name, description, supportsTimePicker);
        item.enabled = prefs.getBoolean(key, false);
        item.time = prefs.getString(KEY_ROUTINE_TIME, "21:30");
        return item;
    }

    private void showRoutineTimePicker(NotificationSettingAdapter.NotificationSetting item) {
        String saved = prefs.getString(KEY_ROUTINE_TIME, "21:30");
        int hour = 21;
        int minute = 30;
        if (saved != null && saved.contains(":")) {
            String[] parts = saved.split(":");
            try {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        new TimePickerDialog(this, (view, selectedHour, selectedMinute) -> {
            String time = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute);
            prefs.edit()
                    .putString(KEY_ROUTINE_TIME, time)
                    .putBoolean(item.key, true)
                    .apply();
            item.time = time;
            item.enabled = true;
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Routine reminder set for " + time, Toast.LENGTH_SHORT).show();
        }, hour, minute, true).show();
    }
}
