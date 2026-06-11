package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.google.android.material.button.MaterialButton;

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
        void onDelete(Product product);
        void onToggleActive(Product product, boolean isActive);
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
        holder.tvStock.setText("Kho: " + product.getStock());

        // Load image
        String imageUrl = product.getThumbnailImages();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            if (!imageUrl.startsWith("http")) {
                imageUrl = "https://tirtir-project.onrender.com/" + imageUrl;
            }
            Glide.with(context).load(imageUrl).into(holder.ivImage);
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Active state
        holder.switchActive.setOnCheckedChangeListener(null); // Prevent trigger during bind
        holder.switchActive.setChecked(product.isActive());
        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleActive(product, isChecked);
            }
        });

        // Buttons
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(product);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(product);
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName, tvPrice, tvStock;
        SwitchCompat switchActive;
        MaterialButton btnEdit, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivAdminProduct);
            tvName = itemView.findViewById(R.id.tvAdminProductName);
            tvPrice = itemView.findViewById(R.id.tvAdminProductPrice);
            tvStock = itemView.findViewById(R.id.tvAdminProductStock);
            switchActive = itemView.findViewById(R.id.switchAdminProductActive);
            btnEdit = itemView.findViewById(R.id.btnAdminEdit);
            btnDelete = itemView.findViewById(R.id.btnAdminDelete);
        }
    }
}
