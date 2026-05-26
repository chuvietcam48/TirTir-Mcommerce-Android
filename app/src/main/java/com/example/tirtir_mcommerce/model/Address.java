package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Model đại diện cho một địa chỉ giao hàng.
 * Implements Serializable để truyền giữa Activity/Fragment qua Bundle/Intent.
 */
public class Address implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("phone")
    private String phone;

    @SerializedName("street")
    private String street;

    @SerializedName("ward")
    private String ward;

    @SerializedName("district")
    private String district;

    @SerializedName("city")
    private String city;

    @SerializedName("isDefault")
    private boolean isDefault;

    // ===== Constructors =====
    public Address() {}

    public Address(String fullName, String phone, String street, String ward, String district, String city) {
        this.fullName = fullName;
        this.phone = phone;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.isDefault = false;
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getStreet() { return street; }
    public String getWard() { return ward; }
    public String getDistrict() { return district; }
    public String getCity() { return city; }
    public boolean isDefault() { return isDefault; }

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStreet(String street) { this.street = street; }
    public void setWard(String ward) { this.ward = ward; }
    public void setDistrict(String district) { this.district = district; }
    public void setCity(String city) { this.city = city; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    /**
     * Trả về địa chỉ đầy đủ đã format cho hiển thị UI.
     * Ví dụ: "12 Lý Tự Trọng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"
     */
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isEmpty()) sb.append(street);
        if (ward != null && !ward.isEmpty()) sb.append(", ").append(ward);
        if (district != null && !district.isEmpty()) sb.append(", ").append(district);
        if (city != null && !city.isEmpty()) sb.append(", ").append(city);
        return sb.toString();
    }
}
