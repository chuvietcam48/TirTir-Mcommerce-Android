package com.example.tirtir_mcommerce.model;

import java.util.List;

/**
 * Request body cho API POST /api/ai/recommend-routine
 * Gửi đầy đủ Skin Profile + sản phẩm shade match tốt nhất.
 */
public class RoutineRecommendRequest {
    private String skinType;
    private String skinTone;
    private String undertone;
    private List<String> concerns;
    private ShadeProduct shadeMatchProduct;

    public static class ShadeProduct {
        private String Product_ID;
        private String Shade_Name;

        public ShadeProduct(String productId, String shadeName) {
            this.Product_ID = productId;
            this.Shade_Name = shadeName;
        }

        public String getProduct_ID() { return Product_ID; }
        public void setProduct_ID(String product_ID) { Product_ID = product_ID; }
        public String getShade_Name() { return Shade_Name; }
        public void setShade_Name(String shade_Name) { Shade_Name = shade_Name; }
    }

    public RoutineRecommendRequest() {}

    public RoutineRecommendRequest(String skinType, String skinTone, String undertone,
                                    List<String> concerns, String productId, String shadeName) {
        this.skinType = skinType;
        this.skinTone = skinTone;
        this.undertone = undertone;
        this.concerns = concerns;
        if (productId != null) {
            this.shadeMatchProduct = new ShadeProduct(productId, shadeName);
        }
    }

    public String getSkinType() { return skinType; }
    public void setSkinType(String skinType) { this.skinType = skinType; }
    public String getSkinTone() { return skinTone; }
    public void setSkinTone(String skinTone) { this.skinTone = skinTone; }
    public String getUndertone() { return undertone; }
    public void setUndertone(String undertone) { this.undertone = undertone; }
    public List<String> getConcerns() { return concerns; }
    public void setConcerns(List<String> concerns) { this.concerns = concerns; }
    public ShadeProduct getShadeMatchProduct() { return shadeMatchProduct; }
    public void setShadeMatchProduct(ShadeProduct shadeMatchProduct) { this.shadeMatchProduct = shadeMatchProduct; }
}
