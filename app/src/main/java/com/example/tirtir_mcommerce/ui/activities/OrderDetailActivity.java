package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.model.ShippingAddress;
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
            getSupportActionBar().setTitle("Order details");
        }
        loading = findViewById(R.id.progressOrderDetail);
        items = findViewById(R.id.containerOrderDetailItems);
        String orderId = getIntent().getStringExtra("ORDER_ID");
        if (orderId == null || orderId.isEmpty()) {
            finish();
            return;
        }
        RetrofitClient.getAuthClient(this).create(ApiService.class).getOrderById(orderId)
                .enqueue(new Callback<OrderResponse>() {
                    @Override public void onResponse(Call<OrderResponse> call, Response<OrderResponse> response) {
                        loading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) render(response.body());
                        else showError();
                    }
                    @Override public void onFailure(Call<OrderResponse> call, Throwable t) {
                        loading.setVisibility(View.GONE);
                        showError();
                    }
                });
    }

    private void render(OrderResponse order) {
        String id = order.getId() == null ? "" : order.getId();
        String shortId = id.length() > 10 ? id.substring(id.length() - 10).toUpperCase(Locale.ENGLISH) : id;
        ((TextView) findViewById(R.id.tvOrderDetailCode)).setText("TRANSACTION #TT-" + shortId);
        ((TextView) findViewById(R.id.tvOrderDetailStatus)).setText(order.getStatus() == null ? "Processing" : order.getStatus());
        ((TextView) findViewById(R.id.tvOrderDetailDate)).setText(order.getCreatedAt() == null ? "" : order.getCreatedAt());
        ((TextView) findViewById(R.id.tvOrderDetailPayment)).setText("PAYMENT   " + (order.getPaymentMethod() == null ? "Pending" : order.getPaymentMethod()));
        ((TextView) findViewById(R.id.tvOrderDetailTotal)).setText("Total  " + PriceUtils.formatPriceUsd(order.getTotalPrice()));

        ShippingAddress address = order.getShippingAddress();
        ((TextView) findViewById(R.id.tvOrderDetailAddress)).setText(address == null ? "Delivery address unavailable"
                : safe(address.getFullName()) + "\n" + safe(address.getPhone()) + "\n" + safe(address.getAddress()) + ", " + safe(address.getCity()));

        int progress = statusProgress(order.getStatus());
        ((ProgressBar) findViewById(R.id.progressOrderTimeline)).setProgress(progress);
        int active = getColor(R.color.tirtir_red_dark);
        int inactive = getColor(R.color.tirtir_text_hint);
        int[] ids = {R.id.tvStepPlaced, R.id.tvStepProcessing, R.id.tvStepShipped, R.id.tvStepDelivered};
        for (int i = 0; i < ids.length; i++) ((TextView) findViewById(ids[i])).setTextColor(i <= progress ? active : inactive);

        items.removeAllViews();
        if (order.getItems() == null || order.getItems().isEmpty()) {
            addLine("Items are unavailable for this order", "");
            return;
        }
        for (OrderResponse.OrderItemResponse item : order.getItems()) {
            String meta = "Qty " + item.getQuantity();
            if (item.getShade() != null && !item.getShade().isEmpty()) meta += " · " + item.getShade();
            addLine(item.getName() == null ? "TirTir product" : item.getName(),
                    meta + "\n" + PriceUtils.formatPriceUsd(item.getPrice() * item.getQuantity()));
        }
    }

    private void addLine(String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(getColor(R.color.tirtir_black));
        name.setTextSize(14);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView meta = new TextView(this);
        meta.setText(detail);
        meta.setGravity(android.view.Gravity.END);
        meta.setTextColor(getColor(R.color.tirtir_text_secondary));
        meta.setTextSize(12);
        row.addView(meta);
        items.addView(row);
    }

    private int statusProgress(String status) {
        if (status == null) return 1;
        if ("Delivered".equalsIgnoreCase(status)) return 3;
        if ("Shipped".equalsIgnoreCase(status) || "Shipping".equalsIgnoreCase(status)) return 2;
        if ("Processing".equalsIgnoreCase(status)) return 1;
        return 0;
    }

    private String safe(String value) { return value == null ? "" : value; }
    private void showError() { Toast.makeText(this, "Unable to load order details", Toast.LENGTH_LONG).show(); }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
