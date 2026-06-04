package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;

import java.text.NumberFormat;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    private TextView tvCheckoutTotal;
    private Button btnPlaceOrder;
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    private double cartTotal;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Using a dynamically generated simple layout instead of creating an XML file to save time.
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Checkout");
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        tvCheckoutTotal = new TextView(this);
        tvCheckoutTotal.setTextSize(18);
        tvCheckoutTotal.setPadding(0, 32, 0, 32);
        layout.addView(tvCheckoutTotal);

        btnPlaceOrder = new Button(this);
        btnPlaceOrder.setText("Place Order");
        btnPlaceOrder.setBackgroundColor(0xFFE91E63);
        btnPlaceOrder.setTextColor(0xFFFFFFFF);
        layout.addView(btnPlaceOrder);

        setContentView(layout);

        cartTotal = getIntent().getDoubleExtra("CART_TOTAL", 0.0);
        tvCheckoutTotal.setText("Total to pay: " + currencyFormat.format(cartTotal) + " đ");

        btnPlaceOrder.setOnClickListener(v -> {
            // Mock placing order
            DatabaseHelper.getInstance(this).clearCart();
            
            Intent intent = new Intent(this, OrderSuccessActivity.class);
            intent.putExtra("ORDER_CODE", "ORD-" + System.currentTimeMillis());
            intent.putExtra("ORDER_TOTAL", cartTotal);
            startActivity(intent);
            finish();
        });
    }
}
