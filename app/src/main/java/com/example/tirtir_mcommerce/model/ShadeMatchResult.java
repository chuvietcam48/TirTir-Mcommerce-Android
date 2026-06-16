package com.example.tirtir_mcommerce.model;

/**
 * Một kết quả từ API POST /api/v1/shades/match
 * matchScore là khoảng cách màu (Delta-E) — càng nhỏ càng phù hợp.
 *
 * Công thức quy đổi sang phần trăm: matchPercent = 100 * exp(-matchScore / 7)
 */
public class ShadeMatchResult {
    // Trường từ API response
    private String Product_ID;
    private String Shade_Name;
    private double matchScore;
    private String productName;
    private String imageUrl;
    private double price;
    private double salePrice;
    private String shadeHex;

    // ---- Computed helper ----

    /**
     * Chuyển đổi matchScore sang phần trăm độ phù hợp (0–100).
     * Công thức: 100 * exp(-matchScore / 7)
     */
    public int getMatchPercent() {
        return (int) Math.round(100.0 * Math.exp(-matchScore / 7.0));
    }

    /**
     * Nhãn chất lượng match dựa trên matchPercent.
     * ≥ 85% → "Perfect", ≥ 65% → "Good", else → "Acceptable"
     */
    public String getQualityLabel() {
        int pct = getMatchPercent();
        if (pct >= 85) return "Perfect";
        if (pct >= 65) return "Good";
        return "Acceptable";
    }

    /**
     * Giá hiển thị: dùng salePrice nếu > 0, ngược lại dùng price.
     */
    public double getDisplayPrice() {
        return salePrice > 0 ? salePrice : price;
    }

    // ---- Getters ----

    public String getProductId() { return Product_ID; }
    public String getShadeName() { return Shade_Name; }
    public double getMatchScore() { return matchScore; }
    public String getProductName() { return productName; }
    public String getImageUrl() { return imageUrl; }
    public double getPrice() { return price; }
    public double getSalePrice() { return salePrice; }
    public String getShadeHex() { return shadeHex; }

    // ---- Setters (dùng khi build client-side fallback) ----

    public void setProductId(String id) { this.Product_ID = id; }
    public void setShadeName(String name) { this.Shade_Name = name; }
    public void setMatchScore(double score) { this.matchScore = score; }
    public void setProductName(String name) { this.productName = name; }
    public void setImageUrl(String url) { this.imageUrl = url; }
    public void setPrice(double price) { this.price = price; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }
    public void setShadeHex(String hex) { this.shadeHex = hex; }
}
