package com.example.tirtir_mcommerce.model;

/**
 * Một bước trong chu trình dưỡng da AI đề xuất.
 * Trả về từ API POST /api/ai/recommend-routine trong mảng "routine".
 *
 * Tính năng Skin Evolution:
 * - Mỗi bước bị skip → Hydration giảm 3%, Texture giảm 2%
 * - Tính toán ở AiRoutineFragment
 */
public class RoutineStep {
    // Trường từ API response
    private int step;
    private String stepName;     // e.g. "Cleanser", "Moisturizer"
    private String productId;
    private String productName;
    private String imageUrl;
    private double price;
    private double salePrice;
    private String description;
    private int hydrationBoost;  // Điểm cải thiện hydration dự kiến (%)
    private int textureBoost;    // Điểm cải thiện texture dự kiến (%)

    // Trạng thái local — không từ API
    private transient boolean skipped = false;

    // ---- Getters ----

    public int getStep() { return step; }
    public String getStepName() { return stepName; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getImageUrl() { return imageUrl; }
    public double getPrice() { return price; }
    public double getSalePrice() { return salePrice; }
    public String getDescription() { return description; }
    public int getHydrationBoost() { return hydrationBoost > 0 ? hydrationBoost : 3; }
    public int getTextureBoost() { return textureBoost > 0 ? textureBoost : 2; }
    public boolean isSkipped() { return skipped; }

    /**
     * Giá hiển thị: dùng salePrice nếu > 0
     */
    public double getDisplayPrice() {
        return salePrice > 0 ? salePrice : price;
    }

    // ---- Setters ----

    public void setStep(int step) { this.step = step; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setPrice(double price) { this.price = price; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }
    public void setDescription(String description) { this.description = description; }
    public void setHydrationBoost(int hydrationBoost) { this.hydrationBoost = hydrationBoost; }
    public void setTextureBoost(int textureBoost) { this.textureBoost = textureBoost; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }
}
