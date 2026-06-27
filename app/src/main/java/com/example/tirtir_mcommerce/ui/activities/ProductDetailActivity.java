package com.example.tirtir_mcommerce.ui.activities;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    private Toolbar toolbarProductDetail;
    private TextView tvProductCategory;
    private TextView tvProductName;
    private TextView tvProductPrice;
    private TextView tvSuitableSkinTypes;
    private TextView tvIngredientList;
    private TextView tvProductDescription;
    private MaterialButton btnWishlist;
    private MaterialButton btnARTryOn;
    private MaterialButton btnIngredientScan;
    private MaterialButton btnChatAdvisor;
    private Button btnAddToCart;
    private MaterialButton btnBuyNow;
    private android.widget.LinearLayout layoutDescriptionImages;

    private DatabaseHelper databaseHelper;



    private String productId, productName, productCategory, productImage, productIngredients;
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
        btnARTryOn = findViewById(R.id.btnARTryOn);
        btnIngredientScan = findViewById(R.id.btnIngredientScan);
        btnChatAdvisor = findViewById(R.id.btnChatAdvisor);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnBuyNow = findViewById(R.id.btnBuyNow);
        layoutDescriptionImages = findViewById(R.id.layoutDescriptionImages);
        
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
        productIngredients  = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
        String description  = getIntent().getStringExtra("PRODUCT_DESCRIPTION");

        // Normalize price: use PriceUtils (safe: < 1000 => USD-style => x25000, >= 1000 => already VND)
        double salePrice = getIntent().getDoubleExtra("PRODUCT_SALE_PRICE", 0.0);
        double activePrice = (salePrice > 0) ? salePrice : productPrice;
        displayPriceVnd = com.example.tirtir_mcommerce.utils.PriceUtils.normalizePrice(activePrice);

        // Populate views
        if (productName != null)     tvProductName.setText(productName);
        if (productCategory != null) tvProductCategory.setText(productCategory);
        tvProductPrice.setText(PriceUtils.formatPriceVnd(displayPriceVnd));
        tvSuitableSkinTypes.setText(skinTypes != null && !skinTypes.isEmpty() ? skinTypes : "Suitable for all skin types");
        tvIngredientList.setText(productIngredients != null && !productIngredients.isEmpty()
                ? productIngredients
                : "Ingredient list is not available yet.");
        tvProductDescription.setText(description != null && !description.isEmpty() ? description : "Premium TirTir beauty care formula.");

        galleryImages = getIntent().getStringArrayListExtra("PRODUCT_GALLERY");
        loadProductImage(viewPager, tabIndicator);
        fetchProductDetailIfDeepLinked(viewPager, tabIndicator);

        // ===========================
        // OUT-OF-STOCK UI (S1.2 gap)
        // ===========================
        // Find optional out-of-stock badge (may be null if not in XML)
        android.widget.TextView tvOutOfStockBadge = findViewById(R.id.tvProductOutOfStockBadge);
        android.view.ViewGroup layoutStepper = (android.view.ViewGroup) btnDecreaseQty.getParent();
        if (stockQuantity <= 0) {
            // Disable Add to Cart button
            btnAddToCart.setEnabled(false);
            btnAddToCart.setText("Out of stock");
            btnAddToCart.setAlpha(0.4f);
            if (btnBuyNow != null) {
                btnBuyNow.setEnabled(false);
                btnBuyNow.setAlpha(0.4f);
            }
            // Hide stepper (qty controls)
            if (layoutStepper != null) layoutStepper.setVisibility(android.view.View.INVISIBLE);
            // Show out-of-stock badge if it exists in XML
            if (tvOutOfStockBadge != null) tvOutOfStockBadge.setVisibility(android.view.View.VISIBLE);
        } else {
            // Enable Add to Cart button
            btnAddToCart.setEnabled(true);
            btnAddToCart.setText(getString(R.string.btn_add_to_cart));
            btnAddToCart.setAlpha(1.0f);
            if (btnBuyNow != null) {
                btnBuyNow.setEnabled(true);
                btnBuyNow.setAlpha(1.0f);
            }
            if (layoutStepper != null) layoutStepper.setVisibility(android.view.View.VISIBLE);
            if (tvOutOfStockBadge != null) tvOutOfStockBadge.setVisibility(android.view.View.GONE);
        }
        // Wishlist button is ALWAYS visible regardless of stock

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
            if (addCurrentProductToCart()) {
                Toast.makeText(this, "Added " + currentQuantity + " item(s) to cart", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnBuyNow != null) {
            btnBuyNow.setOnClickListener(v -> {
                if (!addCurrentProductToCart()) return;
                Intent intent = new Intent(this, CheckoutActivity.class);
                intent.putExtra("CART_SUBTOTAL", displayPriceVnd * currentQuantity);
                startActivity(intent);
            });
        }

        // Wishlist toggle via ContentProvider
        btnWishlist.setOnClickListener(v -> toggleWishlist());

        if (btnARTryOn != null) {
            btnARTryOn.setOnClickListener(v -> {
                Intent intent = new Intent(this, ARTryOnActivity.class);
                intent.putExtra("PRODUCT_ID", productId);
                intent.putExtra("PRODUCT_NAME", productName);
                intent.putExtra("PRODUCT_INGREDIENTS", productIngredients);
                startActivity(intent);
            });
        }

        if (btnIngredientScan != null) {
            btnIngredientScan.setOnClickListener(v -> {
                Intent intent = new Intent(this, IngredientScanActivity.class);
                intent.putExtra("PRODUCT_ID", productId);
                intent.putExtra("PRODUCT_NAME", productName);
                startActivity(intent);
            });
        }

        if (btnChatAdvisor != null) {
            btnChatAdvisor.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("PRODUCT_ID", productId);
                intent.putExtra("PRODUCT_NAME", productName);
                startActivity(intent);
            });
        }
    }

    private boolean addCurrentProductToCart() {
        if (productId == null || productId.isEmpty()) {
            Toast.makeText(this, "Product is not ready", Toast.LENGTH_SHORT).show();
            return false;
        }
        String imageUrl = productImage != null ? productImage : "";
        CartItem item = new CartItem(productId, productName, imageUrl, displayPriceVnd, currentQuantity, "");
        databaseHelper.insertOrUpdateCartItem(item);
        return true;
    }

    private void fetchProductDetailIfDeepLinked(androidx.viewpager2.widget.ViewPager2 viewPager,
                                                com.google.android.material.tabs.TabLayout tabIndicator) {
        if (productName != null && !productName.trim().isEmpty()) return;
        if (productId == null || productId.trim().isEmpty()) return;

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        apiService.getProductById(productId).enqueue(new Callback<ApiResponse<Product>>() {
            @Override
            public void onResponse(Call<ApiResponse<Product>> call, Response<ApiResponse<Product>> response) {
                ApiResponse<Product> body = response.body();
                if (!response.isSuccessful() || body == null || body.getData() == null) return;
                applyProductFromApi(body.getData(), viewPager, tabIndicator);
            }

            @Override
            public void onFailure(Call<ApiResponse<Product>> call, Throwable t) {
                Toast.makeText(ProductDetailActivity.this,
                        "Unable to load product details. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyProductFromApi(Product product,
                                     androidx.viewpager2.widget.ViewPager2 viewPager,
                                     com.google.android.material.tabs.TabLayout tabIndicator) {
        if (product == null) return;

        productId = firstNonEmpty(product.getProductId(), product.getId(), productId);
        productName = product.getName();
        productCategory = product.getCategory();
        productImage = product.getThumbnailImages();
        productIngredients = product.getKeyIngredients();
        productPrice = product.getPrice();
        stockQuantity = product.getStockQuantity();
        double activePrice = product.getSalePrice() > 0 ? product.getSalePrice() : productPrice;
        displayPriceVnd = PriceUtils.normalizePrice(activePrice);

        tvProductName.setText(firstNonEmpty(productName, getString(R.string.product_name_placeholder)));
        tvProductCategory.setText(firstNonEmpty(productCategory, getString(R.string.product_category_placeholder)));
        tvProductPrice.setText(PriceUtils.formatPriceVnd(displayPriceVnd));
        tvSuitableSkinTypes.setText(firstNonEmpty(product.getSkinTypeTarget(), "Suitable for all skin types"));
        tvIngredientList.setText(firstNonEmpty(productIngredients, "Ingredient list is not available yet."));
        tvProductDescription.setText(firstNonEmpty(product.getDescriptionShort(), product.getFullDescription(), "Premium TirTir beauty care formula."));

        // Render Description Images
        if (layoutDescriptionImages != null) {
            layoutDescriptionImages.removeAllViews();
            if (product.getDescriptionImages() != null && !product.getDescriptionImages().isEmpty()) {
                for (String imgUrl : product.getDescriptionImages()) {
                    if (imgUrl == null || imgUrl.trim().isEmpty()) continue;
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 0, 0, 16);
                    iv.setLayoutParams(params);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    
                    Glide.with(this)
                            .load(com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(imgUrl))
                            .into(iv);
                            
                    layoutDescriptionImages.addView(iv);
                }
            }
        }

        galleryImages = product.getGalleryImages() != null
                ? new java.util.ArrayList<>(product.getGalleryImages())
                : null;
        loadProductImage(viewPager, tabIndicator);
        updateTotalPrice();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
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
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            iv.setPadding(padding, padding, padding, padding);
            iv.setBackgroundColor(0xFFF5F5F5); // tirtir_off_white
            return new ImageViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull ImageViewHolder holder, int position) {
            String rawUrl = imageUrls.get(position);
            Glide.with(context)
                    .load(buildImageUrl(rawUrl))
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .fitCenter()
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
        return com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(path);
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
                Toast.makeText(this, "Removed from wishlist", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Added to wishlist", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateWishlistIcon(boolean wishlisted) {
        if (wishlisted) {
            btnWishlist.setIconResource(R.drawable.ic_wishlist);
            btnWishlist.setIconTint(ColorStateList.valueOf(getColor(R.color.tirtir_red_primary)));
        } else {
            btnWishlist.setIconResource(R.drawable.ic_wishlist);
            btnWishlist.setIconTint(ColorStateList.valueOf(getColor(R.color.tirtir_text_secondary)));
        }
    }
}
