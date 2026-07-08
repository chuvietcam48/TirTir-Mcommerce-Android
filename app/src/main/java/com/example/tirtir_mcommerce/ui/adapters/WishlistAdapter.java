package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestOptions;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.WishlistItem;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.ArrayList;
import java.util.List;

public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.WishlistViewHolder> {

    private final Context context;
    private List<WishlistItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onRemoveClick(WishlistItem item, int position);
        void onAddToCartClick(WishlistItem item);
        void onProductClick(WishlistItem item);
    }

    public WishlistAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<WishlistItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public WishlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_wishlist, parent, false);
        return new WishlistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WishlistViewHolder holder, int position) {
        WishlistItem item = items.get(position);
        holder.tvProductName.setText(item.getProductName());
        holder.tvProductSubtitle.setText(item.getSubtitle());
        holder.tvProductPrice.setText(PriceUtils.formatPriceUsd(item.getPrice()));

        Glide.with(context)
                .load(item.getThumbnail())
                .apply(new RequestOptions().transform(new CenterCrop()))
                .placeholder(R.drawable.ic_product_placeholder)
                .into(holder.ivProductImage);

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveClick(item, position);
            }
        });

        holder.btnAddToCart.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddToCartClick(item);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WishlistViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProductImage;
        ImageButton btnRemove;
        TextView tvProductName;
        TextView tvProductSubtitle;
        TextView tvProductPrice;
        ImageButton btnAddToCart;

        public WishlistViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductImage = itemView.findViewById(R.id.ivProductImage);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductSubtitle = itemView.findViewById(R.id.tvProductSubtitle);
            tvProductPrice = itemView.findViewById(R.id.tvProductPrice);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
        }
    }
}
