package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AdminVouchersResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private List<AdminVoucher> data;

    public boolean isSuccess() { return success; }
    public List<AdminVoucher> getData() { return data; }
}
