package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.repository.ChatRepository;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.adapters.ChatMessageAdapter;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {

    private RecyclerView rvChatMessages;
    private LinearLayout layoutChatEmpty; // kept as nullable – removed from layout but referenced safely
    private View layoutTyping;
    private EditText etChatInput;
    private ImageButton btnSendMessage;
    private ChatMessageAdapter adapter;
    private ChatRepository chatRepository;
    private View offlineBanner;
    private boolean welcomeShown;
    private boolean productContextShown;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnectivityState();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        com.example.tirtir_mcommerce.utils.HeaderHelper.bind(
                view, requireContext(), requireActivity().getSupportFragmentManager());
        rvChatMessages = view.findViewById(R.id.rvChatMessages);
        layoutChatEmpty = view.findViewById(R.id.layoutChatEmpty); // may be null in new layout
        layoutTyping = view.findViewById(R.id.layoutTyping);
        etChatInput = view.findViewById(R.id.etChatInput);
        btnSendMessage = view.findViewById(R.id.btnSendMessage);
        offlineBanner = view.findViewById(R.id.tvChatOfflineBanner);
        chatRepository = new ChatRepository(requireContext());

        adapter = new ChatMessageAdapter(product -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra("PRODUCT_ID", product.productId);
            startActivity(intent);
        });
        rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChatMessages.setAdapter(adapter);

        btnSendMessage.setOnClickListener(v -> sendCurrentMessage());
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });

        bindPrompt(view, R.id.chipPromptSkin);
        bindPrompt(view, R.id.chipPromptRoutine);
        bindPrompt(view, R.id.chipPromptIngredient);
        bindPrompt(view, R.id.chipPromptOrder);
        bindProductContextIfAvailable();
        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateConnectivityState();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(networkReceiver);
        } catch (IllegalArgumentException e) {
            // Ignored
        }
    }

    private void bindProductContextIfAvailable() {
        Bundle args = getArguments();
        if (args == null) return;
        String productName = args.getString("PRODUCT_NAME");
        String productId = args.getString("PRODUCT_ID");
        if ((productName == null || productName.isEmpty()) && (productId == null || productId.isEmpty())) {
            return;
        }

        if (layoutChatEmpty != null) layoutChatEmpty.setVisibility(View.GONE);
        productContextShown = true;
        String displayName = productName != null && !productName.isEmpty() ? productName : "this product";
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                "I am looking at " + displayName + ". Ask me about ingredients, skin fit, routine order, or what to pair with it.",
                timeFormat.format(new Date()),
                new ArrayList<>()
        ));
    }

    private void bindPrompt(View root, int chipId) {
        Chip chip = root.findViewById(chipId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                etChatInput.setText(chip.getText());
                etChatInput.setSelection(etChatInput.length());
            });
        }
    }

    private void sendCurrentMessage() {
        String text = etChatInput.getText() == null ? "" : etChatInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (layoutChatEmpty != null) layoutChatEmpty.setVisibility(View.GONE);
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(true, text, timeFormat.format(new Date()), new ArrayList<>()));
        rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
        etChatInput.setText("");

        layoutTyping.setVisibility(View.VISIBLE);
        btnSendMessage.setEnabled(false);
        StringBuilder streamed = new StringBuilder();
        int[] botPosition = {-1};
        chatRepository.sendMessage(text, new ChatRepository.StreamListener() {
            @Override
            public void onChunk(String chunk) {
                if (!isAdded() || chunk == null || chunk.isEmpty()) return;
                requireActivity().runOnUiThread(() -> {
                    streamed.append(chunk);
                    ChatMessageAdapter.ChatMessage message = new ChatMessageAdapter.ChatMessage(
                            false, streamed.toString(), timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) {
                        layoutTyping.setVisibility(View.GONE);
                        botPosition[0] = adapter.addAndReturnPosition(message);
                    } else {
                        adapter.updateMessage(botPosition[0], message);
                    }
                    rvChatMessages.scrollToPosition(adapter.getItemCount() - 1);
                });
            }

            @Override
            public void onDone(ChatRepository.ChatResult result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String finalText = result.message == null || result.message.isEmpty()
                            ? streamed.toString() : result.message;
                    List<ChatMessageAdapter.RecommendedProduct> recommendations = new ArrayList<>();
                    for (ChatRepository.Suggestion suggestion : result.suggestions) {
                        recommendations.add(new ChatMessageAdapter.RecommendedProduct(
                                suggestion.productId, suggestion.name));
                    }
                    
                    // Parse [PRODUCT:id:name] from text
                    Pattern pattern = Pattern.compile("\\[PRODUCT:([^:]+):([^]]+)\\]");
                    Matcher matcher = pattern.matcher(finalText);
                    while (matcher.find()) {
                        String pId = matcher.group(1);
                        String pName = matcher.group(2);
                        recommendations.add(new ChatMessageAdapter.RecommendedProduct(pId, pName));
                    }
                    finalText = matcher.replaceAll(""); // remove tags from display text
                    
                    ChatMessageAdapter.ChatMessage message = new ChatMessageAdapter.ChatMessage(
                            false, finalText.trim(), timeFormat.format(new Date()), recommendations);
                    if (botPosition[0] < 0) {
                        botPosition[0] = adapter.addAndReturnPosition(message);
                    } else {
                        adapter.updateMessage(botPosition[0], message);
                    }
                    finishRequest();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String display = generateLocalFallback(text);
                    ChatMessageAdapter.ChatMessage error = new ChatMessageAdapter.ChatMessage(
                            false, display, timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) botPosition[0] = adapter.addAndReturnPosition(error);
                    else adapter.updateMessage(botPosition[0], error);
                    finishRequest();
                });
            }
        });
    }

    private String generateLocalFallback(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Hi! I'm your TirTir Beauty Advisor. 🌸 How can I help you today?";
        }
        String msgLower = message.toLowerCase(Locale.ENGLISH).trim();
        
        // Get user skin type if cached
        String skinType = "combination";
        try {
            skinType = com.example.tirtir_mcommerce.database.DatabaseHelper.getInstance(requireContext()).getLatestSkinType();
        } catch (Exception ignored) {}

        if (msgLower.contains("sunscreen") || msgLower.contains("spf") || msgLower.contains("uv")) {
            return "Sunscreen is essential daily skincare! For " + skinType + " skin, look for SPF 30+ broad-spectrum protection. Apply as the last step of your morning routine, about 15 minutes before sun exposure. TirTir's Hydro UV Shield is great for lightweight, non-greasy protection. ☀️";
        }

        if (msgLower.contains("routine") || msgLower.contains("steps")) {
            return "A great skincare routine for " + skinType + " skin: 1️⃣ Gentle cleanser → 2️⃣ Hydrating toner → 3️⃣ Targeted serum → 4️⃣ Moisturizer → 5️⃣ SPF (AM only). TirTir has products for each step — shall I recommend specific ones for your skin type?";
        }

        if (msgLower.contains("ingredient") || msgLower.contains("hyaluronic") || msgLower.contains("niacinamide") || msgLower.contains("retinol")) {
            return "Great question about skincare ingredients! For " + skinType + " skin, hyaluronic acid adds lightweight hydration, niacinamide reduces pores and brightens, and retinol promotes cell turnover. Always introduce new actives gradually and use sunscreen when using retinol. Would you like more specific advice?";
        }

        if (msgLower.contains("order") || msgLower.contains("shipping") || msgLower.contains("track")) {
            return "For order and shipping inquiries, please check your Order History in the app. If you need help, our customer service team can assist you. Your beauty journey is our priority! 💌";
        }

        if (msgLower.contains("skin type") || msgLower.contains("my skin") || msgLower.contains("dry") || msgLower.contains("oily") || msgLower.contains("combination")) {
            return "Based on your profile, you have " + skinType + " skin. Key tips: stay hydrated, use products suited for your skin type, and be consistent with your routine. TirTir has a curated range perfect for " + skinType + " skin. Want personalized product recommendations?";
        }

        return "Hi! I'm your TirTir Beauty Advisor. 🌸 I can help with skincare routines, product recommendations for your " + skinType + " skin, ingredient advice, and more. What would you like to know?";
    }

    private void finishRequest() {
        layoutTyping.setVisibility(View.GONE);
        btnSendMessage.setEnabled(true);
        rvChatMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
    }

    private void loadHistory() {
        chatRepository.loadHistory(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getData() == null || response.body().getData().isEmpty()) {
                    ensureWelcomeGreeting();
                    return;
                }
                List<ChatMessageAdapter.ChatMessage> history = new ArrayList<>();
                for (Map<String, Object> item : response.body().getData()) {
                    String sender = value(item.get("sender"));
                    String text = value(item.get("text"));
                    if (text.isEmpty()) continue;
                    history.add(new ChatMessageAdapter.ChatMessage(
                            "user".equalsIgnoreCase(sender), text, "", extractRecommendations(item)));
                }
                requireActivity().runOnUiThread(() -> {
                    if (history.isEmpty()) {
                        ensureWelcomeGreeting();
                        return;
                    }
                    if (layoutChatEmpty != null) layoutChatEmpty.setVisibility(View.GONE);
                    adapter.submitMessages(history);
                    rvChatMessages.scrollToPosition(history.size() - 1);
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (isAdded()) requireActivity().runOnUiThread(() -> ensureWelcomeGreeting());
            }
        });
    }

    private void ensureWelcomeGreeting() {
        if (welcomeShown || productContextShown || adapter == null || !isAdded()) return;
        welcomeShown = true;
        if (layoutChatEmpty != null) layoutChatEmpty.setVisibility(View.GONE);
        com.example.tirtir_mcommerce.model.User user =
                new com.example.tirtir_mcommerce.utils.SharedPrefsManager(requireContext()).getCachedUser();
        String firstName = "there";
        if (user != null && user.getName() != null && !user.getName().trim().isEmpty()) {
            firstName = user.getName().trim().split("\\s+")[0];
        }
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                greeting + ", " + firstName + " 👋\n\nI’m your TirTir Beauty Advisor. I can build a routine, check ingredient compatibility, or help you choose a product. What would you like to explore?",
                timeFormat.format(new Date()),
                new ArrayList<>()));
        rvChatMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageAdapter.RecommendedProduct> extractRecommendations(Map<String, Object> message) {
        List<ChatMessageAdapter.RecommendedProduct> result = new ArrayList<>();
        Object dataObject = message.get("productData");
        if (!(dataObject instanceof Map)) return result;
        Map<String, Object> productData = (Map<String, Object>) dataObject;
        Object recommendations = productData.get("recommendations");
        if (recommendations instanceof List) {
            for (Object item : (List<?>) recommendations) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> product = (Map<String, Object>) item;
                String id = value(product.get("id"));
                String name = value(product.get("name"));
                if (!id.isEmpty() && !name.isEmpty()) {
                    result.add(new ChatMessageAdapter.RecommendedProduct(id, name));
                }
            }
        }
        return result;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void updateConnectivityState() {
        ConnectivityManager manager =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        boolean online = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
        etChatInput.setEnabled(online);
        btnSendMessage.setEnabled(online);
    }
}
