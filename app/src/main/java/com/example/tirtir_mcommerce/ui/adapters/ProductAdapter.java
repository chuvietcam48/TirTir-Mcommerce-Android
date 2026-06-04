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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * ProductAdapter — RecyclerView Adapter hiển thị danh sách sản phẩm.
 *
 * Image URL resolution logic (TASK 2):
 * ────────────────────────────────────
 * API trả về Thumbnail_Images với 2 dạng:
 *
 * 1. "http://localhost:5001/uploads/..." → Dev URL (backend localhost) → Replace với deployed URL
 * 2. "assets/images/products/.../thumb.webp" → Relative path → Prepend BASE_IMAGE_URL
 * 3. "https://..." → Absolute URL → Dùng trực tiếp
 *
 * Price field (TASK 4):
 * ─────────────────────
 * API trả về Price: 10, 45 (số nguyên)
 * KHÔNG rõ đơn vị (USD? VND? Nghìn VND?) → hiển thị nguyên vẹn với đơn vị "đ"
 * TODO: Xác nhận với backend/PM về đơn vị tiền tệ thực tế.
 *
 * Ưu tiên: Sale_Price (nếu > 0) → Price
 *
 * Sprint 1.2 — Task A
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private final Context context;
    private List<Product> productList;
    private final OnProductClickListener clickListener;

    /**
     * BASE_IMAGE_URL của backend đã deploy.
     * Dùng để:
     * - Prepend với relative path từ API (assets/images/...)
     * - Replace "localhost:5001" từ dev URL
     */
    private static final String BASE_IMAGE_URL = "https://tirtir-project.onrender.com/";
    private static final String DEV_LOCALHOST = "http://localhost:5001/";

    /**
     * Price display: Format số nguyên từ API dưới dạng VND locale.
     * Ví dụ: 45 → "45 đ" (đơn vị chưa xác nhận — TODO: confirm với PM)
     * KHÔNG nhân với bất kỳ hệ số nào cho đến khi có xác nhận.
     */
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public ProductAdapter(Context context, List<Product> productList, OnProductClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        holder.bind(productList.get(position));
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

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.ivProductImage);
            tvName     = itemView.findViewById(R.id.tvProductName);
            tvPrice    = itemView.findViewById(R.id.tvProductPrice);
            tvCategory = itemView.findViewById(R.id.tvProductCategory);
        }

        void bind(Product product) {
            // Name
            tvName.setText(product.getName() != null ? product.getName() : "Sản phẩm");

            // Category
            String cat = product.getCategory();
            tvCategory.setText(cat != null && !cat.isEmpty() ? cat.toUpperCase() : "");

            // Price (TASK 4):
            // Price field từ API là số nguyên (ví dụ: 10, 45).
            // Ưu tiên Sale_Price nếu > 0, ngược lại dùng Price.
            // TODO: Xác nhận đơn vị tiền tệ thực tế với PM (USD? VND nghìn đồng?).
            double displayPrice = buildDisplayPrice(product);
            tvPrice.setText(currencyFormat.format(displayPrice) + " đ");

            // Image (TASK 2): resolve URL theo priority chain
            String imageUrl = resolveImageUrl(product);
            Glide.with(context)
                    .load(imageUrl.isEmpty() ? null : imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(imgProduct);

            // Click
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onProductClick(product);
                }
            });
        }

        /**
         * Xác định giá hiển thị.
         * Ưu tiên Sale_Price nếu > 0, ngược lại dùng Price.
         *
         * Price field là số nguyên từ MongoDB (ví dụ 45 = đơn vị chưa xác nhận).
         * KHÔNG nhân hệ số — TODO: confirm với PM/backend.
         */
        private double buildDisplayPrice(Product product) {
            double salePrice = product.getSalePrice();
            double price = product.getPrice();
            return (salePrice > 0) ? salePrice : price;
        }

        /**
         * Resolve ảnh sản phẩm theo thứ tự ưu tiên:
         * 1. Thumbnail_Images (nếu valid URL)
         * 2. Gallery_Images[0] (nếu có)
         * 3. Empty string → Glide dùng placeholder
         *
         * URL transform rules:
         * - "http://localhost:5001/..." → Replace với deployed BASE_IMAGE_URL
         * - "assets/images/..." → Prepend BASE_IMAGE_URL
         * - "https://..." → Dùng nguyên
         */
        private String resolveImageUrl(Product product) {
            // Priority 1: Thumbnail_Images
            String thumb = product.getThumbnailImages();
            String resolved = buildUrl(thumb);
            if (!resolved.isEmpty()) return resolved;

            // Priority 2: Gallery_Images[0]
            if (product.getGalleryImages() != null && !product.getGalleryImages().isEmpty()) {
                resolved = buildUrl(product.getGalleryImages().get(0));
                if (!resolved.isEmpty()) return resolved;
            }

            // Priority 3: no image
            return "";
        }

        /**
         * Biến đổi đường dẫn ảnh thô thành URL đầy đủ có thể tải được.
         *
         * Trường hợp 1: "http://localhost:5001/uploads/..." (dev server) → replace với deployed URL
         * Trường hợp 2: "assets/images/..." (relative path) → prepend BASE_IMAGE_URL
         * Trường hợp 3: "https://..." (absolute URL) → giữ nguyên
         * Trường hợp 4: null / empty → trả về ""
         */
        private String buildUrl(String path) {
            if (path == null || path.trim().isEmpty()) return "";

            // Case 1: dev localhost URL → swap to deployed
            if (path.startsWith(DEV_LOCALHOST)) {
                return BASE_IMAGE_URL + path.substring(DEV_LOCALHOST.length());
            }

            // Case 2: already absolute https
            if (path.startsWith("https://") || path.startsWith("http://")) {
                return path;
            }

            // Case 3: relative path (assets/..., uploads/...)
            return BASE_IMAGE_URL + path;
        }
    }
}
