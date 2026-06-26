package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.CartRecoveryAdapter;

import java.util.ArrayList;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminCartRecoveryActivity extends AppCompatActivity {
    private TextView abandoned;
    private TextView recovered;
    private TextView rate;
    private TextView tvEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_cart_recovery);
        Toolbar toolbar = findViewById(R.id.toolbarAdminCartRecovery);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        abandoned = findViewById(R.id.tvStatAbandoned);
        recovered = findViewById(R.id.tvStatRecovered);
        rate = findViewById(R.id.tvStatRate);
        tvEmpty = findViewById(R.id.tvCartRecoveryEmpty);
        
        RecyclerView list = findViewById(R.id.rvCartRecovery);
        list.setLayoutManager(new LinearLayoutManager(this));
        
        list.setAdapter(new CartRecoveryAdapter(this, new java.util.ArrayList<>()));
        if (tvEmpty != null) {
            tvEmpty.setText("Individual cart recovery details are not available from the API.\nAggregate statistics are shown above.");
            tvEmpty.setVisibility(android.view.View.VISIBLE);
        }
        loadStats();
    }

    private void loadStats() {
        abandoned.setText("—");
        recovered.setText("—");
        rate.setText("—");
        RetrofitClient.getAuthClient(this).create(ApiService.class)
                .getCartRecoveryStats().enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            showError();
                            return;
                        }
                        Map<String, Object> data = response.body();
                        abandoned.setText(String.valueOf(asInt(data.get("totalAbandoned"))));
                        recovered.setText(String.valueOf(asInt(data.get("recoveredCount"))));
                        rate.setText(formatRate(data.get("conversionRate")));
                    }

                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        showError();
                    }
                });
    }

    private int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String formatRate(Object value) {
        return value instanceof Number ? String.format(java.util.Locale.ENGLISH, "%.1f%%",
                ((Number) value).doubleValue()) : "0%";
    }

    private void showError() {
        abandoned.setText("—");
        recovered.setText("—");
        rate.setText("—");
        Toast.makeText(this, "Unable to load recovery metrics.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
