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
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER = "cached_user";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_FIREBASE_UID = "firebase_uid";
    private static final String KEY_LOGIN_AT = "login_at";

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
        sharedPreferences.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_LOGIN_AT, System.currentTimeMillis())
                .apply();
    }

    public void saveSession(String token, String refreshToken) {
        SharedPreferences.Editor editor = sharedPreferences.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_LOGIN_AT, System.currentTimeMillis());
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        }
        editor.apply();
    }

    public String getToken() {
        return sharedPreferences.getString(KEY_TOKEN, null);
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public boolean isLoggedIn() {
        String token = getToken();
        String refreshToken = getRefreshToken();
        if ((token == null || token.isEmpty())
                && (refreshToken == null || refreshToken.isEmpty())) {
            return false;
        }

        long loginAt = sharedPreferences.getLong(KEY_LOGIN_AT, 0L);
        if (loginAt == 0L) return true;

        User user = getCachedUser();
        boolean admin = user != null && user.isAdmin();
        long maxAge = admin
                ? 8L * 60L * 60L * 1000L
                : 30L * 24L * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - loginAt <= maxAge) return true;

        clearAuthSession();
        return false;
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
        return sharedPreferences.getString(KEY_LANGUAGE, "en");
    }

    // ===========================
    // LOGOUT
    // ===========================

    /**
     * Clears authentication data without resetting onboarding, language, or preferences.
     */
    public void clearAuthSession() {
        sharedPreferences.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER)
                .remove(KEY_LOGIN_AT)
                .remove(KEY_FIREBASE_UID)
                .apply();
    }

    public void clear() {
        clearAuthSession();
    }

    // ===========================
    // SKIN PROFILE OFFLINE SYNC
    // ===========================

    private static final String KEY_PENDING_SKIN_SYNC = "pending_skin_profile_sync";

    /**
     * Kiểm tra xem có hồ sơ da chưa sync sau khi user đăng nhập không.
     * Được set khi guest user hoàn thành phân tích da.
     */
    public boolean hasPendingSkinProfileSync() {
        return sharedPreferences.getBoolean(KEY_PENDING_SKIN_SYNC, false);
    }

    /**
     * Đặt cờ "cần sync skin profile lên server" sau khi user đăng nhập.
     */
    public void setPendingSkinProfileSync(boolean pending) {
        sharedPreferences.edit().putBoolean(KEY_PENDING_SKIN_SYNC, pending).apply();
    }
}
