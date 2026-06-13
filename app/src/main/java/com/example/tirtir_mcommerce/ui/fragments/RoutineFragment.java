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
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            adapter = new RoutineStepAdapter(buildTemplate(isMorning), this::openProductPicker);
            list.setAdapter(adapter);
            saveButton = view.findViewById(R.id.btnSaveShareRoutine);

            ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder) {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION || adapter.get(position).product == null) {
                        return makeMovementFlags(0, 0);
                    }
                    return super.getMovementFlags(recyclerView, viewHolder);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView,
                                      @NonNull RecyclerView.ViewHolder viewHolder,
                                      @NonNull RecyclerView.ViewHolder target) {
                    adapter.move(viewHolder.getBindingAdapterPosition(),
                            target.getBindingAdapterPosition());
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            });
            helper.attachToRecyclerView(list);

            TextView suggestion = view.findViewById(R.id.tvRoutineSuggestion);
            suggestion.setText(isMorning
                    ? "Complete your AM protection with an SPF"
                    : "Add a targeted serum for overnight recovery");
            view.findViewById(R.id.btnViewRoutineSuggestion)
                    .setOnClickListener(v -> openProductPicker(findSuggestedPosition()));
            view.findViewById(R.id.btnAddRoutineStep)
                    .setOnClickListener(v -> openProductPicker(findFirstEmptyPosition()));
            saveButton.setOnClickListener(v -> saveAndShare());
            preloadProducts();
            refreshActions();
        }

        private List<RoutineStep> buildTemplate(boolean morning) {
            List<RoutineStep> steps = new ArrayList<>();
            steps.add(new RoutineStep("Cleanser"));
            steps.add(new RoutineStep("Toner"));
            steps.add(new RoutineStep("Serum"));
            steps.add(new RoutineStep("Moisturizer"));
            if (morning) steps.add(new RoutineStep("SPF"));
            return steps;
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

        private void openProductPicker(int stepPosition) {
            if (stepPosition < 0 || stepPosition >= adapter.getItemCount()) return;
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
                    .setTitle("Choose " + step.slot)
                    .setAdapter(new ArrayAdapter<>(requireContext(),
                                    android.R.layout.simple_list_item_1, labels),
                            (dialog, which) -> {
                                adapter.selectProduct(stepPosition, finalMatches.get(which));
                                refreshActions();
                            })
                    .setNegativeButton("Cancel", null)
                    .show();
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

        private int findFirstEmptyPosition() {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.get(i).product == null) return i;
            }
            return 0;
        }

        private int findSuggestedPosition() {
            String target = isMorning ? "SPF" : "Serum";
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (target.equals(adapter.get(i).slot)) return i;
            }
            return findFirstEmptyPosition();
        }

        private void refreshActions() {
            saveButton.setEnabled(adapter.selectedCount() > 0);
        }

        private void saveAndShare() {
            List<SavedRoutineStep> selected = new ArrayList<>();
            StringBuilder share = new StringBuilder(isMorning ? "My TirTir AM routine" : "My TirTir PM routine");
            for (RoutineStep step : adapter.steps) {
                if (step.product == null) continue;
                String productId = step.product.getProductId() != null
                        ? step.product.getProductId() : step.product.getId();
                selected.add(new SavedRoutineStep(step.slot, productId, step.product.getName()));
                share.append("\n").append(selected.size()).append(". ")
                        .append(step.slot).append(": ").append(step.product.getName());
            }
            String key = isMorning ? "routine_am" : "routine_pm";
            requireContext().getSharedPreferences("tirtir_routines", 0)
                    .edit().putString(key, new Gson().toJson(selected)).apply();

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, share.toString());
            startActivity(Intent.createChooser(intent, "Share routine"));
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
            adapter.submitList(new ArrayList<>());
            empty.setVisibility(View.VISIBLE);
        }
    }

    private interface OnStepClickListener { void onStepClick(int position); }

    private static class RoutineStep {
        final String slot;
        Product product;
        boolean hasWarning;

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

        int selectedCount() {
            int count = 0;
            for (RoutineStep step : steps) if (step.product != null) count++;
            return count;
        }

        void move(int from, int to) {
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return;
            Collections.swap(steps, from, to);
            notifyItemMoved(from, to);
            notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1);
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
                boolean selected = step.product != null;
                name.setText(selected ? step.product.getName() : "Add product");
                drag.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                warning.setVisibility(step.hasWarning ? View.VISIBLE : View.GONE);
                if (selected) {
                    String path = step.product.getThumbnailImages();
                    String url = path == null ? null : path.startsWith("http")
                            ? path
                            : "https://tirtir-project.onrender.com/" + path.replaceFirst("^/", "");
                    productImage.setPadding(4, 4, 4, 4);
                    Glide.with(itemView).load(url).fitCenter()
                            .placeholder(R.drawable.ic_product_placeholder)
                            .error(R.drawable.ic_product_placeholder).into(productImage);
                } else {
                    productImage.setImageResource(R.drawable.ic_plus);
                    productImage.setPadding(14, 14, 14, 14);
                }
                itemView.setOnClickListener(v -> listener.onStepClick(getBindingAdapterPosition()));
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
