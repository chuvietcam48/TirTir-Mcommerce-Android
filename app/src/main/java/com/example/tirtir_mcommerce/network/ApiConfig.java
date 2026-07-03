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
        if (path == null || path.trim().isEmpty()) return "";
        String trimmed = path.trim();
        
        // Clean up stringified JSON arrays/strings (e.g., '["url"]' or '"url"')
        if (trimmed.length() >= 4 && trimmed.startsWith("[\"") && trimmed.endsWith("\"]")) {
            trimmed = trimmed.substring(2, trimmed.length() - 2);
            // If it contains multiple URLs, take the first one
            if (trimmed.contains("\",\"")) {
                trimmed = trimmed.split("\",\"")[0];
            } else if (trimmed.contains("\", \"")) {
                trimmed = trimmed.split("\", \"")[0];
            }
        } else if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        
        for (String prefix : LOCAL_BACKEND_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return BASE_URL + trimmed.substring(prefix.length());
            }
        }
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        String cleanPath = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return Uri.parse(BASE_URL).buildUpon().appendEncodedPath(cleanPath).build().toString();
    }
}
