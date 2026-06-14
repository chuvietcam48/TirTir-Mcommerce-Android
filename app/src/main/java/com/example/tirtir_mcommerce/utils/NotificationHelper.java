package com.example.tirtir_mcommerce.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.activities.OrderSuccessActivity;

import java.util.Map;

/**
 * Helper class để quản lý Notification Channel và hiển thị Local Notification.
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    public static final String CHANNEL_ORDERS = "tirtir_orders";
    public static final String CHANNEL_MARKETING = "tirtir_marketing";
    public static final String CHANNEL_SYSTEM = "tirtir_system";

    /**
     * Tạo các Notification Channel cho Android 8.0 (API 26) trở lên.
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            // 1. Channel Order Updates (High Importance)
            NotificationChannel orderChannel = new NotificationChannel(
                    CHANNEL_ORDERS,
                    "Order Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            orderChannel.setDescription("Notifications about order placement and status updates.");
            orderChannel.enableLights(true);
            orderChannel.enableVibration(true);
            orderChannel.setSound(defaultSoundUri, audioAttributes);

            // 2. Channel Marketing (Default Importance)
            NotificationChannel marketingChannel = new NotificationChannel(
                    CHANNEL_MARKETING,
                    "Promotions & Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            marketingChannel.setDescription("Notifications about promotions, deals, and reminders.");
            marketingChannel.enableLights(true);
            marketingChannel.setSound(defaultSoundUri, audioAttributes);

            // 3. Channel System (Default Importance)
            NotificationChannel systemChannel = new NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            systemChannel.setDescription("Important system notifications.");
            systemChannel.enableLights(true);
            systemChannel.setSound(defaultSoundUri, audioAttributes);

            manager.createNotificationChannel(orderChannel);
            manager.createNotificationChannel(marketingChannel);
            manager.createNotificationChannel(systemChannel);

            Log.d(TAG, "Notification Channels created successfully.");
        }
    }

    /**
     * Hiển thị notification.
     */
    public static void showNotification(Context context, String title, String body, Map<String, String> data) {
        String type = data != null ? data.get("type") : "";
        String orderId = data != null ? data.get("orderId") : "";
        String orderCode = data != null ? data.get("orderCode") : "";

        // Chọn channel dựa trên type
        String channelId = CHANNEL_SYSTEM;
        int importance = NotificationCompat.PRIORITY_DEFAULT;

        if ("order_success".equals(type) || "order_status".equals(type) || "order".equals(type)) {
            channelId = CHANNEL_ORDERS;
            importance = NotificationCompat.PRIORITY_HIGH;
        } else if ("cart_recovery".equals(type) || "promotion".equals(type)) {
            channelId = CHANNEL_MARKETING;
        }

        // Định tuyến màn hình khi tap vào notification
        Intent intent;
        if ("order_success".equals(type) || "order_status".equals(type)) {
            // Mở OrderSuccessActivity nếu có ID/Code
            intent = new Intent(context, OrderSuccessActivity.class);
            intent.putExtra("ORDER_CODE", orderCode != null && !orderCode.isEmpty() ? orderCode : orderId);
            // Có thể truyền thêm total nếu backend trả về, mặc định 0.0 nếu thiếu
            double orderTotal = 0.0;
            if (data != null && data.containsKey("orderTotal")) {
                try {
                    orderTotal = Double.parseDouble(data.get("orderTotal"));
                } catch (NumberFormatException ignored) {}
            }
            intent.putExtra("ORDER_TOTAL", orderTotal);
        } else if ("cart_recovery".equals(type)) {
            // Cart là Fragment trong MainActivity, mở MainActivity và gửi flag
            intent = new Intent(context, MainActivity.class);
            intent.putExtra("OPEN_CART", true);
        } else {
            intent = new Intent(context, MainActivity.class);
        }

        // Handle screen deep link from S2.2
        if (data != null && data.containsKey("screen")) {
            String screen = data.get("screen");
            if (screen != null) {
                intent.putExtra("NAVIGATE_TO", screen.toLowerCase());
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent, flags);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher) // Dùng launcher icon của app
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(importance)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Android 13 POST_NOTIFICATIONS check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Notification permission not granted, cannot show notification.");
                    return;
                }
            }
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
