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

    public interface OnAddToCartListener {
        void onAddToCart(RoutineStep step);
    }

    public interface OnProductClickListener {
        void onProductClick(RoutineStep step);
    }

    private final OnSkipToggleListener skipListener;
    private final OnAddToCartListener addToCartListener;
    private final OnProductClickListener productClickListener;

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
                               OnAddToCartListener addToCartListener,
                               OnProductClickListener productClickListener) {
        super(DIFF_CALLBACK);
        this.skipListener = skipListener;
        this.addToCartListener = addToCartListener;
        this.productClickListener = productClickListener;
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
        private final TextView tvOriginalPrice;
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
            tvOriginalPrice = itemView.findViewById(R.id.tvRoutineOriginalPrice);
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
            double originalPrice = step.getPrice();
            
            if (originalPrice > price) {
                if (tvOriginalPrice != null) {
                    tvOriginalPrice.setVisibility(View.VISIBLE);
                    tvOriginalPrice.setText(PriceUtils.formatPriceUsd(originalPrice));
                    tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                }
            } else {
                if (tvOriginalPrice != null) {
                    tvOriginalPrice.setVisibility(View.GONE);
                }
            }
            
            tvProductPrice.setText(price > 0
                    ? PriceUtils.formatPriceUsd(price)
                    : "");

            // Product image
            Object imageSource = null;
            int fallbackDrawable = R.drawable.ic_product_placeholder;
            String stepNameLower = step.getStepName() != null ? step.getStepName().toLowerCase(java.util.Locale.ENGLISH) : "";
            String prodNameLower = step.getProductName() != null ? step.getProductName().toLowerCase(java.util.Locale.ENGLISH) : "";
            
            if (prodNameLower.contains("gift card")) {
                fallbackDrawable = R.drawable.giftcard;
                imageSource = R.drawable.giftcard;
            } else if (prodNameLower.contains("matcha calming cream")) {
                fallbackDrawable = R.drawable.matcha_cream;
                imageSource = R.drawable.matcha_cream;
            } else if (prodNameLower.contains("matcha")) {
                fallbackDrawable = R.drawable.tirtir_matcha_set;
            } else if (prodNameLower.contains("hydro uv shield sunscreen") || prodNameLower.contains("hydro uv") || prodNameLower.contains("sunscreen")
                    || stepNameLower.contains("sunscreen") || prodNameLower.contains("uv shield")) {
                fallbackDrawable = R.drawable.hydrosuncreen;
                imageSource = R.drawable.hydrosuncreen;
            } else if (stepNameLower.contains("cleanser") || prodNameLower.contains("cleanser") || prodNameLower.contains("wash")) {
                fallbackDrawable = R.drawable.ic_category_cleanser;
            } else if (stepNameLower.contains("serum") || prodNameLower.contains("serum") || prodNameLower.contains("ampoule")) {
                fallbackDrawable = R.drawable.ic_category_serum;
            } else if (stepNameLower.contains("toner") || prodNameLower.contains("toner") || prodNameLower.contains("skin")) {
                fallbackDrawable = R.drawable.ic_category_toner;
            } else if (stepNameLower.contains("cream") || stepNameLower.contains("moisturizer") || prodNameLower.contains("cream") || prodNameLower.contains("moisturizer")) {
                fallbackDrawable = R.drawable.ic_category_cream;
            }

            if (imageSource == null) {
                // Try to find the product in DB to get the actual product image URL
                com.example.tirtir_mcommerce.model.Product dbProd = null;
                try {
                    dbProd = com.example.tirtir_mcommerce.database.DatabaseHelper.getInstance(itemView.getContext())
                            .getProductByIdOrName(step.getProductId());
                    if (dbProd == null) {
                        dbProd = com.example.tirtir_mcommerce.database.DatabaseHelper.getInstance(itemView.getContext())
                                .getProductByIdOrName(step.getProductName());
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
                    path = step.getImageUrl();
                }
                
                String resolvedUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(path);
                imageSource = (resolvedUrl != null && !resolvedUrl.isEmpty()) ? resolvedUrl : null;
            }

            Glide.with(itemView.getContext())
                    .load(imageSource)
                    .placeholder(fallbackDrawable)
                    .error(fallbackDrawable)
                    .centerCrop()
                    .into(imgProduct);

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

            // Product click
            itemView.setOnClickListener(v -> {
                if (productClickListener != null) productClickListener.onProductClick(step);
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
