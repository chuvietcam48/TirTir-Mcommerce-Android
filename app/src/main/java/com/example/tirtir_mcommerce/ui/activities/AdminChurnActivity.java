package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.adapters.ChurnUserAdapter;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.ChurnUser;
import com.example.tirtir_mcommerce.model.RetentionStatsResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.tirtir_mcommerce.utils.AdminNavUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminChurnActivity extends AppCompatActivity {

    private TextView tvActiveCount, tvInactiveCount, tvRetentionRate;
    private RecyclerView rvChurnUsers;
    private ChurnUserAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_churn);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvActiveCount = findViewById(R.id.tvActiveCount);
        tvInactiveCount = findViewById(R.id.tvInactiveCount);
        tvRetentionRate = findViewById(R.id.tvRetentionRate);

        rvChurnUsers = findViewById(R.id.rvChurnUsers);
        rvChurnUsers.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new ChurnUserAdapter(this, new ArrayList<>(), null, null);
        rvChurnUsers.setAdapter(adapter);

        findViewById(R.id.btnWinBackPush).setOnClickListener(v -> {
            v.setEnabled(false);
            Toast.makeText(this, "Sending push notifications...", Toast.LENGTH_SHORT).show();
            RetrofitClient.getAuthClient(this).create(ApiService.class)
                .sendWinBackPush().enqueue(new Callback<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Response<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> response) {
                        v.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(AdminChurnActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(AdminChurnActivity.this, "Failed to send notifications", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<com.example.tirtir_mcommerce.model.ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                        v.setEnabled(true);
                        Toast.makeText(AdminChurnActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
        });
        
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavAdmin);
        if (bottomNav != null) {
            AdminNavUtils.setupBottomNav(this, bottomNav, R.id.nav_admin_marketing);
        }

        loadStats();
        loadAtRiskUsers();
    }

    private void loadStats() {
        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.getRetentionAnalytics().enqueue(new Callback<ApiResponse<RetentionStatsResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<RetentionStatsResponse>> call, Response<ApiResponse<RetentionStatsResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    RetentionStatsResponse stats = response.body().getData();
                    if (stats != null) {
                        tvActiveCount.setText(String.valueOf(stats.getActive()));
                        tvInactiveCount.setText(String.valueOf(stats.getInactive()));
                        tvRetentionRate.setText(stats.getRate());
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<RetentionStatsResponse>> call, Throwable t) {
                Toast.makeText(AdminChurnActivity.this, "Failed to load stats", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAtRiskUsers() {
        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.getAtRiskUsers().enqueue(new Callback<ApiResponse<List<ChurnUser>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ChurnUser>>> call, Response<ApiResponse<List<ChurnUser>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<ChurnUser> apiUsers = response.body().getData();
                    List<ChurnUserAdapter.ChurnUser> adapterUsers = new ArrayList<>();
                    if (apiUsers != null) {
                        for (ChurnUser au : apiUsers) {
                            adapterUsers.add(new ChurnUserAdapter.ChurnUser(
                                    au.getId(),
                                    au.getName(),
                                    "", // Email not in ChurnUser model
                                    au.getStatus() != null ? au.getStatus() : "At Risk",
                                    0, 0, (int)au.getLtv()
                            ));
                        }
                    }
                    adapter.setUsers(adapterUsers);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ChurnUser>>> call, Throwable t) {
                Toast.makeText(AdminChurnActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
