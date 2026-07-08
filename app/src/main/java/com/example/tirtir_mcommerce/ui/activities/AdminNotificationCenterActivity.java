package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.AdminNotification;
import com.example.tirtir_mcommerce.ui.adapters.AdminNotificationAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AdminNotificationCenterActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private AdminNotificationAdapter adapter;
    private List<AdminNotification> notificationList;
    private MaterialButton btnMarkAllRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_notification_center);

        rvNotifications = findViewById(R.id.rvAdminNotifications);
        btnMarkAllRead = findViewById(R.id.btnMarkAllReadAdmin);

        // Setup mock data based on HTML mockup
        setupMockData();

        adapter = new AdminNotificationAdapter(notificationList);
        
        // Bento grid setup (defaults to 12 spans for full width on mobile)
        GridLayoutManager layoutManager = new GridLayoutManager(this, 12);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Return 12 for full width on mobile devices
                // In a tablet layout, this could return 8, 4, 6 depending on the item type
                return 12;
            }
        });
        
        rvNotifications.setLayoutManager(layoutManager);
        rvNotifications.setAdapter(adapter);

        btnMarkAllRead.setOnClickListener(v -> markAllAsRead());
        
        android.view.View btnAdminBack = findViewById(R.id.btnAdminBack);
        if (btnAdminBack != null) {
            btnAdminBack.setOnClickListener(v -> finish());
        }
        
        findViewById(R.id.btnAdminNotifSettings).setOnClickListener(v -> {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupMockData() {
        notificationList = new ArrayList<>();
        
        notificationList.add(new AdminNotification(
                "1", AdminNotification.Type.INVENTORY, "2 mins ago",
                "Low Stock Alert: Mask Fit Red Cushion (17C) is below 10 units.",
                "Restock Now", null
        ));

        notificationList.add(new AdminNotification(
                "2", AdminNotification.Type.SALES, "1 hour ago",
                "New VIP Order: Evelyn Dubois placed an order for $1,250.",
                "View Order", null
        ));

        notificationList.add(new AdminNotification(
                "3", AdminNotification.Type.SECURITY, "3 hours ago",
                "New Login Detected: Unusual activity from IP 192.168.1.1 (London, UK).",
                "Review History", "Ignore"
        ));

        notificationList.add(new AdminNotification(
                "4", AdminNotification.Type.FEEDBACK, "5 hours ago",
                "New 1-star review received for 'Ceramic Milk Ampoule'.",
                "Follow Up", "View Review"
        ));

        notificationList.add(new AdminNotification(
                "5", AdminNotification.Type.SYSTEM, "Yesterday, 11:45 PM",
                "API Sync Complete: 1,200 products updated successfully.",
                null, null
        ));
    }

    private void markAllAsRead() {
        for (int i = 0; i < notificationList.size(); i++) {
            final int index = i;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                notificationList.get(index).setRead(true);
                adapter.notifyItemChanged(index);
            }, i * 50L); // Staggered animation effect like in HTML script
        }
    }
}
