package com.example.tirtir_mcommerce;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * NetworkReceiver — BroadcastReceiver theo dõi kết nối mạng.
 *
 * Khi mạng được khôi phục → tự động sync các cart items
 * đang pending (synced=0) từ SQLite lên server.
 *
 * Đăng ký động trong MyApplication (không dùng static receiver
 * vì CONNECTIVITY_ACTION deprecated trong AndroidManifest từ API 28+).
 *
 * Sprint 1.2 — Task B: SQLite Logic / Offline Cart / NetworkReceiver
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            if (isNetworkAvailable(context)) {
                Log.d(TAG, "Network restored — enqueueing CartSyncWorker");
                // Thay thế luồng new Thread() bằng WorkManager
                OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(CartSyncWorker.class).build();
                WorkManager.getInstance(context).enqueue(syncRequest);
            } else {
                Log.d(TAG, "Network lost");
            }
        }
    }

    /**
     * Kiểm tra trạng thái kết nối mạng hiện tại.
     * Hỗ trợ cả API 21+ (NetworkCapabilities) và fallback cũ.
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            // Fallback cho API < 23
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }
}
