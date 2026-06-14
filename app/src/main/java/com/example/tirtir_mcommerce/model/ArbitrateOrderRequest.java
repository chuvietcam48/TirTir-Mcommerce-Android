package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for POST /api/v1/payments/arbitrate
 *
 * The backend will:
 *  1. Read the cart from server-side session (JWT)
 *  2. Call Viettel Post SOAP for real shipping fee
 *  3. Compute authoritative totals (Subtotal + Tax + Shipping − Voucher)
 *  4. Create a pending_payment Order
 *  5. Return a signed VNPAY payment URL
 */
public class ArbitrateOrderRequest {

    @SerializedName("shippingAddress")
    private ShippingAddress shippingAddress;

    /** VNPAY | MOMO | CARD | COD */
    @SerializedName("paymentMethod")
    private String paymentMethod;

    /** Province/city code sent to Viettel for shipping estimate. E.g. "HCM", "HAN" */
    @SerializedName("toProvince")
    private String toProvince;

    /** Optional voucher code, e.g. "TIRTIR_ROUTINE_5" */
    @SerializedName("voucherCode")
    private String voucherCode;

    public ArbitrateOrderRequest() {}

    public ArbitrateOrderRequest(ShippingAddress shippingAddress, String paymentMethod,
                                  String toProvince, String voucherCode) {
        this.shippingAddress = shippingAddress;
        this.paymentMethod   = paymentMethod;
        this.toProvince      = toProvince;
        this.voucherCode     = voucherCode;
    }

    // Getters / Setters
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress v) { this.shippingAddress = v; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String v) { this.paymentMethod = v; }
    public String getToProvince() { return toProvince; }
    public void setToProvince(String v) { this.toProvince = v; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String v) { this.voucherCode = v; }
}
