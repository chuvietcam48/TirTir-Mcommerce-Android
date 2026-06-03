package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * ShippingAddress — Địa chỉ giao hàng khi tạo đơn hàng.
 * Mapping với request body của POST /api/v1/orders/create
 */
public class ShippingAddress {

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("phone")
    private String phone;

    @SerializedName("address")
    private String address;

    @SerializedName("city")
    private String city;

    public ShippingAddress() {}

    public ShippingAddress(String fullName, String phone, String address, String city) {
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.city = city;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
