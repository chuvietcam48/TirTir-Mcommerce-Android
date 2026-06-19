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
    
    @SerializedName("districtId")
    private String districtId;
    
    @SerializedName("wardCode")
    private String wardCode;

    public ShippingAddress() {}

    public ShippingAddress(String fullName, String phone, String address, String city, String districtId, String wardCode) {
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.districtId = districtId;
        this.wardCode = wardCode;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getDistrictId() { return districtId; }
    public void setDistrictId(String districtId) { this.districtId = districtId; }
    
    public String getWardCode() { return wardCode; }
    public void setWardCode(String wardCode) { this.wardCode = wardCode; }
}
