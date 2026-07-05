package com.example.tirtir_mcommerce.model;

import java.util.List;

/**
 * Model kết quả từ API POST /api/v1/ai/analyze-face
 * Backend AI (Python) chạy tiền xử lý CLAHE + L*a*b* color space classification.
 */
public class SkinAnalysisResult {
    private String skinTone;       // e.g. "Light", "Medium", "Dark"
    private String undertone;      // e.g. "Warm", "Cool", "Neutral"
    private String skinType;       // e.g. "Normal", "Dry", "Oily", "Combination"
    private List<String> concerns; // e.g. ["Acne/Blemishes", "Visible Pores"]
    private double confidence;     // 0–100
    private String skinHex;        // e.g. "#D8A087" — có thể null nếu backend chưa trả
    private String imagePath;      // Path to the captured face image

    // Debug values dùng để tính ITA angle
    private DebugValues debug_values;

    public static class DebugValues {
        private double L;
        private double a;
        private double b;

        public double getL() { return L; }
        public double getA() { return a; }
        public double getB() { return b; }
    }

    // ---- Computed helper ----

    /**
     * Tính góc ITA từ debug_values L và b.
     * ITA = atan((L - 50) / b) * 180 / PI
     * @return góc ITA tính bằng độ, hoặc Double.NaN nếu không có debug_values
     */
    public double computeItaAngle() {
        if (debug_values == null || debug_values.getB() == 0) return Double.NaN;
        return Math.toDegrees(Math.atan((debug_values.getL() - 50.0) / debug_values.getB()));
    }

    // ---- Getters ----

    public String getSkinTone() { return skinTone; }
    public String getUndertone() { return undertone; }
    public String getSkinType() { return skinType; }
    public List<String> getConcerns() { return concerns; }
    public double getConfidence() { return confidence; }
    public String getSkinHex() { return skinHex; }
    public String getImagePath() { return imagePath; }
    public DebugValues getDebugValues() { return debug_values; }

    // ---- Setters (dùng khi parse thủ công từ Map<String, Object>) ----

    public void setSkinTone(String skinTone) { this.skinTone = skinTone; }
    public void setUndertone(String undertone) { this.undertone = undertone; }
    public void setSkinType(String skinType) { this.skinType = skinType; }
    public void setConcerns(List<String> concerns) { this.concerns = concerns; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public void setSkinHex(String skinHex) { this.skinHex = skinHex; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
