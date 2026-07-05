package com.example.tirtir_mcommerce.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.LoginRequest;
import com.example.tirtir_mcommerce.model.LoginResponse;
import com.example.tirtir_mcommerce.model.RegisterRequest;
import com.example.tirtir_mcommerce.model.RegisterResponse;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.example.tirtir_mcommerce.data.repository.CloudRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository xử lý toàn bộ logic nghiệp vụ liên quan đến Xác thực.
 *
 * Theo pattern MVVM:
 * - AuthViewModel gọi các method ở Repository này
 * - Repository giao tiếp với API (Retrofit) và SharedPreferences
 * - Repository trả kết quả về ViewModel thông qua LiveData (callback pattern)
 * - ViewModel expose LiveData cho UI (Activity/Fragment) quan sát
 *
 * Điều này giúp tách biệt hoàn toàn business logic ra khỏi UI.
 */
public class AuthRepository {
    private static final String TAG = "AuthRepository";

    private final Context context;
    private final ApiService apiService;
    private final SharedPrefsManager prefsManager;

    public AuthRepository(Context context) {
        this.context = context.getApplicationContext();
        this.apiService = RetrofitClient.getClient().create(ApiService.class);
        this.prefsManager = new SharedPrefsManager(context);
    }

    // ===========================
    // LOGIN
    // ===========================

    /**
     * Thực hiện đăng nhập.
     * @param email    Email người dùng
     * @param password Mật khẩu
     * @param onSuccess Callback khi đăng nhập thành công, trả về User
     * @param onError   Callback khi thất bại, trả về message lỗi
     */
    public void login(String email, String password,
                      OnSuccessListener<User> onSuccess,
                      OnErrorListener onError) {

        LoginRequest request = new LoginRequest(email, password);
        apiService.loginUser(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse body = response.body();
                    if (body.getToken() != null && !body.getToken().trim().isEmpty()) {
                        // Lưu token vào SharedPreferences
                        // Lưu cache user nếu có
                        if (body.getUser() != null) {
                            prefsManager.saveUser(body.getUser());
                        }
                        prefsManager.saveSession(body.getToken(), body.getRefreshToken());
                        if (body.getUser() != null) {
                            // Đồng bộ với Firebase/Firestore bất đồng bộ bằng Email/Password
                            try {
                                CloudRepository cloudRepository = new CloudRepository(context);
                                cloudRepository.syncUserProfileToFirestore(body.getUser(), email, password);
                                cloudRepository.syncFcmToken();
                            } catch (Exception e) {
                                android.util.Log.e("AuthRepository", "Firebase sync failed", e);
                            }
                        }
                        if (onSuccess != null) onSuccess.onSuccess(body.getUser());
                    } else {
                        if (onError != null) onError.onError("Sign-in failed. Please try again.");
                    }
                } else {
                    Log.e(TAG, "Login API failed with HTTP " + response.code());
                    if (response.code() == 401) {
                        if (onError != null) onError.onError("Incorrect email or password");
                    } else if (response.code() == 403) {
                        if (onError != null) {
                            onError.onError("This account is locked. Please contact support.");
                        }
                    } else {
                        if (onError != null) {
                            onError.onError("Sign-in is temporarily unavailable. Please try again.");
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.e(TAG, "Login request failed", t);
                if (onError != null) {
                    onError.onError("Connection error. Please check your network.");
                }
            }
        });
    }

    // ===========================
    // REGISTER
    // ===========================

    /**
     * Thực hiện đăng ký tài khoản mới.
     */
    public void register(String firstName, String lastName, String email, String password,
                         OnSuccessListener<String> onSuccess,
                         OnErrorListener onError) {

        RegisterRequest request = new RegisterRequest(firstName, lastName, email, password);
        apiService.registerUser(request).enqueue(new Callback<RegisterResponse>() {
            @Override
            public void onResponse(Call<RegisterResponse> call, Response<RegisterResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    RegisterResponse body = response.body();
                    if (body.getUser() != null) {
                        prefsManager.saveUser(body.getUser());
                        // Đồng bộ với Firebase/Firestore bất đồng bộ bằng Email/Password sau khi đăng ký thành công
                        try {
                            CloudRepository cloudRepository = new CloudRepository(context);
                            cloudRepository.syncUserProfileToFirestore(body.getUser(), email, password);
                            cloudRepository.syncFcmToken();
                        } catch (Exception e) {
                            android.util.Log.e("AuthRepository", "Firebase registration sync failed", e);
                        }
                    }
                    if (body.getToken() != null && !body.getToken().isEmpty()) {
                        prefsManager.saveSession(body.getToken(), body.getRefreshToken());
                    }
                    onSuccess.onSuccess(body.getMessage());
                } else {
                    onError.onError("Could not create the account. The email may already be in use.");
                }
            }

            @Override
            public void onFailure(Call<RegisterResponse> call, Throwable t) {
                onError.onError("Connection error. Please check your network.");
            }
        });
    }

    public void forgotPassword(String email,
                               OnSuccessListener<String> onSuccess,
                               OnErrorListener onError) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        apiService.forgotPassword(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    String message = response.body() != null ? response.body().getMessage() : null;
                    onSuccess.onSuccess(message == null || message.isEmpty()
                            ? "Password reset instructions have been sent."
                            : message);
                } else {
                    onError.onError("Unable to send reset instructions for this email.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                onError.onError("Connection error. Please check your network.");
            }
        });
    }

    public void verifyOTP(String email, String otp,
                          OnSuccessListener<String> onSuccess,
                          OnErrorListener onError) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("otp", otp);
        apiService.verifyOTP(body).enqueue(new Callback<ApiResponse<String>>() {
            @Override
            public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        String resetToken = response.body().getData();
                        onSuccess.onSuccess(resetToken);
                    } else {
                        onError.onError(response.body().getMessage() != null ? response.body().getMessage() : "Mã OTP không hợp lệ");
                    }
                } else {
                    onError.onError("Mã OTP không hợp lệ hoặc đã hết hạn.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                onError.onError("Connection error. Please check your network.");
            }
        });
    }

    public void resetPassword(String email, String resetToken, String newPassword,
                              OnSuccessListener<String> onSuccess,
                              OnErrorListener onError) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("resetToken", resetToken);
        body.put("newPassword", newPassword);

        apiService.resetPassword(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                // If the response is 401, Retrofit considers isSuccessful() false.
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        onSuccess.onSuccess("Password updated successfully");
                    } else {
                        onError.onError(response.body().getMessage() != null ? response.body().getMessage() : "Failed to reset password.");
                    }
                } else if (response.code() == 401) {
                    onError.onError("401"); // Special signal for token expired
                } else {
                    onError.onError("Failed to reset password. Please try again.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                onError.onError("Connection error. Please check your network.");
            }
        });
    }

    // ===========================
    // LOGOUT
    // ===========================

    /**
     * Đăng xuất: Xóa token local và gọi API invalidate token trên server.
     */
    public void logout(Context context, OnSuccessListener<Void> onSuccess, OnErrorListener onError) {
        // Tạo authenticated client để gọi logout API
        ApiService authApiService = RetrofitClient.getAuthClient(context).create(ApiService.class);
        authApiService.logout().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                // Dù server trả lỗi hay thành công, ta vẫn xóa local data
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                } catch (Exception e) {
                    Log.e(TAG, "Error signing out of Firebase: " + e.getMessage());
                }
                prefsManager.clear();
                onSuccess.onSuccess(null);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                // Offline logout: Vẫn xóa local data kể cả khi không có mạng
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                } catch (Exception e) {
                    Log.e(TAG, "Error signing out of Firebase (offline): " + e.getMessage());
                }
                prefsManager.clear();
                onSuccess.onSuccess(null);
            }
        });
    }

    // ===========================
    // HELPERS
    // ===========================

    public boolean isLoggedIn() {
        return prefsManager.isLoggedIn();
    }

    public User getCachedUser() {
        return prefsManager.getCachedUser();
    }

    // ===========================
    // CALLBACK INTERFACES
    // ===========================

    public interface OnSuccessListener<T> {
        void onSuccess(T result);
    }

    public interface OnErrorListener {
        void onError(String message);
    }
}
