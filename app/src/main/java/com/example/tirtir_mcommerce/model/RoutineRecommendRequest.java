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
    }

    public RoutineRecommendRequest(String skinType, String skinTone, String undertone,
                                    List<String> concerns, String productId, String shadeName) {
        this.skinType = skinType;
        this.skinTone = skinTone;
        this.undertone = undertone;
        this.concerns = concerns;
        this.shadeMatchProduct = new ShadeProduct(productId, shadeName);
    }

    public String getSkinType() { return skinType; }
    public String getSkinTone() { return skinTone; }
    public String getUndertone() { return undertone; }
    public List<String> getConcerns() { return concerns; }
}
