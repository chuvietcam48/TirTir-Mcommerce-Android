package com.example.tirtir_mcommerce.network;

import android.net.Uri;

import com.example.tirtir_mcommerce.BuildConfig;

/**
 * Single source of truth for frontend network endpoints and media URL resolution.
 */
public final class ApiConfig {
    public static final String BASE_URL = normalizeBaseUrl(BuildConfig.API_BASE_URL);
    public static final String CHAT_URL = BASE_URL + "api/v1/chat";

    private static final String[] LOCAL_BACKEND_PREFIXES = {
            "http://localhost:5001/",
            "http://127.0.0.1:5001/",
            "http://10.0.2.2:5001/"
    };

    private ApiConfig() {}

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "https://tirtir-project.onrender.com/";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    public static String resolveMediaUrl(String path) {
        String cleaned = cleanPath(path);
        if (cleaned.isEmpty()) return "";
        return buildAbsoluteUrl(cleaned);
    }

    /**
     * Returns a fallback URL when the primary URL may be missing a subfolder.
     *
     * Some products in the DB store paths like:
     *   assets/images/products/{id}/thumb.webp          ← missing subfolder → 404
     * while the files actually live at:
     *   assets/images/products/{id}/Main-Images/thumb.webp ← correct → 200
     *
     * This method returns the canonical subfolder variant so Glide can retry.
     * Returns "" when the path already contains a subfolder (no fix needed).
     */
    public static String resolveMediaFallbackUrl(String path) {
        String cleaned = cleanPath(path);
        if (cleaned.isEmpty()) return "";

        // Only fix relative product-image paths
        String productPrefix = "assets/images/products/";
        if (!cleaned.startsWith(productPrefix)) return "";

        String afterPrefix = cleaned.substring(productPrefix.length()); // e.g. "PRD-SK-MATCHA-02/thumb.webp"
        String[] segments = afterPrefix.split("/");
        // segments[0] = product-id, segments[1] = filename (no subfolder present)
        if (segments.length != 2) return ""; // already has subfolder or malformed

        // Insert the canonical subfolder used on the server
        String fixed = productPrefix + segments[0] + "/Main-Images/" + segments[1];
        return buildAbsoluteUrl(fixed);
    }

    // ---- private helpers ----

    private static String cleanPath(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String t = path.trim();
        // Unwrap stringified JSON array: '["url"]' → url
        if (t.startsWith("[\"") && t.endsWith("\"]")) {
            t = t.substring(2, t.length() - 2);
            int comma = t.indexOf("\",\"");
            if (comma < 0) comma = t.indexOf("\", \"");
            if (comma >= 0) t = t.substring(0, comma);
        } else if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String buildAbsoluteUrl(String cleaned) {
        for (String prefix : LOCAL_BACKEND_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                return BASE_URL + cleaned.substring(prefix.length());
            }
        }
        if (cleaned.startsWith("https://") || cleaned.startsWith("http://")) {
            return cleaned;
        }
        String clean = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        return Uri.parse(BASE_URL).buildUpon().appendEncodedPath(clean).build().toString();
    }
}
