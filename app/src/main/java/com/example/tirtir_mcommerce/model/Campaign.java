package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class Campaign {

    @SerializedName("_id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("status")
    private String status;

    @SerializedName("targetRevenue")
    private double targetRevenue;

    @SerializedName("currentRevenue")
    private double currentRevenue;

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public double getTargetRevenue() { return targetRevenue; }
    public double getCurrentRevenue() { return currentRevenue; }
}
