package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class RetentionStatsResponse {
    @SerializedName("active")
    private int active;

    @SerializedName("inactive")
    private int inactive;

    @SerializedName("rate")
    private String rate;

    public int getActive() { return active; }
    public int getInactive() { return inactive; }
    public String getRate() { return rate; }
}
