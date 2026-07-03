package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.RoutineRecommendRequest;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoutineFragment extends Fragment {

    private View layoutLanding;
    private View layoutBuilder;
    private ImageView ivLandingBg;

    // Builder Views
    private LinearLayout tabMorning, tabEvening, tabExplore;
    private ImageView ivMorning, ivEvening;
    private TextView tvMorning, tvEvening;
    private TextView tvRoutineSequenceTitle, tvProductCount;
    private RecyclerView rvRoutineSteps;
    private RoutineStepAdapter adapter;

    private boolean isMorning = true;

    private View layoutSkinQuiz;
    private android.widget.ViewFlipper quizViewFlipper;
    private com.google.android.material.button.MaterialButton btnQuizNext;
    private android.widget.ProgressBar quizProgress;
    private TextView tvStepIndicator;

    private String selectedGoal = null;
    private String selectedSkinType = null;
    private int currentQuizStep = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        layoutLanding = view.findViewById(R.id.layoutRoutineLanding);
        layoutBuilder = view.findViewById(R.id.layoutRoutineBuilder);
        layoutSkinQuiz = view.findViewById(R.id.layoutSkinQuiz);
        ivLandingBg = view.findViewById(R.id.ivLandingBg);

        // Load placeholder image for landing
        Glide.with(this)
                .load("https://lh3.googleusercontent.com/aida-public/AB6AXuDrdKwlKT4sLHLyavP4mfMmiUq3OAX12VZcCeCdfU7Q93BcRRfw9LpfE8vn3NAT517w9V0vzDnH2ZyaQfdog7xtEjwBO3R3LkNnj9Frdiz6NXgMuYFmDTSxdQ5216eIfUocY-0pfbrGr98ewZJf2ln9or5tIRhaj1ZxbHEWvJDVGy9quWKse6WFwkYM2t7xFdJxJ7H1zWQemXEN-zOeIU-3mycm9w6V3XJ0riKoSWkWU2e306WF2MsIKrQp7Uzk55bq_TA8GINl52QB")
                .into(ivLandingBg);

        view.findViewById(R.id.btnGetStarted).setOnClickListener(v -> checkUserDataAndProceed());

        setupQuizUI(view);

        // Setup Builder UI
        tabMorning = view.findViewById(R.id.tabMorning);
        tabEvening = view.findViewById(R.id.tabEvening);
        tabExplore = view.findViewById(R.id.tabExplore);
        ivMorning = view.findViewById(R.id.ivMorning);
        ivEvening = view.findViewById(R.id.ivEvening);
        tvMorning = view.findViewById(R.id.tvMorning);
        tvEvening = view.findViewById(R.id.tvEvening);
        tvRoutineSequenceTitle = view.findViewById(R.id.tvRoutineSequenceTitle);
        tvProductCount = view.findViewById(R.id.tvProductCount);

        rvRoutineSteps = view.findViewById(R.id.rvRoutineSteps);
        rvRoutineSteps.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RoutineStepAdapter();
        rvRoutineSteps.setAdapter(adapter);

        view.findViewById(R.id.btnAddProductSlot).setOnClickListener(v -> {
            DiscoverProductsBottomSheet bottomSheet = new DiscoverProductsBottomSheet();
            bottomSheet.show(getChildFragmentManager(), "DiscoverProducts");
        });

        tabMorning.setOnClickListener(v -> switchTab(true));
        tabEvening.setOnClickListener(v -> switchTab(false));
    }

    private void checkUserDataAndProceed() {
        // Ideally fetch from backend to see if user has orders
        // We will trigger the `/recommend-routine` API first. If it returns based on order, we skip quiz.
        // For now, we will simulate showing the quiz for new users.
        boolean hasOrders = false; // Simulate new user

        if (hasOrders) {
            showBuilder();
        } else {
            showSkinQuiz();
        }
    }

    private void showSkinQuiz() {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(300);
        layoutLanding.startAnimation(fadeOut);
        layoutLanding.setVisibility(View.GONE);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        layoutSkinQuiz.setVisibility(View.VISIBLE);
        layoutSkinQuiz.startAnimation(fadeIn);
    }

    private void setupQuizUI(View view) {
        quizViewFlipper = view.findViewById(R.id.quizViewFlipper);
        btnQuizNext = view.findViewById(R.id.btnQuizNext);
        quizProgress = view.findViewById(R.id.quizProgress);
        tvStepIndicator = view.findViewById(R.id.tvStepIndicator);

        View btnQuizBack = view.findViewById(R.id.btnQuizBack);
        if (btnQuizBack != null) {
            btnQuizBack.setOnClickListener(v -> {
                if (currentQuizStep > 1) {
                    currentQuizStep--;
                    updateQuizUI();
                }
            });
        }

        btnQuizNext.setOnClickListener(v -> {
            if (currentQuizStep < 3) {
                currentQuizStep++;
                updateQuizUI();
            }
        });

        // Setup Option Clicks for Step 1
        setupOptionClick(view, R.id.optHydration, "Hydration", 1);
        setupOptionClick(view, R.id.optBrightening, "Brightening", 1);
        setupOptionClick(view, R.id.optAntiAging, "Anti-Aging", 1);
        setupOptionClick(view, R.id.optAcneControl, "Acne Control", 1);

        // Setup Option Clicks for Step 2
        setupOptionClick(view, R.id.optOily, "Oily", 2);
        setupOptionClick(view, R.id.optDry, "Dry", 2);
        setupOptionClick(view, R.id.optCombination, "Combination", 2);
        setupOptionClick(view, R.id.optSensitive, "Sensitive", 2);
    }

    private void setupOptionClick(View parentView, int optionId, String value, int step) {
        View optionView = parentView.findViewById(optionId);
        if (optionView != null) {
            // Set titles and descriptions based on ID
            TextView tvTitle = optionView.findViewById(R.id.quizOptionTitle);
            TextView tvDesc = optionView.findViewById(R.id.quizOptionDesc);
            ImageView ivIcon = optionView.findViewById(R.id.quizOptionIcon);

            if (tvTitle != null) tvTitle.setText(value);

            if (optionId == R.id.optHydration) {
                if (tvDesc != null) tvDesc.setText("Quench thirsty skin and restore a dewy, supple bounce.");
            } else if (optionId == R.id.optBrightening) {
                if (tvDesc != null) tvDesc.setText("Fade dark spots and unveil a luminous, even complexion.");
            } else if (optionId == R.id.optAntiAging) {
                if (tvDesc != null) tvDesc.setText("Firm and smooth the appearance of fine lines and texture.");
            } else if (optionId == R.id.optAcneControl) {
                if (tvDesc != null) tvDesc.setText("Clarify pores and calm inflammation for clearer skin.");
            } else if (optionId == R.id.optOily) {
                if (tvDesc != null) tvDesc.setText("Prone to shine and visible pores throughout the day.");
            } else if (optionId == R.id.optDry) {
                if (tvDesc != null) tvDesc.setText("Feels tight or flaky, lacking natural lipid barrier.");
            } else if (optionId == R.id.optCombination) {
                if (tvDesc != null) tvDesc.setText("Oily T-zone with dry or normal cheeks.");
            } else if (optionId == R.id.optSensitive) {
                if (tvDesc != null) tvDesc.setText("Easily irritated or prone to redness and reactivity.");
            }

            optionView.setOnClickListener(v -> {
                // Clear selection in current step
                clearSelection(parentView, step);

                // Highlight selected
                v.setBackgroundResource(R.drawable.bg_routine_guide); // Just some highlighted bg

                if (step == 1) {
                    selectedGoal = value;
                    btnQuizNext.setEnabled(true);
                } else if (step == 2) {
                    selectedSkinType = value;
                    btnQuizNext.setEnabled(true);
                }
            });
        }
    }

    private void clearSelection(View parentView, int step) {
        if (step == 1) {
            resetOption(parentView, R.id.optHydration);
            resetOption(parentView, R.id.optBrightening);
            resetOption(parentView, R.id.optAntiAging);
            resetOption(parentView, R.id.optAcneControl);
        } else if (step == 2) {
            resetOption(parentView, R.id.optOily);
            resetOption(parentView, R.id.optDry);
            resetOption(parentView, R.id.optCombination);
            resetOption(parentView, R.id.optSensitive);
        }
    }

    private void resetOption(View parentView, int optionId) {
        View optionView = parentView.findViewById(optionId);
        if (optionView != null) {
            optionView.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void updateQuizUI() {
        quizViewFlipper.setDisplayedChild(currentQuizStep - 1);
        quizProgress.setProgress(currentQuizStep);
        tvStepIndicator.setText("STEP " + currentQuizStep + " OF 3");

        View btnQuizBack = getView().findViewById(R.id.btnQuizBack);
        if (currentQuizStep == 1) {
            btnQuizBack.setVisibility(View.INVISIBLE);
            btnQuizNext.setText("NEXT");
            btnQuizNext.setEnabled(selectedGoal != null);
        } else if (currentQuizStep == 2) {
            btnQuizBack.setVisibility(View.VISIBLE);
            btnQuizNext.setText("CONTINUE");
            btnQuizNext.setEnabled(selectedSkinType != null);
        } else if (currentQuizStep == 3) {
            btnQuizBack.setVisibility(View.INVISIBLE);
            btnQuizNext.setVisibility(View.INVISIBLE);
            
            // Simulate curating delay
            new android.os.Handler().postDelayed(() -> {
                showBuilderFromQuiz();
            }, 2000);
        }
    }

    private void showBuilderFromQuiz() {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(300);
        layoutSkinQuiz.startAnimation(fadeOut);
        layoutSkinQuiz.setVisibility(View.GONE);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        layoutBuilder.setVisibility(View.VISIBLE);
        layoutBuilder.startAnimation(fadeIn);

        fetchRoutineData();
    }

    private void showBuilder() {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(300);
        layoutLanding.startAnimation(fadeOut);
        layoutLanding.setVisibility(View.GONE);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        layoutBuilder.setVisibility(View.VISIBLE);
        layoutBuilder.startAnimation(fadeIn);

        // Fetch data once shown
        fetchRoutineData();
    }

    private void switchTab(boolean morning) {
        isMorning = morning;
        if (morning) {
            tabMorning.setBackgroundResource(R.drawable.bg_routine_tab_active);
            ivMorning.setColorFilter(getResources().getColor(android.R.color.white));
            tvMorning.setTextColor(getResources().getColor(android.R.color.white));

            tabEvening.setBackgroundResource(0);
            ivEvening.setColorFilter(0xFF5F5E5E);
            tvEvening.setTextColor(0xFF5F5E5E);

            tvRoutineSequenceTitle.setText("Scientific Sequence");
        } else {
            tabEvening.setBackgroundResource(R.drawable.bg_routine_tab_active);
            ivEvening.setColorFilter(getResources().getColor(android.R.color.white));
            tvEvening.setTextColor(getResources().getColor(android.R.color.white));

            tabMorning.setBackgroundResource(0);
            ivMorning.setColorFilter(0xFF5F5E5E);
            tvMorning.setTextColor(0xFF5F5E5E);

            tvRoutineSequenceTitle.setText("Nocturnal Repair");
        }
        fetchRoutineData(); // Refresh list based on time
    }

    private void fetchRoutineData() {
        // Here we simulate fetching the user's profile to pass to recommendation
        ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        
        List<String> concerns = new ArrayList<>();
        if (selectedGoal != null) {
            concerns.add(selectedGoal);
        } else {
            concerns.add("Dryness"); // Default mock for demo
        }

        String skinType = selectedSkinType != null ? selectedSkinType : "Normal";

        RoutineRecommendRequest request = new RoutineRecommendRequest(
                skinType, "Medium", "Neutral", concerns, null, null);

        api.recommendRoutine(request).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    applyRecommendation(response.body().getData());
                } else {
                    Toast.makeText(getContext(), "Failed to fetch routine", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Log.e("Routine", "Error: " + t.getMessage());
            }
        });
    }

    private void applyRecommendation(Map<String, Object> data) {
        Object rawRoutine = data.get("routine");
        if (!(rawRoutine instanceof List)) return;

        List<RoutineStep> steps = new ArrayList<>();
        int stepNum = 1;

        for (Object rawStep : (List<?>) rawRoutine) {
            if (!(rawStep instanceof Map)) continue;
            Map<String, Object> stepMap = (Map<String, Object>) rawStep;
            String slot = String.valueOf(stepMap.getOrDefault("step", "Treatment"));
            
            // Filter by morning/evening
            if (!isMorning && (slot.toLowerCase(Locale.ENGLISH).contains("sun") || slot.toLowerCase(Locale.ENGLISH).contains("spf"))) {
                continue;
            }

            Object rawProduct = stepMap.get("product");
            if (!(rawProduct instanceof Map)) continue;
            Map<String, Object> productMap = (Map<String, Object>) rawProduct;

            Product product = new Product();
            product.setName(String.valueOf(productMap.get("Name")));
            product.setCategory(String.valueOf(productMap.get("Category")));
            
            Object thumb = productMap.get("Thumbnail_Images");
            if (thumb instanceof String) {
                product.setThumbnailImages((String) thumb);
            } else if (thumb instanceof List && !((List<?>) thumb).isEmpty()) {
                product.setThumbnailImages(String.valueOf(((List<?>) thumb).get(0)));
            }

            RoutineStep step = new RoutineStep();
            step.stepNumber = String.format(Locale.US, "STEP %02d", stepNum++);
            step.type = slot.toUpperCase();
            step.productName = product.getName();
            step.productDesc = product.getCategory();
            step.imageUrl = product.getThumbnailImages();
            step.tags = new ArrayList<>(); // You can parse real tags if available

            steps.add(step);
        }

        tvProductCount.setText(steps.size() + " PRODUCTS");
        adapter.setSteps(steps);
    }

    // ===========================
    // Models & Adapters
    // ===========================

    private static class RoutineStep {
        String stepNumber;
        String type;
        String productName;
        String productDesc;
        String imageUrl;
        List<String> tags;
    }

    private class RoutineStepAdapter extends RecyclerView.Adapter<RoutineStepAdapter.ViewHolder> {
        private List<RoutineStep> steps = new ArrayList<>();

        public void setSteps(List<RoutineStep> newSteps) {
            this.steps = newSteps;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_routine_step_new, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RoutineStep step = steps.get(position);
            holder.tvStepNumber.setText(step.stepNumber);
            holder.tvStepType.setText(step.type);
            holder.tvProductName.setText(step.productName);
            holder.tvProductDesc.setText(step.productDesc);

            Glide.with(holder.itemView.getContext())
                    .load(step.imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .into(holder.ivProductImage);

            // Handle Tags
            holder.llTags.removeAllViews();
            if (step.tags != null && !step.tags.isEmpty()) {
                for (String tag : step.tags) {
                    TextView tvTag = new TextView(holder.itemView.getContext());
                    tvTag.setText(tag);
                    tvTag.setTextSize(10);
                    tvTag.setTextColor(0xFF5A403C);
                    tvTag.setBackgroundResource(R.drawable.bg_tag);
                    tvTag.setPadding(16, 4, 16, 4);
                    
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 0, 8, 0);
                    tvTag.setLayoutParams(params);
                    
                    holder.llTags.addView(tvTag);
                }
            } else {
                // Add a default tag
                TextView tvTag = new TextView(holder.itemView.getContext());
                tvTag.setText("Hydrating");
                tvTag.setTextSize(10);
                tvTag.setTextColor(0xFF5A403C);
                tvTag.setBackgroundResource(R.drawable.bg_tag);
                tvTag.setPadding(16, 4, 16, 4);
                holder.llTags.addView(tvTag);
            }

            // Conflict warning logic example (mocked logic based on name)
            if (step.productName != null && step.productName.toLowerCase().contains("vitamin c")) {
                holder.tvBadge.setText("CONFLICT");
                holder.tvBadge.setBackgroundColor(0xFFBA1A1A); // Error red
            } else {
                holder.tvBadge.setText("PERFECT MATCH");
                holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_primary);
            }
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivProductImage;
            TextView tvStepNumber, tvStepType, tvProductName, tvProductDesc, tvBadge;
            LinearLayout llTags;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivProductImage = itemView.findViewById(R.id.ivProductImage);
                tvStepNumber = itemView.findViewById(R.id.tvStepNumber);
                tvStepType = itemView.findViewById(R.id.tvStepType);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvProductDesc = itemView.findViewById(R.id.tvProductDesc);
                tvBadge = itemView.findViewById(R.id.tvBadge);
                llTags = itemView.findViewById(R.id.llTags);
            }
        }
    }
}
