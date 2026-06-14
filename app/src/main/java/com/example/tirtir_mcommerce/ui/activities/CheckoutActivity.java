package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.ShippingAddress;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.OrderRepository;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.example.tirtir_mcommerce.utils.PriceUtils;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Collections;

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
    private static final String TAG = "CheckoutActivity";

    private TextInputEditText etFullName, etPhone, etStreet, etDistrict, etCity, etNote;
    private RadioGroup rgPaymentMethod;
    private TextView tvCheckoutSubtotal, tvCheckoutShipping, tvCheckoutTax, tvCheckoutTotal;
    private MaterialButton btnPlaceOrder;
    private ProgressBar progressPlaceOrder;

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private DatabaseHelper databaseHelper;
    /** S1.3 gap: Loyalty multiplier badge. Layout visibility GONE by default. */
    private MaterialCardView cvLoyaltyBadge;
    private android.widget.TextView tvLoyaltyBadgeText;

    private double shippingFee = 0;
    private double cartSubtotal = 0;
    private List<CartItem> checkoutItems = Collections.emptyList();
    private boolean orderSubmitting;

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
        cartRepository = new CartRepository(this);
        databaseHelper  = DatabaseHelper.getInstance(this);

        bindViews();
        loadCartTotals();
        prefillSavedAddress();
        updateTotalsUI();
        setupPlaceOrder();
        checkLoyaltyMultiplier();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackAttempt();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            handleBackAttempt();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleBackAttempt() {
        if (orderSubmitting) {
            Toast.makeText(this, "Please wait while your order is being placed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
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
        List<CartItem> items = databaseHelper.getCartItems();
        checkoutItems = items == null ? Collections.emptyList() : items;
        cartSubtotal = 0;
        for (CartItem item : checkoutItems) {
            if (item == null || item.getQuantity() <= 0 || item.getPrice() < 0) continue;
            cartSubtotal += item.getPrice() * item.getQuantity();
        }

        double passedSubtotal = getIntent().getDoubleExtra("CART_SUBTOTAL", 0.0);
        if (cartSubtotal <= 0 && passedSubtotal > 0 && !checkoutItems.isEmpty()) {
            cartSubtotal = passedSubtotal;
        }
        updateTotalsUI();
    }

    private void prefillSavedAddress() {
        User user = new SharedPrefsManager(this).getCachedUser();
        if (user == null) return;
        setTextIfEmpty(etFullName, user.getName());
        setTextIfEmpty(etPhone, user.getPhone());
        List<Address> addresses = user.getAddresses();
        if (addresses == null || addresses.isEmpty()) return;

        Address selected = addresses.get(0);
        for (Address address : addresses) {
            if (address != null && address.isDefault()) {
                selected = address;
                break;
            }
        }
        if (selected == null) return;
        setTextIfEmpty(etFullName, selected.getFullName());
        setTextIfEmpty(etPhone, selected.getPhone());
        String street = joinAddressParts(selected.getStreet(), selected.getWard());
        setTextIfEmpty(etStreet, street);
        setTextIfEmpty(etDistrict, selected.getDistrict());
        setTextIfEmpty(etCity, selected.getCity());
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
        if (btnPlaceOrder == null) {
            Log.e(TAG, "Place order button is missing from activity_checkout");
            return;
        }
        btnPlaceOrder.setOnClickListener(v -> {
            if (orderSubmitting) return;
            loadCartTotals();
            if (checkoutItems.isEmpty() || cartSubtotal <= 0) {
                Toast.makeText(this, "Your cart is empty. Add a product before checkout.",
                        Toast.LENGTH_LONG).show();
                return;
            }
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
        String token = prefs.getToken();
        boolean hasToken = prefs.isLoggedIn() && token != null && !token.trim().isEmpty();

        if (!hasToken) {
            Toast.makeText(this, "Please sign in to place your order", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        if (checkoutItems.isEmpty() || cartSubtotal <= 0) {
            Toast.makeText(this, "Your cart is empty. Add a product before checkout.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        orderSubmitting = true;
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

        try {
            cartRepository.syncPendingToServer(
                    () -> runOnUiThread(() -> {
                        if (canUpdateUi()) submitOrderRequest(request);
                    }),
                    error -> runOnUiThread(() -> {
                        if (!canUpdateUi()) return;
                        orderSubmitting = false;
                        showLoading(false);
                        Toast.makeText(this,
                                "Your cart has not synced yet. Check your connection and try again.",
                                Toast.LENGTH_LONG).show();
                    }));
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start cart sync", error);
            orderSubmitting = false;
            showLoading(false);
            Toast.makeText(this, "We could not start checkout. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void submitOrderRequest(CreateOrderRequest request) {
        try {
            orderRepository.placeOrder(request,
                    orderResponse -> runOnUiThread(() -> {
                        if (!canUpdateUi()) return;
                        orderSubmitting = false;
                        showLoading(false);
                        String orderId = orderResponse == null ? null : orderResponse.getOrderId();
                        if (orderId == null || orderId.trim().isEmpty()) {
                            Toast.makeText(this,
                                    "Your order could not be confirmed. Please try again.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        databaseHelper.clearCart();
                        goToOrderSuccess(orderId, cartSubtotal);
                    }),
                    errorMessage -> runOnUiThread(() -> {
                        if (!canUpdateUi()) return;
                        orderSubmitting = false;
                        showLoading(false);
                        Toast.makeText(this,
                                errorMessage == null || errorMessage.trim().isEmpty()
                                        ? "We could not place your order. Please try again."
                                        : errorMessage,
                                Toast.LENGTH_LONG).show();
                    })
            );
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start order request", error);
            orderSubmitting = false;
            showLoading(false);
            Toast.makeText(this, "We could not start checkout. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
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
        if (TextUtils.isEmpty(getText(etCity))) {
            etCity.setError("Please enter your city");
            if (valid) etCity.requestFocus();
            valid = false;
        }
        if (rgPaymentMethod == null || rgPaymentMethod.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Please choose a payment method", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }

    private String getText(TextInputEditText field) {
        return field != null && field.getText() != null ? field.getText().toString().trim() : "";
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
        if (btnPlaceOrder != null) {
            btnPlaceOrder.setEnabled(!loading);
            btnPlaceOrder.setText(loading ? "Placing order..." : getString(R.string.btn_place_order));
        }
    }

    private void goToOrderSuccess(String orderCode, double total) {
        if (orderCode == null || orderCode.trim().isEmpty() || !canUpdateUi()) return;
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_CODE", orderCode);
        intent.putExtra("ORDER_TOTAL", total);
        startActivity(intent);
        finish();
    }

    private boolean canUpdateUi() {
        return !isFinishing() && !isDestroyed();
    }

    private void setTextIfEmpty(TextInputEditText field, String value) {
        if (field != null && getText(field).isEmpty() && value != null && !value.trim().isEmpty()) {
            field.setText(value.trim());
        }
    }

    private String joinAddressParts(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + ", " + b;
    }
}
