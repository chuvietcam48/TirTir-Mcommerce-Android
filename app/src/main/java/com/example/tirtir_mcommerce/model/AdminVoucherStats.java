package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class AdminVoucherStats {
    @SerializedName("total")
    private int total;
    
    @SerializedName("active")
    private int active;
    
    @SerializedName("totalUsage")
    private int totalUsage;
    
    @SerializedName("totalDiscountValue")
    private double totalDiscountValue;
    
    @SerializedName("avgDiscountValue")
    private double avgDiscountValue;

    public int getTotal() { return total; }
    public int getActive() { return active; }
    public int getTotalUsage() { return totalUsage; }
    public double getTotalDiscountValue() { return totalDiscountValue; }
    public double getAvgDiscountValue() { return avgDiscountValue; }
}
