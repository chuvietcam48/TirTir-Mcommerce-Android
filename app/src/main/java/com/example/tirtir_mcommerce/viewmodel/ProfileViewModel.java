package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.ProfileRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel cho màn hình Profile và quản lý địa chỉ.
 */
public class ProfileViewModel extends AndroidViewModel {

    private final ProfileRepository profileRepository;
    private final SharedPrefsManager prefsManager;

    // LiveData cho UI observe
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<String> successMessage = new MutableLiveData<>();
    public final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    public final MutableLiveData<List<Address>> addressesLiveData = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        profileRepository = new ProfileRepository(application.getApplicationContext());
        prefsManager = new SharedPrefsManager(application.getApplicationContext());
    }

    // ===========================
    // PROFILE
    // ===========================

    public void loadProfile() {
        // Hiển thị cache ngay lập tức để tránh màn hình trắng
        User cachedUser = prefsManager.getCachedUser();
        if (cachedUser != null) {
            userLiveData.setValue(cachedUser);
        }

        isLoading.setValue(true);
        profileRepository.getProfile(
                user -> {
                    isLoading.postValue(false);
                    userLiveData.postValue(user);
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    public void updateProfile(String name, String phone, String gender) {
        Map<String, String> body = new HashMap<>();
        if (name != null && !name.isEmpty()) body.put("name", name);
        if (phone != null && !phone.isEmpty()) body.put("phone", phone);
        if (gender != null && !gender.isEmpty()) body.put("gender", gender);

        isLoading.setValue(true);
        profileRepository.updateProfile(body,
                user -> {
                    isLoading.postValue(false);
                    userLiveData.postValue(user);
                    successMessage.postValue("Cập nhật thành công!");
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    // ===========================
    // ADDRESS
    // ===========================

    public void loadAddresses() {
        profileRepository.getAddresses(
                addresses -> addressesLiveData.postValue(addresses),
                message -> errorMessage.postValue(message)
        );
    }

    public void addAddress(Address address) {
        isLoading.setValue(true);
        profileRepository.addAddress(address,
                user -> {
                    isLoading.postValue(false);
                    if (user != null && user.getAddresses() != null) {
                        addressesLiveData.postValue(user.getAddresses());
                    }
                    successMessage.postValue("Đã thêm địa chỉ mới");
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    public void deleteAddress(String addressId) {
        isLoading.setValue(true);
        profileRepository.deleteAddress(addressId,
                result -> {
                    isLoading.postValue(false);
                    successMessage.postValue("Đã xóa địa chỉ");
                    loadAddresses(); // Reload danh sách
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    public void setDefaultAddress(String addressId) {
        profileRepository.setDefaultAddress(addressId,
                user -> {
                    if (user != null && user.getAddresses() != null) {
                        addressesLiveData.postValue(user.getAddresses());
                    }
                    successMessage.postValue("Đã đặt địa chỉ mặc định");
                },
                message -> errorMessage.postValue(message)
        );
    }
}
