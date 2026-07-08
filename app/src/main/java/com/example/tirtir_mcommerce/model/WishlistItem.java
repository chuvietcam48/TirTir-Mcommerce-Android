package com.example.tirtir_mcommerce.model;

public class WishlistItem {
    private long id;
    private String productId;
    private String productName;
    private String thumbnail;
    private double price;
    private String subtitle; // e.g. "Long-lasting Glow" based on category or main concern
    private int synced;

    public WishlistItem() {}

    public WishlistItem(String productId, String productName, String thumbnail, double price, String subtitle) {
        this.productId = productId;
        this.productName = productName;
        this.thumbnail = thumbnail;
        this.price = price;
        this.subtitle = subtitle != null ? subtitle : "";
        this.synced = 0;
    }

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
    
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public int getSynced() { return synced; }
    public void setSynced(int synced) { this.synced = synced; }
}
