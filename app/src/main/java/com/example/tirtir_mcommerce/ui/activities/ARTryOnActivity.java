package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ARTryOnActivity extends AppCompatActivity {
    private static final int[] SHADE_COLORS = {
            0xFFE9B5A5, 0xFFD99A88, 0xFFC9786D, 0xFFB55E5A,
            0xFFA9474A, 0xFF8F343E, 0xFF74303A, 0xFF57252F
    };

    private LinearLayout layoutColors;
    private TextView tvLoading;
    private int selectedIndex = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_try_on);

        layoutColors = findViewById(R.id.layoutArColorPicker);
        tvLoading = findViewById(R.id.tvArLoading);
        ImageButton btnClose = findViewById(R.id.btnCloseAr);
        FloatingActionButton btnCapture = findViewById(R.id.fabArCapture);

        btnClose.setOnClickListener(v -> finish());
        btnCapture.setOnClickListener(v -> Toast.makeText(this,
                "The try-on preview is still preparing. Please try again shortly.",
                Toast.LENGTH_SHORT).show());

        buildColorPicker();
        tvLoading.postDelayed(() -> tvLoading.setVisibility(View.GONE), 900);
    }

    private void buildColorPicker() {
        layoutColors.removeAllViews();
        for (int i = 0; i < SHADE_COLORS.length; i++) {
            ImageButton button = new ImageButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
            params.setMargins(dp(6), 0, dp(6), 0);
            button.setLayoutParams(params);
            button.setBackground(createShadeBackground(SHADE_COLORS[i], i == selectedIndex));
            button.setContentDescription("Choose shade " + (i + 1));
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setPadding(0, 0, 0, 0);
            final int index = i;
            button.setOnClickListener(v -> {
                selectedIndex = index;
                buildColorPicker();
            });
            layoutColors.addView(button);
        }
    }

    private GradientDrawable createShadeBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(selected ? 4 : 1), selected ? Color.WHITE : 0x55FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
