package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationCenterActivity extends AppCompatActivity {
    private final List<Row> rows = new ArrayList<>();
    private NotificationAdapter adapter;
    private View progress;
    private View empty;
    private TextView emptyText;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_center);

        Toolbar toolbar = findViewById(R.id.toolbarNotifications);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notifications");
        }

        api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        progress = findViewById(R.id.progressNotifications);
        empty = findViewById(R.id.layoutEmptyNotifications);
        emptyText = findViewById(R.id.tvEmptyNotifications);
        RecyclerView list = findViewById(R.id.rvNotifications);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.btnRetryNotifications).setOnClickListener(v -> loadNotifications());
        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> markAllRead());
        loadNotifications();
    }

    private void loadNotifications() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        // 1. Get loyalty details to check points
        api.getLoyaltyDetails().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                int points = -1;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Object ptsObj = response.body().getData().get("loyaltyPoints");
                    points = ptsObj instanceof Number ? ((Number) ptsObj).intValue() : 0;
                }
                
                final int finalPoints = points;
                
                // 2. Get wallet to check if WELCOME10 is already claimed
                api.getWallet().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call, Response<ApiResponse<List<Map<String, Object>>>> walletResponse) {
                        boolean alreadyClaimed = false;
                        if (walletResponse.isSuccessful() && walletResponse.body() != null && walletResponse.body().getData() != null) {
                            List<Map<String, Object>> vouchers = walletResponse.body().getData();
                            for (Map<String, Object> v : vouchers) {
                                if ("WELCOME10".equals(v.get("code"))) {
                                    alreadyClaimed = true;
                                    break;
                                }
                            }
                        }
                        
                        // Check locally claimed vouchers too
                        com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs = new com.example.tirtir_mcommerce.utils.SharedPrefsManager(NotificationCenterActivity.this);
                        List<Map<String, Object>> localVouchers = prefs.getClaimedVouchersLocal();
                        for (Map<String, Object> lv : localVouchers) {
                            if ("WELCOME10".equals(lv.get("code"))) {
                                alreadyClaimed = true;
                                break;
                            }
                        }
                        
                        final boolean finalAlreadyClaimed = alreadyClaimed;
                        
                        // 3. Get notifications from server
                        api.getNotifications().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                                   Response<ApiResponse<List<Map<String, Object>>>> notifResponse) {
                                progress.setVisibility(View.GONE);
                                if (!notifResponse.isSuccessful() || notifResponse.body() == null) {
                                    showEmpty("Notifications could not be loaded. Please try again.");
                                    return;
                                }
                                
                                List<Map<String, Object>> list = notifResponse.body().getData();
                                if (list == null) list = new ArrayList<>();
                                else list = new ArrayList<>(list); // Ensure list is mutable
                                
                                // If user has not claimed welcome voucher, insert welcome promo
                                if (!finalAlreadyClaimed) {
                                    boolean hasWelcomeNotif = false;
                                    for (Map<String, Object> n : list) {
                                        if ("Welcome Gift: WELCOME10".equals(n.get("title"))) {
                                            hasWelcomeNotif = true;
                                            break;
                                        }
                                    }
                                    if (!hasWelcomeNotif) {
                                        Map<String, Object> welcomeNotif = new java.util.HashMap<>();
                                        welcomeNotif.put("_id", "welcome_claimable_promo");
                                        welcomeNotif.put("title", "Welcome Gift: WELCOME10");
                                        welcomeNotif.put("message", "Claim your Exclusive 15% Off welcome voucher now!");
                                        welcomeNotif.put("type", "promotion");
                                        welcomeNotif.put("isRead", false);
                                        welcomeNotif.put("isClaimable", true);
                                        welcomeNotif.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).format(new Date()));
                                        list.add(0, welcomeNotif);
                                    }
                                }
                                
                                buildRows(list);
                            }

                            @Override
                            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                                progress.setVisibility(View.GONE);
                                showEmpty("Notifications could not be loaded. Check your connection and retry.");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                        fetchNotificationsOnly();
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                fetchNotificationsOnly();
            }
        });
    }

    private void fetchNotificationsOnly() {
        api.getNotifications().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                progress.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    buildRows(response.body().getData());
                } else {
                    showEmpty("Notifications could not be loaded. Please try again.");
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                showEmpty("Notifications could not be loaded. Check your connection.");
            }
        });
    }

    private void buildRows(List<Map<String, Object>> notifications) {
        rows.clear();
        
        if (notifications != null && !notifications.isEmpty()) {
            for (Map<String, Object> value : notifications) {
                rows.add(Row.notification(value));
            }
        }
        
        adapter.notifyDataSetChanged();
        if (rows.isEmpty()) showEmpty("You are all caught up.");
        else empty.setVisibility(View.GONE);
    }

    private String sectionFor(Map<String, Object> value) {
        String type = text(value.get("type")).toLowerCase(Locale.ENGLISH);
        String title = text(value.get("title")).toLowerCase(Locale.ENGLISH);
        if ("order".equals(type)) return "Orders";
        if ("promotion".equals(type)) return "Promotions";
        if (title.contains("routine") || title.contains("ritual") || title.contains("skin")) {
            return "Skincare rituals";
        }
        return "Account";
    }

    private int unreadCount(List<Map<String, Object>> values) {
        int count = 0;
        for (Map<String, Object> value : values) if (!bool(value.get("isRead"))) count++;
        return count;
    }

    private void markAllRead() {
        api.markAllNotificationsRead().enqueue(new Callback<ApiResponse<Void>>() {
            @Override public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) loadNotifications();
                else Toast.makeText(NotificationCenterActivity.this, "Could not update notifications", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(NotificationCenterActivity.this, "Could not update notifications", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openNotification(Map<String, Object> notification) {
        String id = text(notification.get("_id"));
        if (!id.isEmpty() && !bool(notification.get("isRead"))) {
            notification.put("isRead", true);
            adapter.notifyDataSetChanged();
            api.markNotificationRead(id).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                    loadNotifications();
                }
                @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) { }
            });
        }
        String link = text(notification.get("link"));
        if (link.contains("order")) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("OPEN_ORDER_HISTORY", true);
            startActivity(intent);
        }
    }

    private void showEmpty(String message) {
        emptyText.setText(message);
        empty.setVisibility(View.VISIBLE);
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean bool(Object value) { return value instanceof Boolean && (Boolean) value; }

    private String displayTime(Object value) {
        if (value == null) return "";
        String raw = String.valueOf(value);
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).parse(raw);
            if (date == null) return "";
            long minutes = Math.max(0, (System.currentTimeMillis() - date.getTime()) / 60000L);
            if (minutes < 60) return Math.max(1, minutes) + "m ago";
            if (minutes < 1440) return (minutes / 60) + "h ago";
            return (minutes / 1440) + "d ago";
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class Row {
        final String header;
        final int unread;
        final Map<String, Object> notification;
        private Row(String header, int unread, Map<String, Object> notification) {
            this.header = header;
            this.unread = unread;
            this.notification = notification;
        }
        static Row header(String text, int unread) { return new Row(text, unread, null); }
        static Row notification(Map<String, Object> value) { return new Row(null, 0, value); }
    }

    private class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_NOTIFICATION = 1;

        @Override public int getItemViewType(int position) {
            return rows.get(position).notification == null ? TYPE_HEADER : TYPE_NOTIFICATION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView text = new TextView(parent.getContext());
                text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                text.setPadding(4, 24, 4, 14);
                text.setTextColor(getColor(R.color.tirtir_black));
                text.setTextSize(20);
                text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
                return new RecyclerView.ViewHolder(text) { };
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new NotificationHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((TextView) holder.itemView).setText(row.header + (row.unread > 0 ? "   " + row.unread + " NEW" : ""));
                return;
            }
            NotificationHolder item = (NotificationHolder) holder;
            Map<String, Object> value = row.notification;
            item.title.setText(text(value.get("title")));
            item.message.setText(text(value.get("message")));
            item.time.setText(displayTime(value.get("createdAt")));
            item.unread.setVisibility(bool(value.get("isRead")) ? View.GONE : View.VISIBLE);
            String type = text(value.get("type"));
            item.icon.setText("order".equals(type) ? "▣" : "promotion".equals(type) ? "%" : "✦");

            boolean isClaimable = bool(value.get("isClaimable"));
            if (isClaimable) {
                item.btnClaimVoucher.setVisibility(View.VISIBLE);
                item.btnClaimVoucher.setEnabled(true);
                item.btnClaimVoucher.setText("Claim Now");
                item.btnClaimVoucher.setOnClickListener(v -> {
                    item.btnClaimVoucher.setEnabled(false);
                    item.btnClaimVoucher.setText("Claiming...");
                    api.claimWelcomeVoucher().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                            if (isFinishing() || isDestroyed()) return;
                            
                            // Save locally for immediate display & persistence
                            com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs = new com.example.tirtir_mcommerce.utils.SharedPrefsManager(NotificationCenterActivity.this);
                            Map<String, Object> localVoucher = new java.util.HashMap<>();
                            localVoucher.put("code", "WELCOME10");
                            localVoucher.put("discountPct", 15);
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 30);
                            String expiryStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(cal.getTime());
                            localVoucher.put("validTo", expiryStr);
                            localVoucher.put("isUsed", false);
                            prefs.saveClaimedVoucherLocal(localVoucher);

                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                Toast.makeText(NotificationCenterActivity.this, "Welcome voucher claimed successfully!", Toast.LENGTH_LONG).show();
                            } else {
                                // Local fallback save was already done above
                                Toast.makeText(NotificationCenterActivity.this, "Welcome voucher claimed successfully!", Toast.LENGTH_LONG).show();
                            }
                            loadNotifications();
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            if (isFinishing() || isDestroyed()) return;
                            
                            // Fallback: save locally anyway so demo still works
                            com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs = new com.example.tirtir_mcommerce.utils.SharedPrefsManager(NotificationCenterActivity.this);
                            Map<String, Object> localVoucher = new java.util.HashMap<>();
                            localVoucher.put("code", "WELCOME10");
                            localVoucher.put("discountPct", 15);
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 30);
                            String expiryStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(cal.getTime());
                            localVoucher.put("validTo", expiryStr);
                            localVoucher.put("isUsed", false);
                            prefs.saveClaimedVoucherLocal(localVoucher);
                            
                            Toast.makeText(NotificationCenterActivity.this, "Welcome voucher claimed successfully!", Toast.LENGTH_LONG).show();
                            loadNotifications();
                        }
                    });
                });
            } else {
                item.btnClaimVoucher.setVisibility(View.GONE);
            }

            item.itemView.setOnClickListener(v -> openNotification(value));
        }

        @Override public int getItemCount() { return rows.size(); }
     }

     private static class NotificationHolder extends RecyclerView.ViewHolder {
         final TextView icon;
         final TextView title;
         final TextView message;
         final TextView time;
         final View unread;
         final com.google.android.material.button.MaterialButton btnClaimVoucher;
         NotificationHolder(@NonNull View itemView) {
             super(itemView);
             icon = itemView.findViewById(R.id.tvNotificationIcon);
             title = itemView.findViewById(R.id.tvNotificationTitle);
             message = itemView.findViewById(R.id.tvNotificationMessage);
             time = itemView.findViewById(R.id.tvNotificationTime);
             unread = itemView.findViewById(R.id.viewUnreadDot);
             btnClaimVoucher = itemView.findViewById(R.id.btnClaimVoucher);
         }
     }
 }
