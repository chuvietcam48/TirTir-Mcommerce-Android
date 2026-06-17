package com.example.tirtir_mcommerce;

import android.util.Log;

import com.example.tirtir_mcommerce.data.repository.CloudRepository;
import com.example.tirtir_mcommerce.utils.NotificationHelper;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.model.ApiResponse;

import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Service xử lý các sự kiện Firebase Cloud Messaging (FCM).
 */
public class TirTirFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token received: " + token);

        // Lưu local
        SharedPrefsManager prefsManager = new SharedPrefsManager(this);
        prefsManager.saveFcmToken(token);

        // Gọi CloudRepository sync nếu có phiên đăng nhập
        if (prefsManager.isLoggedIn()) {
            try {
                CloudRepository cloudRepository = new CloudRepository(this);
                cloudRepository.syncFcmToken();
                
                // Gọi thêm API POST /api/users/device-token theo yêu cầu S2.2
                ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
                Map<String, String> body = new HashMap<>();
                body.put("token", token);
                api.updateDeviceToken(body).enqueue(new Callback<ApiResponse<Object>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                        Log.d(TAG, "updateDeviceToken success: " + response.isSuccessful());
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                        Log.e(TAG, "updateDeviceToken failed", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync FCM Token after generation", e);
            }
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        String title = null;
        String body = null;
        Map<String, String> data = new HashMap<>();

        // 1. Lấy dữ liệu từ Notification payload nếu có
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // 2. Lấy dữ liệu từ Data payload
        if (remoteMessage.getData().size() > 0) {
            data.putAll(remoteMessage.getData());
            
            // Nếu notification payload trống, lấy từ data payload
            if (title == null) {
                title = data.get("title");
            }
            if (body == null) {
                body = data.get("body");
            }
        }

        if (title == null || title.isEmpty()) {
            title = "TirTir Notifications";
        }
        if (body == null || body.isEmpty()) {
            body = "You have a new update.";
        }

        Log.d(TAG, "Notification Title: " + title + ", Body: " + body + ", Data: " + data);

        SharedPrefsManager prefs = new SharedPrefsManager(this);
        String type = data.get("type");
        if ("RESTOCK".equalsIgnoreCase(type) && !prefs.getBoolean("pref_restock", true)) {
            Log.d(TAG, "Restock notification blocked by user preference.");
            return;
        }
        if ("ROUTINE".equalsIgnoreCase(type) && !prefs.getBoolean("pref_routine", true)) {
            Log.d(TAG, "Routine notification blocked by user preference.");
            return;
        }

        // Hiển thị notification cục bộ
        NotificationHelper.showNotification(this, title, body, data);

        // Vibrate 300ms
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(300);
            }
        }
    }
}
