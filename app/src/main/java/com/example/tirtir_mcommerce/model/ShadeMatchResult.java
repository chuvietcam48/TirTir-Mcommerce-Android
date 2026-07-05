package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

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
    
    @SerializedName(value="productName", alternate={"Product_Name"})
    private String productName;
    
    @SerializedName(value="imageUrl", alternate={"Image_URL"})
    private String imageUrl;
    
    @SerializedName(value="price", alternate={"Price"})
    private double price;
    
    @SerializedName(value="salePrice", alternate={"Sale_Price"})
    private double salePrice;
    
    @SerializedName(value="shadeHex", alternate={"Hex_Code"})
    private String shadeHex;

    /**
     * Chuyển đổi matchScore (Delta-E CIELAB) sang phần trăm độ phù hợp (0–100).
     * Delta-E < 2: Perfect match (95-100%)
     * Delta-E 2-5: Very good (85-94%)
     * Delta-E 5-10: Good (70-84%)
     * Delta-E 10-20: Acceptable (50-69%)
     * Delta-E > 20: Poor match (< 50%)
     */
    public int getMatchPercent() {
        if (matchScore <= 0) return 100;
        if (matchScore <= 2) return (int) Math.round(100 - matchScore * 2.5);
        if (matchScore <= 5) return (int) Math.round(95 - (matchScore - 2) * 3.33);
        if (matchScore <= 10) return (int) Math.round(85 - (matchScore - 5) * 3.0);
        if (matchScore <= 20) return (int) Math.round(70 - (matchScore - 10) * 2.0);
        if (matchScore <= 40) return (int) Math.round(50 - (matchScore - 20) * 1.25);
        return Math.max(5, (int) Math.round(25 - (matchScore - 40) * 0.5));
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
