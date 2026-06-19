package com.example.tirtir_mcommerce.network;

import android.content.Context;

import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton cung cấp instance Retrofit đã được cấu hình đầy đủ.
 *
 * Tính năng:
 * - AuthInterceptor: Tự động đính kèm JWT token vào mọi request
 * - Timeout 60s: Xử lý trường hợp Render free-tier ngủ giấc
 * - Singleton: Chỉ tạo 1 instance duy nhất trong toàn app (tiết kiệm bộ nhớ)
 */
public class RetrofitClient {

    private static Retrofit retrofit = null;

    /**
     * Lấy instance Retrofit không có xác thực (dùng cho Login/Register).
     */
    public static Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(ApiConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /**
     * Lấy instance Retrofit có AuthInterceptor (dùng cho mọi API yêu cầu đăng nhập).
     * Phải truyền Context để SharedPrefsManager đọc được token đã lưu.
     */
    public static Retrofit getAuthClient(Context context) {
        SharedPrefsManager prefsManager = new SharedPrefsManager(context);
        AuthInterceptor authInterceptor = new AuthInterceptor(prefsManager);
        TokenAuthenticator tokenAuthenticator = new TokenAuthenticator(context, prefsManager);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .build();

        return new Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
