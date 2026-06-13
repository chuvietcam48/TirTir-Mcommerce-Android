package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

public class CreateOrderResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("orderId")
    private String orderId;

    public String getMessage() { return message; }
    public String getOrderId() { return orderId; }
}
