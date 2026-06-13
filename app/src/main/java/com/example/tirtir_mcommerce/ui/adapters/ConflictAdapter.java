package com.example.tirtir_mcommerce.ui.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class ConflictAdapter extends RecyclerView.Adapter<ConflictAdapter.ConflictViewHolder> {

    public static class IngredientConflict {
        public final String ingredientA;
        public final String ingredientB;
        public final String reason;
        public final String severity;

        public IngredientConflict(String ingredientA, String ingredientB, String reason, String severity) {
            this.ingredientA = ingredientA;
            this.ingredientB = ingredientB;
            this.reason = reason;
            this.severity = severity;
        }
    }

    private final List<IngredientConflict> items = new ArrayList<>();

    public void submitList(List<IngredientConflict> conflicts) {
        items.clear();
        if (conflicts != null) items.addAll(conflicts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConflictViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conflict, parent, false);
        return new ConflictViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConflictViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ConflictViewHolder extends RecyclerView.ViewHolder {
        private final Chip chipSeverity;
        private final TextView tvPair;
        private final TextView tvReason;

        ConflictViewHolder(@NonNull View itemView) {
            super(itemView);
            chipSeverity = itemView.findViewById(R.id.chipSeverity);
            tvPair = itemView.findViewById(R.id.tvConflictPair);
            tvReason = itemView.findViewById(R.id.tvConflictReason);
        }

        void bind(IngredientConflict conflict) {
            chipSeverity.setText(conflict.severity);
            int color = R.color.tirtir_low;
            if ("HIGH".equalsIgnoreCase(conflict.severity)) color = R.color.tirtir_error;
            else if ("MEDIUM".equalsIgnoreCase(conflict.severity)) color = R.color.tirtir_warning;
            chipSeverity.setChipBackgroundColor(ColorStateList.valueOf(itemView.getResources().getColor(color, null)));
            tvPair.setText(conflict.ingredientA + " + " + conflict.ingredientB);
            tvReason.setText(conflict.reason);
        }
    }
}
