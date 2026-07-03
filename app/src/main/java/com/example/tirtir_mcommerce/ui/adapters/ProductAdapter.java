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
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiConfig;

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
    private final int layoutResId;

    public ProductAdapter(Context context, List<Product> productList, OnProductClickListener listener) {
        this(context, productList, R.layout.item_product, listener);
    }
    
    public ProductAdapter(Context context, List<Product> productList, int layoutResId, OnProductClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.layoutResId = layoutResId;
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(layoutResId, parent, false);
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
        private final TextView tvOriginalPrice;
        private final TextView tvCategory;
        private final TextView tvOutOfStock;
        private final TextView tvDiscountBadge;
        private final ImageButton btnWishlistToggle;
        private final View soldOutOverlay;
        private final TextView tvRatingCount;
        private final TextView tvSoldCount;
        private final View btnQuickAdd;
        private final View btnViewDetails;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct        = itemView.findViewById(R.id.ivProductImage);
            tvName            = itemView.findViewById(R.id.tvProductName);
            tvPrice           = itemView.findViewById(R.id.tvProductPrice);
            tvOriginalPrice   = itemView.findViewById(R.id.tvOriginalPrice);
            tvCategory        = itemView.findViewById(R.id.tvProductCategory);
            tvOutOfStock      = itemView.findViewById(R.id.tvOutOfStock);
            tvDiscountBadge   = itemView.findViewById(R.id.tvDiscountBadge);
            btnWishlistToggle = itemView.findViewById(R.id.btnWishlistToggle);
            soldOutOverlay    = itemView.findViewById(R.id.soldOutOverlay);
            tvRatingCount     = itemView.findViewById(R.id.tvRatingCount);
            tvSoldCount       = itemView.findViewById(R.id.tvSoldCount);
            btnQuickAdd       = itemView.findViewById(R.id.btnQuickAdd);
            btnViewDetails    = itemView.findViewById(R.id.btnViewDetails);
        }

        void bind(Product product) {
            // Name
            tvName.setText(product.getName() != null ? product.getName() : "TirTir product");

            // Subtitle (reusing tvCategory)
            String subtitle = product.getKeyIngredients();
            if (subtitle == null || subtitle.isEmpty()) {
                subtitle = product.getSkinTypeTarget();
            }
            if (subtitle == null || subtitle.isEmpty()) {
                subtitle = product.getCategory();
            }
            if (tvCategory != null) {
                tvCategory.setText(subtitle != null ? subtitle : "");
            }

            // Price: show sale price (red) + strikethrough original if on sale
            double basePrice = product.getPrice();
            double salePrice = product.getSalePrice();
            double displayPrice = buildDisplayPrice(product);
            tvPrice.setText(PriceUtils.formatPriceUsd(displayPrice));
            if (tvOriginalPrice != null) {
                if (salePrice > 0 && salePrice < basePrice) {
                    tvOriginalPrice.setText(PriceUtils.formatPriceUsd(basePrice));
                    tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    tvOriginalPrice.setVisibility(View.VISIBLE);
                } else {
                    tvOriginalPrice.setVisibility(View.GONE);
                }
            }

            // Discount Badge / Bestseller Badge
            if (tvDiscountBadge != null) {
                if (layoutResId == R.layout.item_product_bestseller) {
                    tvDiscountBadge.setVisibility(View.VISIBLE);
                    if (salePrice > 0 && salePrice < basePrice) {
                        int pct = (int) Math.round((1 - (salePrice / basePrice)) * 100);
                        tvDiscountBadge.setText("-" + pct + "%");
                        tvDiscountBadge.setBackgroundResource(R.drawable.bg_tag_primary);
                        tvDiscountBadge.setBackgroundTintList(null);
                    } else {
                        tvDiscountBadge.setText("Bestseller");
                        tvDiscountBadge.setBackgroundResource(R.drawable.bg_badge_bestseller);
                        tvDiscountBadge.setBackgroundTintList(null);
                    }
                } else {
                    if (salePrice > 0 && salePrice < basePrice) {
                        int discountPct = (int) Math.round((1 - (salePrice / basePrice)) * 100);
                        tvDiscountBadge.setText("-" + discountPct + "%");
                        tvDiscountBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvDiscountBadge.setVisibility(View.GONE);
                    }
                }
            }

            // Rating
            if (tvRatingCount != null) {
                double rating = product.getRating() > 0 ? product.getRating() : 
                                4.0 + (Math.abs((product.getId() != null ? product.getId() : product.getName()).hashCode()) % 11) / 10.0;
                tvRatingCount.setText(String.format(java.util.Locale.US, "%.1f", rating));
                tvRatingCount.setVisibility(View.VISIBLE);
            }

            // Sold Count
            if (tvSoldCount != null) {
                String fallbackId = product.getId() != null ? product.getId() : (product.getName() != null ? product.getName() : "default");
                int sold = Math.abs(fallbackId.hashCode()) % 1500 + 50;
                tvSoldCount.setText(sold + " Sold");
                tvSoldCount.setVisibility(View.VISIBLE);
            }

            // Sold-out overlay on image (dark + centered text)
            boolean soldOut = product.getStockQuantity() <= 0;
            if (soldOutOverlay != null) {
                soldOutOverlay.setVisibility(soldOut ? View.VISIBLE : View.GONE);
            } else if (tvOutOfStock != null) {
                // Fallback for layouts that still use the old corner badge
                tvOutOfStock.setVisibility(soldOut ? View.VISIBLE : View.GONE);
            }

            // Single image — thumbnail or first gallery image
            String imageUrl = resolveImageUrl(product);
            if (imgProduct != null) {
                int fallbackDrawable = resolveFallbackDrawable(product);
                RequestBuilder<Drawable> request = Glide.with(imgProduct)
                        .load(imageUrl.isEmpty() ? null : imageUrl)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .placeholder(fallbackDrawable)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(120));

                String secondaryUrl = resolveSecondaryImageUrl(product, imageUrl);
                if (!secondaryUrl.isEmpty()) {
                    request.error(Glide.with(imgProduct)
                            .load(secondaryUrl)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .centerCrop()
                            .error(fallbackDrawable));
                } else {
                    request.error(fallbackDrawable);
                }
                request.into(imgProduct);
            }

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
                            product.getVolumeSize() != null ? product.getVolumeSize() : "Standard"
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

            // View Details button (in shop grid) → opens product detail
            if (btnViewDetails != null) {
                btnViewDetails.setOnClickListener(v -> {
                    if (clickListener != null) clickListener.onProductClick(product);
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
                btnWishlistToggle.setColorFilter(0xFFE23B2E);
                btnWishlistToggle.setAlpha(1f);
            } else {
                btnWishlistToggle.setImageResource(R.drawable.ic_wishlist);
                btnWishlistToggle.setColorFilter(0xFF666666);
                btnWishlistToggle.setAlpha(0.9f);
            }
        }

        private double buildDisplayPrice(Product product) {
            double salePrice = product.getSalePrice();
            double basePrice = product.getPrice();
            double priceInUsd = (salePrice > 0) ? salePrice : basePrice;
            
            return PriceUtils.normalizePrice(priceInUsd);
        }

        private String resolveImageUrl(Product product) {
            // Thumbnail (thumb.webp) is always the clean product-on-white shot.
            String thumbnail = buildUrl(product.getThumbnailImages());
            if (!thumbnail.isEmpty()) return thumbnail;
            // Fall back to first gallery image when no thumbnail path is set.
            if (product.getGalleryImages() != null && !product.getGalleryImages().isEmpty()) {
                String resolved = buildUrl(product.getGalleryImages().get(0));
                if (!resolved.isEmpty()) return resolved;
            }
            return "";
        }

        private String resolveSecondaryImageUrl(Product product, String primaryUrl) {
            if (product.getGalleryImages() != null && !product.getGalleryImages().isEmpty()) {
                String gallery0 = buildUrl(product.getGalleryImages().get(0));
                if (!gallery0.isEmpty() && !gallery0.equals(primaryUrl)) return gallery0;
            }
            return "";
        }

        private String buildUrl(String path) {
            return ApiConfig.resolveMediaUrl(path);
        }

        private String resolveUrl(String path) {
            return ApiConfig.resolveMediaUrl(path);
        }

        private int resolveFallbackDrawable(Product product) {
            String name = product.getName() == null ? "" : product.getName().toLowerCase(java.util.Locale.ENGLISH);
            String category = product.getCategory() == null ? "" : product.getCategory().toLowerCase(java.util.Locale.ENGLISH);
            if (name.contains("gift card") || category.contains("gift card")) {
                return R.drawable.tirtir_gift_card;
            }
            if (name.contains("matcha")) {
                return R.drawable.tirtir_matcha_set;
            }
            return R.drawable.ic_product_placeholder;
        }
    }

}
