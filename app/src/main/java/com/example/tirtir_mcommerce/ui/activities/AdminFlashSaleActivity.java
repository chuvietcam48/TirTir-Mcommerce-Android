package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.adapters.AdminCampaignAdapter;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Campaign;
import com.example.tirtir_mcommerce.model.MarketingOverviewResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.tirtir_mcommerce.utils.AdminNavUtils;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminFlashSaleActivity extends AppCompatActivity {

    private EditText etTitle, etMessage, etPath, etTarget;
    private Button btnSend;
    private ProgressBar pbLoading;
    private View btnBack;
    private View cvCreateForm;
    private RecyclerView rvFlashSales;
    private AdminCampaignAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_flash_sale);

        btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        etTitle = findViewById(R.id.etFlashSaleTitle);
        etMessage = findViewById(R.id.etFlashSaleMessage);
        etPath = findViewById(R.id.etFlashSalePath);
        etTarget = findViewById(R.id.etFlashSaleTarget);
        btnSend = findViewById(R.id.btnSendFlashSale);
        pbLoading = findViewById(R.id.pbFlashSaleLoading);
        cvCreateForm = findViewById(R.id.cvCreateForm);
        rvFlashSales = findViewById(R.id.rvFlashSales);
        
        rvFlashSales.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new AdminCampaignAdapter(null);
        rvFlashSales.setAdapter(adapter);

        findViewById(R.id.fabAddFlashSale).setOnClickListener(v -> {
            if (cvCreateForm.getVisibility() == View.VISIBLE) {
                cvCreateForm.setVisibility(View.GONE);
            } else {
                cvCreateForm.setVisibility(View.VISIBLE);
            }
        });

        btnSend.setOnClickListener(v -> sendFlashSale());
        
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavAdmin);
        if (bottomNav != null) {
            AdminNavUtils.setupBottomNav(this, bottomNav, R.id.nav_admin_marketing);
        }
        
        loadCampaigns();
    }
    
    private void loadCampaigns() {
        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.getMarketingOverview().enqueue(new Callback<ApiResponse<MarketingOverviewResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<MarketingOverviewResponse>> call, Response<ApiResponse<MarketingOverviewResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    MarketingOverviewResponse data = response.body().getData();
                    if (data != null && data.getCampaigns() != null) {
                        adapter.setCampaigns(data.getCampaigns());
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<MarketingOverviewResponse>> call, Throwable t) {
                Toast.makeText(AdminFlashSaleActivity.this, "Failed to load campaigns", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendFlashSale() {
        String title = etTitle.getText().toString().trim();
        String message = etMessage.getText().toString().trim();
        String path = etPath.getText().toString().trim();
        String target = etTarget.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
            Toast.makeText(this, "Title and Message are required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);

        Map<String, String> body = new HashMap<>();
        body.put("title", title);
        body.put("message", message);
        if (!TextUtils.isEmpty(path)) body.put("path", path);
        if (!TextUtils.isEmpty(target)) body.put("targetAudience", target);

        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.sendFlashSale(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                btnSend.setEnabled(true);
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(AdminFlashSaleActivity.this, "Flash sale broadcasted successfully!", Toast.LENGTH_LONG).show();
                    cvCreateForm.setVisibility(View.GONE);
                    etTitle.setText("");
                    etMessage.setText("");
                    loadCampaigns();
                } else {
                    Toast.makeText(AdminFlashSaleActivity.this, "Failed to send flash sale", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                btnSend.setEnabled(true);
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                Toast.makeText(AdminFlashSaleActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
