package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * CreateOrderRequest — Body cho POST /api/v1/orders/create
 *
 * Backend validate:
 * - shippingAddress: required (fullName, phone, address, city)
 * - paymentMethod: enum [VNPAY, MOMO, CARD]
 *
 * Sprint 1.3 — Task C: Đặt hàng + PDF
 */
public class CreateOrderRequest {

    @SerializedName("shippingAddress")
    private ShippingAddress shippingAddress;

    @SerializedName("paymentMethod")
    private String paymentMethod; // VNPAY | MOMO | CARD

    public CreateOrderRequest() {}

    public CreateOrderRequest(ShippingAddress shippingAddress, String paymentMethod) {
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
    }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
