package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
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
import com.example.tirtir_mcommerce.model.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class AdminProductAdapter extends RecyclerView.Adapter<AdminProductAdapter.ViewHolder> {

    private final Context context;
    private final List<Product> productList;
    private final OnAdminProductActionListener listener;
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public interface OnAdminProductActionListener {
        void onEdit(Product product);
    }

    public AdminProductAdapter(Context context, List<Product> productList, OnAdminProductActionListener listener) {
        this.context = context;
        this.productList = productList;
        this.listener = listener;
    }

    public void updateData(List<Product> newProducts) {
        this.productList.clear();
        this.productList.addAll(newProducts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);
        
        holder.tvName.setText(product.getName());
        holder.tvPrice.setText(currencyFormat.format(product.getPrice()) + " đ");
        
        String meta = (product.getCategory() != null ? product.getCategory() : "Uncategorized") + 
                      " • SKU: " + (product.getProductId() != null ? product.getProductId() : product.getId());
        holder.tvMeta.setText(meta);
        
        int stock = product.getStockQuantity();
        holder.tvStockCount.setText(stock + " in stock");
        
        if (stock <= 20) {
            holder.tvStatusBadge.setText("LOW STOCK");
            holder.tvStatusBadge.setTextColor(Color.parseColor("#b45309")); // amber-700
            holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#fffbeb")); // amber-50
            holder.tvStockCount.setTextColor(Color.parseColor("#b45309"));
            holder.tvStockCount.setBackgroundColor(Color.parseColor("#fffbeb"));
        } else {
            holder.tvStatusBadge.setText("IN STOCK");
            holder.tvStatusBadge.setTextColor(Color.parseColor("#15803d")); // green-700
            holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#f0fdf4")); // green-50
            holder.tvStockCount.setTextColor(Color.parseColor("#666666"));
            holder.tvStockCount.setBackgroundResource(R.drawable.bg_rounded_border);
        }

        // Load image
        String imageUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(product.getThumbnailImages());
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(context).load(imageUrl).fitCenter()
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .into(holder.ivThumb);
        } else {
            holder.ivThumb.setImageResource(R.drawable.ic_product_placeholder); // default
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(product);
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvStatusBadge, tvStockCount, tvName, tvMeta, tvPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivAdminProductThumb);
            tvStatusBadge = itemView.findViewById(R.id.tvAdminProductStatusBadge);
            tvStockCount = itemView.findViewById(R.id.tvAdminProductStockCount);
            tvName = itemView.findViewById(R.id.tvAdminProductName);
            tvMeta = itemView.findViewById(R.id.tvAdminProductMeta);
            tvPrice = itemView.findViewById(R.id.tvAdminProductPrice);
        }
    }
}
