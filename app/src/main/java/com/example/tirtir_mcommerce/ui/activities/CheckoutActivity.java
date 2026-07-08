package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.example.tirtir_mcommerce.network.ApiConfig;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.repository.OrderRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckoutActivity extends AppCompatActivity {
    private static final String TAG = "CheckoutActivity";

    private EditText etPromoCode; // Changed to generic View or EditText as per XML
    private TextView tvCheckoutSubtotal, tvCheckoutShipping, tvCheckoutTax, tvCheckoutTotal;
    private MaterialButton btnPlaceOrder;
    private ProgressBar progressPlaceOrder;

    private TextView tvLoyaltyBalance;
    private View btnRedeemPoints;
    private MaterialButton btnApplyPromo;
    private View btnRemoveVoucher;
    
    // New UI Elements
    private TextView tvAddressSummary, tvAddressTitle;
    private MaterialCardView cvPaymentCard, cvPaymentVnpay, cvPaymentMomo;
    private android.widget.RadioButton rbCOD, rbBankTransfer, rbMomo;
    private String selectedPaymentMethod = "CARD";

    // Hidden inputs for logic compatibility (mapped in XML)
    private TextView etFullName, etPhone, etStreet, actvProvince, actvDistrict, actvWard, etNote, cvLoyaltyBadge;

    private double discountAmount = 0;
    private int currentLoyaltyPoints = 0;

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private DatabaseHelper databaseHelper;
    
    private double shippingFee = 5.0; // Standard $5.00 like in mockup
    private double cartSubtotal = 0;
    private List<CartItem> checkoutItems = Collections.emptyList();
    private boolean orderSubmitting;
    
    private List<Address> savedAddressesList = new java.util.ArrayList<>();
    private String selectedProvinceId, selectedDistrictId, selectedWardCode;
    private String selectedQuoteId = "manual_quote";
    private String selectedServiceId = "manual_service";
    private String idempotencyKey = java.util.UUID.randomUUID().toString();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        orderRepository = new OrderRepository(this);
        cartRepository = new CartRepository(this);
        databaseHelper = DatabaseHelper.getInstance(this);

        bindViews();
        setupPaymentMethods();
        loadCartTotals();
        prefillSavedAddress();
        updateTotalsUI();
        setupPlaceOrder();
        setupPromoCode();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void bindViews() {
        tvAddressTitle = findViewById(R.id.tvAddressTitle);
        tvAddressSummary = findViewById(R.id.tvAddressSummary);
        
        cvPaymentCard = findViewById(R.id.cvPaymentCard);
        cvPaymentVnpay = findViewById(R.id.cvPaymentVnpay);
        cvPaymentMomo = findViewById(R.id.cvPaymentMomo);
        rbCOD = findViewById(R.id.rbCOD);
        rbBankTransfer = findViewById(R.id.rbBankTransfer);
        rbMomo = findViewById(R.id.rbMomo);
        
        etPromoCode = findViewById(R.id.etPromoCode);
        btnApplyPromo = findViewById(R.id.btnApplyPromo);
        
        tvCheckoutSubtotal = findViewById(R.id.tvCheckoutSubtotal);
        tvCheckoutShipping = findViewById(R.id.tvCheckoutShipping);
        tvCheckoutTax = findViewById(R.id.tvCheckoutTax);
        tvCheckoutTotal = findViewById(R.id.tvCheckoutTotal);
        
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        progressPlaceOrder = findViewById(R.id.progressPlaceOrder);

        // Map hidden views for logic
        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etStreet = findViewById(R.id.etStreet);
        actvProvince = findViewById(R.id.actvProvince);
        actvDistrict = findViewById(R.id.actvDistrict);
        actvWard = findViewById(R.id.actvWard);
        etNote = findViewById(R.id.etNote);
        tvLoyaltyBalance = findViewById(R.id.tvLoyaltyBalance);
        btnRedeemPoints = findViewById(R.id.btnRedeemPoints);
        btnRemoveVoucher = findViewById(R.id.btnRemoveVoucher);
        cvLoyaltyBadge = findViewById(R.id.cvLoyaltyBadge);
        
        findViewById(R.id.cardShippingAddress).setOnClickListener(v -> showSavedAddressesDialog());
    }

    private void setupPaymentMethods() {
        cvPaymentCard.setOnClickListener(v -> updatePaymentSelection("CARD"));
        cvPaymentVnpay.setOnClickListener(v -> updatePaymentSelection("VNPAY"));
        cvPaymentMomo.setOnClickListener(v -> updatePaymentSelection("MOMO"));
        rbCOD.setOnClickListener(v -> updatePaymentSelection("CARD"));
        rbBankTransfer.setOnClickListener(v -> updatePaymentSelection("VNPAY"));
        rbMomo.setOnClickListener(v -> updatePaymentSelection("MOMO"));
        
        updatePaymentSelection("CARD"); // Default
    }

    private void updatePaymentSelection(String method) {
        selectedPaymentMethod = method;
        
        int activeColor = getResources().getColor(R.color.tirtir_red_primary);
        int inactiveColor = Color.parseColor("#10000000");

        cvPaymentCard.setStrokeColor(method.equals("CARD") ? activeColor : inactiveColor);
        cvPaymentCard.setStrokeWidth(method.equals("CARD") ? dpToPx(2) : dpToPx(1));
        rbCOD.setChecked(method.equals("CARD"));
        
        cvPaymentVnpay.setStrokeColor(method.equals("VNPAY") ? activeColor : inactiveColor);
        cvPaymentVnpay.setStrokeWidth(method.equals("VNPAY") ? dpToPx(2) : dpToPx(1));
        rbBankTransfer.setChecked(method.equals("VNPAY"));
        
        cvPaymentMomo.setStrokeColor(method.equals("MOMO") ? activeColor : inactiveColor);
        cvPaymentMomo.setStrokeWidth(method.equals("MOMO") ? dpToPx(2) : dpToPx(1));
        rbMomo.setChecked(method.equals("MOMO"));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void loadCartTotals() {
        checkoutItems = databaseHelper.getCartItems();
        cartSubtotal = 0;
        
        android.widget.LinearLayout llCheckoutProducts = findViewById(R.id.llCheckoutProducts);
        if (llCheckoutProducts != null) llCheckoutProducts.removeAllViews();

        if (!checkoutItems.isEmpty()) {
            for (CartItem item : checkoutItems) {
                cartSubtotal += item.getPrice() * item.getQuantity();

                if (llCheckoutProducts != null) {
                    View itemView = android.view.LayoutInflater.from(this).inflate(R.layout.item_checkout_product, llCheckoutProducts, false);
                    
                    TextView tvName = itemView.findViewById(R.id.tvProductName);
                    TextView tvPrice = itemView.findViewById(R.id.tvProductPrice);
                    TextView tvQuantity = itemView.findViewById(R.id.tvCheckoutQuantity);
                    TextView tvInfo = itemView.findViewById(R.id.tvProductInfo);
                    ImageView ivImage = itemView.findViewById(R.id.ivProductPreviewImage);

                    tvName.setText(item.getProductName());
                    tvPrice.setText(PriceUtils.formatPriceUsd(item.getPrice() * item.getQuantity()));
                    tvQuantity.setText("x" + item.getQuantity());
                    String shade = item.getShade() != null && !item.getShade().isEmpty() ? item.getShade() : "Standard";
                    tvInfo.setText("Shade: " + shade);
                    
                    Glide.with(this)
                        .load(ApiConfig.resolveMediaUrl(item.getThumbnail()))
                        .placeholder(R.drawable.ic_product_placeholder)
                        .into(ivImage);
                        
                    llCheckoutProducts.addView(itemView);
                }
            }
        }
        updateTotalsUI();
    }

    private void prefillSavedAddress() {
        User user = new SharedPrefsManager(this).getCachedUser();
        if (user == null) return;
        
        if (user.getAddresses() != null && !user.getAddresses().isEmpty()) {
            Address selected = user.getAddresses().get(0);
            for (Address a : user.getAddresses()) {
                if (a.isDefault()) {
                    selected = a;
                    break;
                }
            }
            selectSavedAddress(selected);
        }
        fetchSavedAddresses();
    }

    private void fetchSavedAddresses() {
        RetrofitClient.getAuthClient(this).create(ApiService.class).getAddresses()
            .enqueue(new Callback<ApiResponse<List<Address>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<Address>>> call, Response<ApiResponse<List<Address>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        savedAddressesList = response.body().getData();
                        if (savedAddressesList != null && !savedAddressesList.isEmpty()) {
                            if (tvAddressSummary.getText().toString().equals("No address selected yet...")) {
                                Address selected = savedAddressesList.get(0);
                                for (Address a : savedAddressesList) {
                                    if (a.isDefault()) {
                                        selected = a;
                                        break;
                                    }
                                }
                                selectSavedAddress(selected);
                            }
                        }
                    }
                }
                @Override public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {}
            });
    }

    private void showSavedAddressesDialog() {
        if (savedAddressesList.isEmpty()) {
            Toast.makeText(this, "No saved addresses", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[savedAddressesList.size()];
        for (int i = 0; i < savedAddressesList.size(); i++) {
            items[i] = savedAddressesList.get(i).getFormattedAddress();
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Address")
            .setItems(items, (dialog, which) -> selectSavedAddress(savedAddressesList.get(which)))
            .show();
    }

    private void selectSavedAddress(Address addr) {
        tvAddressTitle.setText("Rumah — " + addr.getFullName());
        tvAddressSummary.setText(addr.getFormattedAddress());
        
        // Internal state update for API
        selectedProvinceId = addr.getCity(); // Placeholder
        selectedDistrictId = addr.getDistrict();
        selectedWardCode = addr.getWard();
    }

    private void updateTotalsUI() {
        double tax = cartSubtotal * 0.10;
        double total = cartSubtotal + shippingFee + tax - discountAmount;
        
        tvCheckoutSubtotal.setText(PriceUtils.formatPriceUsd(cartSubtotal));
        tvCheckoutShipping.setText(PriceUtils.formatPriceUsd(shippingFee));
        tvCheckoutTax.setText(PriceUtils.formatPriceUsd(tax));
        tvCheckoutTotal.setText(PriceUtils.formatPriceUsd(total));
    }

    private void setupPlaceOrder() {
        btnPlaceOrder.setOnClickListener(v -> {
            if (orderSubmitting) return;
            if (checkoutItems.isEmpty()) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (tvAddressSummary.getText().toString().contains("No address")) {
                Toast.makeText(this, "Please select a delivery address", Toast.LENGTH_SHORT).show();
                return;
            }
            placeOrderWithApi();
        });
    }

    private void placeOrderWithApi() {
        orderSubmitting = true;
        showLoading(true);

        User user = new SharedPrefsManager(this).getCachedUser();
        ShippingAddress addr = new ShippingAddress();
        // Since we are using a summary card, we take the currently selected address from the user cache or the list
        // This is a simplified version of the logic
        addr.setFullName(user.getName());
        addr.setPhone(user.getPhone());
        addr.setAddress(tvAddressSummary.getText().toString());
        addr.setCity(selectedProvinceId);
        addr.setDistrict(selectedDistrictId);
        addr.setWard(selectedWardCode);

        ArbitrateOrderRequest req = new ArbitrateOrderRequest(
                addr, selectedPaymentMethod, addr.getCity(), 
                new SharedPrefsManager(this).getPendingVoucherCode(), 
                selectedQuoteId, selectedServiceId, idempotencyKey);

        cartRepository.syncAllItemsToServer(() -> {
            RetrofitClient.getAuthClient(this).create(ApiService.class).arbitrateOrder(req)
                .enqueue(new Callback<ApiResponse<ArbitrateOrderResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ArbitrateOrderResponse>> call, Response<ApiResponse<ArbitrateOrderResponse>> response) {
                        orderSubmitting = false;
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            cartRepository.clearCart();
                            String paymentUrl = response.body().getData().getPaymentUrl();
                            String orderId    = response.body().getData().getOrderId();
                            if (paymentUrl != null && !paymentUrl.isEmpty()) {
                                // Open VNPAY inside an in-app WebView so we can intercept
                                // the return URL (http://10.0.2.2:5000) without Chrome failing
                                Intent webIntent = new Intent(CheckoutActivity.this, VNPAYWebViewActivity.class);
                                webIntent.putExtra(VNPAYWebViewActivity.EXTRA_PAYMENT_URL, paymentUrl);
                                webIntent.putExtra(VNPAYWebViewActivity.EXTRA_ORDER_ID, orderId);
                                startActivity(webIntent);
                                finish();
                            } else {
                                goToOrderSuccess(response.body().getData().getOrderId(), response.body().getData().getTotals().getFinalTotal());
                            }
                        } else {
                            Toast.makeText(CheckoutActivity.this, "Order failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ArbitrateOrderResponse>> call, Throwable t) {
                        orderSubmitting = false;
                        showLoading(false);
                        Toast.makeText(CheckoutActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
        }, err -> {
            orderSubmitting = false;
            showLoading(false);
            Toast.makeText(this, "Cart sync failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void goToOrderSuccess(String orderCode, double total) {
        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("ORDER_CODE", orderCode);
        intent.putExtra("ORDER_TOTAL", total);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean loading) {
        if (progressPlaceOrder != null) progressPlaceOrder.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnPlaceOrder != null) {
            btnPlaceOrder.setEnabled(!loading);
            btnPlaceOrder.setText(loading ? "Processing..." : "Checkout");
        }
    }

    private void setupPromoCode() {
        if (btnApplyPromo != null) {
            btnApplyPromo.setOnClickListener(v -> {
                String code = etPromoCode.getText().toString().trim();
                if (code.isEmpty()) return;
                new SharedPrefsManager(this).savePendingVoucher(code, 0);
                Toast.makeText(this, "Code " + code + " applied", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showEditVariantDialog(CartItem item) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheet = android.view.LayoutInflater.from(this).inflate(R.layout.bottom_sheet_cart_variant, null, false);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.tvCartVariantProduct)).setText(item.getProductName());
        ((TextView) sheet.findViewById(R.id.tvCartVariantPrice)).setText(PriceUtils.formatPriceUsd(item.getPrice()));
        
        android.widget.ImageView ivProduct = sheet.findViewById(R.id.ivCartVariantProduct);
        com.bumptech.glide.Glide.with(this)
            .load(com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(item.getThumbnail()))
            .placeholder(R.drawable.ic_product_placeholder)
            .into(ivProduct);

        android.widget.LinearLayout group = sheet.findViewById(R.id.layoutCartVariants);
        View progress = sheet.findViewById(R.id.progressCartVariants);
        TextView tvSelectedShade = sheet.findViewById(R.id.tvCartVariantSelectedShade);
        String[] selected = {item.getShade() == null || item.getShade().trim().isEmpty() ? "Standard" : item.getShade()};
        tvSelectedShade.setText("Selected: " + selected[0]);

        final int[] quantity = {Math.max(1, item.getQuantity())};
        TextView tvQuantity = sheet.findViewById(R.id.tvCartVariantQuantity);
        tvQuantity.setText(String.valueOf(quantity[0]));

        sheet.findViewById(R.id.btnCartVariantDecrease).setOnClickListener(v -> {
            if (quantity[0] > 1) quantity[0]--;
            tvQuantity.setText(String.valueOf(quantity[0]));
        });

        sheet.findViewById(R.id.btnCartVariantIncrease).setOnClickListener(v -> {
            if (quantity[0] < 99) quantity[0]++;
            tvQuantity.setText(String.valueOf(quantity[0]));
        });

        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getProductById(item.getProductId()).enqueue(new Callback<com.example.tirtir_mcommerce.model.ProductDetailResponse>() {
            @Override
            public void onResponse(Call<com.example.tirtir_mcommerce.model.ProductDetailResponse> call, Response<com.example.tirtir_mcommerce.model.ProductDetailResponse> response) {
                com.example.tirtir_mcommerce.model.Product product = response.isSuccessful() && response.body() != null ? response.body().getProduct() : null;
                String parentId = product == null ? null : product.getParentId();
                loadVariants(api, item, parentId, group, progress, selected, tvSelectedShade);
            }

            @Override
            public void onFailure(Call<com.example.tirtir_mcommerce.model.ProductDetailResponse> call, Throwable t) {
                loadVariants(api, item, null, group, progress, selected, tvSelectedShade);
            }
        });

        sheet.findViewById(R.id.btnCancelCartVariant).setOnClickListener(v -> dialog.dismiss());

        sheet.findViewById(R.id.btnConfirmCartVariant).setOnClickListener(v -> {
            item.setShade(selected[0]);
            item.setQuantity(quantity[0]);
            cartRepository.updateShade(item.getProductId(), selected[0], quantity[0]);
            loadCartTotals(); // Recalculate totals
            dialog.dismiss();
        });
        dialog.show();
    }

    private void loadVariants(ApiService api, CartItem item, String parentId, android.widget.LinearLayout group,
                              View progress, String[] selected, TextView tvSelectedShade) {
        String productId = parentId == null || parentId.trim().isEmpty() ? item.getProductId() : null;
        api.getShades(productId, parentId, 100).enqueue(new Callback<java.util.List<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(Call<java.util.List<java.util.Map<String, Object>>> call, Response<java.util.List<java.util.Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    addVariantCircle(group, selected[0], null, true, selected, tvSelectedShade);
                    return;
                }
                group.removeAllViews();
                
                boolean foundSelected = false;
                for (java.util.Map<String, Object> shade : response.body()) {
                    Object nameValue = shade.get("Shade_Name");
                    if (nameValue == null) nameValue = shade.get("Shade_Code");
                    String name = nameValue == null ? "Shade" : String.valueOf(nameValue);
                    
                    Object hexValue = shade.get("Hex_Code");
                    if (hexValue == null) hexValue = shade.get("shade_color_hex");
                    String hexCode = hexValue == null ? null : String.valueOf(hexValue);
                    
                    if (name.equals(selected[0])) foundSelected = true;
                    
                    addVariantCircle(group, name, hexCode, name.equals(selected[0]), selected, tvSelectedShade);
                }
                
                if (!foundSelected && group.getChildCount() > 0) {
                    group.getChildAt(0).performClick();
                }
            }

            @Override public void onFailure(Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                addVariantCircle(group, selected[0], null, true, selected, tvSelectedShade);
            }
        });
    }

    private void addVariantCircle(android.widget.LinearLayout group, String name, String hexCode, boolean isSelected, String[] selected, TextView tvSelectedShade) {
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
            (int) (56 * density), (int) (56 * density));
        lp.setMarginEnd((int) (12 * density));
        frame.setLayoutParams(lp);
        
        // Inner Color Circle
        android.widget.TextView colorCircle = new android.widget.TextView(this);
        android.widget.FrameLayout.LayoutParams ivLp = new android.widget.FrameLayout.LayoutParams(
            (int) (48 * density), (int) (48 * density));
        ivLp.gravity = android.view.Gravity.CENTER;
        colorCircle.setLayoutParams(ivLp);
        colorCircle.setGravity(android.view.Gravity.CENTER);
        colorCircle.setTextSize(10f);
        colorCircle.setTypeface(null, android.graphics.Typeface.BOLD);
        
        String shortCode = name;
        if (name.contains(" ")) {
            shortCode = name.substring(0, name.indexOf(" "));
        }
        if (shortCode.length() > 4) shortCode = shortCode.substring(0, 4);
        colorCircle.setText(shortCode);
        
        int color = android.graphics.Color.LTGRAY;
        if (hexCode != null && !hexCode.trim().isEmpty()) {
            if (!hexCode.startsWith("#")) hexCode = "#" + hexCode;
            try { color = android.graphics.Color.parseColor(hexCode); } catch (Exception ignored) {}
        }
        
        double luminance = 0.2126 * android.graphics.Color.red(color) + 0.7152 * android.graphics.Color.green(color) + 0.0722 * android.graphics.Color.blue(color);
        if (luminance > 128) {
            colorCircle.setTextColor(android.graphics.Color.BLACK);
        } else {
            colorCircle.setTextColor(android.graphics.Color.WHITE);
        }
        
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(color);
        colorCircle.setBackground(drawable);
        frame.addView(colorCircle);
        
        // Outer Ring
        android.widget.ImageView ring = new android.widget.ImageView(this);
        ring.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        android.graphics.drawable.GradientDrawable ringDrawable = new android.graphics.drawable.GradientDrawable();
        ringDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ringDrawable.setColor(android.graphics.Color.TRANSPARENT);
        
        if (isSelected) {
            ringDrawable.setStroke((int) (2 * density), android.graphics.Color.parseColor("#A12E2B")); // Primary red
        } else {
            ringDrawable.setStroke(0, android.graphics.Color.TRANSPARENT);
        }
        ring.setImageDrawable(ringDrawable);
        frame.addView(ring);
        
        // Badge (Top Right)
        android.widget.ImageView badge = new android.widget.ImageView(this);
        android.widget.FrameLayout.LayoutParams badgeLp = new android.widget.FrameLayout.LayoutParams(
            (int) (16 * density), (int) (16 * density));
        badgeLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        badgeLp.setMargins(0, (int) (2 * density), (int) (2 * density), 0);
        badge.setLayoutParams(badgeLp);
        
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        badgeBg.setColor(android.graphics.Color.parseColor("#A12E2B"));
        badge.setBackground(badgeBg);
        badge.setImageResource(R.drawable.ic_check);
        badge.setPadding((int)(3*density), (int)(3*density), (int)(3*density), (int)(3*density));
        badge.setColorFilter(android.graphics.Color.WHITE);
        badge.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        frame.addView(badge);
        
        frame.setTag(name);
        frame.setOnClickListener(v -> {
            selected[0] = name;
            tvSelectedShade.setText("Selected: " + name);
            
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.FrameLayout childFrame = (android.widget.FrameLayout) group.getChildAt(i);
                boolean childSelected = childFrame.getTag().equals(name);
                
                android.widget.ImageView childRing = (android.widget.ImageView) childFrame.getChildAt(1);
                android.graphics.drawable.GradientDrawable cd = (android.graphics.drawable.GradientDrawable) childRing.getDrawable();
                cd.setStroke(childSelected ? (int) (2 * density) : 0, childSelected ? android.graphics.Color.parseColor("#A12E2B") : android.graphics.Color.TRANSPARENT);
                
                android.widget.ImageView childBadge = (android.widget.ImageView) childFrame.getChildAt(2);
                childBadge.setVisibility(childSelected ? View.VISIBLE : View.GONE);
            }
        });
        
        group.addView(frame);
    }
}
