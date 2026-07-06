package com.example.tirtir_mcommerce.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
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

    // ── Chat mode state machine ───────────────────────────────────────────────

    private enum ChatMode { NONE, BEAUTY_ADVISOR, STAFF }
    private ChatMode currentMode = ChatMode.NONE;

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

    // Config from backend (fallback values used until backend responds)
    // TODO: final value comes from backend chatConfig — fallback only for offline/deploy-pending
    private String chatHotline            = "1900-1234";
    private String welcomeMessageTemplate = "";
    private String botName                = "TIRTIR Beauty Advisor";

    private boolean sessionStarted = false;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

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

        rvChatMessages         = view.findViewById(R.id.rvChatMessages);
        layoutTyping           = view.findViewById(R.id.layoutTyping);
        etChatInput            = view.findViewById(R.id.etChatInput);
        btnSendMessage         = view.findViewById(R.id.btnSendMessage);
        offlineBanner          = view.findViewById(R.id.tvChatOfflineBanner);
        layoutChatQuickPrompts = view.findViewById(R.id.layoutChatQuickPrompts);
        chipGroupPrompts       = view.findViewById(R.id.chipGroupPrompts);

        adapter = new ChatMessageAdapter(
                product -> {
                    Intent i = new Intent(requireContext(), ProductDetailActivity.class);
                    i.putExtra("PRODUCT_ID", product.productId);
                    startActivity(i);
                },
                this::handleActionChip,
                this::handleOptionSelected
        );

        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvChatMessages.setLayoutManager(llm);
        rvChatMessages.setAdapter(adapter);

        btnSendMessage.setOnClickListener(v -> sendCurrentMessage());
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrentMessage(); return true; }
            return false;
        });

        // Chips hidden until Beauty Advisor mode — also enforced in XML visibility="gone"
        if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.GONE);

        // Show initial UI immediately using defaults — do not wait for backend
        bindProductContextIfAvailable();
        if (!sessionStarted) {
            startFreshSession();
        }

        // Load config in background to update hotline/botName if backend is available
        loadConfigInBackground();
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

    // ── Background config load (does not gate initial UI) ────────────────────

    private void loadConfigInBackground() {
        chatRepository.loadConfig(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null) {
                    Map<String, Object> data = response.body().getData();
                    String hl = val(data.get("hotline"));
                    String wm = val(data.get("welcomeMessage"));
                    String bn = val(data.get("botName"));
                    if (!hl.isEmpty()) chatHotline = hl;
                    if (!wm.isEmpty()) welcomeMessageTemplate = wm;
                    if (!bn.isEmpty()) botName = bn;
                }
                // Config loaded silently — UI already rendered with defaults
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                // Backend unavailable — continue with defaults, no UI change
            }
        });
    }

    // ── Fresh session: system note + welcome + mode options ──────────────────

    private void startFreshSession() {
        if (sessionStarted) return;
        sessionStarted = true;
        adapter.addMessage(ChatMessageAdapter.ChatMessage.system(
                "Chat history is saved for 24 hours"));
        addWelcomeGreeting();
        showModeOptions();
        scrollToBottom();
    }

    private void addWelcomeGreeting() {
        if (!isAdded()) return;

        User user = prefs != null ? prefs.getCachedUser() : null;
        String firstName = "there";
        if (user != null && user.getName() != null && !user.getName().trim().isEmpty()) {
            firstName = user.getName().trim().split("\\s+")[0];
        }

        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String timeGreeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

        // chatHotline always has a value (fallback: "1900-1234")
        String body;
        if (!welcomeMessageTemplate.isEmpty()) {
            body = welcomeMessageTemplate
                    .replace("{name}",    firstName)
                    .replace("{botName}", botName)
                    .replace("{hotline}", chatHotline);
        } else {
            body = "I'm your " + botName + ". I can help with:\n"
                    + "• Skincare routines\n"
                    + "• Product recommendations\n"
                    + "• Ingredient safety\n"
                    + "• Order support\n\n"
                    + "📞 Hotline: " + chatHotline + "\n"
                    + "💬 Chat history is saved for 24 hours\n\n"
                    + "Please choose how you would like to continue:";
        }

        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                timeGreeting + ", " + firstName + " 👋\n\n" + body,
                timeFormat.format(new Date()),
                new ArrayList<>()));
    }

    // ── Mode selection cards ──────────────────────────────────────────────────

    private void showModeOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("Chat with TIRTIR Beauty Advisor");
        opts.add("Chat with TIRTIR Staff");
        opts.add("📞 Call Hotline " + chatHotline);   // always shown; chatHotline has fallback value
        adapter.addMessage(ChatMessageAdapter.ChatMessage.options(opts));
        scrollToBottom();
    }

    // ── Option card tapped ────────────────────────────────────────────────────

    private void handleOptionSelected(int adapterPosition, String option) {
        adapter.collapseOptions(adapterPosition, option);

        if (option.startsWith("📞 Call Hotline") || option.startsWith("Call Hotline")) {
            String phone = chatHotline.replaceAll("[^0-9+]", "");
            if (!phone.isEmpty()) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            return;
        }

        if (option.startsWith("← ")) {
            // Back-to-menu or explore-more-topics
            if (option.contains("main menu")) {
                currentMode = ChatMode.NONE;
                if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.GONE);
                showModeOptions();
            } else {
                // "← Explore more topics" — reload root categories
                loadRootCategories();
            }
            scrollToBottom();
            return;
        }

        // Echo user's choice as a user bubble
        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                true, option, timeFormat.format(new Date()), new ArrayList<>()));
        scrollToBottom();

        if (option.contains("Beauty Advisor")) {
            currentMode = ChatMode.BEAUTY_ADVISOR;
            if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.VISIBLE);
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false,
                    "Great! Choose a topic to get started 👇",
                    timeFormat.format(new Date()),
                    new ArrayList<>()));
            scrollToBottom();
            loadRootCategories();

        } else if (option.contains("Staff")) {
            currentMode = ChatMode.STAFF;
            if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.GONE);
            triggerHandoff();
        }
    }

    // ── Category tree navigation ──────────────────────────────────────────────

    private void loadRootCategories() {
        loadCategoryLevel(null);
    }

    private void loadCategoryLevel(@Nullable String parentId) {
        chatRepository.loadCategories(parentId, new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                List<Map<String, Object>> cats = null;
                if (response.isSuccessful() && response.body() != null) {
                    cats = response.body().getData();
                }
                populateCategoryChips(cats, parentId == null);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Could not load topics. Please try again.",
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void populateCategoryChips(@Nullable List<Map<String, Object>> categories,
                                       boolean isRoot) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            chipGroupPrompts.removeAllViews();

            if (!isRoot) {
                addNavChip("← Topics", null, true);
            }

            if (categories == null || categories.isEmpty()) return;

            for (Map<String, Object> cat : categories) {
                String id    = val(cat.get("id"));
                String title = val(cat.get("title"));
                String emoji = val(cat.get("emoji"));
                if (title.isEmpty()) continue;
                String label = emoji.isEmpty() ? title : emoji + " " + title;
                addNavChip(label, cat, false);
            }
        });
    }

    private void addNavChip(String label, @Nullable Map<String, Object> category, boolean isBack) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setClickable(true);
        chip.setCheckable(false);
        if (isBack) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFF5F5F5));
            chip.setChipStrokeColor(ColorStateList.valueOf(0xFF8B0000));
            chip.setChipStrokeWidth(dpToPx(1));
            chip.setTextColor(0xFF8B0000);
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(0xE6FFFFFF));
            chip.setChipStrokeColor(ColorStateList.valueOf(0x4DE3BEB8));
            chip.setChipStrokeWidth(dpToPx(1));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_red_dark));
        }
        chip.setOnClickListener(v -> {
            if (isBack) loadRootCategories();
            else if (category != null) handleCategorySelected(category);
        });
        chipGroupPrompts.addView(chip);
    }

    private void handleCategorySelected(Map<String, Object> category) {
        String id         = val(category.get("id"));
        String title      = val(category.get("title"));
        String emoji      = val(category.get("emoji"));
        String intentCode = val(category.get("intentCode"));
        boolean isLeaf    = Boolean.TRUE.equals(category.get("isLeaf"));
        String displayTitle = emoji.isEmpty() ? title : emoji + " " + title;

        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                true, displayTitle, timeFormat.format(new Date()), new ArrayList<>()));
        scrollToBottom();

        if (isLeaf && !intentCode.isEmpty()) {
            dispatchMessage(title, intentCode);
        } else if (!isLeaf && !id.isEmpty()) {
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false, "Choose a specific topic:",
                    timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
            loadCategoryLevel(id);
        }
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
        sessionStarted = true;
        currentMode = ChatMode.BEAUTY_ADVISOR;
        if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.VISIBLE);
    }

    // ── Message sending ───────────────────────────────────────────────────────

    private void sendCurrentMessage() {
        String text = etChatInput.getText() == null ? ""
                : etChatInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        etChatInput.setText("");

        if (currentMode == ChatMode.NONE) {
            // Free-text before mode selection: do not answer, redirect to mode selection
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false,
                    "Please choose how you would like to continue:\n"
                    + "1. Chat with TIRTIR Beauty Advisor\n"
                    + "2. Chat with TIRTIR Staff",
                    timeFormat.format(new Date()),
                    new ArrayList<>()));
            showModeOptions();
            return;
        }

        if (currentMode == ChatMode.STAFF) {
            // Staff mode: just echo; real staff chat handled externally
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false,
                    "Your message has been noted. Our staff will respond shortly.",
                    timeFormat.format(new Date()),
                    new ArrayList<>()));
            scrollToBottom();
            return;
        }

        dispatchMessage(text, null);
    }

    /**
     * Sends to the backend for dataset matching.
     * @param text      Display text / user message
     * @param intentCode  If non-null, sent as selectedQuestionId to skip full-text matching
     */
    private void dispatchMessage(String text, @Nullable String intentCode) {
        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(intentCode)) return;

        // Category-taps already added the user bubble; free-text sends add it here
        if (intentCode == null) {
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
        }

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

                    List<ChatMessageAdapter.RecommendedProduct> recs = new ArrayList<>();
                    for (ChatRepository.Suggestion s : result.suggestions) {
                        recs.add(new ChatMessageAdapter.RecommendedProduct(s.productId, s.name));
                    }
                    Pattern p = Pattern.compile("\\[PRODUCT:([^:]+):([^]]+)\\]");
                    Matcher m = p.matcher(finalText);
                    while (m.find()) recs.add(new ChatMessageAdapter.RecommendedProduct(m.group(1), m.group(2)));
                    finalText = m.replaceAll("").trim();

                    List<ChatMessageAdapter.ChatAction> actions = new ArrayList<>();
                    if (result.isOutOfDataset && result.actions != null) {
                        for (ChatRepository.ChatAction a : result.actions) {
                            actions.add(new ChatMessageAdapter.ChatAction(a.type, a.label));
                        }
                    }

                    ChatMessageAdapter.ChatMessage msg = new ChatMessageAdapter.ChatMessage(
                            false, finalText, timeFormat.format(new Date()), recs, actions);
                    if (botPosition[0] < 0) botPosition[0] = adapter.addAndReturnPosition(msg);
                    else adapter.updateMessage(botPosition[0], msg);

                    if (currentMode == ChatMode.BEAUTY_ADVISOR) showPostAnswerOptions();
                    finishRequest();
                });
            }

            @Override
            public void onError(String errorMsg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    ChatMessageAdapter.ChatMessage err = new ChatMessageAdapter.ChatMessage(
                            false,
                            "Sorry, I couldn't connect right now. Please check your connection and try again.",
                            timeFormat.format(new Date()), new ArrayList<>());
                    if (botPosition[0] < 0) botPosition[0] = adapter.addAndReturnPosition(err);
                    else adapter.updateMessage(botPosition[0], err);
                    finishRequest();
                });
            }
        };

        if (intentCode != null && !intentCode.isEmpty()) {
            chatRepository.sendQuestion(intentCode, text, listener);
        } else {
            chatRepository.sendMessage(text, listener);
        }
    }

    private void showPostAnswerOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("← Explore more topics");
        opts.add("Chat with TIRTIR Staff");
        if (!chatHotline.isEmpty()) opts.add("📞 Call Hotline " + chatHotline);
        adapter.addMessage(ChatMessageAdapter.ChatMessage.options(opts));
        scrollToBottom();
        loadRootCategories();
    }

    // ── OOD action chip handler ───────────────────────────────────────────────

    private void handleActionChip(ChatMessageAdapter.ChatAction action) {
        switch (action.type) {
            case "call_hotline":
                String phone = chatHotline.replaceAll("[^0-9+]", "");
                if (!phone.isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                } else {
                    Toast.makeText(requireContext(), "Hotline not available.", Toast.LENGTH_SHORT).show();
                }
                break;

            case "contact_staff":
                triggerHandoff();
                break;

            case "choose_topic":
                currentMode = ChatMode.BEAUTY_ADVISOR;
                if (layoutChatQuickPrompts != null) {
                    layoutChatQuickPrompts.setVisibility(View.VISIBLE);
                    layoutChatQuickPrompts.smoothScrollTo(0, 0);
                }
                loadRootCategories();
                break;
        }
    }

    // ── Staff handoff ─────────────────────────────────────────────────────────

    private void triggerHandoff() {
        chatRepository.postHandoff("user_requested_staff",
                new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            currentMode = ChatMode.STAFF;
                            if (layoutChatQuickPrompts != null)
                                layoutChatQuickPrompts.setVisibility(View.GONE);
                            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                                    false,
                                    "Your request has been forwarded to TIRTIR Staff. "
                                    + "Please wait a moment while we connect you with an admin. "
                                    + "Thank you for waiting. 🙏\n\n"
                                    + "📞 Hotline: " + chatHotline,
                                    timeFormat.format(new Date()), new ArrayList<>()));
                            scrollToBottom();
                        });
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            // Show staff mode message even if handoff API is unreachable
                            currentMode = ChatMode.STAFF;
                            if (layoutChatQuickPrompts != null)
                                layoutChatQuickPrompts.setVisibility(View.GONE);
                            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                                    false,
                                    "Your request has been forwarded to TIRTIR Staff. "
                                    + "Please wait a moment while we connect you with an admin. "
                                    + "Thank you for waiting. 🙏\n\n"
                                    + "📞 Hotline: " + chatHotline,
                                    timeFormat.format(new Date()), new ArrayList<>()));
                            scrollToBottom();
                        });
                    }
                });
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
        if (offlineBanner != null)  offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
        if (etChatInput != null)     etChatInput.setEnabled(online);
        if (btnSendMessage != null)  btnSendMessage.setEnabled(online);
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageAdapter.RecommendedProduct> extractRecommendations(
            Map<String, Object> message) {
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
                if (!id.isEmpty() && !name.isEmpty())
                    result.add(new ChatMessageAdapter.RecommendedProduct(id, name));
            }
        }
        return result;
    }

    private float dpToPx(float dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }

    private String val(Object v) { return v == null ? "" : String.valueOf(v); }
}
