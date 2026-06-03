package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
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

/**
 * ProductAdapter — RecyclerView Adapter cho danh sách sản phẩm.
 *
 * Sprint 1.2 — Task A:
 * - Hiển thị sản phẩm với ảnh (Glide), tên, giá
 * - Callback onAddToCart khi bấm nút "Thêm vào giỏ"
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnAddToCartListener {
        void onAddToCart(Product product);
    }

    private final Context context;
    private List<Product> productList;
    private final OnAddToCartListener addToCartListener;

    // Base URL của ảnh thumbnail từ backend
    private static final String BASE_IMAGE_URL = "https://tirtir-project.onrender.com/";

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public ProductAdapter(Context context, List<Product> productList, OnAddToCartListener listener) {
        this.context = context;
        this.productList = productList;
        this.addToCartListener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return productList == null ? 0 : productList.size();
    }

    public void updateData(List<Product> newList) {
        this.productList = newList;
        notifyDataSetChanged();
    }

    // ===========================
    // VIEW HOLDER
    // ===========================

    class ProductViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imgProduct;
        private final TextView tvName;
        private final TextView tvPrice;
        private final TextView tvCategory;
        private View btnAddToCart; // Optional — nếu layout có

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvCategory = itemView.findViewById(R.id.tvProductCategory);
            // btnAddToCart: sẽ bind khi FE layout thêm R.id.btnAddToCart
            // btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
        }

        void bind(Product product) {
            tvName.setText(product.getName() != null ? product.getName() : "Sản phẩm");
            tvCategory.setText(product.getCategory() != null ? product.getCategory() : "");

            // Format giá: ưu tiên salePrice nếu > 0
            double displayPrice = product.getSalePrice() > 0 ? product.getSalePrice() : product.getPrice();
            tvPrice.setText(currencyFormat.format(displayPrice) + " đ");

            // Load ảnh thumbnail bằng Glide
            String imageUrl = buildImageUrl(product.getThumbnailImages());
            Glide.with(context)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.stat_notify_error)
                    .into(imgProduct);

            // Click cả item → thêm vào giỏ (fallback nếu chưa có nút)
            itemView.setOnClickListener(v -> {
                if (addToCartListener != null) {
                    addToCartListener.onAddToCart(product);
                }
            });

            // Nút "Thêm vào giỏ" riêng (nếu layout có)
            if (btnAddToCart != null) {
                btnAddToCart.setOnClickListener(v -> {
                    if (addToCartListener != null) {
                        addToCartListener.onAddToCart(product);
                    }
                });
            }
        }

        private String buildImageUrl(String thumbnail) {
            if (thumbnail == null || thumbnail.isEmpty()) return "";
            if (thumbnail.startsWith("http")) return thumbnail;
            return BASE_IMAGE_URL + thumbnail;
        }
    }
}
