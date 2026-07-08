package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.example.tirtir_mcommerce.network.ApiConfig;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public interface CartListener {
        void onQuantityChanged(int position, int newQuantity);
        void onRemoveItem(int position);
        void onEditVariant(int position);
    }

    private final Context context;
    private final List<CartItem> cartItems;
    private final CartListener cartListener;

    public CartAdapter(Context context, List<CartItem> cartItems, CartListener cartListener) {
        this.context = context;
        this.cartItems = cartItems;
        this.cartListener = cartListener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);
        
        double displayPrice = cartItem.getPrice() * cartItem.getQuantity();
        holder.tvPrice.setText(PriceUtils.formatPriceUsd(displayPrice));
        
        // Brand logic (hardcoded to TirTir as per request's HTML)
        TextView tvBrand = holder.itemView.findViewById(R.id.tvCartBrand);
        if (tvBrand != null) {
            tvBrand.setText("TirTir");
        }

        String variant = cartItem.getShade();
        if (holder.tvVariant != null) {
            if (variant != null && !variant.trim().isEmpty()) {
                holder.tvVariant.setText("Shade: " + variant + " ⌄");
                holder.tvVariant.setVisibility(View.VISIBLE);
                holder.tvVariant.setOnClickListener(v -> cartListener.onEditVariant(position));
            } else {
                holder.tvVariant.setVisibility(View.GONE);
            }
        }
        
        String imageUrl = ApiConfig.resolveMediaUrl(cartItem.getThumbnail());
        
        Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_product_placeholder)
                .error(R.drawable.ic_product_placeholder)
                .fitCenter()
                .into(holder.ivImage);
        
        holder.tvQuantity.setText(String.valueOf(cartItem.getQuantity()));

        holder.btnDecrease.setOnClickListener(v -> {
            int qty = cartItem.getQuantity();
            if (qty > 1) {
                cartListener.onQuantityChanged(position, qty - 1);
            } else {
                new android.app.AlertDialog.Builder(context)
                        .setTitle("Remove from cart?")
                        .setMessage("Do you want to remove this product from your cart?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            cartListener.onRemoveItem(position);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        holder.btnIncrease.setOnClickListener(v -> {
            int qty = cartItem.getQuantity();
            cartListener.onQuantityChanged(position, qty + 1);
        });

        holder.btnRemove.setOnClickListener(v -> {
            cartListener.onRemoveItem(position);
        });
    }

    @Override
    public int getItemCount() {
        return cartItems == null ? 0 : cartItems.size();
    }

    class CartViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName;
        TextView tvPrice;
        TextView tvQuantity;
        TextView tvVariant;
        View btnDecrease;
        View btnIncrease;
        View btnRemove;

        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivCartProductImage);
            tvName = itemView.findViewById(R.id.tvCartProductName);
            tvPrice = itemView.findViewById(R.id.tvCartProductPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvVariant = itemView.findViewById(R.id.tvCartVariant);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
            btnRemove = itemView.findViewById(R.id.btnRemoveCartItem);
        }
    }
}
