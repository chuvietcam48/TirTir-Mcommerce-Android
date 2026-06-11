package com.example.tirtir_mcommerce.ui.adapters;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.model.Product;

import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.List;

/**
 * ProductAdapter — RecyclerView Adapter hiển thị danh sách sản phẩm.
 *
 * Image URL resolution logic (TASK 2):
 * API trả về Thumbnail_Images với 2 dạng:
 * 1. "http://localhost:5001/uploads/..." → Dev URL → Replace với deployed URL
 * 2. "assets/images/products/.../thumb.webp" → Relative path → Prepend BASE_IMAGE_URL
 * 3. "https://..." → Absolute URL → Dùng trực tiếp
 *
 * Price field (TASK 4): Ưu tiên Sale_Price (nếu > 0) → Price
 *
 * Features added:
 * - Out-of-stock badge (tvOutOfStock) — shown when stockQuantity <= 0
 * - Wishlist toggle heart icon (btnWishlistToggle) via WishlistContentProvider
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

    private static final String BASE_IMAGE_URL = "https://tirtir-project.onrender.com/";
    private static final String DEV_LOCALHOST = "http://localhost:5001/";



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
        private final TextView tvOutOfStock;
        private final TextView tvDiscountBadge;
        private final ImageButton btnWishlistToggle;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct       = itemView.findViewById(R.id.ivProductImage);
            tvName           = itemView.findViewById(R.id.tvProductName);
            tvPrice          = itemView.findViewById(R.id.tvProductPrice);
            tvCategory       = itemView.findViewById(R.id.tvProductCategory);
            tvOutOfStock     = itemView.findViewById(R.id.tvOutOfStock);
            tvDiscountBadge  = itemView.findViewById(R.id.tvDiscountBadge);
            btnWishlistToggle = itemView.findViewById(R.id.btnWishlistToggle);
        }

        void bind(Product product) {
            // Name
            tvName.setText(product.getName() != null ? product.getName() : "Sản phẩm");

            // Category
            String cat = product.getCategory();
            tvCategory.setText(cat != null && !cat.isEmpty() ? cat.toUpperCase() : "");

            // Price: Sale_Price if > 0, else Price
            double displayPrice = buildDisplayPrice(product);
            tvPrice.setText(PriceUtils.formatPriceVnd(displayPrice));

            // Discount Badge
            if (tvDiscountBadge != null) {
                double basePrice = product.getPrice();
                double salePrice = product.getSalePrice();
                if (salePrice > 0 && salePrice < basePrice) {
                    int discountPct = (int) Math.round((1 - (salePrice / basePrice)) * 100);
                    tvDiscountBadge.setText("-" + discountPct + "%");
                    tvDiscountBadge.setVisibility(View.VISIBLE);
                } else {
                    tvDiscountBadge.setVisibility(View.GONE);
                }
            }

            // Out-of-stock badge
            if (tvOutOfStock != null) {
                if (product.getStockQuantity() <= 0) {
                    tvOutOfStock.setVisibility(View.VISIBLE);
                } else {
                    tvOutOfStock.setVisibility(View.GONE);
                }
            }

            // Image: resolve URL
            String imageUrl = resolveImageUrl(product);
            Glide.with(context)
                    .load(imageUrl.isEmpty() ? null : imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(imgProduct);

            // Wishlist toggle state
            if (btnWishlistToggle != null) {
                String productId = product.getProductId() != null ? product.getProductId() : product.getId();
                boolean wishlisted = checkWishlistStatus(productId);
                updateWishlistIcon(wishlisted);

                btnWishlistToggle.setOnClickListener(v -> {
                    boolean currentState = checkWishlistStatus(productId);
                    if (currentState) {
                        // Remove from wishlist
                        context.getContentResolver().delete(
                                WishlistContentProvider.CONTENT_URI,
                                WishlistContentProvider.COL_PRODUCT_ID + "=?",
                                new String[]{productId});
                        updateWishlistIcon(false);
                    } else {
                        // Add to wishlist
                        ContentValues values = new ContentValues();
                        values.put(WishlistContentProvider.COL_PRODUCT_ID, productId);
                        values.put(WishlistContentProvider.COL_PRODUCT_NAME, product.getName() != null ? product.getName() : "");
                        values.put(WishlistContentProvider.COL_PRODUCT_IMAGE, product.getThumbnailImages() != null ? product.getThumbnailImages() : "");
                        values.put(WishlistContentProvider.COL_PRODUCT_PRICE, displayPrice);
                        Uri result = context.getContentResolver().insert(WishlistContentProvider.CONTENT_URI, values);
                        if (result != null) updateWishlistIcon(true);
                    }
                });
            }

            // Click whole card → detail
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onProductClick(product);
                }
            });
        }

        private boolean checkWishlistStatus(String productId) {
            if (productId == null) return false;
            try (Cursor cursor = context.getContentResolver().query(
                    WishlistContentProvider.CONTENT_URI,
                    new String[]{WishlistContentProvider.COL_ID},
                    WishlistContentProvider.COL_PRODUCT_ID + "=?",
                    new String[]{productId},
                    null)) {
                return cursor != null && cursor.getCount() > 0;
            } catch (Exception e) {
                return false;
            }
        }

        private void updateWishlistIcon(boolean wishlisted) {
            if (btnWishlistToggle == null) return;
            if (wishlisted) {
                btnWishlistToggle.setImageResource(android.R.drawable.btn_star_big_on);
                btnWishlistToggle.setColorFilter(0xFFC62828);
            } else {
                btnWishlistToggle.setImageResource(android.R.drawable.btn_star_big_off);
                btnWishlistToggle.clearColorFilter();
            }
        }

        private double buildDisplayPrice(Product product) {
            double salePrice = product.getSalePrice();
            double basePrice = product.getPrice();
            double priceInUsd = (salePrice > 0) ? salePrice : basePrice;
            
            return PriceUtils.normalizePrice(priceInUsd);
        }

        private String resolveImageUrl(Product product) {
            String thumb = product.getThumbnailImages();
            String resolved = buildUrl(thumb);
            if (!resolved.isEmpty()) return resolved;

            if (product.getGalleryImages() != null && !product.getGalleryImages().isEmpty()) {
                resolved = buildUrl(product.getGalleryImages().get(0));
                if (!resolved.isEmpty()) return resolved;
            }

            return "";
        }

        private String buildUrl(String path) {
            if (path == null || path.trim().isEmpty()) return "";
            if (path.startsWith(DEV_LOCALHOST)) {
                return BASE_IMAGE_URL + path.substring(DEV_LOCALHOST.length());
            }
            if (path.startsWith("https://") || path.startsWith("http://")) {
                return path;
            }
            return BASE_IMAGE_URL + path;
        }
    }
}
