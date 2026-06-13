package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.ProfileRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

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
    public final MutableLiveData<Boolean> avatarUploadLoading = new MutableLiveData<>(false);

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
                    successMessage.postValue("Profile updated.");
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
                addresses -> {
                    isLoading.postValue(false);
                    addressesLiveData.postValue(addresses);
                    successMessage.postValue("Address added.");
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    public void updateAddress(String addressId, Address address) {
        isLoading.setValue(true);
        profileRepository.updateAddress(addressId, address,
                addresses -> {
                    isLoading.postValue(false);
                    addressesLiveData.postValue(addresses);
                    successMessage.postValue("Address updated.");
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
                addresses -> {
                    isLoading.postValue(false);
                    addressesLiveData.postValue(addresses);
                    successMessage.postValue("Address deleted.");
                },
                message -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(message);
                }
        );
    }

    public void setDefaultAddress(String addressId) {
        profileRepository.setDefaultAddress(addressId,
                addresses -> {
                    addressesLiveData.postValue(addresses);
                    successMessage.postValue("Default address updated.");
                },
                message -> errorMessage.postValue(message)
        );
    }

    // ===========================
    // AVATAR UPLOAD (Firebase Storage)
    // ===========================

    /**
     * Upload ảnh avatar lên Firebase Storage.
     * Path: avatars/{userId}.jpg
     * Sau khi upload xong: lấy download URL và gọi API cập nhật profile.
     *
     * @param imageUri URI của ảnh được chọn từ Camera hoặc Gallery
     */
    public void uploadAvatar(Uri imageUri) {
        User cachedUser = prefsManager.getCachedUser();
        if (cachedUser == null) {
            errorMessage.postValue("Please sign in again to update your photo.");
            return;
        }

        String userId = cachedUser.getId();
        avatarUploadLoading.setValue(true);

        // Upload lên Firebase Storage: avatars/{userId}.jpg
        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference("avatars/" + userId + ".jpg");

        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Lấy URL tải xuống
                    storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        String avatarUrl = downloadUri.toString();
                        // Cập nhật avatar URL lên server
                        Map<String, String> body = new HashMap<>();
                        body.put("avatar", avatarUrl);
                        profileRepository.updateProfile(body,
                                user -> {
                                    avatarUploadLoading.postValue(false);
                                    userLiveData.postValue(user);
                                    successMessage.postValue("Profile photo updated.");
                                },
                                message -> {
                                    avatarUploadLoading.postValue(false);
                                    errorMessage.postValue(message);
                                }
                        );
                    });
                })
                .addOnFailureListener(e -> {
                    avatarUploadLoading.postValue(false);
                    errorMessage.postValue("Photo upload failed. Please try again.");
                });
    }
}
