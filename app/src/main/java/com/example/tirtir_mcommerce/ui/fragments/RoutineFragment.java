package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.model.RoutineRecommendRequest;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.utils.RoutineConflictChecker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoutineFragment extends Fragment {
    private static final String RESULT_RECOMMENDATION = "routine_recommendation";
    private static final String RESULT_RECOMMENDATION_AM = RESULT_RECOMMENDATION + "_am";
    private static final String RESULT_RECOMMENDATION_PM = RESULT_RECOMMENDATION + "_pm";
    private static final String RESULT_RECOMMENDATION_JSON = "recommendation_json";
    private int questionnaireStep = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        com.example.tirtir_mcommerce.utils.HeaderHelper.bind(
                view, requireContext(), requireActivity().getSupportFragmentManager());
        ViewPager2 pager = view.findViewById(R.id.viewPagerRoutine);
        TabLayout tabs = view.findViewById(R.id.tabRoutine);
        pager.setAdapter(new RoutinePagerAdapter(this));
        pager.setOffscreenPageLimit(2);
        new TabLayoutMediator(tabs, pager, (tab, position) ->
                tab.setText(position == 0 ? "AM" : position == 1 ? "PM" : "Community")).attach();
        configurePersonalizedFlow(view, pager);
    }

    private void configurePersonalizedFlow(View view, ViewPager2 pager) {
        View onboarding = view.findViewById(R.id.layoutRoutineOnboarding);
        View progress = view.findViewById(R.id.progressRoutineProfile);
        MaterialButton build = view.findViewById(R.id.btnBuildRoutine);
        ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);

        setupQuestionnaire(view, api, onboarding, progress, build, pager);

        progress.setVisibility(View.VISIBLE);
        final Map<String, Object>[] savedProfile = new Map[]{null};
        final List<OrderResponse>[] orderHistory = new List[]{null};
        final boolean[] profileLoaded = {false};
        final boolean[] ordersLoaded = {false};

        Runnable resolveUserState = () -> {
            if (!profileLoaded[0] || !ordersLoaded[0] || !isAdded()) return;
            progress.setVisibility(View.GONE);
            Map<String, Object> profile = savedProfile[0];
            List<OrderResponse> orders = orderHistory[0] == null
                    ? new ArrayList<>() : orderHistory[0];
            boolean hasProfile = hasUsableProfile(profile);
            boolean hasOrders = !orders.isEmpty();

            if (hasProfile && hasOrders) {
                onboarding.setVisibility(View.GONE);
                RoutineRecommendRequest request = new RoutineRecommendRequest(
                        defaultText(profile.get("skinType"), "Normal"),
                        defaultText(profile.get("skinTone"), "Medium"),
                        defaultText(profile.get("undertone"), "Neutral"),
                        stringList(profile.get("concerns")), null, null);
                requestRoutine(api, request, purchaseNames(orders), onboarding, progress, build, pager);
            } else {
                preselectSavedProfile(view, profile);
                questionnaireStep = 0;
                updateQuestionnaireStep(view);
                onboarding.setVisibility(View.VISIBLE);
            }
        };

        api.getLatestSkinProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                Map<String, Object> data = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                savedProfile[0] = unwrapSkinProfile(data);
                profileLoaded[0] = true;
                resolveUserState.run();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                profileLoaded[0] = true;
                resolveUserState.run();
            }
        });

        api.getMyOrders().enqueue(new Callback<ApiResponse<List<OrderResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<OrderResponse>>> call, Response<ApiResponse<List<OrderResponse>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    orderHistory[0] = response.body().getData();
                } else {
                    orderHistory[0] = new ArrayList<>();
                }
                ordersLoaded[0] = true;
                resolveUserState.run();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<OrderResponse>>> call, Throwable t) {
                orderHistory[0] = new ArrayList<>();
                ordersLoaded[0] = true;
                resolveUserState.run();
            }
        });
    }

    private void setupQuestionnaire(View view, ApiService api, View onboarding, View progress,
                                    MaterialButton build, ViewPager2 pager) {
        view.findViewById(R.id.btnRoutineNext).setOnClickListener(v -> {
            questionnaireStep = Math.min(2, questionnaireStep + 1);
            updateQuestionnaireStep(view);
        });
        view.findViewById(R.id.btnRoutineBack).setOnClickListener(v -> {
            questionnaireStep = Math.max(0, questionnaireStep - 1);
            updateQuestionnaireStep(view);
        });
        build.setOnClickListener(v -> {
            String skinType = selectedChipText(view.findViewById(R.id.chipsRoutineSkinType), "Normal");
            String skinTone = selectedChipText(view.findViewById(R.id.chipsRoutineSkinTone), "Medium");
            List<String> concerns = selectedGoals(view.findViewById(R.id.chipsRoutineGoals));
            requestRoutine(api, new RoutineRecommendRequest(skinType, skinTone, "Neutral", concerns, null, null),
                    new ArrayList<>(), onboarding, progress, build, pager);
        });
        updateQuestionnaireStep(view);
    }

    private void updateQuestionnaireStep(View root) {
        root.findViewById(R.id.layoutRoutineQuestionGoals)
                .setVisibility(questionnaireStep == 0 ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.layoutRoutineQuestionType)
                .setVisibility(questionnaireStep == 1 ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.layoutRoutineQuestionTone)
                .setVisibility(questionnaireStep == 2 ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.tvRoutineQuestionStep))
                .setText("STEP " + (questionnaireStep + 1) + " OF 3");
        ((android.widget.ProgressBar) root.findViewById(R.id.progressRoutineQuestions))
                .setProgress(questionnaireStep + 1);
        TextView prompt = root.findViewById(R.id.tvRoutineQuestionPrompt);
        prompt.setText(questionnaireStep == 0 ? "What is your primary skin goal?"
                : questionnaireStep == 1 ? "How does your skin usually feel?"
                : "Which skin tone is closest to yours?");
        root.findViewById(R.id.btnRoutineBack)
                .setVisibility(questionnaireStep == 0 ? View.INVISIBLE : View.VISIBLE);
        root.findViewById(R.id.btnRoutineNext)
                .setVisibility(questionnaireStep < 2 ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.btnBuildRoutine)
                .setVisibility(questionnaireStep == 2 ? View.VISIBLE : View.GONE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapSkinProfile(Map<String, Object> data) {
        if (data == null) return null;
        Object nested = data.get("skinProfile");
        return nested instanceof Map ? (Map<String, Object>) nested : data;
    }

    private boolean hasUsableProfile(Map<String, Object> profile) {
        return profile != null && !text(profile.get("skinTone")).isEmpty()
                && !text(profile.get("skinType")).isEmpty();
    }

    private List<String> purchaseNames(List<OrderResponse> orders) {
        List<String> names = new ArrayList<>();
        for (OrderResponse order : orders) {
            if (order == null || "Cancelled".equalsIgnoreCase(order.getStatus()) || order.getItems() == null) continue;
            for (OrderResponse.OrderItemResponse item : order.getItems()) {
                if (item.getName() != null && !item.getName().trim().isEmpty()
                        && !names.contains(item.getName())) names.add(item.getName());
            }
        }
        return names;
    }

    private void preselectSavedProfile(View root, Map<String, Object> profile) {
        if (profile == null) return;
        selectChipByText(root.findViewById(R.id.chipsRoutineSkinType), text(profile.get("skinType")));
        selectChipByText(root.findViewById(R.id.chipsRoutineSkinTone), text(profile.get("skinTone")));
    }

    private void selectChipByText(ChipGroup group, String value) {
        if (group == null || value == null || value.isEmpty()) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).getText().toString().equalsIgnoreCase(value)) {
                ((Chip) child).setChecked(true);
                return;
            }
        }
    }

    private void requestRoutine(ApiService api, RoutineRecommendRequest request, List<String> purchaseNames, View onboarding,
                                View progress, MaterialButton build, ViewPager2 pager) {
        progress.setVisibility(View.VISIBLE);
        build.setEnabled(false);
        api.recommendRoutine(request).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                build.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                    publishFallbackRecommendation(request, purchaseNames, onboarding, pager);
                    return;
                }
                Map<String, Object> enriched = new HashMap<>(response.body().getData());
                enriched.put("purchasedProductNames", purchaseNames);
                enriched.put("routineMode", purchaseNames.isEmpty() ? "profile" : "returning");
                Bundle result = new Bundle();
                result.putString(RESULT_RECOMMENDATION_JSON, new Gson().toJson(enriched));
                publishRecommendationResult(result);
                onboarding.setVisibility(View.GONE);
                pager.setCurrentItem(0, false);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                build.setEnabled(true);
                publishFallbackRecommendation(request, purchaseNames, onboarding, pager);
            }
        });
    }

    private void publishFallbackRecommendation(RoutineRecommendRequest request,
                                               List<String> purchaseNames,
                                               View onboarding,
                                               ViewPager2 pager) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("routine", new ArrayList<>());
        fallback.put("skinType", request.getSkinType());
        fallback.put("concerns", request.getConcerns());
        fallback.put("purchasedProductNames", purchaseNames);
        fallback.put("routineMode", purchaseNames.isEmpty() ? "profile" : "returning");
        fallback.put("advice", "A balanced routine selected from the live TirTir catalog. You can replace, remove, or expand every step.");
        fallback.put("clientFallback", true);
        Bundle result = new Bundle();
        result.putString(RESULT_RECOMMENDATION_JSON, new Gson().toJson(fallback));
        publishRecommendationResult(result);
        onboarding.setVisibility(View.GONE);
        pager.setCurrentItem(0, false);
    }

    private void publishRecommendationResult(Bundle result) {
        requireActivity().getSupportFragmentManager()
                .setFragmentResult(RESULT_RECOMMENDATION_AM, result);
        requireActivity().getSupportFragmentManager()
                .setFragmentResult(RESULT_RECOMMENDATION_PM, result);
    }

    private String selectedChipText(ChipGroup group, String fallback) {
        if (group == null || group.getCheckedChipId() == View.NO_ID) return fallback;
        Chip chip = group.findViewById(group.getCheckedChipId());
        return chip == null ? fallback : chip.getText().toString();
    }

    private List<String> selectedGoals(ChipGroup group) {
        List<String> values = new ArrayList<>();
        if (group != null) {
            for (Integer id : group.getCheckedChipIds()) {
                Chip chip = group.findViewById(id);
                if (chip == null) continue;
                String goal = chip.getText().toString();
                values.add("Hydration".equals(goal) ? "Dryness" : goal);
            }
        }
        if (values.isEmpty()) values.add("Dryness");
        return values;
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) for (Object item : (List<?>) value) values.add(String.valueOf(item));
        return values;
    }

    private String defaultText(Object value, String fallback) {
        String result = text(value);
        return result.isEmpty() ? fallback : result;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private static class RoutinePagerAdapter extends FragmentStateAdapter {
        RoutinePagerAdapter(@NonNull Fragment fragment) { super(fragment); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == 2
                    ? RoutineCommunityPageFragment.newInstance()
                    : RoutineStepsPageFragment.newInstance(position == 0);
        }

        @Override
        public int getItemCount() { return 3; }
    }


    public static class RoutineStepsPageFragment extends Fragment {
        private static final String ARG_MORNING = "morning";
        private RoutineStepAdapter adapter;
        private MaterialButton saveButton;
        private ProductRepository productRepository;
        private final List<Product> products = new ArrayList<>();
        private boolean productsLoading;
        private boolean isMorning;
        private View suggestionCard;
        private TextView personaTitle;
        private TextView bundlePrice;
        private TextView stepCount;
        private String pendingRecommendationJson;

        static RoutineStepsPageFragment newInstance(boolean morning) {
            RoutineStepsPageFragment fragment = new RoutineStepsPageFragment();
            Bundle args = new Bundle();
            args.putBoolean(ARG_MORNING, morning);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.page_routine_steps, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            isMorning = getArguments() == null || getArguments().getBoolean(ARG_MORNING, true);
            productRepository = new ProductRepository(requireContext());
            RecyclerView list = view.findViewById(R.id.rvRoutineSteps);
            list.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new RoutineStepAdapter(new ArrayList<>(), this::removeProduct, this::replaceProduct);
            list.setAdapter(adapter);
            saveButton = view.findViewById(R.id.btnSaveShareRoutine);
            personaTitle = view.findViewById(R.id.tvRoutinePersonaTitle);
            bundlePrice = view.findViewById(R.id.tvRoutineBundlePrice);
            stepCount = view.findViewById(R.id.tvRoutineStepCount);
            requireActivity().getSupportFragmentManager().setFragmentResultListener(
                    isMorning ? RESULT_RECOMMENDATION_AM : RESULT_RECOMMENDATION_PM,
                    getViewLifecycleOwner(), (key, result) ->
                            applyRecommendation(result.getString(RESULT_RECOMMENDATION_JSON, "")));

            TextView suggestion = view.findViewById(R.id.tvRoutineSuggestion);
            suggestionCard = view.findViewById(R.id.cardRoutineSuggestion);
            suggestion.setText(isMorning
                    ? "Complete your AM protection with an SPF"
                    : "Add a targeted serum for overnight recovery");
            view.findViewById(R.id.btnViewRoutineSuggestion)
                    .setOnClickListener(v -> {
                        if (requireActivity() instanceof MainActivity) {
                            ((MainActivity) requireActivity())
                                    .openHomeWithSearch(isMorning ? "sunscreen" : "serum");
                        }
                    });
            view.findViewById(R.id.btnAddRoutineStep)
                    .setOnClickListener(v -> openProductPicker());
            saveButton.setOnClickListener(v -> saveAndShare());
            preloadProducts();
            refreshActions();
        }

        @SuppressWarnings("unchecked")
        private void applyRecommendation(String json) {
            if (json == null || json.isEmpty()) return;
            pendingRecommendationJson = json;
            if (products.isEmpty()) {
                preloadProducts();
                return;
            }
            Map<String, Object> data = new Gson().fromJson(json, Map.class);
            Object rawRoutine = data.get("routine");
            adapter.steps.clear();
            if (rawRoutine instanceof List) {
                for (Object rawStep : (List<?>) rawRoutine) {
                    if (!(rawStep instanceof Map)) continue;
                    Map<String, Object> stepMap = (Map<String, Object>) rawStep;
                    String slot = String.valueOf(stepMap.getOrDefault("step", "Treatment"));
                    if (!isMorning && (slot.toLowerCase(Locale.ENGLISH).contains("sun") || slot.toLowerCase(Locale.ENGLISH).contains("spf"))) {
                        continue;
                    }
                    Object rawProduct = stepMap.get("product");
                    if (!(rawProduct instanceof Map)) continue;
                    Map<String, Object> productMap = (Map<String, Object>) rawProduct;
                    Product product = new Product();
                    product.setId(value(productMap, "_id"));
                    product.setProductId(value(productMap, "Product_ID"));
                    product.setName(value(productMap, "Name"));
                    product.setCategory(value(productMap, "Category"));
                    product.setThumbnailImages(value(productMap, "Thumbnail_Images"));
                    Object price = productMap.get("Price");
                    if (price instanceof Number) product.setPrice(((Number) price).doubleValue());
                    RoutineStep step = new RoutineStep(slot);
                    step.product = product;
                    adapter.steps.add(step);
                }
            }
            if (adapter.steps.isEmpty()) addCatalogFallbackSteps();
            mergePurchasedPairings(data);
            autoSortRoutine();
            checkConflicts();
            refreshActions();
            TextView suggestion = suggestionCard.findViewById(R.id.tvRoutineSuggestion);
            Object advice = data.get("advice");
            if (advice != null) suggestion.setText(String.valueOf(advice));
            String skinType = String.valueOf(data.getOrDefault("skinType", "Personalized"));
            personaTitle.setText(skinType + " Skin Ritual · " + (isMorning ? "Morning" : "Evening"));
        }

        private void addCatalogFallbackSteps() {
            String[] slots = isMorning
                    ? new String[]{"Cleanser", "Toner", "Serum", "Moisturizer", "SPF"}
                    : new String[]{"Cleanser", "Toner", "Serum", "Moisturizer"};
            for (String slot : slots) {
                List<Product> matches = filterForSlot(slot);
                if (!matches.isEmpty()) addUniqueRoutineProduct(matches.get(0));
            }
            if (adapter.steps.isEmpty()) {
                for (Product product : products) {
                    if (!isRoutineProduct(product)) continue;
                    addUniqueRoutineProduct(product);
                    if (adapter.steps.size() == (isMorning ? 5 : 4)) break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void mergePurchasedPairings(Map<String, Object> data) {
            Object rawPurchases = data.get("purchasedProductNames");
            if (!(rawPurchases instanceof List)) return;
            for (Object rawName : (List<?>) rawPurchases) {
                Product purchased = findProductByName(String.valueOf(rawName));
                if (!isRoutineProduct(purchased)) continue;
                addUniqueRoutineProduct(purchased);

                String parentId = purchased.getParentId();
                if (parentId == null || parentId.trim().isEmpty()) continue;
                int companions = 0;
                for (Product candidate : products) {
                    if (candidate == purchased || candidate.getParentId() == null
                            || !parentId.equalsIgnoreCase(candidate.getParentId())
                            || !isRoutineProduct(candidate)) continue;
                    int before = adapter.steps.size();
                    addUniqueRoutineProduct(candidate);
                    if (adapter.steps.size() > before && ++companions == 2) break;
                }
            }
        }

        private Product findProductByName(String name) {
            if (name == null || name.trim().isEmpty()) return null;
            String needle = name.toLowerCase(Locale.ENGLISH).trim();
            for (Product product : products) {
                String candidate = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ENGLISH);
                if (candidate.equals(needle) || candidate.contains(needle) || needle.contains(candidate)) {
                    return product;
                }
            }
            return null;
        }

        private boolean isRoutineProduct(Product product) {
            if (product == null || product.getName() == null) return false;
            String haystack = (product.getName() + " " + (product.getCategory() == null ? "" : product.getCategory()))
                    .toLowerCase(Locale.ENGLISH);
            if (haystack.contains("gift card") || haystack.contains("duo set")) return false;
            return isMorning || !isSpf(product);
        }

        private void addUniqueRoutineProduct(Product product) {
            String id = product.getProductId() != null ? product.getProductId() : product.getId();
            for (RoutineStep step : adapter.steps) {
                String existingId = step.product.getProductId() != null
                        ? step.product.getProductId() : step.product.getId();
                if (id != null && id.equals(existingId)) return;
                if (step.product.getName() != null && product.getName() != null
                        && step.product.getName().equalsIgnoreCase(product.getName())) return;
            }
            RoutineStep step = new RoutineStep(getCategorySlot(product));
            step.product = product;
            adapter.steps.add(step);
        }

        private String value(Map<String, Object> map, String key) {
            Object value = map.get(key);
            return value == null ? "" : String.valueOf(value);
        }

        private void fetchRecommendation() {
            ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
            api.getRoutineRecommendation().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        String suggestion = (String) response.body().getData().get("suggestion");
                        if (suggestion != null && !suggestion.isEmpty()) {
                            TextView tv = suggestionCard.findViewById(R.id.tvRoutineSuggestion);
                            tv.setText(suggestion);
                            suggestionCard.setVisibility(View.VISIBLE);
                        }
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    // Ignore, fallback to default logic
                }
            });
        }

        private void preloadProducts() {
            if (productsLoading || !products.isEmpty()) return;
            productsLoading = true;
            productRepository.fetchProducts(result -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    productsLoading = false;
                    products.clear();
                    if (result != null) products.addAll(result);
                    if (pendingRecommendationJson != null && !pendingRecommendationJson.isEmpty()) {
                        String pending = pendingRecommendationJson;
                        pendingRecommendationJson = null;
                        applyRecommendation(pending);
                    }
                });
            }, error -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> productsLoading = false);
            });
        }

        private void openProductPicker() {
            openProductPicker(-1);
        }

        private void replaceProduct(int position) {
            if (position < 0 || position >= adapter.steps.size()) return;
            openProductPicker(position);
        }

        private void openProductPicker(int replacePosition) {
            if (replacePosition < 0 && adapter.getItemCount() >= 8) {
                Toast.makeText(getContext(), "Max 8 products allowed per routine.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (products.isEmpty()) {
                preloadProducts();
                Toast.makeText(getContext(), "Loading product catalog. Please try again in a moment.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            View sheet = LayoutInflater.from(requireContext())
                    .inflate(R.layout.bottom_sheet_add_to_routine, null, false);
            dialog.setContentView(sheet);
            TextView title = sheet.findViewById(R.id.tvRoutinePickerTitle);
            title.setText(replacePosition >= 0 ? "Replace Product" : "Add to Routine");
            sheet.findViewById(R.id.btnCloseRoutinePicker).setOnClickListener(v -> dialog.dismiss());

            RecyclerView picker = sheet.findViewById(R.id.rvRoutinePickerProducts);
            picker.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            List<Product> initial = replacePosition >= 0
                    ? filterForSlot(adapter.steps.get(replacePosition).slot)
                    : new ArrayList<>(products);
            RoutinePickerAdapter pickerAdapter = new RoutinePickerAdapter(initial, selected -> {
                if (!isMorning && isSpf(selected)) {
                    Toast.makeText(getContext(), "SPF should only be used in the morning.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (replacePosition >= 0) {
                    adapter.steps.get(replacePosition).product = selected;
                    autoSortRoutine();
                    checkConflicts();
                    refreshActions();
                } else {
                    addProductAndSort(selected);
                }
                dialog.dismiss();
            });
            picker.setAdapter(pickerAdapter);
            setupPickerChips(sheet.findViewById(R.id.chipsRoutinePicker), pickerAdapter);

            if (dialog.getBehavior() != null) {
                dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                dialog.getBehavior().setSkipCollapsed(true);
            }
            dialog.show();
        }

        private void setupPickerChips(ChipGroup group, RoutinePickerAdapter pickerAdapter) {
            String[] filters = {"All", "Cleanser", "Toner", "Serum", "Moisturizer", "Sunscreen"};
            for (String filter : filters) {
                Chip chip = new Chip(requireContext());
                chip.setText(filter);
                chip.setCheckable(true);
                chip.setChecked("All".equals(filter));
                chip.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) pickerAdapter.submit("All".equals(filter)
                            ? new ArrayList<>(products) : filterForSlot(filter));
                });
                group.addView(chip);
            }
        }

        private void addProductAndSort(Product p) {
            RoutineStep step = new RoutineStep(getCategorySlot(p));
            step.product = p;
            adapter.steps.add(step);
            autoSortRoutine();
            checkConflicts();
            refreshActions();
        }

        private void removeProduct(int position) {
            adapter.steps.remove(position);
            autoSortRoutine();
            checkConflicts();
            refreshActions();
        }

        private void autoSortRoutine() {
            Collections.sort(adapter.steps, (a, b) -> getSortWeight(a.slot) - getSortWeight(b.slot));
            adapter.notifyDataSetChanged();
        }

        private void checkConflicts() {
            List<Product> products = new ArrayList<>();
            for (RoutineStep step : adapter.steps) {
                step.hasWarning = false;
                step.conflictReason = null;
                products.add(step.product);
            }
            List<RoutineConflictChecker.ConflictResult> conflicts = RoutineConflictChecker.checkConflicts(requireContext(), products);
            
            if (!conflicts.isEmpty()) {
                for (RoutineConflictChecker.ConflictResult c : conflicts) {
                    for (RoutineStep step : adapter.steps) {
                        if (step.product.getName().equals(c.productA) || step.product.getName().equals(c.productB)) {
                            step.hasWarning = true;
                            step.conflictReason = c.reason;
                        }
                    }
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Ingredient Conflict Warning")
                        .setMessage("We detected conflicts in your routine:\n- " + conflicts.get(0).productA + " & " + conflicts.get(0).productB + "\nReason: " + conflicts.get(0).reason)
                        .setPositiveButton("OK", null)
                        .show();
            }
            adapter.notifyDataSetChanged();
        }

        private static final Map<String, Integer> SLOT_ORDER = new HashMap<>();
        static {
            SLOT_ORDER.put("cleanser", 1);
            SLOT_ORDER.put("toner", 2);
            SLOT_ORDER.put("essence", 3);
            SLOT_ORDER.put("serum", 4);
            SLOT_ORDER.put("eye", 5);
            SLOT_ORDER.put("moisturizer", 6);
            SLOT_ORDER.put("cream", 6);
            SLOT_ORDER.put("oil", 7);
            SLOT_ORDER.put("spf", 8);
            SLOT_ORDER.put("sun", 8);
        }

        private int getSortWeight(String slot) {
            String s = slot.toLowerCase(Locale.ENGLISH);
            for (Map.Entry<String, Integer> entry : SLOT_ORDER.entrySet()) {
                if (s.contains(entry.getKey())) return entry.getValue();
            }
            return 9;
        }

        private String getCategorySlot(Product p) {
            String cat = p.getCategory() != null ? p.getCategory() : "";
            String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ENGLISH);
            if (name.contains("cleanser") || name.contains("cleansing") || name.contains("balm")) return "Cleanser";
            if (name.contains("toner") || name.contains("tonic") || name.contains("mist")) return "Toner";
            if (name.contains("essence")) return "Essence";
            if (name.contains("serum") || name.contains("ampoule")) return "Serum";
            if (name.contains("cream") || name.contains("moisturizer") || name.contains("lotion")) return "Moisturizer";
            if (name.contains("spf") || name.contains("sun") || name.contains("uv")) return "SPF";
            return cat.isEmpty() || "skincare".equalsIgnoreCase(cat) ? "Treatment" : cat;
        }

        private boolean isSpf(Product p) {
            String slot = getCategorySlot(p).toLowerCase(Locale.ENGLISH);
            return slot.contains("spf") || slot.contains("sun");
        }

        private List<Product> filterForSlot(String slot) {
            String keyword = slot.toLowerCase(Locale.ENGLISH);
            List<Product> matches = new ArrayList<>();
            for (Product product : products) {
                String haystack = ((product.getName() == null ? "" : product.getName()) + " "
                        + (product.getCategory() == null ? "" : product.getCategory()) + " "
                        + (product.getDescriptionShort() == null ? "" : product.getDescriptionShort()))
                        .toLowerCase(Locale.ENGLISH);
                boolean match = haystack.contains(keyword);
                if ("moisturizer".equals(keyword)) {
                    match = haystack.contains("cream") || haystack.contains("moistur");
                } else if ("spf".equals(keyword)) {
                    match = haystack.contains("spf") || haystack.contains("sun");
                }
                if (match) matches.add(product);
            }
            return matches;
        }

        private void refreshActions() {
            saveButton.setEnabled(adapter.getItemCount() > 0);
            if (stepCount != null) stepCount.setText(adapter.getItemCount() + " PRODUCTS");
            if (bundlePrice != null) {
                double total = 0;
                for (RoutineStep step : adapter.steps) {
                    double price = step.product.getSalePrice() > 0
                            ? step.product.getSalePrice() : step.product.getPrice();
                    total += price;
                }
                bundlePrice.setText(com.example.tirtir_mcommerce.utils.PriceUtils.formatPriceUsd(total));
            }
        }

        private void saveAndShare() {
            if (adapter.getItemCount() == 0) return;

            ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
            Map<String, Object> req = new HashMap<>();
            req.put("isPublic", true);
            req.put("name", isMorning ? "My AM Routine" : "My PM Routine");
            req.put("description", isMorning ? "Personalized morning ritual" : "Personalized evening ritual");
            
            List<Map<String, String>> items = new ArrayList<>();
            for (RoutineStep step : adapter.steps) {
                Map<String, String> i = new HashMap<>();
                i.put("step", step.slot);
                i.put("productId", step.product.getProductId() != null && !step.product.getProductId().isEmpty()
                        ? step.product.getProductId() : step.product.getId());
                i.put("productName", step.product.getName());
                items.add(i);
            }
            req.put("steps", items);

            api.saveRoutine(req).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        scheduleReminder();
                        Map<String, Object> voucherData = response.body().getVoucher();
                        String voucher = voucherData == null ? "" : String.valueOf(voucherData.getOrDefault("code", ""));
                        new AlertDialog.Builder(requireContext())
                            .setTitle("Routine Saved!")
                            .setMessage(voucher.isEmpty()
                                    ? "Your routine is saved and available across devices."
                                    : "Your public routine earned a 5% sharing voucher: " + voucher)
                            .setPositiveButton("Done", null)
                            .show();
                    } else {
                        Toast.makeText(requireContext(), "Routine could not be saved. Please sign in and retry.", Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    Toast.makeText(requireContext(), "Routine could not be saved. Check your connection and retry.", Toast.LENGTH_LONG).show();
                }
            });
        }

        private void scheduleReminder() {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) requireContext().getSystemService(android.content.Context.ALARM_SERVICE);
            android.content.Intent intent = new android.content.Intent(requireContext(), com.example.tirtir_mcommerce.utils.AlarmReceiver.class);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                    requireContext(), 100, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 20); // 8:00 PM
            calendar.set(java.util.Calendar.MINUTE, 0);
            calendar.set(java.util.Calendar.SECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            if (alarmManager != null) {
                alarmManager.setRepeating(android.app.AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                        android.app.AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        }

    }

    public static class RoutineCommunityPageFragment extends Fragment {
        static RoutineCommunityPageFragment newInstance() { return new RoutineCommunityPageFragment(); }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.page_routine_community, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            RecyclerView list = view.findViewById(R.id.rvCommunityRoutines);
            TextView empty = view.findViewById(R.id.tvCommunityEmpty);
            list.setLayoutManager(new LinearLayoutManager(getContext()));
            RoutineCommunityAdapter adapter = new RoutineCommunityAdapter();
            list.setAdapter(adapter);

            ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
            api.getCommunityRoutines().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                       Response<ApiResponse<List<Map<String, Object>>>> response) {
                    List<CommunityRoutine> routines = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        for (Map<String, Object> item : response.body().getData()) {
                            Object rawSteps = item.get("steps");
                            int count = rawSteps instanceof List ? ((List<?>) rawSteps).size() : number(item.get("stepCount"));
                            routines.add(new CommunityRoutine(
                                    value(item, "id", "_id"),
                                    value(item, "name", "title"),
                                    value(item, "userName", "authorName"),
                                    count));
                        }
                    }
                    adapter.submitList(routines);
                    empty.setText(routines.isEmpty()
                            ? "No public routines are available yet. Save a four-step routine to start the community."
                            : "");
                    empty.setVisibility(routines.isEmpty() ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                    empty.setText("Community routines could not be loaded. Pull back and retry when connected.");
                    empty.setVisibility(View.VISIBLE);
                }
            });
        }

        private int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
        private String value(Map<String, Object> map, String first, String second) {
            Object value = map.get(first);
            if (value == null || String.valueOf(value).isEmpty()) value = map.get(second);
            return value == null ? "" : String.valueOf(value);
        }
    }

    private interface OnStepClickListener { void onStepClick(int position); }
    private interface OnRoutineProductSelected { void onSelected(Product product); }

    private static class RoutineStep {
        final String slot;
        Product product;
        boolean hasWarning;
        String conflictReason;

        RoutineStep(String slot) { this.slot = slot; }
    }

    private static class SavedRoutineStep {
        final String slot;
        final String productId;
        final String productName;

        SavedRoutineStep(String slot, String productId, String productName) {
            this.slot = slot;
            this.productId = productId;
            this.productName = productName;
        }
    }

    private static class RoutineStepAdapter extends RecyclerView.Adapter<RoutineStepAdapter.StepViewHolder> {
        private final List<RoutineStep> steps;
        private final OnStepClickListener removeListener;
        private final OnStepClickListener replaceListener;

        RoutineStepAdapter(List<RoutineStep> steps, OnStepClickListener removeListener,
                           OnStepClickListener replaceListener) {
            this.steps = steps;
            this.removeListener = removeListener;
            this.replaceListener = replaceListener;
        }

        RoutineStep get(int position) { return steps.get(position); }

        void selectProduct(int position, Product product) {
            steps.get(position).product = product;
            notifyItemChanged(position);
        }

        @NonNull
        @Override
        public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new StepViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_routine_step, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
            holder.bind(steps.get(position), position, removeListener, replaceListener);
        }

        @Override
        public int getItemCount() { return steps.size(); }

        static class StepViewHolder extends RecyclerView.ViewHolder {
            private final TextView number;
            private final TextView name;
            private final TextView slot;
            private final ImageView productImage;
            private final ImageView drag;
            private final View warning;
            private final TextView replace;

            StepViewHolder(@NonNull View itemView) {
                super(itemView);
                number = itemView.findViewById(R.id.tvRoutineStepNumber);
                name = itemView.findViewById(R.id.tvRoutineProductName);
                slot = itemView.findViewById(R.id.tvRoutineSlotLabel);
                productImage = itemView.findViewById(R.id.ivRoutineProduct);
                drag = itemView.findViewById(R.id.ivDragHandle);
                warning = itemView.findViewById(R.id.ivRoutineWarning);
                replace = itemView.findViewById(R.id.tvRoutineReplace);
            }

            void bind(RoutineStep step, int position, OnStepClickListener removeListener,
                      OnStepClickListener replaceListener) {
                number.setText(String.valueOf(position + 1));
                slot.setText(step.slot);
                name.setText(step.product.getName());
                drag.setImageResource(android.R.drawable.ic_menu_delete);
                drag.setVisibility(View.VISIBLE);
                drag.setOnClickListener(v -> removeListener.onStepClick(getBindingAdapterPosition()));
                replace.setOnClickListener(v -> replaceListener.onStepClick(getBindingAdapterPosition()));

                warning.setVisibility(step.hasWarning ? View.VISIBLE : View.GONE);
                if (step.hasWarning) {
                    warning.setOnClickListener(v -> {
                        Toast.makeText(itemView.getContext(), step.conflictReason, Toast.LENGTH_LONG).show();
                    });
                }

                productImage.setPadding(4, 4, 4, 4);

                // Smart fallback: use category-appropriate drawable (same logic as ProductAdapter)
                String pName = step.product.getName() == null ? "" : step.product.getName().toLowerCase(java.util.Locale.ENGLISH);
                String pCat  = step.product.getCategory() == null ? "" : step.product.getCategory().toLowerCase(java.util.Locale.ENGLISH);
                String pSlot = step.slot == null ? "" : step.slot.toLowerCase(java.util.Locale.ENGLISH);
                int fallback;
                Object imageSource = null;
                
                if (pName.contains("gift card") || pCat.contains("gift card")) {
                    fallback = R.drawable.giftcard;
                    imageSource = R.drawable.giftcard;
                } else if (pName.contains("matcha calming cream")) {
                    fallback = R.drawable.matcha_cream;
                    imageSource = R.drawable.matcha_cream;
                } else if (pName.contains("matcha")) {
                    fallback = R.drawable.tirtir_matcha_set;
                } else if (pName.contains("hydro uv shield sunscreen") || pName.contains("hydro uv") || pName.contains("sunscreen")
                        || pSlot.contains("sunscreen") || pCat.contains("sunscreen") || pName.contains("uv shield") || pName.contains("spf")) {
                    fallback = R.drawable.hydrosuncreen;
                    imageSource = R.drawable.hydrosuncreen;
                } else if (pSlot.contains("serum") || pName.contains("serum") || pName.contains("ampoule") || pCat.contains("serum")) {
                    fallback = R.drawable.ic_category_serum;
                } else if (pSlot.contains("toner") || pName.contains("toner") || pCat.contains("toner")) {
                    fallback = R.drawable.ic_category_toner;
                } else if (pSlot.contains("cream") || pSlot.contains("moisturizer") || pName.contains("cream") || pName.contains("moisturizer") || pCat.contains("cream")) {
                    fallback = R.drawable.ic_category_cream;
                } else if (pSlot.contains("cleanser") || pName.contains("cleanser") || pName.contains("wash") || pCat.contains("cleanser")) {
                    fallback = R.drawable.ic_category_cleanser;
                } else {
                    fallback = R.drawable.ic_product_placeholder;
                }

                if (imageSource == null) {
                    // Try to find product in DB first to get correct URLs
                    Product dbProd = null;
                    try {
                        String id = step.product.getProductId() != null ? step.product.getProductId() : step.product.getId();
                        dbProd = com.example.tirtir_mcommerce.database.DatabaseHelper.getInstance(itemView.getContext()).getProductByIdOrName(id);
                        if (dbProd == null) {
                            dbProd = com.example.tirtir_mcommerce.database.DatabaseHelper.getInstance(itemView.getContext()).getProductByIdOrName(step.product.getName());
                        }
                    } catch (Exception ignored) {}
                    
                    String path = "";
                    if (dbProd != null) {
                        path = dbProd.getThumbnailImages();
                        if (path == null || path.isEmpty()) {
                            if (dbProd.getGalleryImages() != null && !dbProd.getGalleryImages().isEmpty()) {
                                path = dbProd.getGalleryImages().get(0);
                            }
                        }
                    }
                    if (path == null || path.isEmpty()) {
                        path = step.product.getThumbnailImages();
                        if (path == null || path.isEmpty()) {
                            if (step.product.getGalleryImages() != null && !step.product.getGalleryImages().isEmpty()) {
                                path = step.product.getGalleryImages().get(0);
                            }
                        }
                    }
                    String url = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(path);
                    imageSource = (url != null && !url.isEmpty()) ? url : null;
                }

                Glide.with(itemView).load(imageSource).fitCenter()
                        .placeholder(fallback)
                        .error(fallback).into(productImage);
            }
        }
    }

    private static class RoutinePickerAdapter
            extends RecyclerView.Adapter<RoutinePickerAdapter.PickerViewHolder> {
        private final List<Product> items = new ArrayList<>();
        private final OnRoutineProductSelected listener;

        RoutinePickerAdapter(List<Product> initial, OnRoutineProductSelected listener) {
            this.listener = listener;
            submit(initial);
        }

        void submit(List<Product> products) {
            items.clear();
            if (products != null) items.addAll(products);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PickerViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_routine_picker_product, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PickerViewHolder holder, int position) {
            Product product = items.get(position);
            holder.name.setText(product.getName() == null ? "TirTir product" : product.getName());
            holder.category.setText(product.getCategory() == null ? "Skincare" : product.getCategory());
            
            Object imageSource = null;
            int fallback = R.drawable.ic_product_placeholder;
            String nameLower = (product.getName() == null ? "" : product.getName()).toLowerCase(java.util.Locale.ENGLISH);
            String catLower = (product.getCategory() == null ? "" : product.getCategory()).toLowerCase(java.util.Locale.ENGLISH);
            
            if (nameLower.contains("gift card") || catLower.contains("gift card")) {
                imageSource = R.drawable.giftcard;
                fallback = R.drawable.giftcard;
            } else if (nameLower.contains("matcha calming cream")) {
                imageSource = R.drawable.matcha_cream;
                fallback = R.drawable.matcha_cream;
            } else if (nameLower.contains("matcha")) {
                fallback = R.drawable.tirtir_matcha_set;
            }
            
            if (imageSource == null) {
                String path = product.getThumbnailImages();
                if (path == null || path.isEmpty()) {
                    if (product.getGalleryImages() != null && !product.getGalleryImages().isEmpty()) {
                        path = product.getGalleryImages().get(0);
                    }
                }
                String resolvedUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(path);
                imageSource = (resolvedUrl != null && !resolvedUrl.isEmpty()) ? resolvedUrl : null;
            }
            
            Glide.with(holder.itemView)
                    .load(imageSource)
                    .centerCrop()
                    .placeholder(fallback)
                    .error(fallback)
                    .into(holder.image);
            View.OnClickListener select = v -> {
                if (listener != null) listener.onSelected(product);
            };
            holder.add.setOnClickListener(select);
            holder.itemView.setOnClickListener(select);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class PickerViewHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView name;
            final TextView category;
            final View add;

            PickerViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.ivRoutinePickerProduct);
                name = itemView.findViewById(R.id.tvRoutinePickerName);
                category = itemView.findViewById(R.id.tvRoutinePickerCategory);
                add = itemView.findViewById(R.id.btnRoutinePickerAdd);
            }
        }
    }

    private static class RoutineCommunityAdapter
            extends RecyclerView.Adapter<RoutineCommunityAdapter.CommunityViewHolder> {
        private final List<CommunityRoutine> routines = new ArrayList<>();

        void submitList(List<CommunityRoutine> items) {
            routines.clear();
            if (items != null) routines.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CommunityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new CommunityViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_routine_community, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull CommunityViewHolder holder, int position) {
            CommunityRoutine routine = routines.get(position);
            holder.name.setText(routine.name);
            holder.meta.setText(routine.userName + " - " + routine.stepCount + " steps");

            if (holder.btnApplyRoutine != null) {
                holder.btnApplyRoutine.setOnClickListener(v -> {
                    new AlertDialog.Builder(holder.itemView.getContext())
                            .setTitle("Apply this routine?")
                            .setMessage("Your current active routine will be replaced. You can edit the copied steps afterward.")
                            .setPositiveButton("Apply", (dialog, which) -> {
                                ApiService api = RetrofitClient.getAuthClient(holder.itemView.getContext()).create(ApiService.class);
                                api.applyRoutine(routine.id).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                                    @Override
                                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                                           Response<ApiResponse<Map<String, Object>>> response) {
                                        Toast.makeText(holder.itemView.getContext(),
                                                response.isSuccessful() ? "Routine applied" : "Could not apply routine",
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                                        Toast.makeText(holder.itemView.getContext(), "Could not apply routine", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }
        }

        @Override
        public int getItemCount() { return routines.size(); }

        static class CommunityViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView meta;
            final MaterialButton btnApplyRoutine;

            CommunityViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvCommunityRoutineName);
                meta = itemView.findViewById(R.id.tvCommunityRoutineMeta);
                btnApplyRoutine = itemView.findViewById(R.id.btnApplyRoutine);
            }
        }
    }

    private static class CommunityRoutine {
        final String id;
        final String name;
        final String userName;
        final int stepCount;

        CommunityRoutine(String id, String name, String userName, int stepCount) {
            this.id = id;
            this.name = name;
            this.userName = userName;
            this.stepCount = stepCount;
        }
    }
}
