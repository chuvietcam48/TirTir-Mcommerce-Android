package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ChurnResponseItem implements Serializable {
    @SerializedName("user")
    private User user;

    @SerializedName("rfm")
    private RfmDetails rfm;

    @SerializedName("classification")
    private String classification;

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public RfmDetails getRfm() { return rfm; }
    public void setRfm(RfmDetails rfm) { this.rfm = rfm; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public static class RfmDetails implements Serializable {
        @SerializedName("recency")
        private Integer recency;

        @SerializedName("frequency")
        private Integer frequency;

        @SerializedName("monetary")
        private Double monetary;

        public Integer getRecency() { return recency; }
        public void setRecency(Integer recency) { this.recency = recency; }

        public Integer getFrequency() { return frequency; }
        public void setFrequency(Integer frequency) { this.frequency = frequency; }

        public Double getMonetary() { return monetary; }
        public void setMonetary(Double monetary) { this.monetary = monetary; }
    }
}
