package com.example.tirtir_mcommerce.ui.activities;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.ArrayList;

/**
 * SCR-14 ProductDetailActivity
 *
 * Receives real product data via Intent extras from HomeFragment.
 * - Real product image loaded via Glide
 * - Real name, price, category, skin types, ingredients, description
 * - Stock = 0 → disables Add to Cart, shows "Hết hàng"
 * - Add to Cart → SQLite cart_items via DatabaseHelper
 * - Wishlist toggle → WishlistContentProvider (ContentProvider)
 * - Status bar: white (set in theme)
 *
 * Sprint S1.2
 */
public class ProductDetailActivity extends AppCompatActivity {

    private static final String BASE_IMAGE_URL = "https://tirtir-project.onrender.com/";

    private Toolbar toolbarProductDetail;
    private android.widget.ImageView ivProductImage;
    private TextView tvProductCategory;
    private TextView tvProductName;
    private TextView tvProductPrice;
    private TextView tvSuitableSkinTypes;
    private TextView tvIngredientList;
    private TextView tvProductDescription;
    private ImageButton btnWishlist;
    private Button btnAddToCart;

    private DatabaseHelper databaseHelper;



    private String productId, productName, productCategory, productImage;
    private double productPrice;
    private double displayPriceVnd;
    private int stockQuantity;
    private int currentQuantity = 1;
    private boolean isWishlisted = false;
    private java.util.ArrayList<String> galleryImages;

    private TextView tvQuantity, tvTotalPrice;
    private ImageButton btnDecreaseQty, btnIncreaseQty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        databaseHelper = DatabaseHelper.getInstance(this);

        toolbarProductDetail = findViewById(R.id.toolbarProductDetail);
        // Phase 1 requirement: use ViewPager2 for gallery
        androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.viewPagerProductImages);
        com.google.android.material.tabs.TabLayout tabIndicator = findViewById(R.id.tabIndicatorImages);

        tvProductCategory = findViewById(R.id.tvProductCategory);
        tvProductName = findViewById(R.id.tvProductName);
        tvProductPrice = findViewById(R.id.tvProductPrice);
        tvSuitableSkinTypes = findViewById(R.id.tvSuitableSkinTypes);
        tvIngredientList = findViewById(R.id.tvIngredientList);
        tvProductDescription = findViewById(R.id.tvProductDescription);
        btnWishlist = findViewById(R.id.btnWishlist);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        
        tvQuantity = findViewById(R.id.tvQuantity);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        btnDecreaseQty = findViewById(R.id.btnDecreaseQty);
        btnIncreaseQty = findViewById(R.id.btnIncreaseQty);

        setSupportActionBar(toolbarProductDetail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        // Read from intent
        productId       = getIntent().getStringExtra("PRODUCT_ID");
        productName     = getIntent().getStringExtra("PRODUCT_NAME");
        productPrice    = getIntent().getDoubleExtra("PRODUCT_PRICE", 0.0);
        productCategory = getIntent().getStringExtra("PRODUCT_CATEGORY");
        productImage    = getIntent().getStringExtra("PRODUCT_IMAGE");
        stockQuantity   = getIntent().getIntExtra("PRODUCT_STOCK", 100);
        String skinTypes    = getIntent().getStringExtra("PRODUCT_SKIN_TYPES");
        String ingredients  = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
        String description  = getIntent().getStringExtra("PRODUCT_DESCRIPTION");

        // Normalize price: Backend returns USD (e.g., 45). Convert to VND (rate: 25,000)
        double salePrice = getIntent().getDoubleExtra("PRODUCT_SALE_PRICE", 0.0);
        double activePriceUsd = (salePrice > 0) ? salePrice : productPrice;
        displayPriceVnd = activePriceUsd * 25000.0;

        // Populate views
        if (productName != null)     tvProductName.setText(productName);
        if (productCategory != null) tvProductCategory.setText(productCategory);
        tvProductPrice.setText(PriceUtils.formatPriceVnd(displayPriceVnd));
        tvSuitableSkinTypes.setText(skinTypes != null && !skinTypes.isEmpty() ? skinTypes : "All skin types");
        tvIngredientList.setText(ingredients != null && !ingredients.isEmpty() ? ingredients : "Ingredient list not available.");
        tvProductDescription.setText(description != null && !description.isEmpty() ? description : "Premium skincare formula crafted for your skin.");

        galleryImages = getIntent().getStringArrayListExtra("PRODUCT_GALLERY");
        loadProductImage(viewPager, tabIndicator);

        // Stock status
        if (stockQuantity <= 0) {
            btnAddToCart.setEnabled(false);
            btnAddToCart.setText("Hết hàng");
            btnAddToCart.setAlpha(0.5f);
        }

        // Wishlist state
        isWishlisted = checkWishlistStatus();
        updateWishlistIcon(isWishlisted);

        // Stepper logic
        updateTotalPrice();
        btnIncreaseQty.setOnClickListener(v -> {
            if (currentQuantity < 10 && currentQuantity < stockQuantity) {
                currentQuantity++;
                tvQuantity.setText(String.valueOf(currentQuantity));
                updateTotalPrice();
            }
        });
        
        btnDecreaseQty.setOnClickListener(v -> {
            if (currentQuantity > 1) {
                currentQuantity--;
                tvQuantity.setText(String.valueOf(currentQuantity));
                updateTotalPrice();
            }
        });

        // Add to Cart
        btnAddToCart.setOnClickListener(v -> {
            if (productId == null || productId.isEmpty()) {
                Toast.makeText(this, "Lỗi: không tìm thấy sản phẩm", Toast.LENGTH_SHORT).show();
                return;
            }
            String imageUrl = productImage != null ? productImage : "";
            CartItem item = new CartItem(productId, productName, imageUrl, displayPriceVnd, currentQuantity, "");
            databaseHelper.insertOrUpdateCartItem(item);
            Toast.makeText(this, "Đã thêm " + currentQuantity + " sản phẩm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
        });

        // Wishlist toggle via ContentProvider
        btnWishlist.setOnClickListener(v -> toggleWishlist());
    }

    private void updateTotalPrice() {
        double total = displayPriceVnd * currentQuantity;
        if (tvTotalPrice != null) {
            tvTotalPrice.setText(PriceUtils.formatPriceVnd(total));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ===========================
    // IMAGE LOADING
    // ===========================

    private void loadProductImage(androidx.viewpager2.widget.ViewPager2 viewPager, com.google.android.material.tabs.TabLayout tabIndicator) {
        if (viewPager == null) return;
        viewPager.setVisibility(android.view.View.VISIBLE);

        // Hide static ImageView
        android.widget.ImageView ivStatic = findViewById(R.id.ivProductDetailImage);
        if (ivStatic != null) ivStatic.setVisibility(android.view.View.GONE);

        java.util.List<String> imagesToDisplay = new java.util.ArrayList<>();
        if (galleryImages != null && !galleryImages.isEmpty()) {
            imagesToDisplay.addAll(galleryImages);
        } else if (productImage != null && !productImage.isEmpty()) {
            imagesToDisplay.add(productImage); // fallback to thumbnail
        }

        if (imagesToDisplay.isEmpty()) {
            // no image
            return;
        }

        ImageGalleryAdapter adapter = new ImageGalleryAdapter(this, imagesToDisplay);
        viewPager.setAdapter(adapter);

        if (imagesToDisplay.size() > 1 && tabIndicator != null) {
            tabIndicator.setVisibility(android.view.View.VISIBLE);
            new com.google.android.material.tabs.TabLayoutMediator(tabIndicator, viewPager,
                    (tab, position) -> {}
            ).attach();
        } else if (tabIndicator != null) {
            tabIndicator.setVisibility(android.view.View.GONE);
        }
    }

    // Inner Adapter for Gallery
    private class ImageGalleryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImageGalleryAdapter.ImageViewHolder> {
        private final android.content.Context context;
        private final java.util.List<String> imageUrls;

        public ImageGalleryAdapter(android.content.Context context, java.util.List<String> imageUrls) {
            this.context = context;
            this.imageUrls = imageUrls;
        }

        @androidx.annotation.NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.widget.ImageView iv = new android.widget.ImageView(context);
            iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFFF5F5F5); // tirtir_off_white
            return new ImageViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ImageViewHolder holder, int position) {
            String rawUrl = imageUrls.get(position);
            Glide.with(context)
                    .load(buildImageUrl(rawUrl))
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class ImageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.ImageView imageView;
            ImageViewHolder(@androidx.annotation.NonNull android.view.View itemView) {
                super(itemView);
                this.imageView = (android.widget.ImageView) itemView;
            }
        }
    }

    private String buildImageUrl(String path) {
        if (path == null || path.isEmpty()) return "";
        if (path.startsWith("http")) return path;
        return BASE_IMAGE_URL + path;
    }

    // ===========================
    // WISHLIST via ContentProvider
    // ===========================

    private boolean checkWishlistStatus() {
        if (productId == null) return false;
        Uri uri = WishlistContentProvider.CONTENT_URI;
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{WishlistContentProvider.COL_ID},
                WishlistContentProvider.COL_PRODUCT_ID + "=?",
                new String[]{productId},
                null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleWishlist() {
        if (productId == null) return;

        if (isWishlisted) {
            // Remove from wishlist
            Uri deleteUri = WishlistContentProvider.CONTENT_URI;
            int deleted = getContentResolver().delete(
                    deleteUri,
                    WishlistContentProvider.COL_PRODUCT_ID + "=?",
                    new String[]{productId});
            if (deleted > 0) {
                isWishlisted = false;
                updateWishlistIcon(false);
                Toast.makeText(this, "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Add to wishlist
            ContentValues values = new ContentValues();
            values.put(WishlistContentProvider.COL_PRODUCT_ID, productId);
            values.put(WishlistContentProvider.COL_PRODUCT_NAME, productName != null ? productName : "");
            values.put(WishlistContentProvider.COL_PRODUCT_IMAGE, productImage != null ? productImage : "");
            values.put(WishlistContentProvider.COL_PRODUCT_PRICE, displayPriceVnd);
            Uri result = getContentResolver().insert(WishlistContentProvider.CONTENT_URI, values);
            if (result != null) {
                isWishlisted = true;
                updateWishlistIcon(true);
                Toast.makeText(this, "Đã thêm vào yêu thích!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateWishlistIcon(boolean wishlisted) {
        if (wishlisted) {
            btnWishlist.setImageResource(android.R.drawable.btn_star_big_on);
            btnWishlist.setColorFilter(0xFFE91E63);
        } else {
            btnWishlist.setImageResource(android.R.drawable.btn_star_big_off);
            btnWishlist.clearColorFilter();
        }
    }
}
