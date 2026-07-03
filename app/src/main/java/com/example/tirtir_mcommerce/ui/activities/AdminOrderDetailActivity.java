package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminOrderDetailActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_ID = "EXTRA_ORDER_ID";

    private String orderId;
    private ApiService apiService;
    private ProgressBar progressBar;
    
    // UI References
    private TextView tvOrderId, tvOrderStatus, tvOrderDate;
    private TextView tvCustomerName, tvCustomerEmail, tvShippingAddress;
    private TextView tvDetailSubtotal, tvDetailShippingFee, tvDetailTotal;
    private TextView tvDetailProducts;
    
    private EditText etCarrier, etTracking, etInternalNotes, etCancelReason;
    private AutoCompleteTextView etStatusDropdown;
    
    private String[] orderStatuses = new String[]{"Pending", "Processing", "Shipped", "Delivered", "Cancelled"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_order_detail);
        
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null) {
            Toast.makeText(this, "Order ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        initViews();
        setupListeners();
        loadOrderDetails();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbarAdminOrder);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        progressBar = findViewById(R.id.progressAdminOrderDetail);
        
        tvOrderId = findViewById(R.id.tvOrderId);
        tvOrderStatus = findViewById(R.id.tvOrderStatus);
        tvOrderDate = findViewById(R.id.tvOrderDate);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvCustomerEmail = findViewById(R.id.tvCustomerEmail);
        tvShippingAddress = findViewById(R.id.tvShippingAddress);
        
        tvDetailSubtotal = findViewById(R.id.tvDetailSubtotal);
        tvDetailShippingFee = findViewById(R.id.tvDetailShippingFee);
        tvDetailTotal = findViewById(R.id.tvDetailTotal);
        tvDetailProducts = findViewById(R.id.tvDetailProducts);
        
        etCarrier = findViewById(R.id.etCarrier);
        etTracking = findViewById(R.id.etTracking);
        etInternalNotes = findViewById(R.id.etInternalNotes);
        etCancelReason = findViewById(R.id.etCancelReason);
        
        etStatusDropdown = findViewById(R.id.etStatusDropdown);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, orderStatuses);
        etStatusDropdown.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnUpdateLogistics).setOnClickListener(v -> updateShippingDetails());
        findViewById(R.id.btnSaveNotes).setOnClickListener(v -> saveInternalNotes());
        findViewById(R.id.btnCancelOrder).setOnClickListener(v -> cancelOrder());
    }

    private void loadOrderDetails() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getAdminOrderDetails(orderId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    populateUI(response.body().getData());
                } else {
                    Toast.makeText(AdminOrderDetailActivity.this, "Failed to load order", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdminOrderDetailActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void populateUI(Map<String, Object> order) {
        String id = text(order.get("_id"));
        tvOrderId.setText("#" + (id.length() > 8 ? id.substring(id.length() - 8).toUpperCase() : id));
        
        String status = text(order.get("status"));
        tvOrderStatus.setText(status);
        etStatusDropdown.setText(status, false);
        
        tvOrderDate.setText(text(order.get("createdAt")).split("T")[0]);

        // User info
        Map<String, Object> user = order.get("userId") instanceof Map ? (Map<String, Object>) order.get("userId") : new HashMap<>();
        String name = (text(user.get("firstName")) + " " + text(user.get("lastName"))).trim();
        tvCustomerName.setText(name.isEmpty() ? "Guest User" : name);
        tvCustomerEmail.setText(text(user.get("email")));

        // Shipping Address
        Map<String, Object> address = order.get("shippingAddress") instanceof Map ? (Map<String, Object>) order.get("shippingAddress") : new HashMap<>();
        tvShippingAddress.setText(text(address.get("address")) + ", " + text(address.get("city")));

        // Order Values
        double total = number(order.get("totalPrice"));
        double shipping = number(order.get("shippingFee"));
        tvDetailSubtotal.setText(PriceUtils.formatPriceUsd(total - shipping));
        tvDetailShippingFee.setText(PriceUtils.formatPriceUsd(shipping));
        tvDetailTotal.setText(PriceUtils.formatPriceUsd(total));

        // Products
        StringBuilder productsStr = new StringBuilder();
        if (order.get("items") instanceof List) {
            for (Object itemObj : (List<?>) order.get("items")) {
                if (itemObj instanceof Map) {
                    Map<String, Object> item = (Map<String, Object>) itemObj;
                    productsStr.append("- ").append(text(item.get("name"))).append(" x")
                            .append((int) number(item.get("quantity"))).append("\n");
                }
            }
        }
        tvDetailProducts.setText(productsStr.toString().trim());

        // Logistics
        Map<String, Object> shippingDetails = order.get("shippingDetails") instanceof Map ? (Map<String, Object>) order.get("shippingDetails") : new HashMap<>();
        etCarrier.setText(text(shippingDetails.get("carrier")));
        etTracking.setText(text(shippingDetails.get("trackingNumber")));
        
        // Notes
        etInternalNotes.setText(text(order.get("adminNotes")));
    }

    private void updateShippingDetails() {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("carrier", etCarrier.getText().toString().trim());
        body.put("trackingNumber", etTracking.getText().toString().trim());
        body.put("status", etStatusDropdown.getText().toString().trim());

        apiService.updateAdminOrderShipping(orderId, body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminOrderDetailActivity.this, "Shipping details updated", Toast.LENGTH_SHORT).show();
                    String newStatus = etStatusDropdown.getText().toString().trim();
                    tvOrderStatus.setText(newStatus);
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void saveInternalNotes() {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("adminNotes", etInternalNotes.getText().toString().trim());

        apiService.updateAdminOrderNotes(orderId, body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminOrderDetailActivity.this, "Notes saved", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void cancelOrder() {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("cancellationReason", etCancelReason.getText().toString().trim());

        apiService.cancelAdminOrder(orderId, body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminOrderDetailActivity.this, "Order cancelled", Toast.LENGTH_SHORT).show();
                    tvOrderStatus.setText("Cancelled");
                    etStatusDropdown.setText("Cancelled", false);
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private String text(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private double number(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return obj != null ? Double.parseDouble(String.valueOf(obj)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
