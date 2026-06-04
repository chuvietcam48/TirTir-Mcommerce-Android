package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;

import java.text.NumberFormat;
import java.util.Locale;

public class ProductDetailActivity extends AppCompatActivity {

    private Toolbar toolbarProductDetail;
    private ViewPager2 viewPagerProductImages;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        databaseHelper = DatabaseHelper.getInstance(this);

        toolbarProductDetail = findViewById(R.id.toolbarProductDetail);
        viewPagerProductImages = findViewById(R.id.viewPagerProductImages);
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
        }
        toolbarProductDetail.setNavigationOnClickListener(v -> finish());

        // Get data from intent
        productId = getIntent().getStringExtra("PRODUCT_ID");
        productName = getIntent().getStringExtra("PRODUCT_NAME");
        productPrice = getIntent().getDoubleExtra("PRODUCT_PRICE", 0.0);
        productCategory = getIntent().getStringExtra("PRODUCT_CATEGORY");
        productImage = getIntent().getStringExtra("PRODUCT_IMAGE");
        stockQuantity = getIntent().getIntExtra("PRODUCT_STOCK", 100);
        
        String skinTypes = getIntent().getStringExtra("PRODUCT_SKIN_TYPES");
        String ingredients = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
        String description = getIntent().getStringExtra("PRODUCT_DESCRIPTION");

        if (productName != null) tvProductName.setText(productName);
        if (productCategory != null) tvProductCategory.setText(productCategory);
        tvProductPrice.setText(currencyFormat.format(productPrice) + " đ");

        tvSuitableSkinTypes.setText(skinTypes != null && !skinTypes.isEmpty() ? skinTypes : "All skin types");
        tvIngredientList.setText(ingredients != null && !ingredients.isEmpty() ? ingredients : "No ingredients information.");
        tvProductDescription.setText(description != null && !description.isEmpty() ? description : "Experience the ultimate luxury with our premium formula.");

        if (stockQuantity <= 0) {
            btnAddToCart.setEnabled(false);
            btnAddToCart.setText("Hết hàng");
            btnAddToCart.setBackgroundColor(0xFFBDBDBD); // gray
        }

        btnAddToCart.setOnClickListener(v -> {
            if (productId != null) {
                CartItem item = new CartItem(productId, productName, productImage, productPrice, 1, "");
                databaseHelper.insertOrUpdateCartItem(item);
                Toast.makeText(this, "Added to cart!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error: Missing Product ID", Toast.LENGTH_SHORT).show();
            }
        });

        // Add a simple SharedPreferences wishlist toggle
        boolean isWishlist = getSharedPreferences("WISHLIST", MODE_PRIVATE).getBoolean(productId, false);
        updateWishlistIcon(isWishlist);

        btnWishlist.setOnClickListener(v -> {
            boolean current = getSharedPreferences("WISHLIST", MODE_PRIVATE).getBoolean(productId, false);
            getSharedPreferences("WISHLIST", MODE_PRIVATE).edit().putBoolean(productId, !current).apply();
            updateWishlistIcon(!current);
            Toast.makeText(this, !current ? "Added to wishlist!" : "Removed from wishlist", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateWishlistIcon(boolean isWishlist) {
        if (isWishlist) {
            btnWishlist.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            btnWishlist.setImageResource(android.R.drawable.btn_star_big_off);
        }
    }
}
