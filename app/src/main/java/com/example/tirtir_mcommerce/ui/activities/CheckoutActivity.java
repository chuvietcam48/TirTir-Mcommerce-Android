package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.ShippingAddress;
import com.example.tirtir_mcommerce.repository.OrderRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * SCR-16 CheckoutActivity — Thanh toán
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────
 * placeOrderWithApi():
 *   → POST /api/v1/orders/create via OrderRepository
 *   → Body: { shippingAddress, paymentMethod }
 *   → On success: clear local cart → OrderSuccessActivity with real orderId
 *   → On failure (backend not ready): falls back to local mock order code
 *
 * TODO: Remove handleMockPlaceOrder() when backend order API is confirmed.
 *
 * Cart data: local SQLite cart_items (TASK 5).
 * Shipping: 30,000 VND placeholder (TODO: Viettel Post SOAP).
 *
 * Sprint 1.3
 */
public class CheckoutActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etPhone, etStreet, etDistrict, etCity, etNote;
    private RadioGroup rgPaymentMethod;
    private TextView tvCheckoutSubtotal, tvCheckoutShipping, tvCheckoutTotal;
    private MaterialButton btnPlaceOrder;
    private ProgressBar progressPlaceOrder;

    private OrderRepository orderRepository;
    private DatabaseHelper databaseHelper;
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private ViettelPostSoapClient viettelPostClient;

    private double shippingFee = 0;
    private double cartSubtotal = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        Toolbar toolbar = findViewById(R.id.toolbarCheckout);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Checkout");
        }

        orderRepository = new OrderRepository(this);
        databaseHelper  = DatabaseHelper.getInstance(this);
        viettelPostClient = new ViettelPostSoapClient(this);

        bindViews();
        loadCartTotals();
        calculateShippingFee();
        setupPlaceOrder();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void bindViews() {
        etFullName           = findViewById(R.id.etFullName);
        etPhone              = findViewById(R.id.etPhone);
        etStreet             = findViewById(R.id.etStreet);
        etDistrict           = findViewById(R.id.etDistrict);
        etCity               = findViewById(R.id.etCity);
        etNote               = findViewById(R.id.etNote);
        rgPaymentMethod      = findViewById(R.id.rgPaymentMethod);
        tvCheckoutSubtotal   = findViewById(R.id.tvCheckoutSubtotal);
        tvCheckoutShipping   = findViewById(R.id.tvCheckoutShipping);
        tvCheckoutTotal      = findViewById(R.id.tvCheckoutTotal);
        btnPlaceOrder        = findViewById(R.id.btnPlaceOrder);
        progressPlaceOrder   = findViewById(R.id.progressPlaceOrder);
    }

    private void loadCartTotals() {
        // Receive subtotal passed from CartFragment (TASK 5)
        cartSubtotal = getIntent().getDoubleExtra("CART_SUBTOTAL", 0.0);

        // Fallback: recalculate from SQLite if not passed
        if (cartSubtotal == 0) {
            List<CartItem> items = databaseHelper.getCartItems();
            for (CartItem item : items) {
                cartSubtotal += item.getPrice() * item.getQuantity();
            }
        }

        updateTotalsUI();
    }

    private void updateTotalsUI() {
        double total = cartSubtotal + shippingFee;
        tvCheckoutSubtotal.setText(currencyFormat.format(cartSubtotal) + " đ");
        tvCheckoutShipping.setText(currencyFormat.format(shippingFee) + " đ");
        tvCheckoutTotal.setText(currencyFormat.format(total) + " đ");
    }

    private void calculateShippingFee() {
        // Assume default weight is 500g, from HCM to HN
        tvCheckoutShipping.setText("Đang tính...");
        viettelPostClient.getShippingFees("HCM", "HN", 500, new ViettelPostSoapClient.Callback() {
            @Override
            public void onSuccess(List<com.example.tirtir_mcommerce.model.ShippingOption> options) {
                runOnUiThread(() -> {
                    if (!options.isEmpty()) {
                        shippingFee = options.get(0).getPrice();
                        updateTotalsUI();
                    } else {
                        shippingFee = 30000; // default if empty
                        updateTotalsUI();
                    }
                });
            }

            @Override
            public void onFallback(List<com.example.tirtir_mcommerce.model.ShippingOption> options) {
                runOnUiThread(() -> {
                    Toast.makeText(CheckoutActivity.this, "Đang dùng phí vận chuyển ước tính (Fallback)", Toast.LENGTH_SHORT).show();
                    if (!options.isEmpty()) {
                        shippingFee = options.get(0).getPrice();
                    } else {
                        shippingFee = 30000;
                    }
                    updateTotalsUI();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(CheckoutActivity.this, message, Toast.LENGTH_SHORT).show();
                    shippingFee = 30000; // default
                    updateTotalsUI();
                });
            }
        });
    }

    private void setupPlaceOrder() {
        btnPlaceOrder.setOnClickListener(v -> {
            if (!validateForm()) return;
            placeOrderWithApi();
        });
    }

    // ===========================
    // REAL API ORDER PLACEMENT
    // ===========================

    /**
     * Builds CreateOrderRequest from form fields and calls
     * POST /api/v1/orders/create via OrderRepository.
     *
     * On API success  → clear local cart → OrderSuccessActivity
     * On API failure  → handleMockPlaceOrder() (local order code fallback)
     *
     * Requires valid JWT token (user must be logged in).
     * TODO: Remove mock fallback when backend order endpoint is confirmed.
     */
    private void placeOrderWithApi() {
        // Check if user is logged in (has token)
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        boolean hasToken = prefs.isLoggedIn();

        if (!hasToken) {
            Toast.makeText(this, "Vui lòng đăng nhập để đặt hàng", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Build shipping address from form
        // ShippingAddress has: fullName, phone, address, city
        // We combine street + district into the address field
        ShippingAddress shippingAddress = new ShippingAddress();
        shippingAddress.setFullName(getText(etFullName));
        shippingAddress.setPhone(getText(etPhone));
        String combinedAddress = getText(etStreet);
        String district = getText(etDistrict);
        if (!district.isEmpty()) combinedAddress += ", " + district;
        shippingAddress.setAddress(combinedAddress);
        shippingAddress.setCity(getText(etCity));

        // Determine payment method from radio group
        String paymentMethod = getSelectedPaymentMethod();

        // Build order request (compatible with POST /api/v1/orders/create)
        CreateOrderRequest request = new CreateOrderRequest(shippingAddress, paymentMethod);

        orderRepository.placeOrder(request,
                orderResponse -> {
                    // SUCCESS: real order created
                    showLoading(false);
                    databaseHelper.clearCart();

                    String orderId = orderResponse.getId() != null ? orderResponse.getId() : "TT-" + System.currentTimeMillis();
                    double total = cartSubtotal + shippingFee;
                    goToOrderSuccess(orderId, total);
                },
                errorMessage -> {
                    showLoading(false);
                    Toast.makeText(this, "Order failed: " + errorMessage, Toast.LENGTH_LONG).show();
                }
        );
    }



    // ===========================
    // HELPERS
    // ===========================

    private boolean validateForm() {
        boolean valid = true;
        if (TextUtils.isEmpty(getText(etFullName))) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(getText(etPhone))) {
            etPhone.setError("Phone number is required");
            if (valid) etPhone.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(getText(etStreet))) {
            etStreet.setError("Street address is required");
            if (valid) etStreet.requestFocus();
            valid = false;
        }
        return valid;
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String getSelectedPaymentMethod() {
        if (rgPaymentMethod == null) return "COD";
        int checkedId = rgPaymentMethod.getCheckedRadioButtonId();
        if (checkedId == R.id.rbBankTransfer) return "BANK_TRANSFER";
        if (checkedId == R.id.rbMomo)         return "MOMO";
        return "COD"; // Default
    }

    private void showLoading(boolean loading) {
        if (progressPlaceOrder != null) {
            progressPlaceOrder.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnPlaceOrder.setEnabled(!loading);
        btnPlaceOrder.setText(loading ? "Placing order..." : "Place Order");
    }

    private void goToOrderSuccess(String orderCode, double total) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_CODE", orderCode);
        intent.putExtra("ORDER_TOTAL", total);
        startActivity(intent);
        finish();
    }
}
