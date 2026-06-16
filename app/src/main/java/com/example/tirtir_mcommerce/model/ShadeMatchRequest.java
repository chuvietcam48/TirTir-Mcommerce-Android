package com.example.tirtir_mcommerce.model;

/**
 * Request body cho API POST /api/v1/shades/match
 * Gửi màu RGB trung bình từ 5 điểm landmark + skin type user.
 */
public class ShadeMatchRequest {
    private int r;
    private int g;
    private int b;
    private String skinType; // "Normal", "Dry", "Oily", "Combination"

    public ShadeMatchRequest(int r, int g, int b, String skinType) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.skinType = skinType != null ? skinType : "Normal";
    }

    public int getR() { return r; }
    public int getG() { return g; }
    public int getB() { return b; }
    public String getSkinType() { return skinType; }
}
