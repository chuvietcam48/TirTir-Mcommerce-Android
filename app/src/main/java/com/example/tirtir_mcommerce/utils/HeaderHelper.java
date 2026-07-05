package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.NotificationCenterActivity;
import com.example.tirtir_mcommerce.ui.fragments.CartFragment;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HeaderHelper {

    public static void bind(View root, Context context, FragmentManager fragmentManager) {
        TextView tvGreeting = root.findViewById(R.id.tvGreeting);
        if (tvGreeting != null) {
            SharedPrefsManager prefs = new SharedPrefsManager(context);
            User user = prefs.getCachedUser();
            String name = (user != null && user.getName() != null && !user.getName().isEmpty())
                    ? user.getName().split(" ")[0]
                    : "Guest";
            tvGreeting.setText("Hello, " + name);
        }

        View btnHamburger = root.findViewById(R.id.btnHamburger);
        if (btnHamburger != null) {
            btnHamburger.setOnClickListener(v -> {
                if (context instanceof com.example.tirtir_mcommerce.MainActivity) {
                    ((com.example.tirtir_mcommerce.MainActivity) context).openDrawer();
                }
            });
        }

        View btnNotifications = root.findViewById(R.id.btnNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    context.startActivity(new Intent(context, NotificationCenterActivity.class)));
        }

        View btnCart = root.findViewById(R.id.btnCart);
        if (btnCart != null && fragmentManager != null) {
            btnCart.setOnClickListener(v ->
                    fragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, new CartFragment())
                            .addToBackStack(null)
                            .commit());
        }

        updateNotificationBadge(root, context);
    }

    public static void updateNotificationBadge(View root, Context context) {
        if (root == null || context == null) return;
        TextView tvNotificationBadge = root.findViewById(R.id.tvNotificationBadge);
        if (tvNotificationBadge == null) return;

        SharedPrefsManager prefs = new SharedPrefsManager(context);
        if (!prefs.isLoggedIn()) {
            tvNotificationBadge.setVisibility(View.GONE);
            return;
        }

        ApiService api = RetrofitClient.getAuthClient(context).create(ApiService.class);
        api.getNotifications().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call, Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Map<String, Object>> notifications = response.body().getData();
                    int unreadCount = 0;
                    for (Map<String, Object> notif : notifications) {
                        Object isRead = notif.get("isRead");
                        boolean read = isRead instanceof Boolean && (Boolean) isRead;
                        if (!read) {
                            unreadCount++;
                        }
                    }
                    if (unreadCount > 0) {
                        tvNotificationBadge.setText(String.valueOf(unreadCount));
                        tvNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvNotificationBadge.setVisibility(View.GONE);
                    }
                } else {
                    tvNotificationBadge.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        });
    }
}
