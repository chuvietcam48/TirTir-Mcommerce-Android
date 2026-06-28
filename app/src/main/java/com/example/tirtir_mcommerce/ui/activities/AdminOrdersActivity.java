package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.AdminOrderAdapter;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminOrdersActivity extends AppCompatActivity implements AdminOrderAdapter.OnOrderActionListener {
    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;
    private ApiService api;
    private final List<AdminOrderAdapter.AdminOrder> orders = new ArrayList<>();
    private AdminOrderAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_orders);
        Toolbar toolbar = findViewById(R.id.toolbarAdminOrders);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        list = findViewById(R.id.rvAdminOrders);
        progress = findViewById(R.id.progressAdminOrders);
        empty = findViewById(R.id.tvAdminOrdersEmpty);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminOrderAdapter(this, orders, this);
        list.setAdapter(adapter);
        api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        loadOrders();
    }

    private void loadOrders() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        api.getAdminOrders(100).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                orders.clear();
                if (response.isSuccessful() && response.body() != null) {
                    for (Map<String, Object> row : response.body()) orders.add(mapOrder(row));
                }
                adapter.notifyDataSetChanged();
                empty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                empty.setText("Unable to load orders");
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private AdminOrderAdapter.AdminOrder mapOrder(Map<String, Object> row) {
        String id = text(row.get("_id"));
        Map<String, Object> user = row.get("user") instanceof Map
                ? (Map<String, Object>) row.get("user") : new HashMap<>();
        Map<String, Object> address = row.get("shippingAddress") instanceof Map
                ? (Map<String, Object>) row.get("shippingAddress") : new HashMap<>();
        String addressText = join(text(address.get("address")), text(address.get("ward")),
                text(address.get("district")), text(address.get("city")));
        StringBuilder products = new StringBuilder();
        if (row.get("items") instanceof List) {
            for (Object raw : (List<?>) row.get("items")) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> item = (Map<String, Object>) raw;
                products.append("- ").append(text(item.get("name"))).append(" x")
                        .append((int) number(item.get("quantity"))).append("\n");
            }
        }
        String code = "#" + (id.length() > 8 ? id.substring(id.length() - 8).toUpperCase() : id);
        return new AdminOrderAdapter.AdminOrder(id, code,
                text(user.get("name")).isEmpty() ? "Guest" : text(user.get("name")),
                number(row.get("totalAmount")), text(row.get("status")), addressText,
                number(row.get("shippingCost")), text(row.get("createdAt")),
                products.toString().trim());
    }

    @Override
    public void onShowDetail(AdminOrderAdapter.AdminOrder order) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_order_detail, null);
        ((TextView) dialogView.findViewById(R.id.tvDetailProducts)).setText(order.products);
        ((TextView) dialogView.findViewById(R.id.tvDetailAddress)).setText(order.address);
        ((TextView) dialogView.findViewById(R.id.tvDetailShippingFee))
                .setText(PriceUtils.formatPriceUsd(order.shippingFee));
        ((TextView) dialogView.findViewById(R.id.tvDetailTime)).setText(order.orderTime);
        new AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Close", null).show();
    }

    @Override
    public void onStatusChanged(AdminOrderAdapter.AdminOrder order, String status) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        api.updateAdminOrderStatus(order.id, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    order.status = status;
                    Toast.makeText(AdminOrdersActivity.this, "Order status updated.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminOrdersActivity.this, "Unable to update order status.", Toast.LENGTH_LONG).show();
                    loadOrders();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(AdminOrdersActivity.this, "Connection error.", Toast.LENGTH_LONG).show();
                loadOrders();
            }
        });
    }

    private String join(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(part);
        }
        return result.toString();
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : 0; }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
