package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.adapters.CartRecoveryAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminCartRecoveryActivity extends AppCompatActivity {

    private RecyclerView rvCartRecovery;
    private CartRecoveryAdapter adapter;
    private TextView tvAbandoned, tvRecovered, tvRate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_cart_recovery);

        Toolbar toolbar = findViewById(R.id.toolbarAdminCartRecovery);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvAbandoned = findViewById(R.id.tvStatAbandoned);
        tvRecovered = findViewById(R.id.tvStatRecovered);
        tvRate = findViewById(R.id.tvStatRate);
        rvCartRecovery = findViewById(R.id.rvCartRecovery);
        
        rvCartRecovery.setLayoutManager(new LinearLayoutManager(this));

        setupMockData();
    }

    private void setupMockData() {
        tvAbandoned.setText("245");
        tvRecovered.setText("32");
        tvRate.setText("13.1%");

        List<CartRecoveryAdapter.CartRecoveryItem> items = new ArrayList<>();
        items.add(new CartRecoveryAdapter.CartRecoveryItem("Nguyễn Văn A", "a@gmail.com", "Abandoned", "Ceramic Milk Ampoule, Mask Pack", 500000, "2 giờ trước"));
        items.add(new CartRecoveryAdapter.CartRecoveryItem("Trần Thị B", "b@gmail.com", "Recovered", "Vitamin C Serum", 1200000, "5 giờ trước"));
        items.add(new CartRecoveryAdapter.CartRecoveryItem("Lê Văn C", "c@gmail.com", "Abandoned", "Cushion Foundation", 320000, "1 ngày trước"));

        adapter = new CartRecoveryAdapter(this, items);
        rvCartRecovery.setAdapter(adapter);
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
