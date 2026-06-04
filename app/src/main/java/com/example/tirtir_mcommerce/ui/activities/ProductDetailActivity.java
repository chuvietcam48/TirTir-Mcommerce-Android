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

import java.text.NumberFormat;
import java.util.Locale;

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

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private String productId, productName, productCategory, productImage;
    private double productPrice;
    private int stockQuantity;
    private boolean isWishlisted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        databaseHelper = DatabaseHelper.getInstance(this);

        toolbarProductDetail = findViewById(R.id.toolbarProductDetail);
        // Use a single ImageView instead of ViewPager2 for simplicity;
        // ViewPager2 ID still present in XML for future gallery support
        ivProductImage = new android.widget.ImageView(this);

        tvProductCategory = findViewById(R.id.tvProductCategory);
        tvProductName = findViewById(R.id.tvProductName);
        tvProductPrice = findViewById(R.id.tvProductPrice);
        tvSuitableSkinTypes = findViewById(R.id.tvSuitableSkinTypes);
        tvIngredientList = findViewById(R.id.tvIngredientList);
        tvProductDescription = findViewById(R.id.tvProductDescription);
        btnWishlist = findViewById(R.id.btnWishlist);
        btnAddToCart = findViewById(R.id.btnAddToCart);

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

        // Populate views
        if (productName != null)     tvProductName.setText(productName);
        if (productCategory != null) tvProductCategory.setText(productCategory);
        tvProductPrice.setText(currencyFormat.format(productPrice) + " đ");
        tvSuitableSkinTypes.setText(skinTypes != null && !skinTypes.isEmpty() ? skinTypes : "All skin types");
        tvIngredientList.setText(ingredients != null && !ingredients.isEmpty() ? ingredients : "Ingredient list not available.");
        tvProductDescription.setText(description != null && !description.isEmpty() ? description : "Premium skincare formula crafted for your skin.");

        // Load image into ViewPager2 area (use background ImageView hack or directly set background)
        loadProductImage();

        // Stock status
        if (stockQuantity <= 0) {
            btnAddToCart.setEnabled(false);
            btnAddToCart.setText("Hết hàng");
            btnAddToCart.setAlpha(0.5f);
        }

        // Wishlist state
        isWishlisted = checkWishlistStatus();
        updateWishlistIcon(isWishlisted);

        // Add to Cart
        btnAddToCart.setOnClickListener(v -> {
            if (productId == null || productId.isEmpty()) {
                Toast.makeText(this, "Lỗi: không tìm thấy sản phẩm", Toast.LENGTH_SHORT).show();
                return;
            }
            String imageUrl = productImage != null ? productImage : "";
            CartItem item = new CartItem(productId, productName, imageUrl, productPrice, 1, "");
            databaseHelper.insertOrUpdateCartItem(item);
            Toast.makeText(this, "Đã thêm vào giỏ hàng!", Toast.LENGTH_SHORT).show();
        });

        // Wishlist toggle via ContentProvider
        btnWishlist.setOnClickListener(v -> toggleWishlist());
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

    private void loadProductImage() {
        // Find the ViewPager2 and set background via Glide on its parent
        androidx.viewpager2.widget.ViewPager2 viewPager = findViewById(R.id.viewPagerProductImages);
        if (viewPager == null) return;

        // For Phase 1, use a static ImageView overlay drawn on top of ViewPager2 area
        // We load into the ViewPager2 background as a Drawable using Glide
        String imageUrl = buildImageUrl(productImage);
        if (imageUrl.isEmpty()) return;

        Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(@androidx.annotation.NonNull android.graphics.drawable.Drawable resource,
                                                @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        viewPager.setBackground(resource);
                    }

                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        // no-op
                    }
                });
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
            values.put(WishlistContentProvider.COL_PRODUCT_PRICE, productPrice);
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
