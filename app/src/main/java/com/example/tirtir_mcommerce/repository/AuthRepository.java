package com.example.tirtir_mcommerce.repository;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.LoginRequest;
import com.example.tirtir_mcommerce.model.LoginResponse;
import com.example.tirtir_mcommerce.model.RegisterRequest;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

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

    private final ApiService apiService;
    private final SharedPrefsManager prefsManager;

    public AuthRepository(Context context) {
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
                    if (body.isSuccess() && body.getToken() != null) {
                        // Lưu token vào SharedPreferences
                        prefsManager.saveToken(body.getToken());
                        // Lưu cache user nếu có
                        if (body.getUser() != null) {
                            prefsManager.saveUser(body.getUser());
                        }
                        onSuccess.onSuccess(body.getUser());
                    } else {
                        onError.onError("Đăng nhập thất bại");
                    }
                } else {
                    if (response.code() == 401) {
                        onError.onError("Email hoặc mật khẩu không đúng");
                    } else if (response.code() == 403) {
                        onError.onError("Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ.");
                    } else {
                        onError.onError("Lỗi máy chủ: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                onError.onError("Lỗi kết nối: " + t.getMessage());
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
        apiService.registerUser(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    onSuccess.onSuccess(response.body().getMessage());
                } else {
                    onError.onError("Đăng ký thất bại. Vui lòng thử lại.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                onError.onError("Lỗi kết nối: " + t.getMessage());
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
                prefsManager.clear();
                onSuccess.onSuccess(null);
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                // Offline logout: Vẫn xóa local data kể cả khi không có mạng
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
