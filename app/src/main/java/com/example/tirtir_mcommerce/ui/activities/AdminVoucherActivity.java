package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.adapters.AdminVoucherAdapter;
import com.example.tirtir_mcommerce.model.AdminVoucherStats;
import com.example.tirtir_mcommerce.model.AdminVouchersResponse;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.utils.AdminNavUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminVoucherActivity extends AppCompatActivity {
    private static final String TAG = "AdminVoucherActivity";
    
    private TextView tvTotalCreated, tvRedeemed, tvActiveNow, tvDiscountValue;
    private RecyclerView rvVouchers;
    private AdminVoucherAdapter adapter;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_voucher);

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);

        initViews();
        setupRecyclerView();
        
        loadStats();
        loadVouchers();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        tvTotalCreated = findViewById(R.id.tvTotalCreated);
        tvRedeemed = findViewById(R.id.tvRedeemed);
        tvActiveNow = findViewById(R.id.tvActiveNow);
        tvDiscountValue = findViewById(R.id.tvDiscountValue);
        rvVouchers = findViewById(R.id.rvVouchers);
        
        findViewById(R.id.fabAddVoucher).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, AdminVoucherDetailActivity.class));
        });
        
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavAdmin);
        if (bottomNav != null) {
            AdminNavUtils.setupBottomNav(this, bottomNav, R.id.nav_admin_marketing);
        }
    }

    private void setupRecyclerView() {
        rvVouchers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminVoucherAdapter(new ArrayList<>());
        rvVouchers.setAdapter(adapter);
    }

    private void loadStats() {
        apiService.getAdminVoucherStats().enqueue(new Callback<ApiResponse<AdminVoucherStats>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminVoucherStats>> call, Response<ApiResponse<AdminVoucherStats>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AdminVoucherStats stats = response.body().getData();
                    tvTotalCreated.setText(String.valueOf(stats.getTotal()));
                    tvRedeemed.setText(String.valueOf(stats.getTotalUsage()));
                    tvActiveNow.setText(String.valueOf(stats.getActive()));
                    
                    // Value formatting (e.g. $42k)
                    double val = stats.getTotalDiscountValue();
                    if (val >= 1000) {
                        tvDiscountValue.setText("$" + String.format("%.1fk", val / 1000));
                    } else {
                        tvDiscountValue.setText("$" + (int)val);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminVoucherStats>> call, Throwable t) {
                Log.e(TAG, "Error fetching stats", t);
            }
        });
    }

    private void loadVouchers() {
        apiService.getAdminVouchers().enqueue(new Callback<AdminVouchersResponse>() {
            @Override
            public void onResponse(Call<AdminVouchersResponse> call, Response<AdminVouchersResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    adapter.setVouchers(response.body().getData());
                }
            }

            @Override
            public void onFailure(Call<AdminVouchersResponse> call, Throwable t) {
                Log.e(TAG, "Error fetching vouchers", t);
                Toast.makeText(AdminVoucherActivity.this, "Failed to load vouchers", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
