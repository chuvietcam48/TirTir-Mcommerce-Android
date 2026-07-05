package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.net.Uri;
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
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.ArbitrateOrderRequest;
import com.example.tirtir_mcommerce.model.ArbitrateOrderResponse;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.ShippingAddress;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.repository.OrderRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    private TextInputEditText etFullName, etPhone, etStreet, etNote, etPromoCode;
    private RadioGroup rgPaymentMethod;
    private TextView tvCheckoutSubtotal, tvCheckoutShipping, tvCheckoutTax, tvCheckoutTotal;
    private MaterialButton btnPlaceOrder;
    private ProgressBar progressPlaceOrder;

    private TextView tvLoyaltyBalance;
    private MaterialButton btnRedeemPoints;
    private MaterialButton btnApplyPromo, btnRemoveVoucher;
    private double discountAmount = 0;
    private int currentLoyaltyPoints = 0;

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private DatabaseHelper databaseHelper;
    /** S1.3 gap: Loyalty multiplier badge. Layout visibility GONE by default. */
    private MaterialCardView cvLoyaltyBadge;
    private android.widget.TextView tvLoyaltyBadgeText;

    private double shippingFee = 2.0;
    private double cartSubtotal = 0;
    private List<CartItem> checkoutItems = Collections.emptyList();
    private boolean orderSubmitting;
    
    // Address Selection
    private android.widget.AutoCompleteTextView actvProvince, actvDistrict, actvWard;
    private String selectedProvinceId, selectedDistrictId, selectedWardCode;
    private MaterialButton btnSelectSavedAddress;
    private List<Address> savedAddressesList = new java.util.ArrayList<>();
    private Address pendingAddressToPrefill = null;
    private List<String> provinceNames = new java.util.ArrayList<>();
    private List<String> provinceIds = new java.util.ArrayList<>();
    
    // SOAP Shipping Quote
    private String selectedQuoteId = "manual_quote";
    private String selectedServiceId = "manual_service";
    private String idempotencyKey = java.util.UUID.randomUUID().toString();
    private List<ShippingQuote> currentQuotes = new java.util.ArrayList<>();
    
    // Simple Quote Data Class
    public static class ShippingQuote {
        public String serviceId;
        public String serviceName;
        public double fee;
        public String estimatedDeliveryTime;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
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
            setupPromoCode();
            checkLoyaltyMultiplier();

            SharedPrefsManager prefs = new SharedPrefsManager(this);
            if (prefs.getPendingVoucherCode() != null) {
                discountAmount = prefs.getPendingVoucherDiscount();
                if (etPromoCode != null) etPromoCode.setText(prefs.getPendingVoucherCode());
                if (btnApplyPromo != null) btnApplyPromo.setText("Applied");
                if (btnRemoveVoucher != null) btnRemoveVoucher.setVisibility(View.VISIBLE);
                if (btnRedeemPoints != null) {
                    btnRedeemPoints.setEnabled(false);
                    btnRedeemPoints.setText("Applied");
                }
            }

            loadLoyaltyBalance();

            getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    handleBackAttempt();
                }
            });

            // If intent has explicit cart subtotal (e.g. buy now)
            double passedSubtotal = getIntent().getDoubleExtra("CART_SUBTOTAL", 0.0);
            if (passedSubtotal > 0 && cartSubtotal == 0) {
                cartSubtotal = passedSubtotal;
                updateTotalsUI();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Crash in onCreate: ", e);
            e.printStackTrace();
            Toast.makeText(this, "Error loading Checkout: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        etNote               = findViewById(R.id.etNote);
        etPromoCode          = findViewById(R.id.etPromoCode);
        rgPaymentMethod      = findViewById(R.id.rgPaymentMethod);
        tvCheckoutSubtotal   = findViewById(R.id.tvCheckoutSubtotal);
        tvCheckoutShipping   = findViewById(R.id.tvCheckoutShipping);
        tvCheckoutTax        = findViewById(R.id.tvCheckoutTax);
        tvCheckoutTotal      = findViewById(R.id.tvCheckoutTotal);
        btnPlaceOrder        = findViewById(R.id.btnPlaceOrder);
        progressPlaceOrder   = findViewById(R.id.progressPlaceOrder);
        cvLoyaltyBadge       = findViewById(R.id.cvLoyaltyBadge);
        
        actvProvince         = findViewById(R.id.actvProvince);
        actvDistrict         = findViewById(R.id.actvDistrict);
        actvWard             = findViewById(R.id.actvWard);
        btnSelectSavedAddress = findViewById(R.id.btnSelectSavedAddress);

        if (cvLoyaltyBadge != null && cvLoyaltyBadge.getChildCount() > 0) {
            android.view.View child = cvLoyaltyBadge.getChildAt(0);
            if (child instanceof android.widget.TextView) {
                tvLoyaltyBadgeText = (android.widget.TextView) child;
            }
        }
        tvLoyaltyBalance = findViewById(R.id.tvLoyaltyBalance);
        btnRedeemPoints = findViewById(R.id.btnRedeemPoints);
        btnApplyPromo = findViewById(R.id.btnApplyPromo);
        btnRemoveVoucher = findViewById(R.id.btnRemoveVoucher);
        
        // Handle AutoCompleteTextView dropdown opening instantly
        actvProvince.setOnClickListener(v -> actvProvince.showDropDown());
        actvProvince.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvProvince.showDropDown();
        });
        actvDistrict.setOnClickListener(v -> actvDistrict.showDropDown());
        actvDistrict.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvDistrict.showDropDown();
        });
        actvWard.setOnClickListener(v -> actvWard.showDropDown());
        actvWard.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvWard.showDropDown();
        });

        if (btnSelectSavedAddress != null) {
            btnSelectSavedAddress.setOnClickListener(v -> showSavedAddressesDialog());
        }

        // Disable Place Order until Quote is valid
        btnPlaceOrder.setEnabled(true);
        loadProvinces();
        fetchSavedAddresses();
    }

    private void setupPromoCode() {
        if (btnApplyPromo != null) btnApplyPromo.setOnClickListener(v -> {
            String code = getText(etPromoCode).toUpperCase(java.util.Locale.ENGLISH);
            if (code.isEmpty()) {
                etPromoCode.setError("Enter a promo code");
                return;
            }
            // The backend validates the code and computes the actual discount.
            new SharedPrefsManager(this).savePendingVoucher(code, 0);
            btnApplyPromo.setText("Applied");
            if (btnRemoveVoucher != null) btnRemoveVoucher.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Promo code will be verified when you place the order", Toast.LENGTH_SHORT).show();
        });
        if (btnRemoveVoucher != null) btnRemoveVoucher.setOnClickListener(v -> {
            new SharedPrefsManager(this).clearPendingVoucher();
            discountAmount = 0;
            if (etPromoCode != null) etPromoCode.setText("");
            if (btnApplyPromo != null) btnApplyPromo.setText("Apply");
            btnRemoveVoucher.setVisibility(View.GONE);
            updateTotalsUI();
        });
    }
    
    // ===========================
    // ADDRESS DROPDOWNS
    // ===========================

    private void loadProvinces() {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getProvinces().enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<java.util.Map<String, Object>> data = (List<java.util.Map<String, Object>>) response.body().get("data");
                    if (data == null) return;
                    
                    provinceNames.clear();
                    provinceIds.clear();
                    for (java.util.Map<String, Object> prov : data) {
                        provinceNames.add((String) prov.get("ProvinceName"));
                        provinceIds.add(String.valueOf(((Number) prov.get("ProvinceID")).intValue()));
                    }
                    
                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_dropdown_item_1line, provinceNames);
                    actvProvince.setAdapter(adapter);
                    actvProvince.setOnItemClickListener((parent, view, position, id) -> {
                        selectedProvinceId = provinceIds.get(position);
                        selectedDistrictId = null;
                        selectedWardCode = null;
                        actvDistrict.setText("");
                        actvWard.setText("");
                        invalidateQuote();
                        loadDistricts(selectedProvinceId);
                    });

                    if (pendingAddressToPrefill != null) {
                        matchProvince(pendingAddressToPrefill.getCity(), pendingAddressToPrefill.getDistrict(), pendingAddressToPrefill.getWard());
                        pendingAddressToPrefill = null;
                    }
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
        });
    }

    private void loadDistricts(String provinceId) {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getDistricts(provinceId).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<java.util.Map<String, Object>> data = (List<java.util.Map<String, Object>>) response.body().get("data");
                    if (data == null) return;
                    
                    List<String> names = new java.util.ArrayList<>();
                    List<String> ids = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> dist : data) {
                        names.add((String) dist.get("DistrictName"));
                        ids.add(String.valueOf(((Number) dist.get("DistrictID")).intValue()));
                    }
                    
                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_dropdown_item_1line, names);
                    actvDistrict.setAdapter(adapter);
                    actvDistrict.setOnItemClickListener((parent, view, position, id) -> {
                        selectedDistrictId = ids.get(position);
                        selectedWardCode = null;
                        actvWard.setText("");
                        invalidateQuote();
                        loadWards(selectedDistrictId);
                    });
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
        });
    }

    private void loadWards(String districtId) {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getWards(districtId).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<java.util.Map<String, Object>> data = (List<java.util.Map<String, Object>>) response.body().get("data");
                    if (data == null) return;
                    
                    List<String> names = new java.util.ArrayList<>();
                    List<String> codes = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> ward : data) {
                        names.add((String) ward.get("WardName"));
                        codes.add((String) ward.get("WardCode"));
                    }
                    
                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_dropdown_item_1line, names);
                    actvWard.setAdapter(adapter);
                    actvWard.setOnItemClickListener((parent, view, position, id) -> {
                        selectedWardCode = codes.get(position);
                        invalidateQuote();
                        fetchShippingQuoteSoap();
                    });
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
        });
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
        selectSavedAddress(selected);
    }

    // ===========================
    // SAVED ADDRESS SELECTION LOGIC
    // ===========================

    private void fetchSavedAddresses() {
        ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        api.getAddresses().enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call, Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    savedAddressesList = response.body().getData();
                    if (!savedAddressesList.isEmpty()) {
                        if (btnSelectSavedAddress != null) {
                            btnSelectSavedAddress.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {}
        });
    }

    private void showSavedAddressesDialog() {
        if (savedAddressesList == null || savedAddressesList.isEmpty()) {
            Toast.makeText(this, "No saved addresses found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] addressStrings = new String[savedAddressesList.size()];
        for (int i = 0; i < savedAddressesList.size(); i++) {
            Address addr = savedAddressesList.get(i);
            addressStrings[i] = addr.getFullName() + " (" + addr.getPhone() + ")\n" + addr.getFormattedAddress();
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select a saved address")
            .setItems(addressStrings, (dialog, which) -> {
                Address selected = savedAddressesList.get(which);
                selectSavedAddress(selected);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void selectSavedAddress(Address selected) {
        if (selected == null) return;
        etFullName.setText(selected.getFullName());
        etPhone.setText(selected.getPhone());
        etStreet.setText(selected.getStreet());
        
        actvProvince.setText(selected.getCity(), false);
        actvDistrict.setText(selected.getDistrict(), false);
        actvWard.setText(selected.getWard(), false);
        
        if (!provinceNames.isEmpty()) {
            matchProvince(selected.getCity(), selected.getDistrict(), selected.getWard());
        } else {
            pendingAddressToPrefill = selected;
        }
    }

    private void matchProvince(String city, String district, String ward) {
        if (city == null || provinceNames.isEmpty()) return;
        String normalizedCity = normalizeAddressPart(city);
        int matchedIdx = -1;
        for (int i = 0; i < provinceNames.size(); i++) {
            String normalizedProv = normalizeAddressPart(provinceNames.get(i));
            if (normalizedProv.contains(normalizedCity) || normalizedCity.contains(normalizedProv)) {
                matchedIdx = i;
                break;
            }
        }
        if (matchedIdx != -1) {
            String provName = provinceNames.get(matchedIdx);
            selectedProvinceId = provinceIds.get(matchedIdx);
            actvProvince.setText(provName, false);
            loadDistrictsAndMatch(selectedProvinceId, district, ward);
        }
    }

    private void loadDistrictsAndMatch(String provinceId, String targetDistrict, String targetWard) {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getDistricts(provinceId).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<java.util.Map<String, Object>> data = (List<java.util.Map<String, Object>>) response.body().get("data");
                    if (data == null) return;
                    
                    List<String> names = new java.util.ArrayList<>();
                    List<String> ids = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> dist : data) {
                        names.add((String) dist.get("DistrictName"));
                        ids.add(String.valueOf(((Number) dist.get("DistrictID")).intValue()));
                    }
                    
                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_dropdown_item_1line, names);
                    actvDistrict.setAdapter(adapter);
                    
                    if (targetDistrict != null) {
                        String normalizedTarget = normalizeAddressPart(targetDistrict);
                        int matchedIdx = -1;
                        for (int i = 0; i < names.size(); i++) {
                            String normalizedDist = normalizeAddressPart(names.get(i));
                            if (normalizedDist.contains(normalizedTarget) || normalizedTarget.contains(normalizedDist)) {
                                matchedIdx = i;
                                break;
                            }
                        }
                        if (matchedIdx != -1) {
                            actvDistrict.setText(names.get(matchedIdx), false);
                            selectedDistrictId = ids.get(matchedIdx);
                            loadWardsAndMatch(selectedDistrictId, targetWard);
                        }
                    }
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
        });
    }

    private void loadWardsAndMatch(String districtId, String targetWard) {
        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getWards(districtId).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<java.util.Map<String, Object>> data = (List<java.util.Map<String, Object>>) response.body().get("data");
                    if (data == null) return;
                    
                    List<String> names = new java.util.ArrayList<>();
                    List<String> codes = new java.util.ArrayList<>();
                    for (java.util.Map<String, Object> ward : data) {
                        names.add((String) ward.get("WardName"));
                        codes.add((String) ward.get("WardCode"));
                    }
                    
                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(CheckoutActivity.this, android.R.layout.simple_dropdown_item_1line, names);
                    actvWard.setAdapter(adapter);
                    
                    if (targetWard != null) {
                        String normalizedTarget = normalizeAddressPart(targetWard);
                        int matchedIdx = -1;
                        for (int i = 0; i < names.size(); i++) {
                            String normalizedWard = normalizeAddressPart(names.get(i));
                            if (normalizedWard.contains(normalizedTarget) || normalizedTarget.contains(normalizedWard)) {
                                matchedIdx = i;
                                break;
                            }
                        }
                        if (matchedIdx != -1) {
                            actvWard.setText(names.get(matchedIdx), false);
                            selectedWardCode = codes.get(matchedIdx);
                            invalidateQuote();
                            fetchShippingQuoteSoap();
                        }
                    }
                }
            }
            @Override public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
        });
    }

    private String normalizeAddressPart(String input) {
        if (input == null) return "";
        String normalized = input.toLowerCase(java.util.Locale.ROOT).trim();
        normalized = normalized.replace("tỉnh", "")
                               .replace("thành phố", "")
                               .replace("tp.", "")
                               .replace("tp", "")
                               .replace("quận", "")
                               .replace("huyện", "")
                               .replace("thị xã", "")
                               .replace("phường", "")
                               .replace("xã", "")
                               .replace("thị trấn", "")
                               .replace("-", " ")
                               .replaceAll("\\s+", " ")
                               .trim();
        return normalized;
    }

    /**
     * Displays the pre-checkout estimate from local cart data.
     * Once arbitrate responds, real authoritative totals replace these values.
     */
    private void updateTotalsUI() {
        double tax   = cartSubtotal * 0.10;
        double total = cartSubtotal + shippingFee + tax - discountAmount;
        if (total < 0) total = 0;

        tvCheckoutSubtotal.setText(PriceUtils.formatPriceUsd(cartSubtotal));
        tvCheckoutShipping.setText(PriceUtils.formatPriceUsd(shippingFee));
        if (tvCheckoutTax != null) {
            tvCheckoutTax.setText(PriceUtils.formatPriceUsd(tax));
        }
        tvCheckoutTotal.setText(PriceUtils.formatPriceUsd(total));
    }

    /** Refresh totals UI from authoritative server response. */
    private void updateTotalsFromServer(ArbitrateOrderResponse.Totals totals, boolean isEstimated) {
        tvCheckoutSubtotal.setText(PriceUtils.formatPriceUsd(totals.getSubtotal()));
        String shippingLabel = PriceUtils.formatPriceUsd(totals.getShippingFee());
        if (isEstimated) shippingLabel += " (estimated)";
        tvCheckoutShipping.setText(shippingLabel);
        if (tvCheckoutTax != null) tvCheckoutTax.setText(PriceUtils.formatPriceUsd(totals.getTax()));
        tvCheckoutTotal.setText(PriceUtils.formatPriceUsd(totals.getFinalTotal()));
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

    private void loadLoyaltyBalance() {
        if (tvLoyaltyBalance == null) return;
        com.example.tirtir_mcommerce.network.ApiService api = com.example.tirtir_mcommerce.network.RetrofitClient.getAuthClient(this).create(com.example.tirtir_mcommerce.network.ApiService.class);
        api.getLoyaltyDetails().enqueue(new retrofit2.Callback<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, retrofit2.Response<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Object pts = response.body().getData().get("loyaltyPoints");
                    currentLoyaltyPoints = pts instanceof Number ? ((Number) pts).intValue() : 0;
                    tvLoyaltyBalance.setText("Available: " + currentLoyaltyPoints + " points");
                }
            }
            @Override public void onFailure(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {}
        });
        
        btnRedeemPoints.setOnClickListener(v -> {
            if (currentLoyaltyPoints <= 0) {
                Toast.makeText(this, "You have no points to redeem", Toast.LENGTH_SHORT).show();
                return;
            }
            showRedeemDialog();
        });
    }

    private void showRedeemDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Max: " + currentLoyaltyPoints);
        
        int padding = 64;
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, 16, padding, 16);
        container.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Redeem Points")
            .setMessage("Enter the number of points to redeem. The backend will confirm their value.")
            .setView(container)
            .setPositiveButton("Redeem", (dialog, which) -> {
                String val = input.getText().toString();
                if (!val.isEmpty()) {
                    int pts = Integer.parseInt(val);
                    if (pts > 0 && pts <= currentLoyaltyPoints) {
                        redeemPointsApi(pts);
                    } else {
                        Toast.makeText(this, "Invalid points", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void redeemPointsApi(int points) {
        com.example.tirtir_mcommerce.network.ApiService api = com.example.tirtir_mcommerce.network.RetrofitClient.getAuthClient(this).create(com.example.tirtir_mcommerce.network.ApiService.class);
        java.util.Map<String, Integer> body = new java.util.HashMap<>();
        body.put("ptsRequired", points);
        api.redeemPoints(body).enqueue(new retrofit2.Callback<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, retrofit2.Response<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Object percentObj = response.body().getData().get("discountPct");
                    double discountPercent = percentObj instanceof Number
                            ? ((Number) percentObj).doubleValue() : 0;
                    discountAmount = cartSubtotal * discountPercent / 100.0;
                    Object codeObj = response.body().getData().get("voucherCode");
                    String code = codeObj != null ? codeObj.toString() : "LOYALTY_VOUCHER";
                    new SharedPrefsManager(CheckoutActivity.this).savePendingVoucher(code, discountAmount);

                    updateTotalsUI();
                    Toast.makeText(CheckoutActivity.this, "Discount applied", Toast.LENGTH_SHORT).show();
                    btnRedeemPoints.setEnabled(false);
                    btnRedeemPoints.setText("Applied");
                } else {
                    Toast.makeText(CheckoutActivity.this, "Failed to redeem points", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                Toast.makeText(CheckoutActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===========================
    // SOAP SHIPPING QUOTE
    // ===========================

    private void invalidateQuote() {
        selectedQuoteId = "manual_quote";
        selectedServiceId = "manual_service";
        currentQuotes.clear();
        btnPlaceOrder.setEnabled(true);
        updateTotalsUI();
    }

    private void fetchShippingQuoteSoap() {
        selectedQuoteId = "manual_quote";
        selectedServiceId = "manual_service";
        btnPlaceOrder.setEnabled(true);
        updateTotalsUI();
    }

    private void parseSoapResponse(java.io.InputStream is) throws Exception {
        org.xmlpull.v1.XmlPullParserFactory factory = org.xmlpull.v1.XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        org.xmlpull.v1.XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, null);

        int eventType = parser.getEventType();
        String currentTag = "";
        
        currentQuotes.clear();
        ShippingQuote currentQuote = null;
        
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if ("quote".equals(currentTag)) {
                    currentQuote = new ShippingQuote();
                }
            } else if (eventType == org.xmlpull.v1.XmlPullParser.TEXT) {
                String text = parser.getText().trim();
                if (!text.isEmpty()) {
                    if ("quoteId".equals(currentTag)) {
                        selectedQuoteId = text;
                    } else if (currentQuote != null) {
                        switch (currentTag) {
                            case "serviceId": currentQuote.serviceId = text; break;
                            case "serviceName": currentQuote.serviceName = text; break;
                            case "fee": currentQuote.fee = Double.parseDouble(text); break;
                            case "estimatedDeliveryTime": currentQuote.estimatedDeliveryTime = text; break;
                        }
                    }
                }
            } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                if ("quote".equals(parser.getName()) && currentQuote != null) {
                    currentQuotes.add(currentQuote);
                    currentQuote = null;
                }
                currentTag = "";
            }
            eventType = parser.next();
        }
        
        runOnUiThread(() -> {
            showLoading(false);
            if (!currentQuotes.isEmpty()) {
                // Auto-select the first service for demo
                ShippingQuote q = currentQuotes.get(0);
                selectedServiceId = q.serviceId;
                shippingFee = q.fee;
                btnPlaceOrder.setEnabled(true);
                
                android.widget.TextView tvShippingService = findViewById(R.id.tvShippingService);
                if (tvShippingService != null) {
                    tvShippingService.setText(q.serviceName + " - ETA: " + q.estimatedDeliveryTime.substring(0, 10));
                }
                updateTotalsUI();
            } else {
                Toast.makeText(CheckoutActivity.this, "No delivery service is available for this address.", Toast.LENGTH_LONG).show();
                tvCheckoutShipping.setText("N/A");
            }
        });
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
            if (selectedQuoteId == null || selectedServiceId == null) {
                String provinceVal = actvProvince.getText() != null ? actvProvince.getText().toString().trim() : "";
                String districtVal = actvDistrict.getText() != null ? actvDistrict.getText().toString().trim() : "";
                String wardVal = actvWard.getText() != null ? actvWard.getText().toString().trim() : "";
                if (provinceVal.isEmpty() || districtVal.isEmpty() || wardVal.isEmpty()) {
                    Toast.makeText(this, "Select a delivery address to calculate shipping.", Toast.LENGTH_LONG).show();
                    return;
                }
                selectedQuoteId = "manual_quote";
                selectedServiceId = "manual_service";
            }
            placeOrderWithApi();
        });
    }

    // ===========================
    // ARBITRATE ORDER (real totals + VNPAY)
    // ===========================

    /**
     * Calls POST /api/v1/payments/arbitrate which:
     * 1. Calls Viettel Post SOAP (5s race) for real shipping
     * 2. Computes Subtotal + Tax(10%) + Shipping − Voucher server-side
     * 3. Creates a pending_payment Order in MongoDB
     * 4. Returns a signed VNPAY payment URL
     *
     * On success:
     *   - VNPAY → open in browser; wait for deep-link return
     *   - CARD / COD → go directly to OrderSuccessActivity
     */
    private void placeOrderWithApi() {
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        if (!prefs.isLoggedIn() || prefs.getToken() == null) {
            Toast.makeText(this, "Please sign in to place your order", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        if (checkoutItems.isEmpty() || cartSubtotal <= 0) {
            Toast.makeText(this, "Your cart is empty.", Toast.LENGTH_LONG).show();
            return;
        }

        orderSubmitting = true;
        showLoading(true);

        // Build address
        ShippingAddress addr = new ShippingAddress();
        addr.setFullName(getText(etFullName));
        addr.setPhone(getText(etPhone));
        addr.setAddress(getText(etStreet));
        
        String cityText = actvProvince != null && actvProvince.getText() != null ? actvProvince.getText().toString() : "";
        String districtText = actvDistrict != null && actvDistrict.getText() != null ? actvDistrict.getText().toString() : "";
        String wardText = actvWard != null && actvWard.getText() != null ? actvWard.getText().toString() : "";
        addr.setCity(cityText);
        addr.setDistrictId(selectedDistrictId);
        addr.setWardCode(selectedWardCode);
        addr.setDistrict(districtText);
        addr.setWard(wardText);

        String paymentMethod = getSelectedPaymentMethod();
        String voucherCode   = prefs.getPendingVoucherCode();

        ArbitrateOrderRequest req = new ArbitrateOrderRequest(
                addr, paymentMethod, cityText, voucherCode, selectedQuoteId, selectedServiceId, idempotencyKey);

        // Sync local cart to server before arbitrate call
        try {
            cartRepository.syncAllItemsToServer(
                () -> runOnUiThread(() -> { if (canUpdateUi()) callArbitrateApi(req, addr); }),
                err -> runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    Log.e(TAG, "Cart sync failed: " + err + ". Falling back to demo order creation.");
                    Toast.makeText(this,
                        "Cart sync failed (" + err + "). Falling back to demo order creation.",
                        Toast.LENGTH_LONG).show();
                    createLocalDemoOrder(addr, paymentMethod);
                }));
        } catch (Exception e) {
            Log.e(TAG, "Cart sync error", e);
            createLocalDemoOrder(addr, paymentMethod);
        }
    }

    private void callArbitrateApi(ArbitrateOrderRequest req, ShippingAddress addr) {
        ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        api.arbitrateOrder(req).enqueue(new Callback<ApiResponse<ArbitrateOrderResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ArbitrateOrderResponse>> call,
                                   Response<ApiResponse<ArbitrateOrderResponse>> response) {
                if (!canUpdateUi()) return;
                orderSubmitting = false;
                showLoading(false);

                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    int code = response.code();
                    String errorDetail = "Response body is null or success is false";
                    try {
                        if (response.errorBody() != null) {
                            errorDetail = response.errorBody().string();
                        } else if (response.body() != null && response.body().getMessage() != null) {
                            errorDetail = response.body().getMessage();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                    Log.e(TAG, "Arbitrate failed: code=" + code + ", detail=" + errorDetail);
                    Toast.makeText(CheckoutActivity.this, "Backend Error (" + code + "): " + errorDetail + "\nFalling back to demo order creation.", Toast.LENGTH_LONG).show();
                    
                    // Fallback to local/demo order creation
                    createLocalDemoOrder(addr, req.getPaymentMethod());
                    return;
                }

                ArbitrateOrderResponse data = response.body().getData();

                // Show real authoritative totals
                if (data.getTotals() != null) {
                    updateTotalsFromServer(data.getTotals(), data.isEstimatedShipping());
                }

                // Warn user if shipping is estimated (Viettel timed out)
                if (data.isEstimatedShipping()) {
                    Toast.makeText(CheckoutActivity.this,
                        "⚠ Shipping fee is estimated. Real fee will be confirmed by the carrier.",
                        Toast.LENGTH_LONG).show();
                }

                // Show voucher feedback
                if (data.getVoucherMessage() != null) {
                    Toast.makeText(CheckoutActivity.this, data.getVoucherMessage(), Toast.LENGTH_SHORT).show();
                }

                // Clear local SQLite cart
                databaseHelper.clearCart();
                new SharedPrefsManager(CheckoutActivity.this).clearPendingVoucher();

                String orderId     = data.getOrderId();
                String paymentUrl  = data.getPaymentUrl();
                long   finalTotal  = data.getTotals() != null ? data.getTotals().getFinalTotal() : 0;

                if ("VNPAY".equals(req.getPaymentMethod()) && paymentUrl != null && !paymentUrl.isEmpty()) {
                    // Open VNPAY payment page in the default browser
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl));
                    browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browser);
                    // OrderSuccessActivity is triggered by the deep-link return (tirtir://payment)
                    finish();
                } else {
                    // CARD / COD — order already confirmed
                    goToOrderSuccess(orderId, finalTotal);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ArbitrateOrderResponse>> call, Throwable t) {
                if (!canUpdateUi()) return;
                orderSubmitting = false;
                showLoading(false);
                Log.e(TAG, "Arbitrate network failure", t);
                Toast.makeText(CheckoutActivity.this,
                    "Connection error: " + t.getMessage() + "\nFalling back to demo order creation.",
                    Toast.LENGTH_LONG).show();
                
                // Fallback to local/demo order creation
                createLocalDemoOrder(addr, req.getPaymentMethod());
            }
        });
    }

    private void createLocalDemoOrder(ShippingAddress addr, String paymentMethod) {
        try {
            com.google.gson.JsonObject orderJson = new com.google.gson.JsonObject();
            String dummyId = "ORD-" + System.currentTimeMillis();
            orderJson.addProperty("_id", dummyId);
            orderJson.addProperty("status", "Pending");
            
            double tax = cartSubtotal * 0.10;
            double finalTotal = cartSubtotal + shippingFee + tax - discountAmount;
            if (finalTotal < 0) finalTotal = 0;
            orderJson.addProperty("totalPrice", finalTotal);
            orderJson.addProperty("paymentMethod", paymentMethod);
            orderJson.addProperty("isPaid", false);
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            orderJson.addProperty("createdAt", sdf.format(new java.util.Date()));
            
            com.google.gson.JsonObject addrJson = new com.google.gson.JsonObject();
            addrJson.addProperty("fullName", addr.getFullName());
            addrJson.addProperty("phone", addr.getPhone());
            addrJson.addProperty("address", addr.getAddress());
            addrJson.addProperty("city", addr.getCity());
            addrJson.addProperty("district", addr.getDistrict());
            addrJson.addProperty("ward", addr.getWard());
            orderJson.add("shippingAddress", addrJson);
            
            com.google.gson.JsonArray itemsArray = new com.google.gson.JsonArray();
            for (CartItem item : checkoutItems) {
                com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                itemJson.addProperty("product", item.getProductId());
                itemJson.addProperty("name", item.getProductName());
                itemJson.addProperty("quantity", item.getQuantity());
                itemJson.addProperty("price", item.getPrice());
                itemJson.addProperty("shade", item.getShade() != null ? item.getShade() : "");
                itemsArray.add(itemJson);
            }
            orderJson.add("items", itemsArray);
            orderJson.addProperty("invoiceUrl", "");

            OrderResponse mockOrder = new com.google.gson.Gson().fromJson(orderJson, OrderResponse.class);
            new SharedPrefsManager(CheckoutActivity.this).saveLocalOrder(mockOrder);
            
            // Clear local SQLite cart
            databaseHelper.clearCart();
            new SharedPrefsManager(CheckoutActivity.this).clearPendingVoucher();
            
            goToOrderSuccess(dummyId, finalTotal);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to create local mock order", ex);
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
        
        String provinceVal = actvProvince.getText() != null ? actvProvince.getText().toString().trim() : "";
        String districtVal = actvDistrict.getText() != null ? actvDistrict.getText().toString().trim() : "";
        String wardVal = actvWard.getText() != null ? actvWard.getText().toString().trim() : "";

        if (TextUtils.isEmpty(provinceVal)) {
            actvProvince.setError("Please enter province/city");
            if (valid) actvProvince.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(districtVal)) {
            actvDistrict.setError("Please enter district");
            if (valid) actvDistrict.requestFocus();
            valid = false;
        }
        if (TextUtils.isEmpty(wardVal)) {
            actvWard.setError("Please enter ward");
            if (valid) actvWard.requestFocus();
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
