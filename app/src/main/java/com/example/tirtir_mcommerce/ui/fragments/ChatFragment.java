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
    private boolean postAnswerOptionsShown = false; // Only show once per session

    // Product context — set when Chat opened via "Ask AI Before Buying"
    private String ctxProductName        = "";
    private String ctxProductIngredients = "";
    private String ctxProductSkinTypes   = "";
    private String ctxProductHowToUse    = "";
    private String ctxProductDescription = "";

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

        // If launched from "Ask AI before buying" button, auto-send the pre-built question
        android.os.Bundle args = getArguments();
        if (args != null && args.containsKey("CHAT_AUTO_MESSAGE")) {
            String autoMsg = args.getString("CHAT_AUTO_MESSAGE");
            if (autoMsg != null && !autoMsg.isEmpty()) {
                currentMode = ChatMode.BEAUTY_ADVISOR;
                
                String pId = args.getString("CHAT_PRODUCT_ID");
                String pName = args.getString("CHAT_PRODUCT_NAME");
                String pImage = args.getString("CHAT_PRODUCT_IMAGE");
                String pPrice = args.getString("CHAT_PRODUCT_PRICE");
                String pRating = args.getString("CHAT_PRODUCT_RATING");
                
                ChatMessageAdapter.ProductContext pCtx = null;
                if (pId != null && pName != null) {
                    pCtx = new ChatMessageAdapter.ProductContext(pId, pName, pImage, pPrice, pRating);
                }
                
                final ChatMessageAdapter.ProductContext finalPCtx = pCtx;
                // dispatchMessage (with intentCode=null) will add the user bubble + send
                rvChatMessages.postDelayed(() -> {
                    if (!isAdded()) return;
                    dispatchMessage(autoMsg, null, finalPCtx);
                }, 800);
            }
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
        List<ChatMessageAdapter.MenuOption> items = new ArrayList<>();
        items.add(new ChatMessageAdapter.MenuOption("✨", "Chat with TIRTIR Beauty Advisor",
                "Get guided skincare support, product recommendations, and ingredient help"));
        items.add(new ChatMessageAdapter.MenuOption("👩‍💼", "Chat with TIRTIR Staff",
                "Connect with a human support staff for direct assistance"));
        items.add(new ChatMessageAdapter.MenuOption("📞", "Call Hotline " + chatHotline,
                "Call TIRTIR customer support directly"));
        adapter.addMessage(ChatMessageAdapter.ChatMessage.menu("", items));
        scrollToBottom();
    }

    // ── Structured Beauty Advisor support menu (Shopee/TikTok style) ─────────

    // Structured Beauty Advisor decision tree.
    // TODO(backend): tree lives in ChatSupportTree as a temporary frontend mirror
    // of the chat dataset until the backend is deployed and chatCategories seeded.
    private final Map<String, com.example.tirtir_mcommerce.utils.ChatSupportTree.Node> menuIndex =
            new java.util.HashMap<>();
    private List<com.example.tirtir_mcommerce.utils.ChatSupportTree.Node> primaryMenu;

    // Submenu currently on screen — resolves duplicate titles (e.g. "Basic routine"
    // exists under every skin type) to the correct branch.
    @Nullable
    private com.example.tirtir_mcommerce.utils.ChatSupportTree.Node currentMenuParent;

    private void buildSupportMenuTree() {
        if (primaryMenu != null) return;
        primaryMenu = com.example.tirtir_mcommerce.utils.ChatSupportTree.buildTree();
        indexMenu(primaryMenu);
    }

    private void indexMenu(List<com.example.tirtir_mcommerce.utils.ChatSupportTree.Node> nodes) {
        for (com.example.tirtir_mcommerce.utils.ChatSupportTree.Node n : nodes) {
            menuIndex.put(n.title, n);
            if (n.children != null) indexMenu(n.children);
        }
    }

    private void showPrimaryMenu() {
        buildSupportMenuTree();
        currentMenuParent = null;
        adapter.addMessage(ChatMessageAdapter.ChatMessage.menu(
                "How can I help you today?", toMenuOptions(primaryMenu, false)));
        scrollToBottom();
    }

    private void showSubMenu(com.example.tirtir_mcommerce.utils.ChatSupportTree.Node parent) {
        currentMenuParent = parent;
        String header = (parent.question != null && !parent.question.isEmpty())
                ? parent.question
                : parent.emoji + " " + parent.title;
        adapter.addMessage(ChatMessageAdapter.ChatMessage.menu(
                header, toMenuOptions(parent.children, true)));
        scrollToBottom();
    }

    private List<ChatMessageAdapter.MenuOption> toMenuOptions(
            List<com.example.tirtir_mcommerce.utils.ChatSupportTree.Node> nodes, boolean withBack) {
        List<ChatMessageAdapter.MenuOption> items = new ArrayList<>();
        for (com.example.tirtir_mcommerce.utils.ChatSupportTree.Node n : nodes) {
            items.add(new ChatMessageAdapter.MenuOption(n.emoji, n.title, n.desc));
        }
        if (withBack) {
            items.add(new ChatMessageAdapter.MenuOption("↩️", "Main menu", "Back to all support topics"));
        }
        return items;
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
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false,
                    "Great! Here's what I can help you with 👇",
                    timeFormat.format(new Date()),
                    new ArrayList<>()));
            scrollToBottom();
            showPrimaryMenu();

        } else if (option.contains("Staff")) {
            currentMode = ChatMode.STAFF;
            if (layoutChatQuickPrompts != null) layoutChatQuickPrompts.setVisibility(View.GONE);
            triggerHandoff();

        } else {
            // Navigation shortcuts back to the overall Beauty Advisor menu
            if (option.equals("Main menu") || option.equals("Explore more topics")
                    || option.equals("Explore another topic")
                    || option.equals("Back to main Beauty Advisor menu")) {
                showPrimaryMenu();
                return;
            }

            // Structured decision-tree navigation.
            // Look in the currently displayed submenu first — duplicate titles
            // (e.g. "Basic routine") exist under multiple branches.
            buildSupportMenuTree();
            com.example.tirtir_mcommerce.utils.ChatSupportTree.Node node = null;
            if (currentMenuParent != null && currentMenuParent.children != null) {
                for (com.example.tirtir_mcommerce.utils.ChatSupportTree.Node c : currentMenuParent.children) {
                    if (c.title.equals(option)) { node = c; break; }
                }
            }
            if (node == null) node = menuIndex.get(option);
            if (node != null && !node.isLeaf()) {
                showSubMenu(node);
                return;
            }
            if (node != null && node.answer != null && !node.answer.isEmpty()) {
                // TODO(backend): when the backend is deployed & seeded, dispatch
                // node.intentCode via dispatchMessage() instead of the local mirror.
                adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                        false, node.answer, timeFormat.format(new Date()), new ArrayList<>()));
                scrollToBottom();
                showFollowUpMenu();
                return;
            }
            if (node != null && node.intentCode != null && !node.intentCode.isEmpty()) {
                dispatchMessage(node.title, node.intentCode);
                return;
            }

            // Product-info question card or local-answer item
            String clean = option.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
            if (clean.contains("Hotline Support")) {
                adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                        false,
                        "You can reach TIRTIR support at:\n\n"
                        + "📞 Hotline: " + chatHotline + " (8:00–22:00 daily)\n"
                        + "💬 Or tap \"Chat with TIRTIR Staff\" to message us directly.",
                        timeFormat.format(new Date()), new ArrayList<>()));
                showModeOptions();
                return;
            }
            String localAnswer = answerFromProductInfo(clean.isEmpty() ? option : clean);
            if (localAnswer != null) {
                adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                        false, localAnswer, timeFormat.format(new Date()), new ArrayList<>()));
                adapter.addMessage(ChatMessageAdapter.ChatMessage.options(productQuestionOptions()));
                scrollToBottom();
            } else if (currentMode == ChatMode.BEAUTY_ADVISOR) {
                dispatchMessage(clean.isEmpty() ? option : clean, null);
            }
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
                // Backend unavailable — show local topic catalogue as option cards
                if (isAdded() && parentId == null) {
                    requireActivity().runOnUiThread(() -> showFallbackTopicOptions());
                }
            }
        });
    }

    /** Local topic catalogue shown when backend categories are unavailable. */
    private void showFallbackTopicOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("🌿 Skincare Routine");
        opts.add("✨ Product Recommendation");
        opts.add("🔬 Ingredient Safety");
        opts.add("📦 Order Support");
        opts.add("🎁 Promotion / Combo");
        opts.add("🛒 Cart / Wishlist");
        opts.add("👤 Account Support");
        opts.add("☎️ Hotline Support");
        adapter.addMessage(ChatMessageAdapter.ChatMessage.options(opts));
        scrollToBottom();
    }

    private void populateCategoryChips(@Nullable List<Map<String, Object>> categories,
                                       boolean isRoot) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            chipGroupPrompts.removeAllViews();

            if (!isRoot) {
                addNavChip("← Topics", null, true);
            }

            if (categories == null || categories.isEmpty()) {
                if (isRoot) showFallbackTopicOptions();
                return;
            }

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
            dispatchMessage(title, intentCode, null);
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

        ctxProductName        = productName != null ? productName : "this product";
        ctxProductIngredients = safe(args.getString("PRODUCT_INGREDIENTS"));
        ctxProductSkinTypes   = safe(args.getString("PRODUCT_SKIN_TYPES"));
        ctxProductHowToUse    = safe(args.getString("PRODUCT_HOW_TO_USE"));
        ctxProductDescription = safe(args.getString("PRODUCT_DESCRIPTION"));

        adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                false,
                "You're asking about \"" + ctxProductName + "\" 🛍️\n\n"
                + "Ask me about its ingredients, suitable skin types, or how to use it — "
                + "I'll answer from the product information.",
                timeFormat.format(new Date()),
                new ArrayList<>()));
        adapter.addMessage(ChatMessageAdapter.ChatMessage.options(productQuestionOptions()));
        sessionStarted = true;
        currentMode = ChatMode.BEAUTY_ADVISOR;
    }

    private List<String> productQuestionOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("🧪 What are the ingredients?");
        opts.add("👩 Which skin types is it for?");
        opts.add("📖 How do I use it?");
        opts.add("ℹ️ Tell me about this product");
        return opts;
    }

    /** Returns non-null answer from product DB info if we are in product context. */
    @Nullable
    private String answerFromProductInfo(String question) {
        if (ctxProductName.isEmpty()) return null;
        String q = question.toLowerCase(Locale.ROOT);

        if (q.contains("ingredient") || q.contains("thành phần")) {
            return ctxProductIngredients.isEmpty()
                    ? "The ingredient list for " + ctxProductName + " is not available yet. "
                      + "Please contact our staff or hotline " + chatHotline + " for details."
                    : "Key ingredients of " + ctxProductName + ":\n\n" + ctxProductIngredients;
        }
        if (q.contains("skin type") || q.contains("skin") || q.contains("loại da") || q.contains("da ")) {
            return ctxProductSkinTypes.isEmpty()
                    ? ctxProductName + " is suitable for all skin types."
                    : ctxProductName + " is suitable for: " + ctxProductSkinTypes;
        }
        if (q.contains("how") || q.contains("use") || q.contains("usage") || q.contains("guideline")
                || q.contains("apply") || q.contains("cách dùng") || q.contains("sử dụng")) {
            return ctxProductHowToUse.isEmpty()
                    ? "Application directions for " + ctxProductName + " are not available yet. "
                      + "Please contact our staff or hotline " + chatHotline + " for guidance."
                    : "How to use " + ctxProductName + ":\n\n" + ctxProductHowToUse;
        }
        if (q.contains("about") || q.contains("tell me") || q.contains("what is") || q.contains("giới thiệu")) {
            StringBuilder sb = new StringBuilder("About " + ctxProductName + ":\n");
            if (!ctxProductDescription.isEmpty()) sb.append("\n").append(ctxProductDescription);
            if (!ctxProductSkinTypes.isEmpty())   sb.append("\n\n👩 Skin types: ").append(ctxProductSkinTypes);
            if (!ctxProductIngredients.isEmpty()) sb.append("\n\n🧪 Ingredients: ").append(ctxProductIngredients);
            if (!ctxProductHowToUse.isEmpty())    sb.append("\n\n📖 How to use: ").append(ctxProductHowToUse);
            return sb.toString();
        }
        return null; // not a product-info question → fall through to normal flow
    }

    private static String safe(@Nullable String s) { return s == null ? "" : s.trim(); }

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
            // Staff mode: only echo the user's message — no automatic bot reply.
            // A real admin/staff member responds when they pick up the session.
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
            return;
        }

        // Beauty Advisor mode: product-context questions answered from DB info first
        String localAnswer = answerFromProductInfo(text);
        if (localAnswer != null) {
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>()));
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    false, localAnswer, timeFormat.format(new Date()), new ArrayList<>()));
            scrollToBottom();
            return;
        }

        dispatchMessage(text, null, null);
    }

    /**
     * Sends to the backend for dataset matching.
     * @param text      Display text / user message
     * @param intentCode  If non-null, sent as selectedQuestionId to skip full-text matching
     */
    private void dispatchMessage(String text, @Nullable String intentCode, @Nullable ChatMessageAdapter.ProductContext productContext) {
        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(intentCode)) return;

        // Category-taps already added the user bubble; free-text sends add it here
        if (intentCode == null) {
            adapter.addMessage(new ChatMessageAdapter.ChatMessage(
                    true, text, timeFormat.format(new Date()), new ArrayList<>(), null, productContext));
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

        String pId = productContext != null ? productContext.productId : null;
        if (intentCode != null && !intentCode.isEmpty()) {
            chatRepository.sendQuestion(intentCode, text, pId, listener);
        } else {
            chatRepository.sendMessage(text, pId, listener);
        }
    }

    private void showPostAnswerOptions() {
        showFollowUpMenu();
    }

    /** After a final answer: always guide the user back — never leave them stuck. */
    private void showFollowUpMenu() {
        List<ChatMessageAdapter.MenuOption> items = new ArrayList<>();
        items.add(new ChatMessageAdapter.MenuOption("🧭", "Explore another topic",
                "Continue with a different question"));
        items.add(new ChatMessageAdapter.MenuOption("↩️", "Back to main Beauty Advisor menu",
                "See all 5 support categories"));
        items.add(new ChatMessageAdapter.MenuOption("👩‍💼", "Chat with TIRTIR Staff",
                "Connect with a human support staff"));
        items.add(new ChatMessageAdapter.MenuOption("📞", "Call Hotline " + chatHotline,
                "Call TIRTIR customer support directly"));
        adapter.addMessage(ChatMessageAdapter.ChatMessage.menu(
                "Would you like to continue?", items));
        scrollToBottom();
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
                showPrimaryMenu();
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
