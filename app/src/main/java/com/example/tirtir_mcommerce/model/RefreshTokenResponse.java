package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class RefreshTokenResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName(value = "token", alternate = {"accessToken", "access_token"})
    private String token;

    @SerializedName("refreshToken")
    private String refreshToken;

    public boolean isSuccess() {
        return success;
    }

    public String getToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
