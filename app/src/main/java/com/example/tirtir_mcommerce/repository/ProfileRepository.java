package com.example.tirtir_mcommerce.repository;

import android.content.Context;

import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository xử lý toàn bộ logic liên quan đến Profile và Địa chỉ người dùng.
 * Dùng authenticated Retrofit client (kèm JWT token trong header).
 */
public class ProfileRepository {

    private final ApiService authApiService;
    private final SharedPrefsManager prefsManager;

    public ProfileRepository(Context context) {
        this.authApiService = RetrofitClient.getAuthClient(context).create(ApiService.class);
        this.prefsManager = new SharedPrefsManager(context);
    }

    // ===========================
    // PROFILE
    // ===========================

    public void getProfile(OnSuccessListener<User> onSuccess, OnErrorListener onError) {
        authApiService.getProfile().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    User user = response.body().getData();
                    // Cập nhật cache
                    if (user != null) prefsManager.saveUser(user);
                    onSuccess.onSuccess(user);
                } else {
                    onError.onError("Unable to load your account. HTTP: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                // Trả về cache nếu không có mạng
                User cachedUser = prefsManager.getCachedUser();
                if (cachedUser != null) {
                    onSuccess.onSuccess(cachedUser);
                } else {
                    onError.onError("Connection error. Please try again.");
                }
            }
        });
    }

    public void updateProfile(Map<String, String> body, OnSuccessListener<User> onSuccess, OnErrorListener onError) {
        authApiService.updateProfile(body).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    User updatedUser = response.body().getData();
                    if (updatedUser != null) prefsManager.saveUser(updatedUser);
                    onSuccess.onSuccess(updatedUser);
                } else {
                    onError.onError("Profile update failed. HTTP: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
    }

    // ===========================
    // ADDRESS
    // ===========================

    public void getAddresses(OnSuccessListener<List<Address>> onSuccess, OnErrorListener onError) {
        authApiService.getAddresses().enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call, Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    onSuccess.onSuccess(response.body().getData());
                } else {
                    onError.onError("Unable to load saved addresses.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
    }

    public void addAddress(Address address, OnSuccessListener<List<Address>> onSuccess, OnErrorListener onError) {
        authApiService.addAddress(address).enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call, Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    onSuccess.onSuccess(response.body().getData());
                } else {
                    onError.onError("Unable to add this address.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
    }

    public void updateAddress(String addressId, Address address,
                              OnSuccessListener<List<Address>> onSuccess,
                              OnErrorListener onError) {
        authApiService.updateAddress(addressId, address).enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    onSuccess.onSuccess(response.body().getData());
                } else {
                    onError.onError("Unable to update this address.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
    }

    public void deleteAddress(String addressId, OnSuccessListener<List<Address>> onSuccess, OnErrorListener onError) {
        authApiService.deleteAddress(addressId).enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    onSuccess.onSuccess(response.body().getData());
                } else {
                    onError.onError("Unable to delete this address.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
    }

    public void setDefaultAddress(String addressId, OnSuccessListener<List<Address>> onSuccess, OnErrorListener onError) {
        authApiService.setDefaultAddress(addressId).enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    onSuccess.onSuccess(response.body().getData());
                } else {
                    onError.onError("Unable to set the default address.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                onError.onError("Connection error. Please try again.");
            }
        });
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
