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
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.utils.RoutineConflictChecker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewPager2 pager = view.findViewById(R.id.viewPagerRoutine);
        TabLayout tabs = view.findViewById(R.id.tabRoutine);
        pager.setAdapter(new RoutinePagerAdapter(this));
        pager.setOffscreenPageLimit(2);
        new TabLayoutMediator(tabs, pager, (tab, position) ->
                tab.setText(position == 0 ? "AM" : position == 1 ? "PM" : "Community")).attach();
    }

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
            fetchRecommendation();
        }
            preloadProducts();
            refreshActions();
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

            RoutineStep step = adapter.get(stepPosition);
            List<Product> matches = filterForSlot(step.slot);
            if (matches.isEmpty()) matches = new ArrayList<>(products);
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

        private int getSortWeight(String slot) {
            String s = slot.toLowerCase(Locale.ENGLISH);
            if (s.contains("cleanser")) return 1;
            if (s.contains("toner")) return 2;
            if (s.contains("essence")) return 3;
            if (s.contains("serum")) return 4;
            if (s.contains("eye")) return 5;
            if (s.contains("moisturizer") || s.contains("cream")) return 6;
            if (s.contains("oil")) return 7;
            if (s.contains("spf") || s.contains("sun")) return 8;
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

            scheduleReminder();

            // Gamification API Call
            ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
            Map<String, Object> req = new HashMap<>();
            req.put("isMorning", isMorning);
            
            List<Map<String, String>> items = new ArrayList<>();
            for (RoutineStep step : adapter.steps) {
                Map<String, String> i = new HashMap<>();
                i.put("productId", step.product.getId());
                i.put("name", step.product.getName());
                items.add(i);
            }
            req.put("items", items);

            api.saveRoutine(req).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String voucher = (String) response.body().getData().get("voucher");
                        new AlertDialog.Builder(requireContext())
                            .setTitle("Routine Saved!")
                            .setMessage("Great job maintaining your skincare! You earned a 5% discount voucher: " + voucher)
                            .setPositiveButton("Awesome", (d, w) -> shareToCommunity(items))
                            .show();
                    } else {
                        shareToCommunity(items); // fallback if no gamification
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    shareToCommunity(items);
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

        private void shareToCommunity(List<Map<String, String>> items) {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anonymous";
            Map<String, Object> routineData = new HashMap<>();
            routineData.put("name", isMorning ? "My AM Routine" : "My PM Routine");
            routineData.put("userName", "BeautyLover");
            routineData.put("stepCount", items.size());
            routineData.put("createdAt", System.currentTimeMillis());

            FirebaseFirestore.getInstance().collection("public_routines").add(routineData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(requireContext(), "Shared to Community Feed!", Toast.LENGTH_SHORT).show();
                });
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

            FirebaseFirestore.getInstance().collection("public_routines")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    List<CommunityRoutine> routines = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        routines.add(new CommunityRoutine(
                            doc.getString("name"),
                            doc.getString("userName"),
                            doc.getLong("stepCount") != null ? doc.getLong("stepCount").intValue() : 0
                        ));
                    }
                    adapter.submitList(routines);
                    empty.setVisibility(routines.isEmpty() ? View.VISIBLE : View.GONE);
                });
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
            private final View drag;
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
                drag.setImageResource(R.drawable.ic_trash); // repurpose drag icon as delete
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
        }

        @Override
        public int getItemCount() { return routines.size(); }

        static class CommunityViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView meta;

            CommunityViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvCommunityRoutineName);
                meta = itemView.findViewById(R.id.tvCommunityRoutineMeta);
            }
        }
    }

    private static class CommunityRoutine {
        final String name;
        final String userName;
        final int stepCount;

        CommunityRoutine(String name, String userName, int stepCount) {
            this.name = name;
            this.userName = userName;
            this.stepCount = stepCount;
        }
    }
}
