package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MarketingOverviewResponse {

    @SerializedName("insights")
    private Insights insights;

    @SerializedName("campaigns")
    private List<Campaign> campaigns;

    @SerializedName("activities")
    private List<MarketingActivity> activities;

    public Insights getInsights() { return insights; }
    public List<Campaign> getCampaigns() { return campaigns; }
    public List<MarketingActivity> getActivities() { return activities; }

    public static class Insights {
        @SerializedName("revenueRecovered")
        private double revenueRecovered;

        @SerializedName("atRiskUsers")
        private int atRiskUsers;

        @SerializedName("vouchersUsed")
        private int vouchersUsed;

        @SerializedName("conversionRate")
        private double conversionRate;

        public double getRevenueRecovered() { return revenueRecovered; }
        public int getAtRiskUsers() { return atRiskUsers; }
        public int getVouchersUsed() { return vouchersUsed; }
        public double getConversionRate() { return conversionRate; }
    }
}
