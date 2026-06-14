package com.example.tirtir_mcommerce;

import android.os.Bundle;
import android.util.Log;

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
            } else if (itemId == R.id.nav_cart) {
                selectedFragment = new CartFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, selectedFragment)
                        .commit();
            }
            return true;
        });

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
                    bottomNav.setSelectedItemId(R.id.nav_cart);
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
        if ("cart".equals(navigateTo)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_cart);
            return true;
        } else if ("order_history".equals(navigateTo)) {
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new OrderHistoryFragment())
                    .addToBackStack("order_history")
                    .commit();
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
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            int cartCount = DatabaseHelper.getInstance(this).getCartCount();
            if (cartCount > 0) {
                BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_cart);
                badge.setVisible(true);
                badge.setNumber(cartCount);
                badge.setBackgroundColor(getResources().getColor(R.color.tirtir_red_primary, null));
            } else {
                bottomNav.removeBadge(R.id.nav_cart);
            }
        }
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
