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
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static class Suggestion {
        public final String productId;
        public final String name;

        public Suggestion(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }
    }

    public static class ChatResult {
        public final String message;
        public final List<Suggestion> suggestions;

        ChatResult(String message, List<Suggestion> suggestions) {
            this.message = message;
            this.suggestions = suggestions;
        }
    }

    public interface StreamListener {
        void onChunk(String text);
        void onDone(ChatResult result);
        void onError(String message);
    }

    private final Context context;
    private final SharedPrefsManager prefs;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(55, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public ChatRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new SharedPrefsManager(context);
    }

    public void loadHistory(retrofit2.Callback<ApiResponse<List<Map<String, Object>>>> callback) {
        RetrofitClient.getAuthClient(context).create(ApiService.class).getChatHistory().enqueue(callback);
    }

    public void sendMessage(String message, StreamListener listener) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        Request.Builder request = new Request.Builder()
                .url(ApiConfig.CHAT_URL)
                .header("Accept", "application/json")
                .post(RequestBody.create(JSON, payload.toString()));
        String token = prefs.getToken();
        if (token != null && !token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }

        client.newCall(request.build()).enqueue(new Callback() {
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
                    Log.e(TAG, "Chat API failed with HTTP " + response.code() + " " + errorBody);
                    listener.onError("Debug Error: HTTP " + response.code() + " " + errorBody);
                    if (response.body() != null) response.close();
                    return;
                }

                try {
                    String rawBody = response.body().string();
                    String[] lines = rawBody.split("\n");
                    
                    boolean isJsonHandled = false;
                    StringBuilder legacyTextAccumulator = new StringBuilder();
                    String event = "";

                    for (String lineRaw : lines) {
                        String line = lineRaw.trim();
                        if (line.isEmpty()) continue;

                        // Case 1: Standard JSON Object
                        if (line.startsWith("{") && line.endsWith("}")) {
                            try {
                                JsonObject json = new JsonParser().parse(line).getAsJsonObject();
                                if (json.has("success") && !json.get("success").getAsBoolean() && json.has("message")) {
                                    listener.onError(json.get("message").getAsString());
                                    isJsonHandled = true;
                                    break;
                                }
                                
                                String reply = "";
                                if (json.has("reply") && !json.get("reply").isJsonNull()) {
                                    reply = json.get("reply").getAsString();
                                } else if (json.has("message") && !json.get("message").isJsonNull()) {
                                    reply = json.get("message").getAsString();
                                }

                                String[] words = reply.split(" ");
                                for (String word : words) {
                                    listener.onChunk(word + " ");
                                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                                }

                                List<Suggestion> suggestions = new ArrayList<>();
                                if (json.has("recommendedProductIds") && json.get("recommendedProductIds").isJsonArray()) {
                                    for (JsonElement idElem : json.getAsJsonArray("recommendedProductIds")) {
                                        suggestions.add(new Suggestion(idElem.getAsString(), "Suggested Product"));
                                    }
                                }
                                listener.onDone(new ChatResult(reply, suggestions));
                                isJsonHandled = true;
                                break;
                            } catch (Exception ignored) {}
                        }

                        // Case 2: SSE Stream Format
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            String dataStr = line.substring(5).trim();
                            if ("[DONE]".equals(dataStr)) {
                                listener.onDone(new ChatResult(legacyTextAccumulator.toString(), new ArrayList<>()));
                            } else if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                                try {
                                    JsonObject chunkJson = new JsonParser().parse(dataStr).getAsJsonObject();
                                    if ("error".equals(event)) {
                                        String errMsg = chunkJson.has("message") ? chunkJson.get("message").getAsString() : "Lỗi hệ thống";
                                        listener.onError(errMsg);
                                        isJsonHandled = true;
                                    } else if ("chunk".equals(event)) {
                                        String text = chunkJson.has("text") ? chunkJson.get("text").getAsString() : "";
                                        legacyTextAccumulator.append(text);
                                        listener.onChunk(text);
                                        isJsonHandled = true;
                                    } else if ("done".equals(event)) {
                                        listener.onDone(new ChatResult(legacyTextAccumulator.toString(), new ArrayList<>()));
                                        isJsonHandled = true;
                                    }
                                } catch (Exception ignored) {}
                            } else {
                                String chunkText = dataStr + " ";
                                legacyTextAccumulator.append(chunkText);
                                listener.onChunk(chunkText);
                                isJsonHandled = true;
                            }
                        }
                    }

                    if (!isJsonHandled && legacyTextAccumulator.length() == 0) {
                        listener.onError("Debug Raw Response: [" + rawBody + "]");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unable to parse chat response", e);
                    listener.onError("The advisor sent an unreadable response.");
                } finally {
                    response.close();
                }
            }
        });
    }



    private List<Suggestion> extractSuggestions(JsonElement productData) {
        List<Suggestion> result = new ArrayList<>();
        if (productData == null || productData.isJsonNull()) return result;
        if (productData.isJsonArray()) {
            addSuggestions(productData.getAsJsonArray(), result);
        } else if (productData.isJsonObject()) {
            JsonObject object = productData.getAsJsonObject();
            if (object.has("recommendations") && object.get("recommendations").isJsonArray()) {
                addSuggestions(object.getAsJsonArray("recommendations"), result);
            } else if (object.has("products") && object.get("products").isJsonArray()) {
                addSuggestions(object.getAsJsonArray("products"), result);
            } else if (object.has("id") || object.has("_id")) {
                addSuggestion(object, result);
            }
        }
        return result;
    }

    private void addSuggestions(JsonArray array, List<Suggestion> result) {
        for (JsonElement item : array) {
            if (item.isJsonObject()) addSuggestion(item.getAsJsonObject(), result);
        }
    }

    private void addSuggestion(JsonObject object, List<Suggestion> result) {
        String id = stringValue(object, "id");
        if (id.isEmpty()) id = stringValue(object, "_id");
        if (id.isEmpty()) id = stringValue(object, "productId");
        String name = stringValue(object, "name");
        if (!id.isEmpty() && !name.isEmpty()) result.add(new Suggestion(id, name));
    }

    private String stringValue(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private String safeUserMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "The advisor is temporarily unavailable. Please try again shortly.";
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("10.0.2.2")
                || normalized.contains(":8002")
                || normalized.contains("service chưa")
                || normalized.contains("exception")
                || normalized.contains("stacktrace")) {
            return "The advisor is temporarily unavailable. You can still browse products and routines.";
        }
        return message.trim();
    }
}
