package com.example.tirtir_mcommerce;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.tirtir_mcommerce.ui.fragments.HomeFragment;
import com.example.tirtir_mcommerce.ui.fragments.OrderHistoryFragment;
import com.example.tirtir_mcommerce.ui.fragments.ProfileFragment;
import com.example.tirtir_mcommerce.ui.fragments.CartFragment;
import com.example.tirtir_mcommerce.ui.fragments.ChatFragment;
import com.example.tirtir_mcommerce.ui.fragments.RoutineFragment;
import com.example.tirtir_mcommerce.ui.fragments.LoyaltyFragment;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ==========================================
        // KHỞI TẠO BOTTOM NAVIGATION
        // ==========================================
        bottomNav = findViewById(R.id.bottomNavigationView);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_ai) {
                selectedFragment = new ChatFragment();
            } else if (itemId == R.id.nav_routine) {
                selectedFragment = new RoutineFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            } else if (itemId == R.id.nav_placeholder) {
                return false; // Ignore clicks on the placeholder
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
            }
            return true;
        });

        // FAB Scan Button
        View fabScan = findViewById(R.id.fabScan);
        if (fabScan != null) {
            fabScan.setOnClickListener(v -> {
                // Open Scanner (Ingredient Scan or AR Try On)
                android.content.Intent intent = new android.content.Intent(MainActivity.this, com.example.tirtir_mcommerce.ui.activities.IngredientScanActivity.class);
                startActivity(intent);
            });
        }

        // Hiển thị HomeFragment lúc mới mở app
        if (savedInstanceState == null) {
            if (!handleIntent(getIntent())) {
                if (getIntent().getBooleanExtra("OPEN_ORDER_HISTORY", false)) {
                    bottomNav.setSelectedItemId(R.id.nav_profile);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new OrderHistoryFragment())
                            .addToBackStack("order_history")
                            .commit();
                } else if (getIntent().getBooleanExtra("OPEN_CART", false)) {
                    // Load CartFragment directly since it's removed from bottom nav
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new CartFragment())
                            .commit();
                } else if (getIntent().getBooleanExtra("OPEN_PROFILE", false)) {
                    bottomNav.setSelectedItemId(R.id.nav_profile);
                } else {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(android.content.Intent intent) {
        if (intent == null) return false;

        // Handle VNPAY return deep-link: tirtir://payment?status=success&orderId=...
        android.net.Uri data = intent.getData();
        if (data != null && "tirtir".equals(data.getScheme()) && "payment".equals(data.getHost())) {
            String status  = data.getQueryParameter("status");
            String orderId = data.getQueryParameter("orderId");
            if ("success".equals(status) && orderId != null) {
                android.content.Intent successIntent = new android.content.Intent(this,
                        com.example.tirtir_mcommerce.ui.activities.OrderSuccessActivity.class);
                successIntent.putExtra("ORDER_CODE", orderId);
                startActivity(successIntent);
            } else {
                android.widget.Toast.makeText(this, "Payment cancelled or failed. Your order is pending.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        }

        String navigateTo = intent.getStringExtra("NAVIGATE_TO");
        if ("cart".equals(navigateTo) || "cart_recovery".equals(navigateTo)) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new CartFragment())
                    .addToBackStack(null)
                    .commit();
            return true;
        } else if ("order_history".equals(navigateTo)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new OrderHistoryFragment())
                    .addToBackStack("order_history")
                    .commit();
            return true;
        } else if ("voucher_wallet".equals(navigateTo) || "voucher".equals(navigateTo)) {
            android.content.Intent voucherIntent = new android.content.Intent(this,
                    com.example.tirtir_mcommerce.ui.activities.VoucherWalletActivity.class);
            startActivity(voucherIntent);
            return true;
        } else if ("loyalty".equals(navigateTo) || "loyalty_milestone".equals(navigateTo)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new LoyaltyFragment())
                    .addToBackStack("loyalty")
                    .commit();
            return true;
        } else if ("product_detail".equals(navigateTo) || "restock_alert".equals(navigateTo)) {
            String productId = intent.getStringExtra("PRODUCT_ID");
            android.content.Intent productIntent = new android.content.Intent(this,
                    com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity.class);
            productIntent.putExtra("PRODUCT_ID", productId);
            startActivity(productIntent);
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    public void updateCartBadge() {
        // Cart badge is now handled directly in HomeFragment's header
        // This method can be kept empty or left for other global badge updates
    }

    public void openHomeWithSearch(String query) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment.newInstance(query))
                .commit();
    }
}
