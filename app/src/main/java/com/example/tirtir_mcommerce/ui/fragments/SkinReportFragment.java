package com.example.tirtir_mcommerce.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.SkinAnalysisResult;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.bumptech.glide.Glide;
import java.io.File;

import java.util.List;

public class SkinReportFragment extends Fragment {

    private SkinAnalysisResult analysisResult;
    private int textureScore = -1, poresScore = -1, hydrationScore = -1, rednessScore = -1;
    private double itaAngle = Double.NaN;

    // UI
    private View cardConfidenceWarning;
    private TextView tvSkinType;
    private ImageView imgSkinToneFace;
    private TextView tvToneValue;
    private TextView tvUndertoneValue;
    private TextView tvDermNote;

    // Metric items
    private View metricHydration, metricEvenness, metricTexture, metricSensitivity;

    public static SkinReportFragment newInstance() {
        return new SkinReportFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skin_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        
        // Let's set up the button click (can navigate to AI Routine tab, or we can leave it empty since ViewPager handles tabs)
        View btnViewRoutine = view.findViewById(R.id.btnViewRoutine);
        if (btnViewRoutine != null) {
            btnViewRoutine.setOnClickListener(v -> {
                // For now just simulate clicking the tab
                if (getActivity() != null && getActivity() instanceof com.example.tirtir_mcommerce.ui.activities.SkinResultActivity) {
                    androidx.viewpager2.widget.ViewPager2 viewPager = getActivity().findViewById(R.id.viewPagerSkinResult);
                    if (viewPager != null) {
                        viewPager.setCurrentItem(2, true);
                    }
                }
            });
        }
        
        if (analysisResult != null) populateData();
    }

    public void updateData(SkinAnalysisResult result, int texture, int pores, int hydration, int redness,
                           double itaDegrees) {
        this.analysisResult = result;
        this.textureScore = texture;
        this.poresScore = pores;
        this.hydrationScore = hydration;
        this.rednessScore = redness;
        this.itaAngle = itaDegrees;
        if (getView() != null) populateData();
    }

    private void bindViews(View view) {
        cardConfidenceWarning = view.findViewById(R.id.cardConfidenceWarning);
        tvSkinType            = view.findViewById(R.id.tvSkinType);
        imgSkinToneFace       = view.findViewById(R.id.imgSkinToneFace);
        tvToneValue           = view.findViewById(R.id.tvToneValue);
        tvUndertoneValue      = view.findViewById(R.id.tvUndertoneValue);
        tvDermNote            = view.findViewById(R.id.tvDermNote);
        
        metricHydration       = view.findViewById(R.id.metricHydration);
        metricEvenness        = view.findViewById(R.id.metricEvenness);
        metricTexture         = view.findViewById(R.id.metricTexture);
        metricSensitivity     = view.findViewById(R.id.metricSensitivity);
    }

    private void populateData() {
        if (analysisResult == null) return;

        // Confidence warning (< 50%)
        boolean lowConfidence = analysisResult.getConfidence() > 0
                && analysisResult.getConfidence() < 50.0;
        if (cardConfidenceWarning != null) {
            cardConfidenceWarning.setVisibility(lowConfidence ? View.VISIBLE : View.GONE);
        }

        // Skin tone face image
        if (analysisResult.getImagePath() != null && !analysisResult.getImagePath().isEmpty()) {
            Glide.with(this).load(new File(analysisResult.getImagePath())).into(imgSkinToneFace);
        } else {
            String hex = analysisResult.getSkinHex();
            if (hex != null && !hex.isEmpty()) {
                try {
                    imgSkinToneFace.setBackgroundColor(Color.parseColor(hex));
                } catch (Exception ignored) { }
            }
        }

        // Top info
        tvSkinType.setText(safeStr(analysisResult.getSkinType(), "—"));
        tvToneValue.setText(safeStr(analysisResult.getSkinTone(), "Medium"));
        tvUndertoneValue.setText(safeStr(analysisResult.getUndertone(), "Neutral"));

        // Build derm note from concerns
        List<String> concerns = analysisResult.getConcerns();
        String concernsText = "";
        if (concerns != null && !concerns.isEmpty()) {
            concernsText = " with notable " + String.join(", ", concerns);
        }
        String note = String.format("\"Your skin presents characteristics of %s type%s. A gentle, consistent routine targeting your specific concerns is the foundation of healthy skin.\"", 
                safeStr(analysisResult.getSkinType(), "Normal"), concernsText);
        tvDermNote.setText(note);

        // Bind Metrics
        // Hydration (mapped to moisture)
        bindMetric(metricHydration, "Hydration", hydrationScore >= 0 ? hydrationScore : 62, "Deep skin hydration and surface barrier protection", false);
        
        // Evenness (mapped to texture/evenness score)
        bindMetric(metricEvenness, "Skin Evenness", textureScore >= 0 ? textureScore : 60, "Skin tone evenness and dark spot presence", false);
        
        // Texture & Pores (mapped to pores variance)
        bindMetric(metricTexture, "Texture & Pores", poresScore >= 0 ? poresScore : 60, "Surface smoothness, pore size, and skin texture", false);
        
        // Sensitivity (mapped to redness score)
        bindMetric(metricSensitivity, "Sensitivity Index", rednessScore >= 0 ? rednessScore : 30, "Reactivity level to environmental factors", true);
    }

    private void bindMetric(View metricView, String title, int score, String subtitle, boolean isSensitivity) {
        if (metricView == null) return;
        
        TextView tvTitle = metricView.findViewById(R.id.tvMetricTitle);
        TextView tvScore = metricView.findViewById(R.id.tvMetricScore);
        TextView tvSubtitle = metricView.findViewById(R.id.tvMetricSubtitle);
        LinearProgressIndicator progress = metricView.findViewById(R.id.progressMetric);
        TextView tvTag = metricView.findViewById(R.id.tvMetricTag);
        
        tvTitle.setText(title);
        tvScore.setText(String.valueOf(score));
        tvSubtitle.setText(subtitle);
        
        progress.setProgressCompat(score, true);
        
        if (isSensitivity) {
            progress.setIndicatorColor(Color.parseColor("#4A4A4A"));
            tvTag.setVisibility(View.VISIBLE);
            if (score <= 40) {
                tvTag.setText("NORMAL RANGE");
                tvTag.setBackgroundColor(Color.parseColor("#4A4A4A"));
            } else if (score <= 70) {
                tvTag.setText("MODERATE");
                tvTag.setBackgroundColor(Color.parseColor("#FF9800")); // Orange
            } else {
                tvTag.setText("HIGH SENSITIVITY");
                tvTag.setBackgroundColor(Color.parseColor("#E50000")); // Red
            }
        } else {
            tvTag.setVisibility(View.GONE);
            progress.setIndicatorColor(Color.parseColor("#E0E0E0"));
        }
    }

    private String safeStr(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
