package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoutineFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewPager2 pager = view.findViewById(R.id.viewPagerRoutine);
        TabLayout tabs = view.findViewById(R.id.tabRoutine);
        pager.setAdapter(new RoutinePagerAdapter(this));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            if (position == 0) tab.setText("AM");
            else if (position == 1) tab.setText("PM");
            else tab.setText("Community");
        }).attach();
    }

    private static class RoutinePagerAdapter extends FragmentStateAdapter {
        RoutinePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 2) return RoutineCommunityPageFragment.newInstance();
            return RoutineStepsPageFragment.newInstance(position == 0);
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    public static class RoutineStepsPageFragment extends Fragment {
        private static final String ARG_MORNING = "morning";
        private RoutineStepAdapter adapter;

        static RoutineStepsPageFragment newInstance(boolean morning) {
            RoutineStepsPageFragment fragment = new RoutineStepsPageFragment();
            Bundle args = new Bundle();
            args.putBoolean(ARG_MORNING, morning);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.page_routine_steps, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            boolean isMorning = getArguments() == null || getArguments().getBoolean(ARG_MORNING, true);
            RecyclerView rvSteps = view.findViewById(R.id.rvRoutineSteps);
            rvSteps.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new RoutineStepAdapter(buildTemplate(isMorning));
            rvSteps.setAdapter(adapter);

            ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    adapter.move(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }
            });
            helper.attachToRecyclerView(rvSteps);

            TextView suggestion = view.findViewById(R.id.tvRoutineSuggestion);
            suggestion.setText(isMorning
                    ? "You are missing SPF for your AM routine"
                    : "You are missing a recovery serum for your PM routine");
            view.findViewById(R.id.btnViewRoutineSuggestion).setOnClickListener(v ->
                    Toast.makeText(getContext(), "Product API picker will open by routine slot.", Toast.LENGTH_SHORT).show());
            view.findViewById(R.id.btnAddRoutineStep).setOnClickListener(v ->
                    Toast.makeText(getContext(), "Product picker will open from Product API.", Toast.LENGTH_SHORT).show());
            view.findViewById(R.id.btnSaveShareRoutine).setOnClickListener(v ->
                    Toast.makeText(getContext(), "Routine will be saved to Firestore/API public_routines.", Toast.LENGTH_SHORT).show());
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
    }

    public static class RoutineCommunityPageFragment extends Fragment {
        static RoutineCommunityPageFragment newInstance() {
            return new RoutineCommunityPageFragment();
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.page_routine_community, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            RecyclerView rvCommunity = view.findViewById(R.id.rvCommunityRoutines);
            TextView empty = view.findViewById(R.id.tvCommunityEmpty);
            rvCommunity.setLayoutManager(new LinearLayoutManager(getContext()));
            RoutineCommunityAdapter adapter = new RoutineCommunityAdapter();
            rvCommunity.setAdapter(adapter);
            adapter.submitList(new ArrayList<>());
            empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private static class RoutineStep {
        final String slot;
        String productName = "Not selected yet";
        boolean hasWarning;

        RoutineStep(String slot) {
            this.slot = slot;
        }
    }

    private static class RoutineStepAdapter extends RecyclerView.Adapter<RoutineStepAdapter.StepViewHolder> {
        private final List<RoutineStep> steps;

        RoutineStepAdapter(List<RoutineStep> steps) {
            this.steps = steps;
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_routine_step, parent, false);
            return new StepViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
            holder.bind(steps.get(position), position);
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }

        static class StepViewHolder extends RecyclerView.ViewHolder {
            private final TextView number;
            private final TextView name;
            private final TextView slot;
            private final View warning;

            StepViewHolder(@NonNull View itemView) {
                super(itemView);
                number = itemView.findViewById(R.id.tvRoutineStepNumber);
                name = itemView.findViewById(R.id.tvRoutineProductName);
                slot = itemView.findViewById(R.id.tvRoutineSlotLabel);
                warning = itemView.findViewById(R.id.ivRoutineWarning);
            }

            void bind(RoutineStep step, int position) {
                number.setText(String.valueOf(position + 1));
                name.setText(step.productName);
                slot.setText(step.slot);
                warning.setVisibility(step.hasWarning ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static class RoutineCommunityAdapter extends RecyclerView.Adapter<RoutineCommunityAdapter.CommunityViewHolder> {
        private final List<CommunityRoutine> routines = new ArrayList<>();

        void submitList(List<CommunityRoutine> items) {
            routines.clear();
            if (items != null) routines.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CommunityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_routine_community, parent, false);
            return new CommunityViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CommunityViewHolder holder, int position) {
            holder.bind(routines.get(position));
        }

        @Override
        public int getItemCount() {
            return routines.size();
        }

        static class CommunityViewHolder extends RecyclerView.ViewHolder {
            private final TextView name;
            private final TextView meta;

            CommunityViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvCommunityRoutineName);
                meta = itemView.findViewById(R.id.tvCommunityRoutineMeta);
            }

            void bind(CommunityRoutine routine) {
                name.setText(routine.name);
                meta.setText(routine.userName + " - " + routine.stepCount + " steps");
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
