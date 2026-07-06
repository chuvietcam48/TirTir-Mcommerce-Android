package com.example.tirtir_mcommerce.repository;

import android.content.Context;
import android.util.Log;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.ApiConfig;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatRepository {
    private static final String TAG = "ChatRepository";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    // ── Data classes ──────────────────────────────────────────────────────────

    public static class Suggestion {
        public final String productId;
        public final String name;
        public Suggestion(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }
    }

    public static class ChatAction {
        public final String type;  // choose_topic | contact_staff | call_hotline
        public final String label;
        public ChatAction(String type, String label) {
            this.type = type;
            this.label = label;
        }
    }

    public static class ChatResult {
        public final String message;
        public final List<Suggestion> suggestions;
        public final boolean isOutOfDataset;
        public final List<ChatAction> actions;

        ChatResult(String message, List<Suggestion> suggestions) {
            this.message = message;
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
            this.isOutOfDataset = false;
            this.actions = new ArrayList<>();
        }

        ChatResult(String message, List<Suggestion> suggestions, boolean isOutOfDataset, List<ChatAction> actions) {
            this.message = message;
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
            this.isOutOfDataset = isOutOfDataset;
            this.actions = actions != null ? actions : new ArrayList<>();
        }
    }

    public interface StreamListener {
        void onChunk(String text);
        void onDone(ChatResult result);
        void onError(String message);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context context;
    private final SharedPrefsManager prefs;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public ChatRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new SharedPrefsManager(context);
    }

    // ── Retrofit calls ────────────────────────────────────────────────────────

    public void loadHistory(retrofit2.Callback<ApiResponse<List<Map<String, Object>>>> callback) {
        RetrofitClient.getAuthClient(context).create(ApiService.class).getChatHistory().enqueue(callback);
    }

    public void loadConfig(retrofit2.Callback<ApiResponse<Map<String, Object>>> callback) {
        RetrofitClient.getAuthClient(context).create(ApiService.class).getChatConfig().enqueue(callback);
    }

    public void loadSuggestedQuestions(retrofit2.Callback<ApiResponse<List<Map<String, Object>>>> callback) {
        RetrofitClient.getAuthClient(context).create(ApiService.class).getChatSuggestedQuestions().enqueue(callback);
    }

    public void postHandoff(String reason, retrofit2.Callback<ApiResponse<Map<String, Object>>> callback) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("reason", reason != null ? reason : "");
        RetrofitClient.getAuthClient(context).create(ApiService.class).postChatHandoff(body).enqueue(callback);
    }

    // ── SSE streaming calls ───────────────────────────────────────────────────

    /** Send a free-text message. */
    public void sendMessage(String message, StreamListener listener) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        sendRequest(payload, listener);
    }

    /** Send a chip-tap: includes selectedQuestionId so the backend skips matching. */
    public void sendQuestion(String questionId, String questionText, StreamListener listener) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", questionText);
        if (questionId != null && !questionId.isEmpty()) {
            payload.addProperty("selectedQuestionId", questionId);
        }
        sendRequest(payload, listener);
    }

    // ── Internal OkHttp SSE request ───────────────────────────────────────────

    private void sendRequest(JsonObject payload, StreamListener listener) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(ApiConfig.CHAT_URL)
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(JSON_MEDIA, payload.toString()));

        String token = prefs.getToken();
        if (token != null && !token.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Chat request failed", e);
                listener.onError(e instanceof SocketTimeoutException
                        ? "The advisor is taking longer than expected. Please try again."
                        : "The advisor is temporarily unavailable. You can still browse products and routines.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    Log.e(TAG, "Chat API HTTP " + response.code() + ": " + errorBody);
                    listener.onError("Sorry, I could not generate a response right now.");
                    if (response.body() != null) response.close();
                    return;
                }

                try {
                    String rawBody = response.body().string();
                    String[] lines = rawBody.split("\n");

                    boolean handled = false;
                    StringBuilder accumulated = new StringBuilder();
                    String event = "";

                    for (String lineRaw : lines) {
                        String line = lineRaw.trim();
                        if (line.isEmpty()) continue;

                        // Plain JSON fallback (non-SSE response)
                        if (line.startsWith("{") && line.endsWith("}") && !handled) {
                            try {
                                JsonObject json = new JsonParser().parse(line).getAsJsonObject();
                                if (json.has("success") && !json.get("success").getAsBoolean()) {
                                    listener.onError(strOf(json, "message"));
                                    handled = true;
                                    break;
                                }
                                String reply = !json.has("reply") || json.get("reply").isJsonNull()
                                        ? strOf(json, "message") : strOf(json, "reply");
                                for (String word : reply.split(" ")) {
                                    listener.onChunk(word + " ");
                                    try { Thread.sleep(25); } catch (InterruptedException ignored) {}
                                }
                                listener.onDone(new ChatResult(reply, new ArrayList<>()));
                                handled = true;
                                break;
                            } catch (Exception ignored) {}
                        }

                        // SSE format
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            String dataStr = line.substring(5).trim();
                            if ("[DONE]".equals(dataStr)) {
                                listener.onDone(new ChatResult(accumulated.toString(), new ArrayList<>()));
                                handled = true;
                            } else if (dataStr.startsWith("{")) {
                                try {
                                    JsonObject chunk = new JsonParser().parse(dataStr).getAsJsonObject();
                                    if ("error".equals(event)) {
                                        listener.onError(strOf(chunk, "message"));
                                        handled = true;
                                    } else if ("chunk".equals(event)) {
                                        String text = strOf(chunk, "text");
                                        accumulated.append(text);
                                        listener.onChunk(text);
                                        handled = true;
                                    } else if ("done".equals(event)) {
                                        // Parse OOD flags and actions
                                        boolean ood = chunk.has("isOutOfDataset")
                                                && !chunk.get("isOutOfDataset").isJsonNull()
                                                && chunk.get("isOutOfDataset").getAsBoolean();

                                        List<ChatAction> actions = new ArrayList<>();
                                        if (chunk.has("actions") && chunk.get("actions").isJsonArray()) {
                                            for (JsonElement a : chunk.getAsJsonArray("actions")) {
                                                if (!a.isJsonObject()) continue;
                                                JsonObject ao = a.getAsJsonObject();
                                                actions.add(new ChatAction(
                                                        strOf(ao, "type"),
                                                        strOf(ao, "label")
                                                ));
                                            }
                                        }

                                        // Parse product suggestions
                                        List<Suggestion> sug = new ArrayList<>();
                                        if (chunk.has("suggestions") && chunk.get("suggestions").isJsonArray()) {
                                            for (JsonElement s : chunk.getAsJsonArray("suggestions")) {
                                                if (!s.isJsonObject()) continue;
                                                JsonObject so = s.getAsJsonObject();
                                                String id = strOf(so, "id");
                                                String name = strOf(so, "name");
                                                if (!id.isEmpty()) sug.add(new Suggestion(id, name));
                                            }
                                        }

                                        listener.onDone(new ChatResult(
                                                accumulated.toString(), sug, ood, actions));
                                        handled = true;
                                    }
                                } catch (Exception ignored) {}
                            } else {
                                accumulated.append(dataStr).append(" ");
                                listener.onChunk(dataStr + " ");
                                handled = true;
                            }
                        }
                    }

                    if (!handled) {
                        Log.e(TAG, "Unhandled chat response: " + rawBody);
                        listener.onError("Sorry, I could not generate a response right now.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Chat response parse error", e);
                    listener.onError("Sorry, I could not generate a response right now.");
                } finally {
                    response.close();
                }
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String strOf(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : "";
    }
}
