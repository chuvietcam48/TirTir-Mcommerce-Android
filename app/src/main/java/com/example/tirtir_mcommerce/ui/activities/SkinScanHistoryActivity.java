package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SkinScanHistoryActivity extends AppCompatActivity {

    private ImageView ivUserAvatar;
    private TextView tvSkinShade;
    private View vSkinHex;
    private ChipGroup cgSkinTypes;
    private TextView tvTechTone, tvTechUndertone, tvTechITA, tvTechTexture, tvTechPores, tvTechHydration;
    private LinearLayout llInsightsContainer;
    private View contentContainer;
    private TextView tvEmptyState;
    private View btnBack, btnCart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_scan_history);

        initViews();
        fetchUserProfile();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnCart = findViewById(R.id.btnCart);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvSkinShade = findViewById(R.id.tvSkinShade);
        vSkinHex = findViewById(R.id.vSkinHex);
        cgSkinTypes = findViewById(R.id.cgSkinTypes);
        tvTechTone = findViewById(R.id.tvTechTone);
        tvTechUndertone = findViewById(R.id.tvTechUndertone);
        tvTechITA = findViewById(R.id.tvTechITA);
        tvTechTexture = findViewById(R.id.tvTechTexture);
        tvTechPores = findViewById(R.id.tvTechPores);
        tvTechHydration = findViewById(R.id.tvTechHydration);
        llInsightsContainer = findViewById(R.id.llInsightsContainer);
        contentContainer = findViewById(R.id.contentContainer);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        btnBack.setOnClickListener(v -> finish());
    }

    private void fetchUserProfile() {
        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.getProfile().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    User user = response.body().getData();
                    bindUserData(user);
                } else {
                    showEmptyState();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                Toast.makeText(SkinScanHistoryActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                showEmptyState();
            }
        });
    }

    private void bindUserData(User user) {
        User.SkinProfile profile = user.getSkinProfile();
        if (profile == null) {
            showEmptyState();
            return;
        }

        contentContainer.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        // Avatar
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            Glide.with(this).load(user.getAvatar()).circleCrop().into(ivUserAvatar);
        } else {
            ivUserAvatar.setImageResource(R.drawable.ic_person); // fallback
        }

        // Summary Card
        tvSkinShade.setText(profile.getSkinTone() != null ? profile.getSkinTone() : "Unknown");
        if (profile.getSkinHex() != null && !profile.getSkinHex().isEmpty()) {
            try {
                vSkinHex.getBackground().setTint(Color.parseColor(profile.getSkinHex()));
            } catch (Exception ignored) { }
        }

        // Skin Type Chips
        cgSkinTypes.removeAllViews();
        if (profile.getSkinType() != null) {
            addChip(profile.getSkinType());
        }
        if (profile.getConcerns() != null) {
            for (String concern : profile.getConcerns()) {
                addChip(concern);
            }
        }

        // Technical Analysis
        tvTechTone.setText(safeStr(profile.getSkinTone()));
        tvTechUndertone.setText(safeStr(profile.getUndertone()));
        tvTechITA.setText(safeStr(profile.getItaCategory()));
        tvTechTexture.setText(safeStr(profile.getTexture()));
        tvTechPores.setText(safeStr(profile.getPores()));
        tvTechHydration.setText(safeStr(profile.getHydration()));

        // Insights
        llInsightsContainer.removeAllViews();
        if (profile.getRecommendations() != null) {
            for (String insight : profile.getRecommendations()) {
                View insightView = getLayoutInflater().inflate(R.layout.item_skin_insight, llInsightsContainer, false);
                TextView tvTitle = insightView.findViewById(R.id.tvInsightTitle);
                TextView tvDesc = insightView.findViewById(R.id.tvInsightDesc);
                tvTitle.setText(insight);
                tvDesc.setText("Based on your AI Skin Scan.");
                llInsightsContainer.addView(insightView);
            }
        }
    }

    private void addChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setTextSize(10f);
        chip.setChipBackgroundColorResource(R.color.tirtir_gray_light);
        chip.setTextColor(getResources().getColor(R.color.tirtir_text_secondary, null));
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setChipMinHeightResource(R.dimen.chip_min_height); // Ensure you have this dimen or set layout params
        cgSkinTypes.addView(chip);
    }

    private String safeStr(String str) {
        return (str != null && !str.isEmpty()) ? str : "N/A";
    }

    private void showEmptyState() {
        contentContainer.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
    }
}
