package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.AuthRepository;

/**
 * ViewModel cho màn hình Đăng nhập và Đăng ký.
 * Kế thừa AndroidViewModel để có thể truy cập Application context (cần cho SharedPrefs).
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;

    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<User> loginSuccess = new MutableLiveData<>();
    public final MutableLiveData<String> registerSuccess = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application.getApplicationContext());
    }

    public void login(String email, String password) {
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

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    private boolean validateLoginInput(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            errorMessage.setValue(getApplication().getString(R.string.error_email_empty));
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue(getApplication().getString(R.string.error_email_invalid));
            return false;
        }
        if (password == null || password.isEmpty()) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_empty));
            return false;
        }
        return true;
    }

    private boolean validateRegisterInput(String firstName, String lastName, String email, String password, String confirmPassword) {
        if (firstName == null || firstName.trim().isEmpty()) {
            errorMessage.setValue(getApplication().getString(R.string.error_name_empty));
            return false;
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            errorMessage.setValue(getApplication().getString(R.string.error_name_empty));
            return false;
        }
        if (email == null || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage.setValue(getApplication().getString(R.string.error_email_invalid));
            return false;
        }
        if (password == null || password.length() < 8) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_short));
            return false;
        }
        if (!password.equals(confirmPassword)) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_mismatch));
            return false;
        }
        return true;
    }
}
