package com.example.tirtir_mcommerce.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class CushionMatchAdapter extends RecyclerView.Adapter<CushionMatchAdapter.CushionViewHolder> {

    public interface Listener {
        void onAddToCart(CushionMatch item);
    }

    public static class CushionMatch {
        public final String productId;
        public final String name;
        public final String imageUrl;
        public final String shadeHex;
        public final String quality;
        public final double price;
        public final String shadeName;
        public final int matchPercent;

        public CushionMatch(String productId, String name, String imageUrl, String shadeHex, String quality, double price, String shadeName, int matchPercent) {
            this.productId = productId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.shadeHex = shadeHex;
            this.quality = quality;
            this.price = price;
            this.shadeName = shadeName;
            this.matchPercent = matchPercent;
        }
    }

    private final List<CushionMatch> items = new ArrayList<>();
    private final Listener listener;

    public CushionMatchAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<CushionMatch> matches) {
        items.clear();
        if (matches != null) items.addAll(matches);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CushionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cushion_match, parent, false);
        return new CushionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CushionViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class CushionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView name;
        private final TextView shade;
        private final TextView price;
        private final View swatch;
        private final Chip quality;

        CushionViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivCushionThumb);
            name = itemView.findViewById(R.id.tvCushionName);
            shade = itemView.findViewById(R.id.tvCushionShade);
            price = itemView.findViewById(R.id.tvCushionPrice);
            swatch = itemView.findViewById(R.id.viewShadeSwatch);
            quality = itemView.findViewById(R.id.chipMatchQuality);
            itemView.findViewById(R.id.btnAddCushionToCart).setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onAddToCart(items.get(getAdapterPosition()));
                }
            });
        }

        void bind(CushionMatch item) {
            name.setText(item.name);
            shade.setText("Shade: " + (item.shadeName != null ? item.shadeName : "—"));
            price.setText(com.example.tirtir_mcommerce.utils.PriceUtils.formatVnd(item.price));
            quality.setText(item.matchPercent + "% — " + item.quality);
            try {
                swatch.setBackgroundColor(Color.parseColor(item.shadeHex));
            } catch (Exception ignored) {
                swatch.setBackgroundResource(R.drawable.bg_shade_swatch);
            }
            Glide.with(image)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .into(image);
        }
    }
}
