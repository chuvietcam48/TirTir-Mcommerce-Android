package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model đại diện cho request đăng ký FCM token lên backend.
 */
public class FcmTokenRequest {

    @SerializedName("token")
    private String token;

    @SerializedName("platform")
    private String platform;

    @SerializedName("firebaseUid")
    private String firebaseUid;

    @SerializedName("deviceModel")
    private String deviceModel;

    @SerializedName("appVersion")
    private String appVersion;

    public FcmTokenRequest() {
    }

    public FcmTokenRequest(String token, String platform, String firebaseUid, String deviceModel, String appVersion) {
        this.token = token;
        this.platform = platform;
        this.firebaseUid = firebaseUid;
        this.deviceModel = deviceModel;
        this.appVersion = appVersion;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }
}
