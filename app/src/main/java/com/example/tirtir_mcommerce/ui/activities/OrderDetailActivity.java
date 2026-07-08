package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderDetailActivity extends AppCompatActivity {
    private ProgressBar loading;
    private LinearLayout items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        Toolbar toolbar = findViewById(R.id.toolbarOrderDetail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Order Details");
        }
        loading = findViewById(R.id.progressOrderDetail);
        items = findViewById(R.id.containerOrderDetailItems);
        String orderId = getIntent().getStringExtra("ORDER_ID");
        if (orderId == null || orderId.isEmpty()) {
            finish();
            return;
        }
        RetrofitClient.getAuthClient(this).create(ApiService.class).getOrderById(orderId)
                .enqueue(new Callback<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>>() {
                    @Override public void onResponse(Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Response<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> response) {
                        loading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            renderMap(response.body().getData());
                        } else {
                            showError();
                        }
                    }
                    @Override public void onFailure(Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                        loading.setVisibility(View.GONE);
                        showError();
                    }
                });
    }

    private String text(Object obj) {
        if (obj == null) return "";
        return String.valueOf(obj);
    }

    private double number(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); } catch (Exception ignored) {}
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private void renderMap(java.util.Map<String, Object> order) {
        String id = text(order.get("_id"));
        String shortId = id.length() > 10 ? id.substring(id.length() - 10).toUpperCase(Locale.ENGLISH) : id;
        ((TextView) findViewById(R.id.tvOrderDetailCode)).setText("#TR-" + shortId);
        
        String status = text(order.get("status"));
        if (status.isEmpty()) status = "Processing";
        ((TextView) findViewById(R.id.tvOrderDetailStatus)).setText(status);
        
        String createdAt = text(order.get("createdAt"));
        ((TextView) findViewById(R.id.tvOrderDetailDate)).setText("Placed on " + createdAt);
        
        String payment = text(order.get("paymentMethod"));
        ((TextView) findViewById(R.id.tvOrderDetailPayment)).setText(payment.isEmpty() ? "Pending" : payment);
        
        double total = number(order.get("totalPrice"));
        double shippingFee = number(order.get("shippingFee"));
        double discount = number(order.get("discount")); // Assuming discount might exist later
        
        if (total == 0) total = number(order.get("totalAmount"));
        
        double subtotal = total - shippingFee + discount;

        ((TextView) findViewById(R.id.tvOrderDetailSubtotal)).setText(PriceUtils.formatPriceUsd(subtotal));
        ((TextView) findViewById(R.id.tvOrderDetailShipping)).setText(PriceUtils.formatPriceUsd(shippingFee));
        ((TextView) findViewById(R.id.tvOrderDetailDiscount)).setText("-" + PriceUtils.formatPriceUsd(discount));
        ((TextView) findViewById(R.id.tvOrderDetailTotal)).setText(PriceUtils.formatPriceUsd(total));

        String addressText = "Delivery address unavailable";
        if (order.get("shippingAddress") instanceof java.util.Map) {
            java.util.Map<String, Object> addr = (java.util.Map<String, Object>) order.get("shippingAddress");
            String fullName = text(addr.get("fullName"));
            String phone = text(addr.get("phone"));
            String addressLine = text(addr.get("address"));
            String city = text(addr.get("city"));
            addressText = fullName + "\n" + phone + "\n" + addressLine + ", " + city;
        }
        ((TextView) findViewById(R.id.tvOrderDetailAddress)).setText(addressText);

        int progress = statusProgress(status);
        ((ProgressBar) findViewById(R.id.progressOrderTimeline)).setProgress(progress);
        
        int activeNode = R.drawable.bg_timeline_node_active;
        int inactiveNode = R.drawable.bg_timeline_node_inactive;
        
        ImageView[] ivs = {
                findViewById(R.id.ivStep1),
                findViewById(R.id.ivStep2),
                findViewById(R.id.ivStep3),
                findViewById(R.id.ivStep4)
        };
        TextView[] tvs = {
                findViewById(R.id.tvStepPlaced),
                findViewById(R.id.tvStepProcessing),
                findViewById(R.id.tvStepShipped),
                findViewById(R.id.tvStepDelivered)
        };
        
        int activeColor = getColor(R.color.tirtir_red_primary);
        int inactiveColor = getColor(R.color.tirtir_text_secondary);

        for (int i = 0; i < 4; i++) {
            boolean isActive = i <= progress;
            ivs[i].setBackgroundResource(isActive ? activeNode : inactiveNode);
            tvs[i].setTextColor(isActive ? activeColor : inactiveColor);
            tvs[i].setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            if (i > 0) { // Keep check icon for active ones
                ivs[i].setImageResource(isActive ? R.drawable.ic_check : 0);
            }
        }

        items.removeAllViews();
        if (!(order.get("items") instanceof java.util.List) || ((java.util.List<?>) order.get("items")).isEmpty()) {
            return;
        }
        
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Object rawItem : (java.util.List<?>) order.get("items")) {
            if (!(rawItem instanceof java.util.Map)) continue;
            java.util.Map<String, Object> item = (java.util.Map<String, Object>) rawItem;
            
            View row = inflater.inflate(R.layout.item_order_detail, items, false);
            
            int qty = (int) number(item.get("quantity"));
            double price = number(item.get("price"));
            String shade = text(item.get("shade"));
            String name = text(item.get("name"));
            String imageUrl = text(item.get("imageUrl")); // May be empty
            
            TextView tvName = row.findViewById(R.id.tvItemName);
            TextView tvVariant = row.findViewById(R.id.tvItemVariant);
            TextView tvQty = row.findViewById(R.id.tvItemQty);
            TextView tvPrice = row.findViewById(R.id.tvItemPrice);
            ImageView ivImage = row.findViewById(R.id.ivItemImage);
            
            tvName.setText(name.isEmpty() ? "TirTir product" : name);
            if (!shade.isEmpty()) {
                tvVariant.setText("Variant: " + shade);
                tvVariant.setVisibility(View.VISIBLE);
            } else {
                tvVariant.setVisibility(View.GONE);
            }
            tvQty.setText("Qty: " + qty);
            tvPrice.setText(PriceUtils.formatPriceUsd(price));
            
            if (!imageUrl.isEmpty()) {
                Glide.with(this).load(imageUrl).into(ivImage);
            } else {
                // Try product map fallback if needed or just leave default
            }
            
            items.addView(row);
        }
    }

    private int statusProgress(String status) {
        if (status == null) return 1;
        if ("Delivered".equalsIgnoreCase(status)) return 3;
        if ("Shipped".equalsIgnoreCase(status) || "Shipping".equalsIgnoreCase(status)) return 2;
        if ("Processing".equalsIgnoreCase(status) || "Confirmed".equalsIgnoreCase(status)) return 1;
        return 0; // Placed / Pending
    }

    private void showError() { Toast.makeText(this, "Unable to load order details", Toast.LENGTH_LONG).show(); }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
