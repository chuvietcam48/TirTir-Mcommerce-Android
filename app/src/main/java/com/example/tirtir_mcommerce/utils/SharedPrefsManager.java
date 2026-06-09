package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tirtir_mcommerce.model.User;
import com.google.gson.Gson;

/**
 * Quản lý toàn bộ dữ liệu lưu cục bộ trong SharedPreferences.
 *
 * Lưu trữ:
 * - JWT access token (để gọi API)
 * - Thông tin user (cache, hiển thị nhanh mà không cần gọi API)
 * - Ngôn ngữ đã chọn
 *
 * Sử dụng trong toàn bộ app: AuthRepository, ProfileFragment, LoginActivity...
 */
public class SharedPrefsManager {

    private static final String PREF_NAME = "tirtir_prefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER = "cached_user";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_FIREBASE_UID = "firebase_uid";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    public SharedPrefsManager(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // ===========================
    // TOKEN MANAGEMENT
    // ===========================

    public void saveToken(String token) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    // ===========================
    // CLOUD / FIREBASE
    // ===========================

    public void saveFcmToken(String token) {
        sharedPreferences.edit().putString(KEY_FCM_TOKEN, token).apply();
    }

    public String getFcmToken() {
        return sharedPreferences.getString(KEY_FCM_TOKEN, null);
    }

    public void saveFirebaseUid(String uid) {
        sharedPreferences.edit().putString(KEY_FIREBASE_UID, uid).apply();
    }

    public String getFirebaseUid() {
        return sharedPreferences.getString(KEY_FIREBASE_UID, null);
    }

    public void clearCloudSession() {
        sharedPreferences.edit()
                .remove(KEY_FCM_TOKEN)
                .remove(KEY_FIREBASE_UID)
                .apply();
    }

    // ===========================
    // USER CACHE
    // ===========================

    /**
     * Lưu cache thông tin user sau khi đăng nhập / cập nhật profile.
     * Dùng Gson để serialize object thành JSON String.
     */
    public void saveUser(User user) {
        String userJson = gson.toJson(user);
        sharedPreferences.edit().putString(KEY_USER, userJson).apply();
    }

    /**
     * Đọc thông tin user từ cache (không cần gọi API lại).
     * Trả về null nếu chưa có cache.
     */
    public User getCachedUser() {
        String userJson = sharedPreferences.getString(KEY_USER, null);
        if (userJson == null) return null;
        return gson.fromJson(userJson, User.class);
    }

    // ===========================
    // LANGUAGE
    // ===========================

    public void saveLanguage(String languageCode) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "vi"); // Mặc định Tiếng Việt
    }

    // ===========================
    // LOGOUT
    // ===========================

    /**
     * Xóa toàn bộ dữ liệu khi đăng xuất.
     */
    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
}
