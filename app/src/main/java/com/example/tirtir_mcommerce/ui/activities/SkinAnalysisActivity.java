package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;

public class SkinAnalysisActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_analysis);
        findViewById(R.id.btnCloseSkinAnalysis).setOnClickListener(v -> finish());
        findViewById(R.id.btnCaptureSkin).setOnClickListener(v ->
                startActivity(new Intent(this, SkinResultActivity.class)));
    }
}
