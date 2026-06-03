package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OrderResponse — Response từ POST /api/v1/orders/create và GET /api/v1/orders/{id}
 *
 * Sprint 1.3 — Task C: Đặt hàng + PDF
 */
public class OrderResponse {

    @SerializedName("_id")
    private String id;

    @SerializedName("status")
    private String status; // Pending, Processing, Shipped, Delivered, Cancelled

    @SerializedName("totalPrice")
    private double totalPrice;

    @SerializedName("paymentMethod")
    private String paymentMethod;

    @SerializedName("isPaid")
    private boolean isPaid;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("shippingAddress")
    private ShippingAddress shippingAddress;

    @SerializedName("items")
    private List<OrderItemResponse> items;

    // Một số backend trả về PDF URL trong response
    // Nếu backend không hỗ trợ, sẽ build URL thủ công từ orderId
    @SerializedName("invoiceUrl")
    private String invoiceUrl;

    // ===========================
    // INNER CLASS: Order Item
    // ===========================

    public static class OrderItemResponse {

        @SerializedName("product")
        private String productId;

        @SerializedName("name")
        private String name;

        @SerializedName("quantity")
        private int quantity;

        @SerializedName("price")
        private double price;

        @SerializedName("shade")
        private String shade;

        public String getProductId() { return productId; }
        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
        public String getShade() { return shade; }
    }

    // ===========================
    // GETTERS & SETTERS
    // ===========================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }

    public List<OrderItemResponse> getItems() { return items; }

    public String getInvoiceUrl() { return invoiceUrl; }
    public void setInvoiceUrl(String invoiceUrl) { this.invoiceUrl = invoiceUrl; }
}
