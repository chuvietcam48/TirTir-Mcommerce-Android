package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;

public class IngredientScanActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingredient_scan);
        findViewById(R.id.btnCloseIngredientScan).setOnClickListener(v -> finish());
        findViewById(R.id.btnCaptureIngredient).setOnClickListener(v -> {
            Intent intent = new Intent(this, ConflictResultActivity.class);
            intent.putExtra("PRODUCT_ID", getIntent().getStringExtra("PRODUCT_ID"));
            intent.putExtra("PRODUCT_NAME", getIntent().getStringExtra("PRODUCT_NAME"));
            intent.putExtra("SECOND_PRODUCT_ID", getIntent().getStringExtra("SECOND_PRODUCT_ID"));
            intent.putExtra("SECOND_PRODUCT_NAME", getIntent().getStringExtra("SECOND_PRODUCT_NAME"));
            startActivity(intent);
        });
    }
}
