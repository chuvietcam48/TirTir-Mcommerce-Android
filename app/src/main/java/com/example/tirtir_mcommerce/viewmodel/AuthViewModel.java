package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.AuthRepository;

/**
 * ViewModel cho màn hình Đăng nhập và Đăng ký.
 * Kế thừa AndroidViewModel để có thể truy cập Application context (cần cho SharedPrefs).
 *
 * LiveData:
 * - isLoading: true khi đang gọi API (UI hiện ProgressBar)
 * - errorMessage: thông báo lỗi (UI hiện Toast/Snackbar)
 * - loginSuccess: kích hoạt điều hướng sang màn hình chính
 * - registerSuccess: kích hoạt thông báo đăng ký thành công
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;

    // LiveData cho UI observe
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<User> loginSuccess = new MutableLiveData<>();
    public final MutableLiveData<String> registerSuccess = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application.getApplicationContext());
    }

    // ===========================
    // LOGIN
    // ===========================

    public void login(String email, String password) {
        // Validate input trước khi gọi API
        if (!validateLoginInput(email, password)) return;

        isLoading.setValue(true);
        authRepository.login(email, password,
                user -> {
                    isLoading.postValue(false);
                    loginSuccess.postValue(user);
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    // ===========================
    // REGISTER
    // ===========================

    public void register(String firstName, String lastName, String email, String password, String confirmPassword) {
        if (!validateRegisterInput(firstName, lastName, email, password, confirmPassword)) return;

        isLoading.setValue(true);
        authRepository.register(firstName, lastName, email, password,
                message -> {
                    isLoading.postValue(false);
                    registerSuccess.postValue(message);
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    // ===========================
    // LOGOUT
    // ===========================

    public void logout(Runnable onLogoutDone) {
        authRepository.logout(getApplication(),
                result -> {
                    if (onLogoutDone != null) onLogoutDone.run();
                },
                message -> {
                    if (onLogoutDone != null) onLogoutDone.run(); // Vẫn logout dù lỗi
                }
        );
    }

    // ===========================
    // HELPERS
    // ===========================

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    private boolean validateLoginInput(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            errorMessage.setValue("Email không được để trống");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue("Email không hợp lệ");
            return false;
        }
        if (password == null || password.isEmpty()) {
            errorMessage.setValue("Mật khẩu không được để trống");
            return false;
        }
        return true;
    }

    private boolean validateRegisterInput(String firstName, String lastName, String email, String password, String confirmPassword) {
        if (firstName == null || firstName.trim().isEmpty()) {
            errorMessage.setValue("Tên không được để trống");
            return false;
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            errorMessage.setValue("Họ không được để trống");
            return false;
        }
        if (email == null || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue("Email không hợp lệ");
            return false;
        }
        if (password == null || password.length() < 8) {
            errorMessage.setValue("Mật khẩu phải có ít nhất 8 ký tự");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            errorMessage.setValue("Mật khẩu xác nhận không khớp");
            return false;
        }
        return true;
    }
}
