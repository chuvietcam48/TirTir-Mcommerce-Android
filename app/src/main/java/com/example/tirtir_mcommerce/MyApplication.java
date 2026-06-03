package com.example.tirtir_mcommerce;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.google.firebase.FirebaseApp;

/**
 * MyApplication — Application class khởi tạo toàn bộ app.
 *
 * Đăng ký NetworkReceiver động ở đây thay vì AndroidManifest
 * (CONNECTIVITY_ACTION bị deprecated trong manifest từ API 28+).
 */
public class MyApplication extends Application {

    private NetworkReceiver networkReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

        // Đăng ký NetworkReceiver để tự động sync giỏ hàng khi có mạng
        networkReceiver = new NetworkReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // Hủy đăng ký receiver khi app kết thúc
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }
    }
}