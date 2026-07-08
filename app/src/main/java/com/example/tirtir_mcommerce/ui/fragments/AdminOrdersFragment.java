package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
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

public class AdminOrdersFragment extends Fragment implements AdminOrderAdapter.OnOrderActionListener {
    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;
    private TextView tvOrderCount;
    private ApiService api;
    private final List<AdminOrderAdapter.AdminOrder> orders = new ArrayList<>();
    private AdminOrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        list = view.findViewById(R.id.rvAdminOrders);
        progress = view.findViewById(R.id.progressAdminOrders);
        empty = view.findViewById(R.id.tvAdminOrdersEmpty);
        tvOrderCount = view.findViewById(R.id.tvOrderCount);
        
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminOrderAdapter(requireContext(), orders, this);
        list.setAdapter(adapter);
        
        api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        loadOrders();
    }

    private void loadOrders() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        api.getAdminOrders(100).enqueue(new Callback<com.example.tirtir_mcommerce.model.ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<com.example.tirtir_mcommerce.model.ApiResponse<List<Map<String, Object>>>> call,
                                   Response<com.example.tirtir_mcommerce.model.ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                orders.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    for (Map<String, Object> row : response.body().getData()) orders.add(mapOrder(row));
                }
                adapter.notifyDataSetChanged();
                empty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
                if (tvOrderCount != null) {
                    tvOrderCount.setText("Showing " + orders.size() + " orders");
                }
            }

            @Override
            public void onFailure(Call<com.example.tirtir_mcommerce.model.ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                empty.setText("Unable to load orders");
                empty.setVisibility(View.VISIBLE);
                if (tvOrderCount != null) {
                    tvOrderCount.setText("Showing 0 orders");
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private AdminOrderAdapter.AdminOrder mapOrder(Map<String, Object> row) {
        String id = text(row.get("_id"));
        Map<String, Object> user = row.get("userId") instanceof Map
                ? (Map<String, Object>) row.get("userId") : new HashMap<>();
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
        
        String userName = text(user.get("firstName")) + " " + text(user.get("lastName"));
        if (userName.trim().isEmpty()) {
            userName = text(user.get("email")).isEmpty() ? "Guest" : text(user.get("email"));
        }
        
        return new AdminOrderAdapter.AdminOrder(id, code, userName.trim(),
                number(row.get("totalAmount")), text(row.get("status")), addressText,
                number(row.get("shippingCost")), text(row.get("createdAt")),
                products.toString().trim());
    }

    @Override
    public void onShowDetail(AdminOrderAdapter.AdminOrder order) {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.tirtir_mcommerce.ui.activities.AdminOrderDetailActivity.class);
        intent.putExtra(com.example.tirtir_mcommerce.ui.activities.AdminOrderDetailActivity.EXTRA_ORDER_ID, order.id);
        startActivity(intent);
    }

    @Override
    public void onStatusChanged(AdminOrderAdapter.AdminOrder order, String status) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        api.updateAdminOrderStatus(order.id, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    order.status = status;
                    Toast.makeText(requireContext(), "Order status updated.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Unable to update order status.", Toast.LENGTH_LONG).show();
                    loadOrders();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Connection error.", Toast.LENGTH_LONG).show();
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
}
