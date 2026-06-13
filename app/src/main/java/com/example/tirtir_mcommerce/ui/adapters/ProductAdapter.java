package com.example.tirtir_mcommerce.ui.adapters;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;

import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.button.MaterialButton;

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
        private final TextView tvImageFallback;
        private final ImageButton btnWishlistToggle;
        private final MaterialButton btnQuickAdd;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct       = itemView.findViewById(R.id.ivProductImage);
            tvName           = itemView.findViewById(R.id.tvProductName);
            tvPrice          = itemView.findViewById(R.id.tvProductPrice);
            tvCategory       = itemView.findViewById(R.id.tvProductCategory);
            tvOutOfStock     = itemView.findViewById(R.id.tvOutOfStock);
            tvDiscountBadge  = itemView.findViewById(R.id.tvDiscountBadge);
            tvImageFallback  = itemView.findViewById(R.id.tvImageFallback);
            btnWishlistToggle = itemView.findViewById(R.id.btnWishlistToggle);
            btnQuickAdd = itemView.findViewById(R.id.btnQuickAdd);
        }

        void bind(Product product) {
            // Name
            tvName.setText(product.getName() != null ? product.getName() : "TirTir product");

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
                    if (btnQuickAdd != null) btnQuickAdd.setEnabled(false);
                } else {
                    tvOutOfStock.setVisibility(View.GONE);
                    if (btnQuickAdd != null) btnQuickAdd.setEnabled(true);
                }
            }

            // Image: resolve URL
            String imageUrl = resolveImageUrl(product);
            if (tvImageFallback != null) tvImageFallback.setVisibility(View.GONE);
            Glide.with(context)
                    .load(imageUrl.isEmpty() ? null : imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .fitCenter()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            if (tvImageFallback != null) tvImageFallback.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            if (tvImageFallback != null) tvImageFallback.setVisibility(View.GONE);
                            return false;
                        }
                    })
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
                        Toast.makeText(context, "Removed from wishlist", Toast.LENGTH_SHORT).show();
                    } else {
                        // Add to wishlist
                        ContentValues values = new ContentValues();
                        values.put(WishlistContentProvider.COL_PRODUCT_ID, productId);
                        values.put(WishlistContentProvider.COL_PRODUCT_NAME, product.getName() != null ? product.getName() : "");
                        values.put(WishlistContentProvider.COL_PRODUCT_IMAGE, product.getThumbnailImages() != null ? product.getThumbnailImages() : "");
                        values.put(WishlistContentProvider.COL_PRODUCT_PRICE, displayPrice);
                        Uri result = context.getContentResolver().insert(WishlistContentProvider.CONTENT_URI, values);
                        if (result != null) {
                            updateWishlistIcon(true);
                            Toast.makeText(context, "Added to wishlist", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            if (btnQuickAdd != null) {
                btnQuickAdd.setOnClickListener(v -> {
                    String productId = product.getProductId() != null ? product.getProductId() : product.getId();
                    if (productId == null || productId.isEmpty()) {
                        Toast.makeText(context, "Product is not ready to add", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CartItem item = new CartItem(
                            productId,
                            product.getName() != null ? product.getName() : "TirTir product",
                            product.getThumbnailImages() != null ? product.getThumbnailImages() : "",
                            displayPrice,
                            1,
                            ""
                    );
                    CartRepository cartRepository = new CartRepository(context);
                    cartRepository.addToCartLocal(item);
                    cartRepository.syncItemToServer(item, null, error -> {
                        // Local-first cart remains usable; NetworkReceiver retries later.
                    });
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).updateCartBadge();
                    }
                    Toast.makeText(context, "Added to cart", Toast.LENGTH_SHORT).show();
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
                btnWishlistToggle.setImageResource(R.drawable.ic_wishlist);
                btnWishlistToggle.setColorFilter(0xFFC62828);
            } else {
                btnWishlistToggle.setImageResource(R.drawable.ic_wishlist);
                btnWishlistToggle.setColorFilter(0xFF9E9E9E);
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
            // Strip leading slash to prevent double-slash: "https://host.com//assets/..."
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            String finalUrl = BASE_IMAGE_URL + cleanPath;
            android.util.Log.d("ProductAdapter", "Image URL: " + finalUrl);
            return finalUrl;
        }
    }
}
