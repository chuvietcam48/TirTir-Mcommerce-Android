package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.RoutineStep;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter cho danh sách các bước AI Routine trong AiRoutineFragment.
 * Hỗ trợ tính năng "Skip step" và cập nhật Skin Evolution scores.
 */
public class RoutineStepAdapter extends ListAdapter<RoutineStep, RoutineStepAdapter.StepViewHolder> {

    /**
     * Callback được gọi khi user skip/unskip một bước.
     * AiRoutineFragment dùng để tính lại Skin Evolution scores.
     */
    public interface OnSkipToggleListener {
        void onSkipToggled(RoutineStep step, boolean isSkipped);
    }

    /**
     * Callback được gọi khi user nhấn "Add to Cart".
     */
    public interface OnAddToCartListener {
        void onAddToCart(RoutineStep step);
    }

    private final OnSkipToggleListener skipListener;
    private final OnAddToCartListener addToCartListener;

    private static final DiffUtil.ItemCallback<RoutineStep> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<RoutineStep>() {
                @Override
                public boolean areItemsTheSame(@NonNull RoutineStep oldItem,
                                               @NonNull RoutineStep newItem) {
                    return oldItem.getStep() == newItem.getStep();
                }

                @Override
                public boolean areContentsTheSame(@NonNull RoutineStep oldItem,
                                                   @NonNull RoutineStep newItem) {
                    return oldItem.isSkipped() == newItem.isSkipped()
                            && equals(oldItem.getProductName(), newItem.getProductName());
                }

                private boolean equals(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    public RoutineStepAdapter(OnSkipToggleListener skipListener,
                               OnAddToCartListener addToCartListener) {
        super(DIFF_CALLBACK);
        this.skipListener = skipListener;
        this.addToCartListener = addToCartListener;
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_routine_step, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public void swapItems(int fromPosition, int toPosition) {
        List<RoutineStep> currentList = new ArrayList<>(getCurrentList());
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(currentList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(currentList, i, i - 1);
            }
        }
        // Update steps to reflect new order
        for (int i = 0; i < currentList.size(); i++) {
            currentList.get(i).setStep(i + 1);
        }
        submitList(currentList);
    }

    public List<RoutineStep> getReorderedList() {
        return new ArrayList<>(getCurrentList());
    }

    class StepViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvStepNumber;
        private final TextView tvStepName;
        private final MaterialButton btnSkipStep;
        private final ImageView imgProduct;
        private final TextView tvProductName;
        private final TextView tvProductPrice;
        private final MaterialButton btnAddToCart;
        private final TextView tvHydrationBadge;
        private final TextView tvTextureBadge;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStepNumber    = itemView.findViewById(R.id.tvStepNumber);
            tvStepName      = itemView.findViewById(R.id.tvStepName);
            btnSkipStep     = itemView.findViewById(R.id.btnSkipStep);
            imgProduct      = itemView.findViewById(R.id.imgRoutineProduct);
            tvProductName   = itemView.findViewById(R.id.tvAiRoutineProductName);
            tvProductPrice  = itemView.findViewById(R.id.tvRoutineProductPrice);
            btnAddToCart    = itemView.findViewById(R.id.btnRoutineAddToCart);
            tvHydrationBadge = itemView.findViewById(R.id.tvHydrationBadge);
            tvTextureBadge  = itemView.findViewById(R.id.tvTextureBadge);
        }

        void bind(RoutineStep step) {
            // Step number và name
            tvStepNumber.setText(String.valueOf(step.getStep()));
            tvStepName.setText(step.getStepName() != null ? step.getStepName() : "Step " + step.getStep());

            // Product info
            String productName = step.getProductName();
            tvProductName.setText(productName != null ? productName : "Product TBD");

            double price = step.getDisplayPrice();
            tvProductPrice.setText(price > 0
                    ? PriceUtils.formatVnd(price)
                    : "");

            // Product image
            if (step.getImageUrl() != null && !step.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(step.getImageUrl())
                        .placeholder(R.drawable.ic_skin)
                        .into(imgProduct);
            } else {
                imgProduct.setImageResource(R.drawable.ic_skin);
            }

            // Improvement badges
            tvHydrationBadge.setText("💧 +" + step.getHydrationBoost() + "% Hydration");
            tvTextureBadge.setText("✨ +" + step.getTextureBoost() + "% Texture");

            // Skip state
            updateSkipState(step);

            // Skip toggle
            btnSkipStep.setOnClickListener(v -> {
                boolean newSkipped = !step.isSkipped();
                step.setSkipped(newSkipped);
                updateSkipState(step);
                if (skipListener != null) skipListener.onSkipToggled(step, newSkipped);
            });

            // Add to cart
            btnAddToCart.setOnClickListener(v -> {
                if (addToCartListener != null) addToCartListener.onAddToCart(step);
            });
        }

        private void updateSkipState(RoutineStep step) {
            boolean skipped = step.isSkipped();
            itemView.setAlpha(skipped ? 0.5f : 1.0f);
            btnSkipStep.setText(skipped ? "Undo" : "Skip");
            btnAddToCart.setEnabled(!skipped);
        }
    }
}
