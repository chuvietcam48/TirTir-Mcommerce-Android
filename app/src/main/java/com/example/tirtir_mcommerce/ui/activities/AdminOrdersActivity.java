package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.adapters.AdminOrderAdapter;

import java.util.ArrayList;
import java.util.List;

public class AdminOrdersActivity extends AppCompatActivity implements AdminOrderAdapter.OnOrderActionListener {

    private RecyclerView rvAdminOrders;
    private AdminOrderAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_orders);

        Toolbar toolbar = findViewById(R.id.toolbarAdminOrders);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvAdminOrders = findViewById(R.id.rvAdminOrders);
        rvAdminOrders.setLayoutManager(new LinearLayoutManager(this));

        setupMockData();
    }

    private void setupMockData() {
        List<AdminOrderAdapter.AdminOrder> mockOrders = new ArrayList<>();
        mockOrders.add(new AdminOrderAdapter.AdminOrder("#ORD-1001", "Nguyễn Văn A", 450000, "confirmed", "123 Lê Lợi, Q1, HCM", 30000, "11/06/2026 10:00", "- Ceramic Milk Ampoule x1\n- Mask Pack x5"));
        mockOrders.add(new AdminOrderAdapter.AdminOrder("#ORD-1002", "Trần Thị B", 1200000, "pending", "456 Nguyễn Huệ, Đà Nẵng", 45000, "11/06/2026 12:30", "- Vitamin C Serum x2\n- Sunscreen x1"));
        mockOrders.add(new AdminOrderAdapter.AdminOrder("#ORD-1003", "Lê Văn C", 320000, "shipping", "789 Cách Mạng Tháng 8, Hà Nội", 25000, "10/06/2026 09:15", "- Cushion Foundation x1"));

        adapter = new AdminOrderAdapter(this, mockOrders, this);
        rvAdminOrders.setAdapter(adapter);
    }

    @Override
    public void onShowDetail(AdminOrderAdapter.AdminOrder order) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_order_detail, null);
        
        TextView tvProducts = dialogView.findViewById(R.id.tvDetailProducts);
        TextView tvAddress = dialogView.findViewById(R.id.tvDetailAddress);
        TextView tvFee = dialogView.findViewById(R.id.tvDetailShippingFee);
        TextView tvTime = dialogView.findViewById(R.id.tvDetailTime);

        tvProducts.setText(order.products);
        tvAddress.setText(order.address);
        tvFee.setText(String.format("%,.0f đ", order.shippingFee));
        tvTime.setText(order.orderTime);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Đóng", null)
                .show();
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
