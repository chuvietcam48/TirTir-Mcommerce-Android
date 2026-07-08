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

    @SerializedName(value = "totalAmount", alternate = {"totalPrice"})
    private double totalPrice;

    @SerializedName("subtotal")
    private double subtotal;

    @SerializedName(value = "shippingFee", alternate = {"shippingCost"})
    private double shippingFee;

    @SerializedName("tax")
    private double tax;

    @SerializedName("discount")
    private double discount;

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

        @SerializedName("thumbnail")
        private String thumbnail;

        @SerializedName("subtitle")
        private String subtitle;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getShade() { return shade; }
        public void setShade(String shade) { this.shade = shade; }
        public String getThumbnail() { return thumbnail; }
        public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
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

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getShippingFee() { return shippingFee; }
    public void setShippingFee(double shippingFee) { this.shippingFee = shippingFee; }

    public double getTax() { return tax; }
    public void setTax(double tax) { this.tax = tax; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }

    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public String getInvoiceUrl() { return invoiceUrl; }
    public void setInvoiceUrl(String invoiceUrl) { this.invoiceUrl = invoiceUrl; }
}
