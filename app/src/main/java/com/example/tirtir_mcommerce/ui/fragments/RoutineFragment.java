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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
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
    private static final String RESULT_RECOMMENDATION_JSON = "recommendation_json";

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

        build.setOnClickListener(v -> {
            String skinType = selectedChipText(view.findViewById(R.id.chipsRoutineSkinType), "Normal");
            String skinTone = selectedChipText(view.findViewById(R.id.chipsRoutineSkinTone), "Medium");
            List<String> concerns = selectedGoals(view.findViewById(R.id.chipsRoutineGoals));
            requestRoutine(api, new RoutineRecommendRequest(skinType, skinTone, "Neutral", concerns, null, null),
                    onboarding, progress, build, pager);
        });

        progress.setVisibility(View.VISIBLE);
        api.getLatestSkinProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                Map<String, Object> profile = response.isSuccessful() && response.body() != null
                        ? response.body().getData() : null;
                if (profile == null || text(profile.get("skinTone")).isEmpty()) {
                    onboarding.setVisibility(View.VISIBLE);
                    return;
                }
                onboarding.setVisibility(View.GONE);
                RoutineRecommendRequest request = new RoutineRecommendRequest(
                        defaultText(profile.get("skinType"), "Normal"),
                        defaultText(profile.get("skinTone"), "Medium"),
                        defaultText(profile.get("undertone"), "Neutral"),
                        stringList(profile.get("concerns")), null, null);
                requestRoutine(api, request, onboarding, progress, build, pager);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                onboarding.setVisibility(View.VISIBLE);
            }
        });
    }

    private void requestRoutine(ApiService api, RoutineRecommendRequest request, View onboarding,
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
                    Toast.makeText(requireContext(), "We could not build your routine. Check your profile and retry.", Toast.LENGTH_LONG).show();
                    return;
                }
                Bundle result = new Bundle();
                result.putString(RESULT_RECOMMENDATION_JSON, new Gson().toJson(response.body().getData()));
                getChildFragmentManager().setFragmentResult(RESULT_RECOMMENDATION, result);
                onboarding.setVisibility(View.GONE);
                pager.setCurrentItem(0, false);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                build.setEnabled(true);
                Toast.makeText(requireContext(), "The routine service is unavailable. You can retry or build manually.", Toast.LENGTH_LONG).show();
                onboarding.setVisibility(View.GONE);
            }
        });
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
            adapter = new RoutineStepAdapter(new ArrayList<>(), this::removeProduct);
            list.setAdapter(adapter);
            saveButton = view.findViewById(R.id.btnSaveShareRoutine);
            getParentFragmentManager().setFragmentResultListener(
                    RESULT_RECOMMENDATION, getViewLifecycleOwner(), (key, result) ->
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
            Map<String, Object> data = new Gson().fromJson(json, Map.class);
            Object rawRoutine = data.get("routine");
            if (!(rawRoutine instanceof List)) return;
            adapter.steps.clear();
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
            autoSortRoutine();
            refreshActions();
            TextView suggestion = suggestionCard.findViewById(R.id.tvRoutineSuggestion);
            Object advice = data.get("advice");
            if (advice != null) suggestion.setText(String.valueOf(advice));
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
                });
            }, error -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> productsLoading = false);
            });
        }

        private void openProductPicker() {
            if (adapter.getItemCount() >= 8) {
                Toast.makeText(getContext(), "Max 8 products allowed per routine.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (products.isEmpty()) {
                preloadProducts();
                Toast.makeText(getContext(), "Loading product catalog. Please try again in a moment.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            List<Product> matches = new ArrayList<>(products);
            List<String> labels = new ArrayList<>();
            for (Product product : matches) {
                String category = product.getCategory() == null ? "" : " · " + product.getCategory();
                labels.add(product.getName() + category);
            }
            List<Product> finalMatches = matches;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Choose Product")
                    .setAdapter(new ArrayAdapter<>(requireContext(),
                                    android.R.layout.simple_list_item_1, labels),
                            (dialog, which) -> {
                                Product selected = finalMatches.get(which);
                                if (!isMorning && isSpf(selected)) {
                                    Toast.makeText(getContext(), "SPF should only be used in the morning.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                addProductAndSort(selected);
                            })
                    .setNegativeButton("Cancel", null)
                    .show();
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
            if (cat.isEmpty()) {
                String name = p.getName().toLowerCase();
                if (name.contains("cleanser")) return "Cleanser";
                if (name.contains("toner")) return "Toner";
                if (name.contains("serum")) return "Serum";
                if (name.contains("cream") || name.contains("moisturizer")) return "Moisturizer";
                if (name.contains("spf") || name.contains("sun")) return "SPF";
                return "Treatment";
            }
            return cat;
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
        private final OnStepClickListener listener;

        RoutineStepAdapter(List<RoutineStep> steps, OnStepClickListener listener) {
            this.steps = steps;
            this.listener = listener;
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
            holder.bind(steps.get(position), position, listener);
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

            StepViewHolder(@NonNull View itemView) {
                super(itemView);
                number = itemView.findViewById(R.id.tvRoutineStepNumber);
                name = itemView.findViewById(R.id.tvRoutineProductName);
                slot = itemView.findViewById(R.id.tvRoutineSlotLabel);
                productImage = itemView.findViewById(R.id.ivRoutineProduct);
                drag = itemView.findViewById(R.id.ivDragHandle);
                warning = itemView.findViewById(R.id.ivRoutineWarning);
            }

            void bind(RoutineStep step, int position, OnStepClickListener listener) {
                number.setText(String.valueOf(position + 1));
                slot.setText(step.slot);
                name.setText(step.product.getName());
                drag.setImageResource(android.R.drawable.ic_menu_delete);
                drag.setVisibility(View.VISIBLE);
                drag.setOnClickListener(v -> listener.onStepClick(getBindingAdapterPosition()));

                warning.setVisibility(step.hasWarning ? View.VISIBLE : View.GONE);
                if (step.hasWarning) {
                    warning.setOnClickListener(v -> {
                        Toast.makeText(itemView.getContext(), step.conflictReason, Toast.LENGTH_LONG).show();
                    });
                }

                String path = step.product.getThumbnailImages();
                String url = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(path);
                productImage.setPadding(4, 4, 4, 4);
                Glide.with(itemView).load(url).fitCenter()
                        .placeholder(R.drawable.ic_product_placeholder)
                        .error(R.drawable.ic_product_placeholder).into(productImage);
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
