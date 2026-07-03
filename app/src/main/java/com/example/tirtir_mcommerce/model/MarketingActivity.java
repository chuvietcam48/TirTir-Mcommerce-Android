package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class MarketingActivity {

    @SerializedName("_id")
    private String id;

    @SerializedName("type")
    private String type; // success, system, draft

    @SerializedName("title")
    private String title;

    @SerializedName("targetOrStatus")
    private String targetOrStatus;

    @SerializedName("createdAt")
    private String createdAt;

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getTargetOrStatus() { return targetOrStatus; }
    public String getCreatedAt() { return createdAt; }
}
