package com.example.tirtir_mcommerce.model;

public class ShippingOption {

    private String serviceCode;   // ma_dich_vu
    private String serviceName;   // ten_dich_vu
    private long price;           // gia_cuoc (VND)
    private String estimatedTime; // thoi_gian_du_kien
    private String description;   // mo_ta

    public ShippingOption() {}

    public ShippingOption(String serviceCode, String serviceName,
                          long price, String estimatedTime, String description) {
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.price = price;
        this.estimatedTime = estimatedTime;
        this.description = description;
    }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Format giá cước hiển thị: 30000 → "30.000 ₫"
    public String getFormattedPrice() {
        return String.format("%,d ₫", price).replace(',', '.');
    }
}
