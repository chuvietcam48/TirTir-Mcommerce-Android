package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response from POST /api/v1/payments/arbitrate
 *
 * {
 *   "success": true,
 *   "data": {
 *     "orderId": "...",
 *     "paymentUrl": "https://sandbox.vnpayment.vn/...",
 *     "invoiceUrl": "https://api.../invoice",
 *     "isEstimatedShipping": false,
 *     "voucherMessage": "...",
 *     "totals": {
 *       "subtotal": 200000,
 *       "shippingFee": 35000,
 *       "tax": 20000,
 *       "discount": 10000,
 *       "finalTotal": 245000
 *     }
 *   }
 * }
 */
public class ArbitrateOrderResponse {

    @SerializedName("orderId")
    private String orderId;

    /** Signed VNPAY payment URL — open in Chrome Custom Tab or external browser */
    @SerializedName("paymentUrl")
    private String paymentUrl;

    @SerializedName("invoiceUrl")
    private String invoiceUrl;

    /** true → shipping fee is a fallback estimate (show orange warning in UI) */
    @SerializedName("isEstimatedShipping")
    private boolean isEstimatedShipping;

    @SerializedName("voucherMessage")
    private String voucherMessage;

    @SerializedName("totals")
    private Totals totals;

    // ─── Inner class ──────────────────────────────────────────────────────────
    public static class Totals {
        @SerializedName("subtotal")
        private long subtotal;
        @SerializedName("shippingFee")
        private long shippingFee;
        @SerializedName("tax")
        private long tax;
        @SerializedName("discount")
        private long discount;
        @SerializedName("finalTotal")
        private long finalTotal;

        public long getSubtotal()    { return subtotal; }
        public long getShippingFee() { return shippingFee; }
        public long getTax()         { return tax; }
        public long getDiscount()    { return discount; }
        public long getFinalTotal()  { return finalTotal; }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public String getOrderId()           { return orderId; }
    public String getPaymentUrl()        { return paymentUrl; }
    public String getInvoiceUrl()        { return invoiceUrl; }
    public boolean isEstimatedShipping() { return isEstimatedShipping; }
    public String getVoucherMessage()    { return voucherMessage; }
    public Totals getTotals()            { return totals; }
}
