package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.MainActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class OrderSuccessActivity extends AppCompatActivity {

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(64, 128, 64, 64);
        layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Order Successful!");
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF4CAF50); // Green
        layout.addView(title);

        String orderCode = getIntent().getStringExtra("ORDER_CODE");
        double orderTotal = getIntent().getDoubleExtra("ORDER_TOTAL", 0.0);

        TextView details = new TextView(this);
        details.setText("Order Code: " + orderCode + "\nTotal: " + currencyFormat.format(orderTotal) + " đ");
        details.setTextSize(16);
        details.setPadding(0, 32, 0, 64);
        details.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        layout.addView(details);

        Button btnHome = new Button(this);
        btnHome.setText("Back to Home");
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
        layout.addView(btnHome);

        setContentView(layout);
    }
}
