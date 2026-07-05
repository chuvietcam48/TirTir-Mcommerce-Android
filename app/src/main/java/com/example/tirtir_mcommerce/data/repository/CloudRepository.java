package com.example.tirtir_mcommerce.data.repository;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.FcmTokenRequest;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * CloudRepository quản lý Firebase Auth (Anonymous), Firestore và FCM Token sync.
 */
public class CloudRepository {

    private static final String TAG = "CloudRepository";

    private final Context context;
    private final SharedPrefsManager prefsManager;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public CloudRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefsManager = new SharedPrefsManager(context);
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public interface AuthCallback {
        void onComplete(String firebaseUid);
    }

    /**
     * 1. Khởi tạo / Đảm bảo Firebase User đã được xác thực ẩn danh hoặc bằng email/password.
     */
    public void ensureFirebaseUser(AuthCallback callback) {
        ensureFirebaseUser(null, null, callback);
    }

    public void ensureFirebaseUser(String email, String password, AuthCallback callback) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (email != null && !email.trim().isEmpty() && password != null && !password.trim().isEmpty()) {
            if (currentUser != null && email.equalsIgnoreCase(currentUser.getEmail())) {
                String uid = currentUser.getUid();
                prefsManager.saveFirebaseUid(uid);
                Log.d(TAG, "Firebase session already active for email: " + email + ". Reusing UID: " + uid);
                if (callback != null) callback.onComplete(uid);
            } else {
                firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                                String uid = firebaseAuth.getCurrentUser().getUid();
                                prefsManager.saveFirebaseUid(uid);
                                Log.d(TAG, "Firebase Email Sign-in Successful. UID: " + uid);
                                if (callback != null) callback.onComplete(uid);
                            } else {
                                // If user doesn't exist, try to create one
                                firebaseAuth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(createTask -> {
                                            if (createTask.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                                                String uid = firebaseAuth.getCurrentUser().getUid();
                                                prefsManager.saveFirebaseUid(uid);
                                                Log.d(TAG, "Firebase Email Registration Successful. UID: " + uid);
                                                if (callback != null) callback.onComplete(uid);
                                            } else {
                                                Log.e(TAG, "Firebase Email Auth failed, falling back to anonymous", createTask.getException());
                                                signInAnonymously(callback);
                                            }
                                        });
                            }
                        });
            }
        } else if (currentUser != null) {
            String uid = currentUser.getUid();
            prefsManager.saveFirebaseUid(uid);
            if (callback != null) {
                callback.onComplete(uid);
            }
        } else {
            signInAnonymously(callback);
        }
    }

    private void signInAnonymously(AuthCallback callback) {
        firebaseAuth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        String uid = firebaseAuth.getCurrentUser().getUid();
                        prefsManager.saveFirebaseUid(uid);
                        Log.d(TAG, "Firebase Anonymous Sign-in Successful. UID: " + uid);
                        if (callback != null) {
                            callback.onComplete(uid);
                        }
                    } else {
                        Log.e(TAG, "Firebase Anonymous Sign-in Failed", task.getException());
                        if (callback != null) {
                            callback.onComplete(null);
                        }
                    }
                });
    }

    /**
     * 2. Đồng bộ profile thông tin User lên Firestore.
     */
    public void syncUserProfileToFirestore(User user) {
        syncUserProfileToFirestore(user, null, null);
    }

    public void syncUserProfileToFirestore(User user, String email, String password) {
        if (user == null) return;

        ensureFirebaseUser(email, password, firebaseUid -> {
            if (firebaseUid == null) {
                Log.e(TAG, "Cannot sync profile: Firebase UID is null");
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("uid", firebaseUid);
            data.put("backendUserId", user.getId());
            data.put("email", user.getEmail());
            data.put("displayName", user.getName());
            data.put("fullName", user.getName());
            data.put("phone", user.getPhone());
            data.put("role", user.getRole() != null ? user.getRole() : "user");
            
            // Set defaults only if document does not exist, done using merge or set
            data.put("skinType", null);
            data.put("knownAllergies", new ArrayList<String>());
            data.put("loyaltyPoints", 0);
            data.put("loyaltyTier", "Bronze");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            data.put("lastLoginAt", FieldValue.serverTimestamp());

            firestore.collection("users").document(firebaseUid)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile successfully synced to Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error syncing user profile to Firestore", e));
        });
    }

    /**
     * 3. Đồng bộ FCM Token lên Firestore và gửi lên Node.js backend.
     */
    public void syncFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    prefsManager.saveFcmToken(token);

                    ensureFirebaseUser(firebaseUid -> {
                        if (firebaseUid == null) {
                            Log.e(TAG, "Cannot sync FCM Token: Firebase UID is null");
                            return;
                        }

                        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
                        String appVersion = getAppVersionName();
                        String safeTokenId = token.replaceAll("[^a-zA-Z0-9]", "_");

                        // a. Lưu lên Firestore
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("token", token);
                        tokenData.put("platform", "android");
                        tokenData.put("deviceModel", deviceModel);
                        tokenData.put("appVersion", appVersion);
                        tokenData.put("active", true);
                        tokenData.put("createdAt", FieldValue.serverTimestamp());
                        tokenData.put("updatedAt", FieldValue.serverTimestamp());

                        firestore.collection("users").document(firebaseUid)
                                .collection("fcmTokens").document(safeTokenId)
                                .set(tokenData, SetOptions.merge())
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token synced to Firestore"))
                                .addOnFailureListener(e -> Log.e(TAG, "Error syncing FCM token to Firestore", e));

                        // Cập nhật deviceToken và updatedAt trên user document để tuân thủ BRD
                        Map<String, Object> userUpdate = new HashMap<>();
                        userUpdate.put("deviceToken", token);
                        userUpdate.put("updatedAt", FieldValue.serverTimestamp());
                        firestore.collection("users").document(firebaseUid)
                                .set(userUpdate, SetOptions.merge());

                        // b. Gửi lên Node.js Backend API
                        sendFcmTokenToBackend(token, firebaseUid, deviceModel, appVersion);
                    });
                });
    }

    /**
     * Gửi FCM Token lên backend qua Retrofit API.
     */
    private void sendFcmTokenToBackend(String token, String firebaseUid, String deviceModel, String appVersion) {
        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);
        FcmTokenRequest request = new FcmTokenRequest(token, "android", firebaseUid, deviceModel, appVersion);

        apiService.registerFcmToken(request).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM token successfully registered on Backend server.");
                } else {
                    Log.w(TAG, "Failed to register FCM token on backend. Code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                Log.e(TAG, "Error connecting backend to register FCM Token", t);
            }
        });
    }

    /**
     * 4. Cập nhật Skin Type lên Firestore.
     */
    public void updateSkinType(String skinType) {
        String firebaseUid = getCurrentFirebaseUid();
        if (firebaseUid == null) {
            Log.e(TAG, "Cannot update skin type: Firebase UID is null");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("skinType", skinType);
        data.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection("users").document(firebaseUid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Skin type updated to: " + skinType))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating skin type", e));
    }

    /**
     * 5. Cập nhật Known Allergies lên Firestore.
     */
    public void updateKnownAllergies(List<String> allergies) {
        String firebaseUid = getCurrentFirebaseUid();
        if (firebaseUid == null) {
            Log.e(TAG, "Cannot update known allergies: Firebase UID is null");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("knownAllergies", allergies);
        data.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection("users").document(firebaseUid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Known allergies updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating known allergies", e));
    }

    /**
     * 6. Lấy Firebase UID hiện tại.
     */
    public String getCurrentFirebaseUid() {
        String localUid = prefsManager.getFirebaseUid();
        if (localUid != null) {
            return localUid;
        }
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }

    private String getAppVersionName() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }
}
