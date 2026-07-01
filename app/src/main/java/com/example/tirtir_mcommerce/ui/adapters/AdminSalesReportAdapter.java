package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminSalesReportAdapter extends RecyclerView.Adapter<AdminSalesReportAdapter.ViewHolder> {
    private List<Map<String, Object>> products = new ArrayList<>();
    private final NumberFormat currency = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public void setProducts(List<Map<String, Object>> products) {
        this.products = products;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_sales_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> item = products.get(position);
        holder.tvRank.setText(String.valueOf(position + 1));
        
        Object productRaw = item.get("product");
        if (productRaw instanceof Map) {
            Map<String, Object> product = (Map<String, Object>) productRaw;
            String name = String.valueOf(product.getOrDefault("name", "Product"));
            holder.tvProductName.setText(name);
            
            Object mainImage = product.get("mainImage");
            if (mainImage instanceof String) {
                Glide.with(holder.itemView.getContext())
                        .load(mainImage)
                        .placeholder(R.drawable.ic_product_placeholder)
                        .error(R.drawable.ic_product_placeholder)
                        .into(holder.ivProductImage);
            } else {
                holder.ivProductImage.setImageResource(R.drawable.ic_product_placeholder);
            }
        }
        
        holder.tvSold.setText(String.valueOf(number(item.get("salesCount"))));
        
        double revenue = number(item.get("revenue"));
        // Display in K for brevity if needed, but VN currency is large. We can format it nicely.
        // The HTML mockup showed "$82,5K", we'll just format it locally.
        if (revenue >= 1000000) {
            holder.tvProfits.setText(currency.format(revenue / 1000000) + "M");
        } else if (revenue >= 1000) {
            holder.tvProfits.setText(currency.format(revenue / 1000) + "K");
        } else {
            holder.tvProfits.setText(currency.format(revenue));
        }
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvProductName, tvSold, tvProfits;
        ImageView ivProductImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvSold = itemView.findViewById(R.id.tvSold);
            tvProfits = itemView.findViewById(R.id.tvProfits);
            ivProductImage = itemView.findViewById(R.id.ivProductImage);
        }
    }
}
