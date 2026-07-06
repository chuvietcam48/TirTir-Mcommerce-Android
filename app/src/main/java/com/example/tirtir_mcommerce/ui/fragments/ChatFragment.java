package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.ChatRepository;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.adapters.ChatMessageAdapter;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView rvChatMessages;
    private View layoutTyping;
    private EditText etChatInput;
    private ImageButton btnSendMessage;
    private View offlineBanner;
    private HorizontalScrollView layoutChatQuickPrompts;
    private ChipGroup chipGroupPrompts;

    // ── State ─────────────────────────────────────────────────────────────────

    private ChatMessageAdapter adapter;
    private ChatRepository chatRepository;
    private SharedPrefsManager prefs;

    // Config loaded from backend
    private String chatHotline            = "";
    private String welcomeMessageTemplate = "";
    private String botName                = "";

    private boolean sessionGreetingShown = false;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

    // ── Session key: one greeting per user per day ────────────────────────────

    private String sessionKey() {
        User user = prefs != null ? prefs.getCachedUser() : null;
        String uid = user != null && user.getId() != null ? user.getId() : "guest";
        long day = System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        return "chat_greeted_" + uid + "_" + day;
    }

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateConnectivityState(); }
    };

    // ── Fragment lifecycle ────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        com.example.tirtir_mcommerce.utils.HeaderHelper.bind(
                view, requireContext(), requireActivity().getSupportFragmentManager());

        prefs = new SharedPrefsManager(requireContext());
        chatRepository = new ChatRepository(requireContext());

        rvChatMessages       = view.findViewById(R.id.rvChatMessages);
        layoutTyping         = view.findViewById(R.id.layoutTyping);
        etChatInput          = view.findViewById(R.id.etChatInput);
        btnSendMessage       = view.findViewById(R.id.btnSendMessage);
        offlineBanner        = view.findViewById(R.id.tvChatOfflineBanner);
        layoutChatQuickPrompts = view.findViewById(R.id.layoutChatQuickPrompts);
        chipGroupPrompts     = view.findViewById(R.id.chipGroupPrompts);

        adapter = new ChatMessageAdapter(
                product -> {
                    Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
                    intent.putExtra("PRODUCT_ID", product.productId);
                    startActivity(intent);
                },
                this::handleActionChip
        );

        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(llm);
        rvChatMessages.setAdapter(adapter);

        btnSendMessage.setOnClickListener(v -> sendCurrentMessage());
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrentMessage(); return true; }
            return false;
        });

        bindProductContextIfAvailable();

        // Load config first (sets welcomeMessageTemplate + hotline), then chips + history in parallel
        loadConfig();
        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireContext().registerReceiver(networkReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateConnectivityState();
    }

    @Override
    public void onPause() {
        super.onPause();
        try { requireContext().unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
    }

    // ── Config & suggested questions ──────────────────────────────────────────

    private void loadConfig() {
        chatRepository.loadConfig(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null) {
                    Map<String, Object> data = response.body().getData();
                    chatHotline            = val(data.get("hotline"));
                    welcomeMessageTemplate = val(data.get("welcomeMessage"));
                    botName                = val(data.get("botName"));
                }
                loadSuggestedQuestions();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (isAdded()) loadSuggestedQuestions();
            }
        });
    }

    private void loadSuggestedQuestions() {
        chatRepository.loadSuggestedQuestions(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                List<Map<String, Object>> questions = null;
                if (response.isSuccessful() && response.body() != null) {
                    questions = response.body().getData();
                }
                populateChips(questions);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (isAdded()) populateChips(null);
            }
        });
    }

    private void populateChips(@Nullable List<Map<String, Object>> questions) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (questions == null || questions.isEmpty()) {
                // Backend unavailable — wire the static chips from XML as fallback
                bindStaticChips(getView());
                return;
            }

            chipGroupPrompts.removeAllViews();
            for (Map<String, Object> q : questions) {
                String id       = val(q.get("id"));
                String question = val(q.get("question"));
                if (question.isEmpty()) continue;

                Chip chip = new Chip(requireContext());
                chip.setText(question);
                chip.setClickable(true);
                chip.setCheckable(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(0xE6FFFFFF));
                chip.setChipStrokeColor(ColorStateList.valueOf(0x4DE3BEB8));
                chip.setChipStrokeWidth(dpToPx(1));
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_red_dark));

                String finalId = id;
                chip.setOnClickListener(v ->
                        dispatchMessage(question, finalId.isEmpty() ? null : finalId));
                chipGroupPrompts.addView(chip);
            }
        });
    }

    /** Wires the 4 static XML chips when the backend is unavailable. */
    private void bindStaticChips(View root) {
        if (root == null) return;
        wireStaticChip(root, R.id.chipPromptSkin,       "What routine is suitable for my skin type?");
        wireStaticChip(root, R.id.chipPromptIngredient,  "Can I combine serum and moisturizer together?");
        wireStaticChip(root, R.id.chipPromptRoutine,     "What is the correct morning skincare order?");
        wireStaticChip(root, R.id.chipPromptOrder,       "How can I check my order status?");
    }

    private void wireStaticChip(View root, int chipId, String question) {
        Chip chip = root.findViewById(chipId);
        if (chip == null) return;
        chip.setText(question);
        chip.setOnClickListener(v -> dispatchMessage(question, null));
    }

    // ── Product context (launched from product detail) ────────────────────────

    private void bindProductContextIfAvailable() {
        Bundle args = getArguments();
        if (args == null) return;
        String productName = args.getString("PRODUCT_NAME");
        String productId   = args.getString("PRODUCT_ID");
        if ((productName == null || productName.isEmpty())
                && (productId == null || productId.isEmpty())) return;

        String name = (productName != null && !productName.isEmpty()) ? productName : "this product";
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                "I'm looking at " + name + ". Ask me about ingredients, skin fit, routine order, or what to pair with it.",
                timeFormat.format(new Date()),
                new ArrayList<>()));
        if (prefs != null) chatSessionPrefs().edit().putBoolean(sessionKey(), true).apply();
        sessionGreetingShown = true;
    }

    // ── History loading ───────────────────────────────────────────────────────

    private void loadHistory() {
        chatRepository.loadHistory(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getData() == null
                        || response.body().getData().isEmpty()) {
                    requireActivity().runOnUiThread(() -> startFreshSession());
                    return;
                }

                List<ChatMessageAdapter.ChatMessage> history = new ArrayList<>();
                for (Map<String, Object> item : response.body().getData()) {
                    String sender = val(item.get("sender"));
                    String text   = val(item.get("text"));
                    if (text.isEmpty()) continue;
                    history.add(new ChatMessageAdapter.ChatMessage(
                            "user".equalsIgnoreCase(sender), text, "", extractRecommendations(item)));
                }

                requireActivity().runOnUiThread(() -> {
                    if (history.isEmpty()) { startFreshSession(); return; }

                    boolean newSession = !chatSessionPrefs().getBoolean(sessionKey(), false);
                    adapter.submitMessages(history);
                    adapter.addMessage(ChatMessageAdapter.ChatMessage.system(
                            "Chat history is saved for 24 hours"));
                    scrollToBottom();

                    if (newSession) addWelcomeGreeting();
                    if (prefs != null) chatSessionPrefs().edit().putBoolean(sessionKey(), true).apply();
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (isAdded()) requireActivity().runOnUiThread(() -> startFreshSession());
            }
        });
    }

    private void startFreshSession() {
        adapter.addMessage(ChatMessageAdapter.ChatMessage.system(
                "Chat history is saved for 24 hours"));
        addWelcomeGreeting();
        if (prefs != null) chatSessionPrefs().edit().putBoolean(sessionKey(), true).apply();
    }

    private void addWelcomeGreeting() {
        if (sessionGreetingShown || adapter == null || !isAdded()) return;
        sessionGreetingShown = true;

        User user = prefs != null ? prefs.getCachedUser() : null;
        String firstName = "there";
        if (user != null && user.getName() != null && !user.getName().trim().isEmpty()) {
            firstName = user.getName().trim().split("\\s+")[0];
        }

        String welcomeText;
        if (!welcomeMessageTemplate.isEmpty()) {
            String bn = botName.isEmpty() ? "TIRTIR Beauty Advisor" : botName;
            welcomeText = welcomeMessageTemplate
                    .replace("{name}", firstName)
                    .replace("{botName}", bn);
        } else {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String timeGreeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
            welcomeText = timeGreeting + ", " + firstName + " 👋\n\n"
                    + "I'm your TIRTIR Beauty Advisor. I can help with:\n"
                    + "• Skincare routines\n"
                    + "• Product recommendations\n"
                    + "• Ingredient safety\n"
                    + "• Order support\n\n"
                    + "What would you like to explore?";
        }

        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false, welcomeText, timeFormat.format(new Date()), new ArrayList<>()));
        scrollToBottom();
    }

    // ── Message sending ───────────────────────────────────────────────────────

    private void sendCurrentMessage() {
        String text = etChatInput.getText() == null ? ""
                : etChatInput.getText().toString().trim();
        dispatchMessage(text, null);
    }

    private void dispatchMessage(String text, @Nullable String questionId) {
        if (TextUtils.isEmpty(text)) return;

        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                true, text, timeFormat.format(new Date()), new ArrayList<>()));
        scrollToBottom();
        etChatInput.setText("");

        layoutTyping.setVisibility(View.VISIBLE);
        btnSendMessage.setEnabled(false);
        StringBuilder streamed = new StringBuilder();
        int[] botPosition = {-1};

        ChatRepository.StreamListener listener = new ChatRepository.StreamListener() {
            @Override
            public void onChunk(String chunk) {
                if (!isAdded() || chunk == null || chunk.isEmpty()) return;
                requireActivity().runOnUiThread(() -> {
                    streamed.append(chunk);
                    ChatMessageAdapter.ChatMessage msg = new ChatMessageAdapter.ChatMessage(
                            false, streamed.toString(), timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) {
                        layoutTyping.setVisibility(View.GONE);
                        botPosition[0] = adapter.addAndReturnPosition(msg);
                    } else {
                        adapter.updateMessage(botPosition[0], msg);
                    }
                    scrollToBottom();
                });
            }

            @Override
            public void onDone(ChatRepository.ChatResult result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String finalText = (result.message == null || result.message.isEmpty())
                            ? streamed.toString() : result.message;

                    List<ChatMessageAdapter.RecommendedProduct> recommendations = new ArrayList<>();
                    for (ChatRepository.Suggestion s : result.suggestions) {
                        recommendations.add(new ChatMessageAdapter.RecommendedProduct(s.productId, s.name));
                    }

                    Pattern p = Pattern.compile("\\[PRODUCT:([^:]+):([^]]+)\\]");
                    Matcher m = p.matcher(finalText);
                    while (m.find()) {
                        recommendations.add(new ChatMessageAdapter.RecommendedProduct(
                                m.group(1), m.group(2)));
                    }
                    finalText = m.replaceAll("").trim();

                    // OOD action chips
                    List<ChatMessageAdapter.ChatAction> actions = new ArrayList<>();
                    if (result.isOutOfDataset && result.actions != null) {
                        for (ChatRepository.ChatAction a : result.actions) {
                            actions.add(new ChatMessageAdapter.ChatAction(a.type, a.label));
                        }
                    }

                    ChatMessageAdapter.ChatMessage msg = new ChatMessageAdapter.ChatMessage(
                            false, finalText, timeFormat.format(new Date()), recommendations, actions);
                    if (botPosition[0] < 0) botPosition[0] = adapter.addAndReturnPosition(msg);
                    else adapter.updateMessage(botPosition[0], msg);
                    finishRequest();
                });
            }

            @Override
            public void onError(String errorMsg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    ChatMessageAdapter.ChatMessage err = new ChatMessageAdapter.ChatMessage(
                            false,
                            "Sorry, I couldn't connect right now. Please check your internet connection and try again.",
                            timeFormat.format(new Date()),
                            new ArrayList<>());
                    if (botPosition[0] < 0) botPosition[0] = adapter.addAndReturnPosition(err);
                    else adapter.updateMessage(botPosition[0], err);
                    finishRequest();
                });
            }
        };

        if (questionId != null && !questionId.isEmpty()) {
            chatRepository.sendQuestion(questionId, text, listener);
        } else {
            chatRepository.sendMessage(text, listener);
        }
    }

    // ── OOD action chip handler ───────────────────────────────────────────────

    private void handleActionChip(ChatMessageAdapter.ChatAction action) {
        switch (action.type) {
            case "call_hotline":
                String phone = chatHotline.isEmpty() ? "" : chatHotline.replaceAll("[^0-9+]", "");
                if (!phone.isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                } else {
                    Toast.makeText(requireContext(), "Hotline not available right now.", Toast.LENGTH_SHORT).show();
                }
                break;

            case "contact_staff":
                chatRepository.postHandoff("user_requested_staff",
                        new Callback<ApiResponse<Map<String, Object>>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                                   Response<ApiResponse<Map<String, Object>>> response) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(),
                                                "A staff member will be with you shortly.",
                                                Toast.LENGTH_SHORT).show());
                            }

                            @Override
                            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(),
                                                "Could not connect to staff. Please try again.",
                                                Toast.LENGTH_SHORT).show());
                            }
                        });
                break;

            case "choose_topic":
                if (layoutChatQuickPrompts != null) {
                    layoutChatQuickPrompts.smoothScrollTo(0, 0);
                }
                break;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void finishRequest() {
        layoutTyping.setVisibility(View.GONE);
        btnSendMessage.setEnabled(true);
        scrollToBottom();
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) rvChatMessages.scrollToPosition(count - 1);
    }

    private void updateConnectivityState() {
        ConnectivityManager cm =
                (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network net = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = net == null ? null : cm.getNetworkCapabilities(net);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (offlineBanner != null) offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
        if (etChatInput != null)    etChatInput.setEnabled(online);
        if (btnSendMessage != null) btnSendMessage.setEnabled(online);
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageAdapter.RecommendedProduct> extractRecommendations(Map<String, Object> message) {
        List<ChatMessageAdapter.RecommendedProduct> result = new ArrayList<>();
        Object dataObj = message.get("productData");
        if (!(dataObj instanceof Map)) return result;
        Map<String, Object> pd = (Map<String, Object>) dataObj;
        Object recs = pd.get("recommendations");
        if (recs instanceof List) {
            for (Object item : (List<?>) recs) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> prod = (Map<String, Object>) item;
                String id   = val(prod.get("id"));
                String name = val(prod.get("name"));
                if (!id.isEmpty() && !name.isEmpty()) {
                    result.add(new ChatMessageAdapter.RecommendedProduct(id, name));
                }
            }
        }
        return result;
    }

    private android.content.SharedPreferences chatSessionPrefs() {
        return requireContext().getSharedPreferences("tirtir_chat_session", Context.MODE_PRIVATE);
    }

    private float dpToPx(float dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }

    private String val(Object v) { return v == null ? "" : String.valueOf(v); }
}
