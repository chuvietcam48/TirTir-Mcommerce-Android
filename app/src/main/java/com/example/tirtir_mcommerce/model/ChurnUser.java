package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class ChurnUser {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("avatar")
    private String avatar;

    @SerializedName("ltv")
    private double ltv;

    @SerializedName("status")
    private String status;

    @SerializedName("lastActiveStr")
    private String lastActiveStr;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public double getLtv() { return ltv; }
    public String getStatus() { return status; }
    public String getLastActiveStr() { return lastActiveStr; }
}
