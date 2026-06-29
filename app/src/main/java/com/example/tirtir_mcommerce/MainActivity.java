package com.example.tirtir_mcommerce;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

public class MainActivity extends AppCompatActivity {

    private View navTabHome;
    private View navTabRoutine;
    private View navTabScan;
    private View navTabChat;
    private View navTabProfile;

    // Track currently active tab id
    private int activeTabId = R.id.navTabHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Top inset → push content below the status bar
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            // Bottom inset → applied directly to the nav bar so its content clears the gesture indicator
            // The nav bar itself extends to the screen edge (proper EdgeToEdge pattern)
            View navBar = v.findViewById(R.id.customBottomBar);
            if (navBar != null) {
                navBar.setPadding(0, 0, 0, systemBars.bottom);
            }
            return insets;
        });

        // ==========================================
        // KHỞI TẠO CUSTOM BOTTOM NAV
        // ==========================================
        navTabHome    = findViewById(R.id.navTabHome);
        navTabRoutine = findViewById(R.id.navTabRoutine);
        navTabScan    = findViewById(R.id.navTabScan);
        navTabChat    = findViewById(R.id.navTabChat);
        navTabProfile = findViewById(R.id.navTabProfile);

        navTabHome.setOnClickListener(v -> {
            setActiveTab(R.id.navTabHome);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        });

        navTabRoutine.setOnClickListener(v -> {
            setActiveTab(R.id.navTabRoutine);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new RoutineFragment())
                    .commit();
        });

        navTabScan.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this,
                    com.example.tirtir_mcommerce.ui.activities.SkinAnalysisActivity.class);
            startActivity(intent);
        });

        navTabChat.setOnClickListener(v -> {
            setActiveTab(R.id.navTabChat);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ChatFragment())
                    .commit();
        });

        navTabProfile.setOnClickListener(v -> {
            setActiveTab(R.id.navTabProfile);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .commit();
        });

        // Hiển thị HomeFragment lúc mới mở app
        if (savedInstanceState == null) {
            if (!handleIntent(getIntent())) {
                if (getIntent().getBooleanExtra("OPEN_ORDER_HISTORY", false)) {
                    setActiveTab(R.id.navTabProfile);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new OrderHistoryFragment())
                            .addToBackStack("order_history")
                            .commit();
                } else if (getIntent().getBooleanExtra("OPEN_CART", false)) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new CartFragment())
                            .commit();
                } else if (getIntent().getBooleanExtra("OPEN_PROFILE", false)) {
                    setActiveTab(R.id.navTabProfile);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new ProfileFragment())
                            .commit();
                } else {
                    setActiveTab(R.id.navTabHome);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new HomeFragment())
                            .commit();
                }
            }
        }
    }

    /**
     * Updates icon tint and label color for all tabs.
     * Active tab → tirtir_red_primary, inactive → tirtir_text_secondary.
     */
    private void setActiveTab(int tabId) {
        activeTabId = tabId;

        int[][] tabData = {
            {R.id.navTabHome,    R.id.navIconHome,    R.id.navLabelHome},
            {R.id.navTabRoutine, R.id.navIconRoutine, R.id.navLabelRoutine},
            {R.id.navTabChat,    R.id.navIconChat,    R.id.navLabelChat},
            {R.id.navTabProfile, R.id.navIconProfile, R.id.navLabelProfile},
        };

        int activeColor   = ContextCompat.getColor(this, R.color.tirtir_red_primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.tirtir_text_secondary);

        for (int[] row : tabData) {
            int tabViewId   = row[0];
            int iconViewId  = row[1];
            int labelViewId = row[2];
            boolean isActive = (tabViewId == tabId);
            int color = isActive ? activeColor : inactiveColor;

            View tab = findViewById(tabViewId);
            if (tab == null) continue;
            ImageView icon  = tab.findViewById(iconViewId);
            TextView  label = tab.findViewById(labelViewId);
            if (icon  != null) icon.setColorFilter(color);
            if (label != null) label.setTextColor(color);
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
            setActiveTab(R.id.navTabProfile);
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
            setActiveTab(R.id.navTabProfile);
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
        com.example.tirtir_mcommerce.utils.SharedPrefsManager prefs = new com.example.tirtir_mcommerce.utils.SharedPrefsManager(this);
        if (!prefs.isLoggedIn()) {
            android.widget.Toast.makeText(this, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", android.widget.Toast.LENGTH_LONG).show();
            android.content.Intent intent = new android.content.Intent(this, com.example.tirtir_mcommerce.ui.activities.LoginActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }
        updateCartBadge();
    }

    public void updateCartBadge() {
        // Cart badge is now handled directly in HomeFragment's header
        // This method can be kept empty or left for other global badge updates
    }

    public void openHomeWithSearch(String query) {
        setActiveTab(R.id.navTabHome);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment.newInstance(query))
                .commit();
    }
}
