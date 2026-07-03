package com.example.tirtir_mcommerce.ui.activities;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductDetailResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

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

    private TextView tvProductCategory;
    private TextView tvProductName;
    private TextView tvProductPrice;
    private TextView tvProductOriginalPrice;
    private TextView tvProductDiscountBadge;
    private TextView tvProductRating;
    private LinearLayout layoutRatingRow;
    private LinearLayout layoutFeatureBadges;
    private TextView tvSuitableSkinTypes;
    private TextView tvIngredientList;
    private TextView tvProductDescription;
    private MaterialButton btnWishlist;
    private MaterialButton btnARTryOn;
    private Button btnAddToCart;
    private com.google.android.material.chip.ChipGroup chipGroupSkinTypes;
    private MaterialButton btnBuyNow;
    private android.widget.LinearLayout layoutDescriptionImages;

    private DatabaseHelper databaseHelper;



    private String productId, productName, productCategory, productImage, productIngredients;
    private String productParentId, productVolume, productHowToUse, productFullDescription;
    private String selectedProductId, selectedShade;
    private double productPrice;
    private double displayPriceVnd;
    private int stockQuantity;
    private int currentQuantity = 1;
    private boolean isWishlisted = false;
    private java.util.ArrayList<String> galleryImages;
    private final java.util.List<java.util.Map<String, Object>> availableShades = new java.util.ArrayList<>();

    private TextView tvQuantity, tvTotalPrice;
    private ImageButton btnDecreaseQty, btnIncreaseQty;
    private ImageButton btnImagePrevious, btnImageNext;
    private int galleryPageCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        databaseHelper = DatabaseHelper.getInstance(this);

        // Wire back button in the header
        View btnDetailBack = findViewById(R.id.btnDetailBack);
        if (btnDetailBack != null) btnDetailBack.setOnClickListener(v -> onBackPressed());

        // Phase 1 requirement: use ViewPager2 for gallery
        androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.viewPagerProductImages);
        com.google.android.material.tabs.TabLayout tabIndicator = findViewById(R.id.tabIndicatorImages);
        btnImagePrevious = findViewById(R.id.btnImagePrevious);
        btnImageNext = findViewById(R.id.btnImageNext);

        tvProductCategory      = findViewById(R.id.tvProductCategory);
        tvProductName          = findViewById(R.id.tvProductName);
        tvProductPrice         = findViewById(R.id.tvProductPrice);
        tvProductOriginalPrice = findViewById(R.id.tvProductOriginalPrice);
        tvProductDiscountBadge = findViewById(R.id.tvProductDiscountBadge);
        tvProductRating        = findViewById(R.id.tvProductRating);
        layoutRatingRow        = findViewById(R.id.layoutRatingRow);
        layoutFeatureBadges    = findViewById(R.id.layoutFeatureBadges);
        tvSuitableSkinTypes    = findViewById(R.id.tvSuitableSkinTypes);
        chipGroupSkinTypes     = findViewById(R.id.chipGroupSkinTypes);
        tvIngredientList = findViewById(R.id.tvIngredientList);
        tvProductDescription = findViewById(R.id.tvProductDescription);
        btnWishlist = findViewById(R.id.btnWishlist);
        btnARTryOn = findViewById(R.id.btnARTryOn);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnBuyNow = findViewById(R.id.btnBuyNow);
        layoutDescriptionImages = findViewById(R.id.layoutDescriptionImages);
        
        tvQuantity = findViewById(R.id.tvQuantity);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        btnDecreaseQty = findViewById(R.id.btnDecreaseQty);
        btnIncreaseQty = findViewById(R.id.btnIncreaseQty);

        if (btnImagePrevious != null) {
            btnImagePrevious.setOnClickListener(v -> {
                if (viewPager != null && viewPager.getCurrentItem() > 0) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
                }
            });
        }
        if (btnImageNext != null) {
            btnImageNext.setOnClickListener(v -> {
                if (viewPager != null && viewPager.getCurrentItem() < galleryPageCount - 1) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
                }
            });
        }
        if (viewPager != null) {
            viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateGalleryControls(position);
                }
            });
        }

        // Read from intent
        productId       = getIntent().getStringExtra("PRODUCT_ID");
        productName     = getIntent().getStringExtra("PRODUCT_NAME");
        productPrice    = getIntent().getDoubleExtra("PRODUCT_PRICE", 0.0);
        productCategory = getIntent().getStringExtra("PRODUCT_CATEGORY");
        productImage    = getIntent().getStringExtra("PRODUCT_IMAGE");
        productParentId = getIntent().getStringExtra("PRODUCT_PARENT_ID");
        productVolume   = getIntent().getStringExtra("PRODUCT_VOLUME");
        productHowToUse = getIntent().getStringExtra("PRODUCT_HOW_TO_USE");
        productFullDescription = getIntent().getStringExtra("PRODUCT_FULL_DESCRIPTION");
        selectedProductId = productId;
        selectedShade = firstNonEmpty(productVolume, "Standard");
        stockQuantity   = getIntent().getIntExtra("PRODUCT_STOCK", 100);
        String skinTypes        = getIntent().getStringExtra("PRODUCT_SKIN_TYPES");
        String isSkincareIntent = getIntent().getStringExtra("PRODUCT_IS_SKINCARE");
        productIngredients      = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
        String description      = getIntent().getStringExtra("PRODUCT_DESCRIPTION");

        // Normalize price: use PriceUtils (safe: < 1000 => USD-style => x25000, >= 1000 => already VND)
        double salePrice = getIntent().getDoubleExtra("PRODUCT_SALE_PRICE", 0.0);
        double activePrice = (salePrice > 0) ? salePrice : productPrice;
        displayPriceVnd = com.example.tirtir_mcommerce.utils.PriceUtils.normalizePrice(activePrice);

        // Populate views
        if (productName != null)     tvProductName.setText(productName);
        if (productCategory != null) tvProductCategory.setText(productCategory);
        tvProductPrice.setText(PriceUtils.formatPriceUsd(displayPriceVnd));
        bindSalePriceDetail(productPrice, salePrice);
        tvSuitableSkinTypes.setText(skinTypes != null && !skinTypes.isEmpty() ? skinTypes : "Suitable for all skin types");
        applyConditionalSections(productCategory, isSkincareIntent);
        populateSkinTypeChips(skinTypes);
        tvIngredientList.setText(productIngredients != null && !productIngredients.isEmpty()
                ? productIngredients
                : "Ingredient list is not available yet.");
        tvProductDescription.setText(firstNonEmpty(productFullDescription, description, "Premium TirTir beauty care formula."));
        TextView tvHowToUse = findViewById(R.id.tvHowToUse);
        tvHowToUse.setText(firstNonEmpty(productHowToUse, "Application directions are not available yet."));

        galleryImages = getIntent().getStringArrayListExtra("PRODUCT_GALLERY");
        loadProductImage(viewPager, tabIndicator);
        fetchProductDetailIfDeepLinked(viewPager, tabIndicator);
        fetchShades();
        fetchReviews();
        setupAccordions();

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

        btnAddToCart.setOnClickListener(v -> showProductOptions(false));

        if (btnBuyNow != null) {
            btnBuyNow.setOnClickListener(v -> {
                showProductOptions(true);
            });
        }

        // Wishlist toggle via ContentProvider
        btnWishlist.setOnClickListener(v -> toggleWishlist());

        // Ask AI access → CHAT tab in bottom navigation
    }

    private boolean addCurrentProductToCart() {
        if (productId == null || productId.isEmpty()) {
            Toast.makeText(this, "Product is not ready", Toast.LENGTH_SHORT).show();
            return false;
        }
        String cartProductId = firstNonEmpty(selectedProductId, productId);
        String imageUrl = productImage != null ? productImage : "";
        CartItem item = new CartItem(cartProductId, productName, imageUrl, displayPriceVnd,
                currentQuantity, firstNonEmpty(selectedShade, productVolume, "Standard"));
        CartRepository repository = new CartRepository(this);
        repository.addToCartLocal(item);
        repository.syncItemToServer(item, null, error -> { });
        return true;
    }

    private void fetchProductDetailIfDeepLinked(androidx.viewpager2.widget.ViewPager2 viewPager,
                                                com.google.android.material.tabs.TabLayout tabIndicator) {
        if (productId == null || productId.trim().isEmpty()) return;

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        apiService.getProductById(productId).enqueue(new Callback<ProductDetailResponse>() {
            @Override
            public void onResponse(Call<ProductDetailResponse> call, Response<ProductDetailResponse> response) {
                ProductDetailResponse body = response.body();
                if (!response.isSuccessful() || body == null) return;
                applyProductFromApi(body.getProduct(), viewPager, tabIndicator);
            }

            @Override
            public void onFailure(Call<ProductDetailResponse> call, Throwable t) {
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
        productParentId = product.getParentId();
        productVolume = product.getVolumeSize();
        productHowToUse = product.getHowToUse();
        productFullDescription = product.getFullDescription();
        productPrice = product.getPrice();
        stockQuantity = product.getStockQuantity();
        double activePrice = product.getSalePrice() > 0 ? product.getSalePrice() : productPrice;
        displayPriceVnd = PriceUtils.normalizePrice(activePrice);

        tvProductName.setText(firstNonEmpty(productName, getString(R.string.product_name_placeholder)));
        tvProductCategory.setText(firstNonEmpty(productCategory, getString(R.string.product_category_placeholder)));
        tvProductPrice.setText(PriceUtils.formatPriceUsd(displayPriceVnd));
        tvSuitableSkinTypes.setText(firstNonEmpty(product.getSkinTypeTarget(), "Suitable for all skin types"));
        tvIngredientList.setText(firstNonEmpty(productIngredients, "Ingredient list is not available yet."));
        tvProductDescription.setText(firstNonEmpty(product.getFullDescription(), product.getDescriptionShort(), "Premium TirTir beauty care formula."));
        TextView tvHowToUse = findViewById(R.id.tvHowToUse);
        tvHowToUse.setText(firstNonEmpty(productHowToUse, "Application directions are not available yet."));

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

        bindSalePriceDetail(productPrice, product.getSalePrice());
        bindRatingAndBadges(product);
        applyConditionalSections(product.getCategory(), product.getIsSkincare());
        populateSkinTypeChips(product.getSkinTypeTarget());

        galleryImages = product.getGalleryImages() != null
                ? new java.util.ArrayList<>(product.getGalleryImages())
                : null;
        loadProductImage(viewPager, tabIndicator);
        updateTotalPrice();
        fetchShades();
    }

    private void bindRatingAndBadges(Product product) {
        if (layoutRatingRow != null) {
            double rating = product.getRating();
            if (rating > 0) {
                if (tvProductRating != null) {
                    int count = product.getReviewCount();
                    String ratingText = String.format(java.util.Locale.ENGLISH, "%.1f", rating)
                            + (count > 0 ? " (" + count + ")" : "");
                    tvProductRating.setText(ratingText);
                }
                layoutRatingRow.setVisibility(View.VISIBLE);
            } else {
                layoutRatingRow.setVisibility(View.GONE);
            }
        }

        if (layoutFeatureBadges != null) {
            boolean vegan = product.isVeganFormula();
            boolean derma = product.isDermatologistTested();
            if (vegan || derma) {
                layoutFeatureBadges.setVisibility(View.VISIBLE);
                View cardVegan = findViewById(R.id.cardVeganFormula);
                View cardDerma = findViewById(R.id.cardDermatologistTested);
                if (cardVegan != null) cardVegan.setVisibility(vegan ? View.VISIBLE : View.GONE);
                if (cardDerma != null) cardDerma.setVisibility(derma ? View.VISIBLE : View.GONE);
            } else {
                layoutFeatureBadges.setVisibility(View.GONE);
            }
        }
    }

    private void bindSalePriceDetail(double basePrice, double salePrice) {
        boolean onSale = salePrice > 0 && salePrice < basePrice;
        if (tvProductOriginalPrice != null) {
            if (onSale) {
                double normalizedBase = com.example.tirtir_mcommerce.utils.PriceUtils.normalizePrice(basePrice);
                tvProductOriginalPrice.setText(PriceUtils.formatPriceUsd(normalizedBase));
                tvProductOriginalPrice.setPaintFlags(
                        tvProductOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                tvProductOriginalPrice.setVisibility(android.view.View.VISIBLE);
            } else {
                tvProductOriginalPrice.setVisibility(android.view.View.GONE);
            }
        }
        if (tvProductDiscountBadge != null) {
            if (onSale) {
                int pct = (int) Math.round((1 - salePrice / basePrice) * 100);
                tvProductDiscountBadge.setText("-" + pct + "%");
                tvProductDiscountBadge.setVisibility(android.view.View.VISIBLE);
            } else {
                tvProductDiscountBadge.setVisibility(android.view.View.GONE);
            }
        }
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
            tvTotalPrice.setText(PriceUtils.formatPriceUsd(total));
        }
    }

    private void setupAccordions() {
        bindAccordion(R.id.tvHeaderIngredients, R.id.tvIngredientList, "KEY INGREDIENTS");
        bindAccordion(R.id.tvHeaderDescription, R.id.tvProductDescription, "DESCRIPTION");
        bindAccordion(R.id.tvHeaderHowToUse, R.id.tvHowToUse, "HOW TO USE");
        bindAccordion(R.id.tvHeaderReviews, R.id.tvReviewSummary, "REVIEWS");
    }

    private void bindAccordion(int headerId, int contentId, String label) {
        TextView header = findViewById(headerId);
        View content = findViewById(contentId);
        if (header == null || content == null) return;
        header.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText(label + (show ? "  −" : "  +"));
        });
    }

    private void fetchShades() {
        if (productId == null || productId.trim().isEmpty()) return;
        String byProduct = (productParentId == null || productParentId.trim().isEmpty()) ? productId : null;
        String byParent = (productParentId == null || productParentId.trim().isEmpty()) ? null : productParentId;
        RetrofitClient.getClient().create(ApiService.class)
                .getShades(byProduct, byParent, 100)
                .enqueue(new Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<java.util.List<java.util.Map<String, Object>>> call,
                                           Response<java.util.List<java.util.Map<String, Object>>> response) {
                        availableShades.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            availableShades.addAll(response.body());
                        }
                        renderInlineShades();
                    }

                    @Override public void onFailure(Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) { }
                });
    }

    private void renderInlineShades() {
        LinearLayout container = findViewById(R.id.layoutShadesContainer);
        TextView tvSelectedName = findViewById(R.id.tvSelectedShadeName);
        if (container == null || tvSelectedName == null) return;
        
        container.removeAllViews();
        boolean showShade = !availableShades.isEmpty() && isShadeProduct();
        View row = findViewById(R.id.layoutShadeSelection);
        if (row != null) row.setVisibility(showShade ? View.VISIBLE : View.GONE);
        
        if (btnARTryOn != null) {
            btnARTryOn.setVisibility(!availableShades.isEmpty() ? View.VISIBLE : View.GONE);
            if (!availableShades.isEmpty()) {
                btnARTryOn.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ARTryOnActivity.class);
                    intent.putExtra("PRODUCT_ID", productId);
                    intent.putExtra("PRODUCT_NAME", productName);
                    startActivity(intent);
                });
            }
        }

        if (!showShade) return;
        
        float density = getResources().getDisplayMetrics().density;
        
        for (int i = 0; i < availableShades.size(); i++) {
            java.util.Map<String, Object> shade = availableShades.get(i);
            String shadeName = mapText(shade, "Shade_Name", "Shade_Code", "Shade " + (i+1));
            String hexCode = mapText(shade, "Hex_Code", "shade_color_hex", null);
            
            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (44 * density), (int) (44 * density));
            lp.setMarginEnd((int) (8 * density));
            frame.setLayoutParams(lp);
            
            ImageView colorCircle = new ImageView(this);
            android.widget.FrameLayout.LayoutParams ivLp = new android.widget.FrameLayout.LayoutParams(
                (int) (36 * density), (int) (36 * density));
            ivLp.gravity = android.view.Gravity.CENTER;
            colorCircle.setLayoutParams(ivLp);
            
            int color = android.graphics.Color.LTGRAY;
            if (hexCode != null && !hexCode.trim().isEmpty()) {
                if (!hexCode.startsWith("#")) hexCode = "#" + hexCode;
                try { color = android.graphics.Color.parseColor(hexCode); } catch (Exception ignored) {}
            }
            
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke((int)(1 * density), android.graphics.Color.LTGRAY);
            colorCircle.setImageDrawable(drawable);
            frame.addView(colorCircle);
            
            ImageView ring = new ImageView(this);
            ring.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            android.graphics.drawable.GradientDrawable ringDrawable = new android.graphics.drawable.GradientDrawable();
            ringDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ringDrawable.setColor(android.graphics.Color.TRANSPARENT);
            
            boolean isSelected = shadeName.equals(selectedShade);
            if (isSelected || (selectedShade == null && i == 0)) {
                if (selectedShade == null) {
                    selectedShade = shadeName;
                    selectedProductId = mapText(shade, "Product_ID", "Shade_ID", productId);
                }
                ringDrawable.setStroke((int) (2 * density), android.graphics.Color.parseColor("#A12E2B")); // Primary red
                tvSelectedName.setText(shadeName);
            } else {
                ringDrawable.setStroke(0, android.graphics.Color.TRANSPARENT);
            }
            ring.setImageDrawable(ringDrawable);
            frame.addView(ring);
            
            frame.setOnClickListener(v -> {
                selectedShade = shadeName;
                selectedProductId = mapText(shade, "Product_ID", "Shade_ID", productId);
                renderInlineShades(); // Re-render to update selection ring
            });
            
            container.addView(frame);
        }
    }

    private void fetchReviews() {
        if (productId == null || productId.trim().isEmpty()) return;
        RetrofitClient.getClient().create(ApiService.class).getProductReviews(productId, 1, 3)
                .enqueue(new Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<java.util.Map<String, Object>> call,
                                           Response<java.util.Map<String, Object>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        Object raw = response.body().get("data");
                        TextView summary = findViewById(R.id.tvReviewSummary);
                        if (!(raw instanceof java.util.List) || ((java.util.List<?>) raw).isEmpty()) {
                            summary.setText("No reviews yet. Be the first verified buyer to review this product.");
                            return;
                        }
                        StringBuilder text = new StringBuilder();
                        for (Object value : (java.util.List<?>) raw) {
                            if (!(value instanceof java.util.Map)) continue;
                            java.util.Map<?, ?> review = (java.util.Map<?, ?>) value;
                            Object rating = review.get("rating");
                            Object title = review.get("title");
                            Object comment = review.get("comment");
                            if (text.length() > 0) text.append("\n\n");
                            text.append(rating == null ? "★" : rating + " ★");
                            if (title != null) text.append("  ").append(title);
                            if (comment != null) text.append("\n").append(comment);
                        }
                        if (text.length() > 0) summary.setText(text.toString());
                    }

                    @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) { }
                });
    }

    private void showProductOptions(boolean buyNowPreferred) {
        if (stockQuantity <= 0) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_product_options, null, false);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.tvOptionProductName)).setText(firstNonEmpty(productName, "TirTir product"));
        ((TextView) sheet.findViewById(R.id.tvOptionPrice)).setText(PriceUtils.formatPriceUsd(displayPriceVnd));
        ImageView image = sheet.findViewById(R.id.ivOptionProduct);
        int fallback = resolveProductFallback();
        Glide.with(this).load(buildImageUrl(productImage)).placeholder(fallback).error(fallback).fallback(fallback).into(image);

        ChipGroup group = sheet.findViewById(R.id.chipGroupOptions);
        TextView label = sheet.findViewById(R.id.tvOptionVariantLabel);
        if (availableShades.isEmpty()) {
            label.setText("VARIANT");
            Chip chip = buildOptionChip(firstNonEmpty(productVolume, "Standard"), null);
            chip.setChecked(true);
            group.addView(chip);
        } else {
            label.setText("SELECT SHADE");
            for (int index = 0; index < availableShades.size(); index++) {
                java.util.Map<String, Object> shade = availableShades.get(index);
                String shadeName = mapText(shade, "Shade_Name", "Shade_Code", "Shade");
                String hexCode = mapText(shade, "Hex_Code", "", null);
                Chip chip = buildOptionChip(shadeName, hexCode);
                chip.setTag(shade);
                chip.setChecked(index == 0 && (selectedShade == null || selectedShade.equals(productVolume)));
                if (shadeName.equals(selectedShade)) chip.setChecked(true);
                chip.setOnCheckedChangeListener((button, checked) -> {
                    if (!checked) return;
                    selectedShade = shadeName;
                    selectedProductId = mapText(shade, "Product_ID", "Shade_ID", productId);
                });
                group.addView(chip);
            }
            if (group.getCheckedChipId() == View.NO_ID && group.getChildCount() > 0) {
                ((Chip) group.getChildAt(0)).setChecked(true);
            }
        }

        final int[] quantity = {Math.max(1, currentQuantity)};
        TextView quantityText = sheet.findViewById(R.id.tvOptionQuantity);
        quantityText.setText(String.valueOf(quantity[0]));
        sheet.findViewById(R.id.btnOptionDecrease).setOnClickListener(v -> {
            if (quantity[0] > 1) quantity[0]--;
            quantityText.setText(String.valueOf(quantity[0]));
        });
        sheet.findViewById(R.id.btnOptionIncrease).setOnClickListener(v -> {
            if (quantity[0] < Math.max(1, stockQuantity)) quantity[0]++;
            quantityText.setText(String.valueOf(quantity[0]));
        });
        sheet.findViewById(R.id.btnCloseOptions).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnOptionAddCart).setOnClickListener(v -> {
            currentQuantity = quantity[0];
            if (addCurrentProductToCart()) {
                Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        sheet.findViewById(R.id.btnOptionBuyNow).setOnClickListener(v -> {
            currentQuantity = quantity[0];
            if (!addCurrentProductToCart()) return;
            dialog.dismiss();
            Intent intent = new Intent(this, CheckoutActivity.class);
            intent.putExtra("CART_SUBTOTAL", displayPriceVnd * currentQuantity);
            startActivity(intent);
        });
        if (buyNowPreferred) sheet.findViewById(R.id.btnOptionBuyNow).requestFocus();
        dialog.show();
    }

    private Chip buildOptionChip(String text, String hexCode) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChipBackgroundColorResource(R.color.chip_selector);
        chip.setTextColor(getColor(R.color.tirtir_text_primary));
        chip.setChipStrokeColorResource(R.color.chip_stroke_profile);
        chip.setChipStrokeWidth(1f);
        
        if (hexCode != null && !hexCode.trim().isEmpty()) {
            if (!hexCode.startsWith("#")) hexCode = "#" + hexCode;
            try {
                int color = android.graphics.Color.parseColor(hexCode);
                android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
                drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                drawable.setColor(color);
                drawable.setStroke(2, android.graphics.Color.LTGRAY);
                drawable.setSize(48, 48); // approx 16dp
                chip.setChipIcon(drawable);
                chip.setChipIconVisible(true);
                chip.setIconStartPadding(12f);
                chip.setChipIconSize(48f);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
        
        return chip;
    }

    private String mapText(java.util.Map<String, Object> map, String firstKey, String secondKey, String fallback) {
        Object first = map.get(firstKey);
        if (first != null && !String.valueOf(first).trim().isEmpty()) return String.valueOf(first);
        Object second = map.get(secondKey);
        if (second != null && !String.valueOf(second).trim().isEmpty()) return String.valueOf(second);
        return fallback;
    }

    private boolean isShadeProduct() {
        String value = ((productName == null ? "" : productName) + " "
                + (productCategory == null ? "" : productCategory)).toLowerCase(java.util.Locale.ENGLISH);
        return value.contains("cushion") || value.contains("foundation") || value.contains("concealer")
                || value.contains("tint") || value.contains("lip") || value.contains("blush")
                || value.contains("contour") || value.contains("base");
    }

    private boolean isMakeupCategory(String category) {
        if (category == null) return false;
        String lower = category.toLowerCase(java.util.Locale.ENGLISH);
        return lower.contains("makeup") || lower.contains("foundation")
                || lower.contains("cushion") || lower.contains("blush")
                || lower.contains("lip") || lower.contains("mascara")
                || lower.contains("eyeliner") || lower.contains("concealer")
                || lower.contains("eyeshadow") || lower.contains("contour");
    }

    private void applyConditionalSections(String category, String isSkincare) {
        boolean showSkinTypes;
        if ("FALSE".equalsIgnoreCase(isSkincare)) {
            showSkinTypes = false;
        } else if ("TRUE".equalsIgnoreCase(isSkincare)) {
            showSkinTypes = true;
        } else {
            showSkinTypes = !isMakeupCategory(category);
        }
        View skinSection = findViewById(R.id.layoutSkinTypesSection);
        if (skinSection != null) {
            skinSection.setVisibility(showSkinTypes ? View.VISIBLE : View.GONE);
        }
    }

    private void populateSkinTypeChips(String skinTypeTarget) {
        if (chipGroupSkinTypes == null) return;
        chipGroupSkinTypes.removeAllViews();
        String[] types;
        if (skinTypeTarget != null && !skinTypeTarget.trim().isEmpty()) {
            types = skinTypeTarget.split("[,;]+");
        } else {
            types = new String[]{"All skin types"};
        }
        float density = getResources().getDisplayMetrics().density;
        for (String type : types) {
            String label = type.trim();
            if (label.isEmpty()) continue;
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(label);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipBackgroundColorResource(R.color.tirtir_off_white);
            chip.setTextColor(getColor(R.color.tirtir_text_secondary));
            chip.setChipStrokeColorResource(R.color.tirtir_rose_outline);
            chip.setChipStrokeWidth(1.5f * density);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            chipGroupSkinTypes.addView(chip);
        }
    }

    private int resolveProductFallback() {
        String value = ((productName == null ? "" : productName) + " "
                + (productCategory == null ? "" : productCategory)).toLowerCase(java.util.Locale.ENGLISH);
        if (value.contains("gift card")) return R.drawable.tirtir_gift_card;
        if (value.contains("matcha")) return R.drawable.tirtir_matcha_set;
        return R.drawable.ic_product_placeholder;
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
        // Thumbnail (main product image) always first so the customer recognises it immediately
        if (productImage != null && !productImage.isEmpty()) {
            imagesToDisplay.add(productImage);
        }
        // Append gallery images that aren't already the thumbnail
        if (galleryImages != null) {
            for (String img : galleryImages) {
                if (img != null && !img.isEmpty() && !img.equals(productImage)) {
                    imagesToDisplay.add(img);
                }
            }
        }

        if (imagesToDisplay.isEmpty()) {
            galleryPageCount = 0;
            updateGalleryControls(0);
            viewPager.setVisibility(android.view.View.GONE);
            if (ivStatic != null) {
                ivStatic.setVisibility(android.view.View.VISIBLE);
                ivStatic.setImageResource(resolveProductFallback());
            }
            return;
        }

        ImageGalleryAdapter adapter = new ImageGalleryAdapter(this, imagesToDisplay);
        viewPager.setAdapter(adapter);
        galleryPageCount = imagesToDisplay.size();
        viewPager.setCurrentItem(0, false);
        updateGalleryControls(0);

        if (tabIndicator != null && imagesToDisplay.size() > 1) {
            tabIndicator.setVisibility(android.view.View.VISIBLE);
            new com.google.android.material.tabs.TabLayoutMediator(tabIndicator, viewPager,
                    (tab, position) -> {}
            ).attach();
        } else if (tabIndicator != null) {
            tabIndicator.setVisibility(android.view.View.GONE);
        }
    }

    private void updateGalleryControls(int position) {
        boolean hasMultiple = galleryPageCount > 1;
        if (btnImagePrevious != null) {
            btnImagePrevious.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
            btnImagePrevious.setEnabled(position > 0);
            btnImagePrevious.setAlpha(position > 0 ? 1f : 0.35f);
        }
        if (btnImageNext != null) {
            btnImageNext.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
            boolean canMoveNext = position < galleryPageCount - 1;
            btnImageNext.setEnabled(canMoveNext);
            btnImageNext.setAlpha(canMoveNext ? 1f : 0.35f);
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
