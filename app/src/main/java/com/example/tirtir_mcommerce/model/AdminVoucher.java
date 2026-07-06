package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class AdminVoucher {
    @SerializedName("_id")
    private String id;
    
    @SerializedName("code")
    private String code;
    
    @SerializedName("discountType")
    private String discountType; // "percentage", "fixed", "free_ship"
    
    @SerializedName("discountValue")
    private double discountValue;
    
    @SerializedName("validTo")
    private String validTo;
    
    @SerializedName("usageLimit")
    private int usageLimit;
    
    @SerializedName("usedCount")
    private int usedCount;
    
    @SerializedName("description")
    private String description;
    
    @SerializedName("limitPerUser")
    private int limitPerUser;
    
    @SerializedName("minOrderValue")
    private double minOrderValue;
    
    @SerializedName("active")
    private boolean active;

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getDiscountType() { return discountType; }
    public double getDiscountValue() { return discountValue; }
    public String getValidTo() { return validTo; }
    public int getUsageLimit() { return usageLimit; }
    public int getUsedCount() { return usedCount; }
    public String getDescription() { return description; }
    public int getLimitPerUser() { return limitPerUser; }
    public double getMinOrderValue() { return minOrderValue; }
    public boolean isActive() { return active; }
}
