package com.example.tirtir_mcommerce.network;

import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * OkHttp Interceptor tự động đính kèm JWT token vào mọi request HTTP.
 *
 * Nguyên lý hoạt động:
 * - Mỗi request gửi đi đều bị interceptor này "chặn" lại.
 * - Interceptor sẽ đọc token từ SharedPreferences và gắn vào header:
 *   Authorization: Bearer <token>
 * - Nếu không có token (chưa đăng nhập), request vẫn được gửi bình thường
 *   (các API public như login/register không cần token).
 */
public class AuthInterceptor implements Interceptor {

    private final SharedPrefsManager prefsManager;

    public AuthInterceptor(SharedPrefsManager prefsManager) {
        this.prefsManager = prefsManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String token = prefsManager.getToken();

        // Nếu không có token, gửi request bình thường
        if (token == null || token.isEmpty()) {
            return chain.proceed(originalRequest);
        }

        // Đính kèm token vào header Authorization
        Request authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();

        return chain.proceed(authenticatedRequest);
    }
}
