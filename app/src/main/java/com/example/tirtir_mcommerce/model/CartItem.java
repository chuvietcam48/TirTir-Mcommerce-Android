package com.example.tirtir_mcommerce.model;

/**
 * CartItem — Model cho giỏ hàng offline (SQLite).
 *
 * Mapping với bảng cart_items trong DatabaseHelper.
 * Field "synced": 0 = chưa sync, 1 = đã sync với server.
 *
 * Sprint 1.2 — Task B: SQLite Logic / Offline Cart
 */
public class CartItem {

    private long id;          // SQLite row ID
    private String productId; // _id từ MongoDB (dùng để gọi API)
    private String productName;
    private String thumbnail;
    private double price;
    private int quantity;
    private String shade;     // Màu sắc (nếu có), mặc định ""
    private int synced;       // 0 = pending, 1 = synced với server

    // ===========================
    // CONSTRUCTORS
    // ===========================

    public CartItem() {}

    public CartItem(String productId, String productName, String thumbnail,
                    double price, int quantity, String shade) {
        this.productId = productId;
        this.productName = productName;
        this.thumbnail = thumbnail;
        this.price = price;
        this.quantity = quantity;
        this.shade = shade != null ? shade : "";
        this.synced = 0; // Mặc định chưa sync
    }

    // ===========================
    // GETTERS & SETTERS
    // ===========================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getShade() { return shade; }
    public void setShade(String shade) { this.shade = shade != null ? shade : ""; }

    public int getSynced() { return synced; }
    public void setSynced(int synced) { this.synced = synced; }

    public boolean isSynced() { return synced == 1; }

    public double getTotalPrice() { return price * quantity; }
}
