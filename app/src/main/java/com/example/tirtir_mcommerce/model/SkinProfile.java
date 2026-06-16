package com.example.tirtir_mcommerce.model;

import java.util.List;

/**
 * Wrapper object lưu toàn bộ kết quả phân tích da để offline storage.
 * Lưu vào SQLite bảng skin_profiles khi user chưa đăng nhập (guest mode).
 * Khi user đăng nhập → sync lên server.
 */
public class SkinProfile {
    private long id;              // SQLite auto-increment
    private String userId;        // null nếu guest
    private SkinAnalysisResult analysisResult;
    private List<ShadeMatchResult> shadeMatches;
    private List<RoutineStep> routineSteps;
    private long timestamp;
    private boolean synced;

    public SkinProfile(String userId,
                       SkinAnalysisResult analysisResult,
                       List<ShadeMatchResult> shadeMatches,
                       List<RoutineStep> routineSteps) {
        this.userId = userId;
        this.analysisResult = analysisResult;
        this.shadeMatches = shadeMatches;
        this.routineSteps = routineSteps;
        this.timestamp = System.currentTimeMillis();
        this.synced = false;
    }

    // ---- Getters ----

    public long getId() { return id; }
    public String getUserId() { return userId; }
    public SkinAnalysisResult getAnalysisResult() { return analysisResult; }
    public List<ShadeMatchResult> getShadeMatches() { return shadeMatches; }
    public List<RoutineStep> getRoutineSteps() { return routineSteps; }
    public long getTimestamp() { return timestamp; }
    public boolean isSynced() { return synced; }

    // ---- Setters ----

    public void setId(long id) { this.id = id; }
    public void setSynced(boolean synced) { this.synced = synced; }
}
