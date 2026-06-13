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

import com.example.tirtir_mcommerce.utils.PriceUtils;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * SCR-16 CheckoutActivity — Thanh toán
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────
 * placeOrderWithApi():
 *   → POST /api/v1/orders/create via OrderRepository
 *   → Body: { shippingAddress, paymentMethod }
 *   → On success: clear local cart → OrderSuccessActivity with real orderId
 * Cart data: local SQLite cart_items (TASK 5).
 * Shipping: Viettel Post client with an estimated-fee fallback.
 *
 * Sprint 1.3
 */
public class CheckoutActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etPhone, etStreet, etDistrict, etCity, etNote;
    private RadioGroup rgPaymentMethod;
    private TextView tvCheckoutSubtotal, tvCheckoutShipping, tvCheckoutTax, tvCheckoutTotal;
    private MaterialButton btnPlaceOrder;
    private ProgressBar progressPlaceOrder;

    private OrderRepository orderRepository;
    private DatabaseHelper databaseHelper;
    /** S1.3 gap: Loyalty multiplier badge. Layout visibility GONE by default. */
    private MaterialCardView cvLoyaltyBadge;
    private android.widget.TextView tvLoyaltyBadgeText;

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

        bindViews();
        loadCartTotals();
        updateTotalsUI();
        setupPlaceOrder();
        checkLoyaltyMultiplier();
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
        tvCheckoutTax        = findViewById(R.id.tvCheckoutTax);
        tvCheckoutTotal      = findViewById(R.id.tvCheckoutTotal);
        btnPlaceOrder        = findViewById(R.id.btnPlaceOrder);
        progressPlaceOrder   = findViewById(R.id.progressPlaceOrder);
        cvLoyaltyBadge       = findViewById(R.id.cvLoyaltyBadge);
        // The loyalty badge has a single child TextView
        if (cvLoyaltyBadge != null && cvLoyaltyBadge.getChildCount() > 0) {
            android.view.View child = cvLoyaltyBadge.getChildAt(0);
            if (child instanceof android.widget.TextView) {
                tvLoyaltyBadgeText = (android.widget.TextView) child;
            }
        }
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
        double total = cartSubtotal;

        tvCheckoutSubtotal.setText(PriceUtils.formatPriceVnd(cartSubtotal));
        tvCheckoutShipping.setText(PriceUtils.formatPriceVnd(shippingFee));
        if (tvCheckoutTax != null) {
            tvCheckoutTax.setText("Included");
        }
        tvCheckoutTotal.setText(PriceUtils.formatPriceVnd(total));
    }

    // ===========================
    // LOYALTY MULTIPLIER BADGE (S1.3 gap)
    // ===========================

    /**
     * The backend loyalty summary currently exposes points and tier, but no
     * checkout multiplier. Keep the promotional badge hidden until the API
     * returns an explicit multiplier for this order.
     */
    private void checkLoyaltyMultiplier() {
        if (cvLoyaltyBadge != null) cvLoyaltyBadge.setVisibility(View.GONE);
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
     * Requires valid JWT token (user must be logged in).
     */
    private void placeOrderWithApi() {
        // Check if user is logged in (has token)
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        boolean hasToken = prefs.isLoggedIn();

        if (!hasToken) {
            Toast.makeText(this, "Please sign in to place your order", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
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

                    String orderId = orderResponse.getOrderId();
                    goToOrderSuccess(orderId, cartSubtotal);
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
            etFullName.setError("Please enter your full name");
            etFullName.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(getText(etPhone))) {
            etPhone.setError("Please enter your phone number");
            if (valid) etPhone.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(getText(etStreet))) {
            etStreet.setError("Please enter your address");
            if (valid) etStreet.requestFocus();
            valid = false;
        }
        return valid;
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String getSelectedPaymentMethod() {
        if (rgPaymentMethod == null) return "CARD";
        int checkedId = rgPaymentMethod.getCheckedRadioButtonId();
        if (checkedId == R.id.rbBankTransfer) return "VNPAY";
        if (checkedId == R.id.rbMomo)         return "MOMO";
        return "CARD";
    }

    private void showLoading(boolean loading) {
        if (progressPlaceOrder != null) {
            progressPlaceOrder.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnPlaceOrder.setEnabled(!loading);
        btnPlaceOrder.setText(loading ? "Placing order..." : getString(R.string.btn_place_order));
    }

    private void goToOrderSuccess(String orderCode, double total) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_CODE", orderCode);
        intent.putExtra("ORDER_TOTAL", total);
        startActivity(intent);
        finish();
    }
}
