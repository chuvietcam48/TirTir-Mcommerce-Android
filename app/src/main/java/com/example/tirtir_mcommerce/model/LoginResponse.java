package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model hứng phản hồi khi đăng nhập thành công.
 * Backend trả về:
 * {
 *   "success": true,
 *   "token": "eyJhbGci...",
 *   "refreshToken": "...",
 *   "user": { "_id": ..., "name": ..., "email": ..., "role": ... }
 * }
 */
public class LoginResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName(value = "token", alternate = {"accessToken", "access_token"})
    private String token;

    @SerializedName("refreshToken")
    private String refreshToken;

    @SerializedName("user")
    private User user;

    // ===== Getters =====
    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getRefreshToken() { return refreshToken; }
    public User getUser() { return user; }
}
