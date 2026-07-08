package com.example.tirtir_mcommerce.ui.fragments;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiConfig;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.RoutineRecommendRequest;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.utils.HeaderHelper;
import com.example.tirtir_mcommerce.utils.RoutineManager;
import com.example.tirtir_mcommerce.viewmodel.ProductViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RoutineFragment extends Fragment {

    // ── Quiz steps ───────────────────────────────────────────────────────────
    private static final int STEP_WELCOME   = 1;
    private static final int STEP_SKIN_TYPE = 2;
    private static final int STEP_CONCERNS  = 3;
    private static final int STEP_GOALS     = 4;
    private static final int STEP_CURRENT   = 5;
    private static final int STEP_LEVEL     = 6;
    private static final int TOTAL_STEPS    = 6;

    // ── Quiz state ───────────────────────────────────────────────────────────
    private int     currentStep      = STEP_WELCOME;
    private String  selectedSkinType = "";
    private final Set<String> selectedConcerns = new HashSet<>();
    private final Set<String> selectedGoals    = new HashSet<>();
    private String  selectedCurrentRoutine = "";
    private String  selectedLevel    = "";

    // ── Views ────────────────────────────────────────────────────────────────
    private View           quizContainer;
    private NestedScrollView resultContainer;
    private ProgressBar    quizProgressBar;
    private TextView       tvQuizStepCounter;
    private LinearLayout   stepContentContainer;
    private MaterialButton btnQuizBack;
    private MaterialButton btnQuizNext;
    private TextView       tvSkinProfileLabel;
    private LinearLayout   amProductsContainer;
    private LinearLayout   pmProductsContainer;
    private LinearLayout   comboSection;
    private LinearLayout   comboProductsContainer;
    private LinearLayout   routineEmptyState;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private RoutineManager  routineManager;
    private CartRepository  cartRepository;
    private ProductViewModel productViewModel;

    // ── Data ─────────────────────────────────────────────────────────────────
    private List<Product> allProducts = new ArrayList<>();
    private boolean viewInitialized = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        HeaderHelper.bind(view, requireContext(), requireActivity().getSupportFragmentManager());

        routineManager = new RoutineManager(requireContext());
        cartRepository = new CartRepository(requireContext());
        productViewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);

        bindViews(view);
        setupButtons();
        loadProductsAndStart();
    }

    private void bindViews(View v) {
        quizContainer        = v.findViewById(R.id.quizContainer);
        resultContainer      = v.findViewById(R.id.resultContainer);
        quizProgressBar      = v.findViewById(R.id.quizProgressBar);
        tvQuizStepCounter    = v.findViewById(R.id.tvQuizStepCounter);
        stepContentContainer = v.findViewById(R.id.stepContentContainer);
        btnQuizBack          = v.findViewById(R.id.btnQuizBack);
        btnQuizNext          = v.findViewById(R.id.btnQuizNext);
        tvSkinProfileLabel   = v.findViewById(R.id.tvSkinProfileLabel);
        amProductsContainer  = v.findViewById(R.id.amProductsContainer);
        pmProductsContainer  = v.findViewById(R.id.pmProductsContainer);
        comboSection         = v.findViewById(R.id.comboSection);
        comboProductsContainer = v.findViewById(R.id.comboProductsContainer);
        routineEmptyState    = v.findViewById(R.id.routineEmptyState);

        MaterialButton btnRetake = v.findViewById(R.id.btnRetakeQuiz);
        if (btnRetake != null) btnRetake.setOnClickListener(x -> retakeQuiz());

        MaterialButton btnBrowse = v.findViewById(R.id.btnBrowseShop);
        if (btnBrowse != null) btnBrowse.setOnClickListener(x -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new ShopFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void setupButtons() {
        btnQuizBack.setOnClickListener(v -> {
            if (currentStep > STEP_WELCOME) {
                currentStep--;
                renderStep(currentStep);
            }
        });
        btnQuizNext.setOnClickListener(v -> handleNextTap());
    }

    private void loadProductsAndStart() {
        productViewModel.productsLiveData.observe(getViewLifecycleOwner(), products -> {
            if (products != null) allProducts = products;
            // Only show initial state once (first emission)
            if (allProducts.isEmpty() && products == null) return;
            decideInitialView();
        });
        if (productViewModel.productsLiveData.getValue() == null
                || productViewModel.productsLiveData.getValue().isEmpty()) {
            productViewModel.loadProducts();
        } else {
            allProducts = productViewModel.productsLiveData.getValue();
            decideInitialView();
        }
    }

    // ── Entry point logic ─────────────────────────────────────────────────────

    private void decideInitialView() {
        if (viewInitialized) return; // Only decide once per Fragment view lifecycle
        viewInitialized = true;
        if (routineManager.isQuizDone()) {
            showResultScreen();
        } else {
            showQuizScreen();
        }
    }

    private void showResultScreen() {
        // Try to restore cached AI result first
        String cached = routineManager.getSavedAiRoutineResult();
        if (cached != null) {
            try {
                Gson gson = new Gson();
                Map<String, Object> aiData = gson.fromJson(cached, new TypeToken<Map<String, Object>>(){}.getType());
                showResultScreenWithAi(aiData);
                return;
            } catch (Exception e) {
                // ignore parse error, fall through to local
            }
        }
        showResultScreenWithAi(null);
    }

    // ── Quiz flow ─────────────────────────────────────────────────────────────

    private void showQuizScreen() {
        quizContainer.setVisibility(View.VISIBLE);
        resultContainer.setVisibility(View.GONE);
        currentStep = STEP_WELCOME;
        renderStep(currentStep);
    }

    private void renderStep(int step) {
        stepContentContainer.removeAllViews();
        quizProgressBar.setProgress(step);
        tvQuizStepCounter.setText(step + " / " + TOTAL_STEPS);

        btnQuizBack.setVisibility(step > STEP_WELCOME ? View.VISIBLE : View.INVISIBLE);

        // Hide bottom nav bar on welcome; show inline button instead
        View navBar = (View) btnQuizNext.getParent();
        navBar.setVisibility(step == STEP_WELCOME ? View.GONE : View.VISIBLE);

        switch (step) {
            case STEP_WELCOME:   buildWelcomeStep();   break;
            case STEP_SKIN_TYPE: buildSkinTypeStep();  break;
            case STEP_CONCERNS:  buildConcernsStep();  break;
            case STEP_GOALS:     buildGoalsStep();     break;
            case STEP_CURRENT:   buildCurrentStep();   break;
            case STEP_LEVEL:     buildLevelStep();     break;
        }
    }

    private void handleNextTap() {
        switch (currentStep) {
            case STEP_WELCOME:
                currentStep = STEP_SKIN_TYPE;
                renderStep(currentStep);
                break;
            case STEP_SKIN_TYPE:
                if (selectedSkinType.isEmpty()) {
                    Toast.makeText(requireContext(), "Please select your skin type", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentStep = STEP_CONCERNS;
                renderStep(currentStep);
                break;
            case STEP_CONCERNS:
                currentStep = STEP_GOALS;
                renderStep(currentStep);
                break;
            case STEP_GOALS:
                currentStep = STEP_CURRENT;
                renderStep(currentStep);
                break;
            case STEP_CURRENT:
                currentStep = STEP_LEVEL;
                renderStep(currentStep);
                break;
            case STEP_LEVEL:
                if (selectedLevel.isEmpty()) {
                    Toast.makeText(requireContext(), "Please choose a routine level", Toast.LENGTH_SHORT).show();
                    return;
                }
                finishQuiz();
                break;
        }
    }

    // ── Step builders ─────────────────────────────────────────────────────────

    private void buildWelcomeStep() {
        btnQuizNext.setText("Get Started");
        addHeading("Welcome to TirTir Routine");
        addSubtitle("Answer 5 quick questions and we'll build a personalized K-beauty routine just for you.");
        addSpacer(16);

        String[][] features = {
                {"Personalized steps", "Based on your skin type and concerns"},
                {"Real TirTir products", "Matched to your profile"},
                {"AM + PM routine", "Morning and evening separated"},
        };
        for (String[] f : features) {
            addBulletRow(f[0], f[1]);
        }

        addSpacer(32);

        MaterialButton getStarted = new MaterialButton(requireContext());
        getStarted.setText("Get Started");
        getStarted.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        getStarted.setAllCaps(false);
        getStarted.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_white));
        getStarted.setCornerRadius(dp(12));
        getStarted.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary)));
        getStarted.setOnClickListener(v -> handleNextTap());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;

        stepContentContainer.addView(getStarted, btnLp);
    }

    private void buildSkinTypeStep() {
        btnQuizNext.setText("Next");
        addHeading("What's your skin type?");
        addSubtitle("Select the option that best describes your skin.");
        addSpacer(12);

        String[][] options = {
                {"oily",        "Oily",        "Shiny, especially the T-zone"},
                {"dry",         "Dry",         "Tight, flaky, or rough"},
                {"combination", "Combination", "Oily T-zone, drier cheeks"},
                {"normal",      "Normal",      "Balanced, comfortable"},
                {"sensitive",   "Sensitive",   "Easily irritated or red"},
                {"not_sure",    "Not Sure",    "I'll let TirTir decide"},
        };
        for (String[] o : options) {
            boolean selected = o[0].equals(selectedSkinType);
            addSingleOptionCard(o[0], o[1], o[2], selected, key -> {
                selectedSkinType = key;
                renderStep(STEP_SKIN_TYPE);
            });
        }
    }

    private void buildConcernsStep() {
        btnQuizNext.setText("Next");
        addHeading("What are your skin concerns?");
        addSubtitle("Select all that apply — we'll tailor products for each one.");
        addSpacer(12);

        String[][] options = {
                {"acne",       "Acne & Breakouts"},
                {"dark_spots", "Dark Spots"},
                {"pores",      "Large Pores"},
                {"dry_flaky",  "Dry & Flaky Skin"},
                {"dull",       "Dull Skin"},
                {"excess_oil", "Excess Oil"},
                {"irritation", "Irritation / Redness"},
                {"aging",      "Early Aging"},
        };
        for (String[] o : options) {
            boolean selected = selectedConcerns.contains(o[0]);
            addMultiOptionCard(o[0], o[1], selected, key -> {
                if (selectedConcerns.contains(key)) selectedConcerns.remove(key);
                else selectedConcerns.add(key);
                renderStep(STEP_CONCERNS);
            });
        }
    }

    private void buildGoalsStep() {
        btnQuizNext.setText("Next");
        addHeading("What are your skincare goals?");
        addSubtitle("Pick your top goals — we'll prioritize products accordingly.");
        addSpacer(12);

        String[][] options = {
                {"hydration",    "Deep Hydration"},
                {"brightening",  "Even Skin Tone"},
                {"anti_acne",    "Reduce Breakouts"},
                {"sun_protection","Sun Protection"},
                {"anti_aging",   "Anti-Aging"},
                {"basics",       "Keep It Simple"},
        };
        for (String[] o : options) {
            boolean selected = selectedGoals.contains(o[0]);
            addMultiOptionCard(o[0], o[1], selected, key -> {
                if (selectedGoals.contains(key)) selectedGoals.remove(key);
                else selectedGoals.add(key);
                renderStep(STEP_GOALS);
            });
        }
    }

    private void buildCurrentStep() {
        btnQuizNext.setText("Next");
        addHeading("What does your current routine look like?");
        addSubtitle("This helps us suggest the right complexity for you.");
        addSpacer(12);

        String[][] options = {
                {"none",       "No routine yet",            "Starting from scratch"},
                {"wash_only",  "Just face wash",            "One step only"},
                {"basic",      "Cleanser + moisturizer",    "The essentials"},
                {"with_toner", "Adding toner",              "3-step routine"},
                {"full",       "Full routine with SPF",     "Already committed"},
        };
        for (String[] o : options) {
            boolean selected = o[0].equals(selectedCurrentRoutine);
            addSingleOptionCard(o[0], o[1], o[2], selected, key -> {
                selectedCurrentRoutine = key;
                renderStep(STEP_CURRENT);
            });
        }
    }

    private void buildLevelStep() {
        btnQuizNext.setText("Build My Routine");
        addHeading("Choose your routine level");
        addSubtitle("You can always change this later.");
        addSpacer(12);

        String[][] options = {
                {"minimal", "Minimal",  "3 steps — cleanser, moisturizer, SPF"},
                {"basic",   "Basic",    "4–5 steps — add toner for better results"},
                {"full",    "Full",     "5–6 steps — complete care with serum"},
        };
        for (String[] o : options) {
            boolean selected = o[0].equals(selectedLevel);
            addSingleOptionCard(o[0], o[1], o[2], selected, key -> {
                selectedLevel = key;
                renderStep(STEP_LEVEL);
            });
        }
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private void addHeading(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_black));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6));
        stepContentContainer.addView(tv, lp);
    }

    private void addSubtitle(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(4));
        stepContentContainer.addView(tv, lp);
    }

    private void addSpacer(int dpHeight) {
        View v = new View(requireContext());
        stepContentContainer.addView(v,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpHeight)));
    }

    private void addBulletRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(10));

        TextView dot = new TextView(requireContext());
        dot.setText("•");
        dot.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary));
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        dot.setPadding(0, 0, dp(8), 0);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_black));

        TextView tvSub = new TextView(requireContext());
        tvSub.setText(subtitle);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSub.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_text_secondary));

        col.addView(tvTitle);
        col.addView(tvSub);
        row.addView(dot);
        row.addView(col);
        stepContentContainer.addView(row, rowLp);
    }

    interface OptionCallback { void onSelected(String key); }

    private void addSingleOptionCard(String key, String label, String subtitle,
                                     boolean selected, OptionCallback cb) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(selected ? dp(2) : dp(1));
        card.setRadius(dp(10));
        card.setStrokeWidth(selected ? dp(2) : dp(1));
        card.setStrokeColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_rose_outline));
        card.setCardBackgroundColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_surface)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_white));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setTextColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_black));

        textCol.addView(tvLabel);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(requireContext());
            tvSub.setText(subtitle);
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvSub.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_text_secondary));
            textCol.addView(tvSub);
        }

        if (selected) {
            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            check.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary));
            check.setPadding(dp(8), 0, 0, 0);
            inner.addView(textCol);
            inner.addView(check);
        } else {
            inner.addView(textCol);
        }

        card.addView(inner);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));

        card.setOnClickListener(v -> cb.onSelected(key));
        card.setClickable(true);
        card.setFocusable(true);
        stepContentContainer.addView(card, lp);
    }

    private void addMultiOptionCard(String key, String label, boolean selected, OptionCallback cb) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(selected ? dp(2) : dp(1));
        card.setRadius(dp(10));
        card.setStrokeWidth(selected ? dp(2) : dp(1));
        card.setStrokeColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_rose_outline));
        card.setCardBackgroundColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_surface)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_white));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setTextColor(selected
                ? ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary)
                : ContextCompat.getColor(requireContext(), R.color.tirtir_black));
        if (selected) tvLabel.setTypeface(null, Typeface.BOLD);

        inner.addView(tvLabel);

        if (selected) {
            TextView check = new TextView(requireContext());
            check.setText("✓");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            check.setTextColor(ContextCompat.getColor(requireContext(), R.color.tirtir_red_primary));
            inner.addView(check);
        }

        card.addView(inner);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));

        card.setOnClickListener(v -> cb.onSelected(key));
        card.setClickable(true);
        card.setFocusable(true);
        stepContentContainer.addView(card, lp);
    }

    private View loadingOverlay;
    private ApiService apiService;

    // ── Quiz completion ───────────────────────────────────────────────────────

    private void finishQuiz() {
        if (selectedCurrentRoutine.isEmpty()) selectedCurrentRoutine = "none";
        
        // Save locally first
        routineManager.saveQuizResult(
                selectedSkinType.isEmpty() ? "Normal" : capitalize(selectedSkinType),
                selectedConcerns,
                selectedGoals,
                selectedCurrentRoutine,
                selectedLevel
        );
        
        // Setup loading
        if (loadingOverlay == null) {
            loadingOverlay = requireView().findViewById(R.id.loadingOverlay);
            apiService = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        }
        
        quizContainer.setVisibility(View.GONE);
        loadingOverlay.setVisibility(View.VISIBLE);
        
        // Call AI API
        RoutineRecommendRequest req = new RoutineRecommendRequest();
        req.setSkinType(routineManager.getSkinType());
        req.setConcerns(new ArrayList<>(routineManager.getConcerns()));
        
        apiService.recommendRoutine(req).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                loadingOverlay.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Map<String, Object> aiData = response.body().getData();
                    // Cache to disk so it survives app restarts
                    try {
                        String json = new Gson().toJson(aiData);
                        routineManager.saveAiRoutineResult(json);
                    } catch (Exception ignored) {}
                    showResultScreenWithAi(aiData);
                } else {
                    Toast.makeText(requireContext(), "Lỗi kết nối AI, hiển thị cơ bản", Toast.LENGTH_SHORT).show();
                    showResultScreenWithAi(null);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                loadingOverlay.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Không thể kết nối máy chủ", Toast.LENGTH_SHORT).show();
                showResultScreenWithAi(null);
            }
        });
    }

    private void showResultScreenWithAi(Map<String, Object> aiData) {
        quizContainer.setVisibility(View.GONE);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
        resultContainer.setVisibility(View.VISIBLE);

        String skinType = routineManager.getSkinType();
        Set<String> concerns = routineManager.getConcerns();

        // Build profile label
        StringBuilder profile = new StringBuilder(skinType + " skin");
        if (!concerns.isEmpty()) {
            profile.append("  •  ");
            List<String> readable = new ArrayList<>();
            for (String c : concerns) readable.add(readableConcern(c));
            profile.append(android.text.TextUtils.join(", ", readable));
        }
        tvSkinProfileLabel.setText(profile.toString());

        if (aiData != null) {
            buildAiResultUI(aiData);
        } else {
            buildResultUI(); // Fallback to local
        }
    }
    
    @SuppressWarnings("unchecked")
    private void buildAiResultUI(Map<String, Object> data) {
        amProductsContainer.removeAllViews();
        pmProductsContainer.removeAllViews();
        comboProductsContainer.removeAllViews();
        comboSection.setVisibility(View.GONE);

        Object routineObj = data.get("routine");
        if (!(routineObj instanceof List)) {
            routineEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        List<?> list = (List<?>) routineObj;
        if (list.isEmpty()) {
            routineEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        routineEmptyState.setVisibility(View.GONE);
        
        // Hide PM section and change AM section title
        View pmSection = requireView().findViewById(R.id.pmSection);
        if (pmSection != null) pmSection.setVisibility(View.GONE);
        
        TextView amTitle = requireView().findViewById(R.id.tvAmTitle);
        if (amTitle != null) {
            amTitle.setText("YOUR AI ROUTINE");
        }
        
        int stepIndex = 1;
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) item;
            
            String sName = String.valueOf(map.get("step"));
            if (sName == null || sName.equals("null") || sName.isEmpty()) {
                sName = String.valueOf(map.get("stepName"));
            }
            
            RoutineManager.RoutineStep step = new RoutineManager.RoutineStep("", sName, stepIndex++);
            
            step.reason = String.valueOf(map.get("reason"));
            if (step.reason == null || step.reason.equals("null") || step.reason.isEmpty()) {
                step.reason = String.valueOf(map.get("description"));
            }
            
            Object productObj = map.get("product");
            if (productObj instanceof Map) {
                Map<String, Object> pMap = (Map<String, Object>) productObj;
                Product p = new Product();
                
                String pId = String.valueOf(pMap.get("Product_ID"));
                if (pId == null || pId.equals("null")) pId = String.valueOf(pMap.get("productId"));
                p.setProductId(pId);
                
                String realId = String.valueOf(pMap.get("_id"));
                if (realId == null || realId.equals("null")) realId = String.valueOf(pMap.get("id"));
                if (realId != null && !realId.equals("null")) {
                    p.setId(realId);
                } else {
                    p.setId(pId); // fallback
                }
                
                String pName = String.valueOf(pMap.get("Name"));
                if (pName == null || pName.equals("null")) pName = String.valueOf(map.get("productName"));
                p.setName(pName);
                
                Object priceObj = pMap.get("Price");
                if (priceObj instanceof Number) p.setPrice(((Number) priceObj).doubleValue());
                else if (map.get("price") instanceof Number) p.setPrice(((Number) map.get("price")).doubleValue());
                
                String imgUrl = String.valueOf(pMap.get("imageUrl"));
                if (imgUrl == null || imgUrl.equals("null")) {
                    imgUrl = String.valueOf(pMap.get("Thumbnail_Images"));
                }
                
                if (imgUrl != null && !imgUrl.equals("null")) {
                    if (imgUrl.startsWith("[")) {
                        imgUrl = imgUrl.replaceAll("^\\[\"?|\"?\\]$|\"", "").split(",")[0].trim();
                    }
                    if (!imgUrl.startsWith("http") && !imgUrl.startsWith("android.resource://")) {
                        imgUrl = ApiConfig.BASE_URL + (imgUrl.startsWith("/") ? imgUrl.substring(1) : imgUrl);
                    }
                    p.setThumbnailImages(imgUrl);
                }
                
                step.product = p;
            }
            
            View card = buildProductCard(step, step.order);
            // Put all AI steps into AM container to form a single unified list
            amProductsContainer.addView(card);
        }
    }

    private void retakeQuiz() {
        routineManager.resetQuiz();
        selectedSkinType = "";
        selectedConcerns.clear();
        selectedGoals.clear();
        selectedCurrentRoutine = "";
        selectedLevel = "";
        showQuizScreen();
    }

    private void buildResultUI() {
        amProductsContainer.removeAllViews();
        pmProductsContainer.removeAllViews();
        comboProductsContainer.removeAllViews();

        if (allProducts.isEmpty()) {
            routineEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        List<RoutineManager.RoutineStep> steps = routineManager.generateRoutine(allProducts);
        List<Product> combos = routineManager.findCombos(allProducts);

        // Combos
        if (!combos.isEmpty()) {
            comboSection.setVisibility(View.VISIBLE);
            int max = Math.min(combos.size(), 2);
            for (int i = 0; i < max; i++) {
                Product p = combos.get(i);
                RoutineManager.RoutineStep comboStep = new RoutineManager.RoutineStep("", "Set", 0);
                comboStep.product = p;
                comboStep.reason = "Complete set for a curated K-beauty routine.";
                comboStep.tag = "Bundle value";
                View card = buildProductCard(comboStep, i + 1);
                comboProductsContainer.addView(card);
            }
        } else {
            comboSection.setVisibility(View.GONE);
        }

        // AM / PM steps
        int amCount = 0, pmCount = 0;
        for (RoutineManager.RoutineStep step : steps) {
            if (step.product == null) continue;
            View card = buildProductCard(step, step.order);
            if ("AM".equals(step.timeOfDay)) {
                amProductsContainer.addView(card);
                amCount++;
            } else {
                pmProductsContainer.addView(card);
                pmCount++;
            }
        }

        if (amCount == 0 && pmCount == 0) {
            routineEmptyState.setVisibility(View.VISIBLE);
        } else {
            routineEmptyState.setVisibility(View.GONE);
        }
    }

    private View buildProductCard(RoutineManager.RoutineStep step, int displayOrder) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_routine_product_card, null, false);

        ImageView ivProduct     = card.findViewById(R.id.ivRoutineProduct);
        TextView  tvStepLabel   = card.findViewById(R.id.tvRoutineStepLabel);
        TextView  tvName        = card.findViewById(R.id.tvRoutineProductName);
        TextView  tvReason      = card.findViewById(R.id.tvRoutineProductReason);
        TextView  tvTag         = card.findViewById(R.id.tvRoutineTag);
        TextView  tvPrice       = card.findViewById(R.id.tvRoutineProductPrice);
        ImageButton btnWishlist = card.findViewById(R.id.btnRoutineWishlist);
        MaterialButton btnCart  = card.findViewById(R.id.btnRoutineAddCart);

        Product p = step.product;
        if (p == null) return card;

        // Step label
        if (step.timeOfDay != null && !step.timeOfDay.isEmpty()) {
            tvStepLabel.setText(step.timeOfDay + "  Step " + displayOrder + "  •  " + step.stepName);
        } else {
            tvStepLabel.setText(step.stepName);
        }

        // Name
        tvName.setText(p.getName() != null ? p.getName() : "");

        // Reason
        tvReason.setText(step.reason != null ? step.reason : "");

        // Tag
        if (step.tag != null && !step.tag.isEmpty()) {
            tvTag.setVisibility(View.VISIBLE);
            tvTag.setText(step.tag);
        } else {
            tvTag.setVisibility(View.GONE);
        }

        // Price
        double displayPrice = (p.getSalePrice() > 0) ? p.getSalePrice() : p.getPrice();
        tvPrice.setText(String.format(Locale.US, "$%.2f", displayPrice));

        // Image
        String imgPath = p.getThumbnailImages();
        if (imgPath != null && !imgPath.isEmpty()) {
            String primary  = ApiConfig.resolveMediaUrl(imgPath);
            String fallback = ApiConfig.resolveMediaFallbackUrl(imgPath);
            Glide.with(this)
                    .load(primary)
                    .error(Glide.with(this).load(fallback))
                    .placeholder(R.color.tirtir_bg_gray)
                    .into(ivProduct);
        } else {
            ivProduct.setImageResource(R.color.tirtir_bg_gray);
        }

        // Wishlist state
        boolean wishlisted = isWishlisted(p.getId());
        updateWishlistIcon(btnWishlist, wishlisted);
        btnWishlist.setOnClickListener(v -> toggleWishlist(p, btnWishlist, displayPrice));

        // Add to cart
        btnCart.setOnClickListener(v -> {
            CartItem item = new CartItem(
                    p.getId(),
                    p.getName() != null ? p.getName() : "",
                    imgPath != null ? imgPath : "",
                    displayPrice,
                    1,
                    ""
            );
            cartRepository.addToCartLocal(item);
            Toast.makeText(requireContext(), "Added to cart", Toast.LENGTH_SHORT).show();
        });

        // Tap card → product detail
        card.setOnClickListener(v -> launchProductDetail(p));
        tvName.setOnClickListener(v -> launchProductDetail(p));
        ivProduct.setOnClickListener(v -> launchProductDetail(p));

        return card;
    }

    // ── Product detail navigation ─────────────────────────────────────────────

    private void launchProductDetail(Product p) {
        Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID",       p.getId());
        intent.putExtra("PRODUCT_NAME",     p.getName());
        intent.putExtra("PRODUCT_PRICE",    p.getPrice());
        intent.putExtra("PRODUCT_SALE_PRICE", p.getSalePrice());
        intent.putExtra("PRODUCT_CATEGORY", p.getCategory());
        intent.putExtra("PRODUCT_STOCK",    p.getStockQuantity());
        intent.putExtra("PRODUCT_IMAGE",    p.getThumbnailImages());
        if (p.getGalleryImages() != null) {
            intent.putStringArrayListExtra("PRODUCT_GALLERY", new ArrayList<>(p.getGalleryImages()));
        }
        startActivity(intent);
    }

    // ── Wishlist helpers ──────────────────────────────────────────────────────

    private boolean isWishlisted(String productId) {
        if (productId == null) return false;
        try (Cursor c = requireContext().getContentResolver().query(
                WishlistContentProvider.CONTENT_URI,
                new String[]{WishlistContentProvider.COL_ID},
                WishlistContentProvider.COL_PRODUCT_ID + "=?",
                new String[]{productId},
                null)) {
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleWishlist(Product p, ImageButton btn, double displayPrice) {
        String id = p.getId();
        if (id == null) return;

        if (isWishlisted(id)) {
            requireContext().getContentResolver().delete(
                    WishlistContentProvider.CONTENT_URI,
                    WishlistContentProvider.COL_PRODUCT_ID + "=?",
                    new String[]{id});
            updateWishlistIcon(btn, false);
            Toast.makeText(requireContext(), "Removed from wishlist", Toast.LENGTH_SHORT).show();
        } else {
            ContentValues cv = new ContentValues();
            cv.put(WishlistContentProvider.COL_PRODUCT_ID, id);
            cv.put(WishlistContentProvider.COL_PRODUCT_NAME, p.getName() != null ? p.getName() : "");
            cv.put(WishlistContentProvider.COL_PRODUCT_IMAGE, p.getThumbnailImages() != null ? p.getThumbnailImages() : "");
            cv.put(WishlistContentProvider.COL_PRODUCT_PRICE, displayPrice);
            requireContext().getContentResolver().insert(WishlistContentProvider.CONTENT_URI, cv);
            updateWishlistIcon(btn, true);
            Toast.makeText(requireContext(), "Added to wishlist", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateWishlistIcon(ImageButton btn, boolean wishlisted) {
        if (btn == null) return;
        if (wishlisted) {
            btn.setImageResource(R.drawable.ic_wishlist);
            btn.setColorFilter(0xFFE23B2E);
        } else {
            btn.setImageResource(R.drawable.ic_wishlist_outline);
            btn.setColorFilter(0xFF888888);
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                requireContext().getResources().getDisplayMetrics()));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String readableConcern(String key) {
        switch (key) {
            case "acne":       return "Acne";
            case "dark_spots": return "Dark spots";
            case "pores":      return "Pores";
            case "dry_flaky":  return "Dryness";
            case "dull":       return "Dullness";
            case "excess_oil": return "Excess oil";
            case "irritation": return "Sensitivity";
            case "aging":      return "Aging";
            default:           return capitalize(key);
        }
    }
}
