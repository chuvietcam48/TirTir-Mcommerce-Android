package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * SCR-17 OrderSuccessActivity
 *
 * Displays:
 * - Generated order code
 * - Status (Confirmed)
 * - Total paid
 * - View Order History button
 * - Back to Home button
 *
 * Sprint S1.3
 */
public class OrderSuccessActivity extends AppCompatActivity {



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        String orderCode = getIntent().getStringExtra("ORDER_CODE");
        double orderTotal = getIntent().getDoubleExtra("ORDER_TOTAL", 0.0);

        TextView tvOrderCode = findViewById(R.id.tvOrderCode);
        TextView tvOrderTotal = findViewById(R.id.tvOrderTotal);
        MaterialButton btnBackHome = findViewById(R.id.btnBackHome);
        MaterialButton btnViewOrder = findViewById(R.id.btnViewOrder);

        if (orderCode != null) tvOrderCode.setText(orderCode);
        tvOrderTotal.setText(PriceUtils.formatPriceUsd(orderTotal));

        btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnViewOrder.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("OPEN_ORDER_HISTORY", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
