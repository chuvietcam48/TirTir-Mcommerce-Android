package com.example.tirtir_mcommerce.network;

import android.net.Uri;

/**
 * Single source of truth for frontend network endpoints and media URL resolution.
 */
public final class ApiConfig {
    public static final String BASE_URL = "https://tirtir-project.onrender.com/";
    public static final String CHAT_URL = BASE_URL + "api/v1/chat";

    private static final String[] LOCAL_BACKEND_PREFIXES = {
            "http://localhost:5001/",
            "http://127.0.0.1:5001/",
            "http://10.0.2.2:5001/"
    };

    private ApiConfig() {}

    public static String resolveMediaUrl(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String trimmed = path.trim();
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
