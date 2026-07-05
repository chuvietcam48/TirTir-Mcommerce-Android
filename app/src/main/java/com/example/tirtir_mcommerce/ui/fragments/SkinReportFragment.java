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
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.flexbox.FlexboxLayout;
import com.bumptech.glide.Glide;
import java.io.File;

import java.util.List;

/**
 * Tab 2: Skin Report — Hồ sơ da.
 * Hiển thị kết quả từ API /api/v1/ai/analyze-face:
 * - SkinTone swatch + Undertone + ITA angle
 * - SkinType badge
 * - 3 CircularProgressIndicator (Texture, Pores, Hydration)
 * - Concerns tags (FlexboxLayout)
 * - Confidence warning banner nếu confidence < 50%
 */
public class SkinReportFragment extends Fragment {

    private SkinAnalysisResult analysisResult;
    private int textureScore = -1, poresScore = -1, hydrationScore = -1;
    private double itaAngle = Double.NaN;

    // UI
    private View cardConfidenceWarning;
    private ImageView imgSkinToneFace;
    private TextView tvSkinToneMeta;
    private TextView tvItaAngle;
    private TextView tvSkinType;
    private CircularProgressIndicator progressOverall, progressTexture, progressPores, progressHydration;
    private TextView tvOverallScore, tvOverallDesc;
    private TextView tvTextureScore, tvTextureDesc;
    private TextView tvPoresScore, tvPoresDesc;
    private TextView tvHydrationScore, tvHydrationDesc;
    private FlexboxLayout flexConcerns;
    private TextView tvNoConcerns;

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
        if (analysisResult != null) populateData();
    }

    public void updateData(SkinAnalysisResult result, int texture, int pores, int hydration,
                           double itaDegrees) {
        this.analysisResult = result;
        this.textureScore = texture;
        this.poresScore = pores;
        this.hydrationScore = hydration;
        this.itaAngle = itaDegrees;
        if (getView() != null) populateData();
    }

    private void bindViews(View view) {
        cardConfidenceWarning = view.findViewById(R.id.cardConfidenceWarning);
        imgSkinToneFace       = view.findViewById(R.id.imgSkinToneFace);
        tvSkinToneMeta        = view.findViewById(R.id.tvSkinToneMeta);
        tvItaAngle            = view.findViewById(R.id.tvItaAngle);
        tvSkinType            = view.findViewById(R.id.tvSkinType);
        progressOverall       = view.findViewById(R.id.progressOverall);
        tvOverallScore        = view.findViewById(R.id.tvOverallScore);
        tvOverallDesc         = view.findViewById(R.id.tvOverallDesc);
        
        progressTexture       = view.findViewById(R.id.progressTexture);
        progressPores         = view.findViewById(R.id.progressPores);
        progressHydration     = view.findViewById(R.id.progressHydration);
        
        tvTextureScore        = view.findViewById(R.id.tvTextureScore);
        tvTextureDesc         = view.findViewById(R.id.tvTextureDesc);
        
        tvPoresScore          = view.findViewById(R.id.tvPoresScore);
        tvPoresDesc           = view.findViewById(R.id.tvPoresDesc);
        
        tvHydrationScore      = view.findViewById(R.id.tvHydrationScore);
        tvHydrationDesc       = view.findViewById(R.id.tvHydrationDesc);
        flexConcerns          = view.findViewById(R.id.flexConcerns);
        tvNoConcerns          = view.findViewById(R.id.tvNoConcerns);
    }

    private void populateData() {
        if (analysisResult == null) return;

        // Confidence warning (< 50%)
        boolean lowConfidence = analysisResult.getConfidence() > 0
                && analysisResult.getConfidence() < 50.0;
        cardConfidenceWarning.setVisibility(lowConfidence ? View.VISIBLE : View.GONE);

        // Skin tone face image / swatch fallback
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

        // Tone + Undertone meta
        String tone = safeStr(analysisResult.getSkinTone(), "Skin tone unavailable");
        String undertone = safeStr(analysisResult.getUndertone(), "Undertone unavailable");
        tvSkinToneMeta.setText(tone + " · " + undertone);

        // ITA angle
        if (!Double.isNaN(itaAngle)) {
            tvItaAngle.setText(String.format("ITA %.1f°", itaAngle));
            tvItaAngle.setVisibility(View.VISIBLE);
        } else {
            tvItaAngle.setVisibility(View.GONE);
        }

        // Skin type badge
        tvSkinType.setText(safeStr(analysisResult.getSkinType(), "—"));

        // Overall Score Calculation
        if (textureScore >= 0 && poresScore >= 0 && hydrationScore >= 0) {
            int overall = (textureScore + Math.max(0, 100 - poresScore) + hydrationScore) / 3;
            progressOverall.setProgressCompat(overall, true);
            tvOverallScore.setText(String.valueOf(overall));
            if (overall >= 90) tvOverallDesc.setText("Your skin is looking excellent!");
            else if (overall >= 70) tvOverallDesc.setText("Your skin is healthy and well-maintained.");
            else if (overall >= 50) tvOverallDesc.setText("Your skin could use a little more care.");
            else tvOverallDesc.setText("Your skin needs some attention.");
        } else {
            progressOverall.setProgressCompat(0, false);
            tvOverallScore.setText("-");
            tvOverallDesc.setText("Not enough data.");
        }

        // Circular scores inside indicators
        bindScore(progressTexture, tvTextureScore, textureScore);
        bindScore(progressPores, tvPoresScore, poresScore);
        bindScore(progressHydration, tvHydrationScore, hydrationScore);
        
        // Descriptions
        setDesc(tvTextureDesc, textureScore, "Smooth", "Fair", "Uneven", true);
        setDesc(tvPoresDesc, poresScore, "Large", "Visible", "Tight", false); // lower pores is better
        setDesc(tvHydrationDesc, hydrationScore, "Hydrated", "Normal", "Dry", true);

        // Concerns FlexboxLayout
        List<String> concerns = analysisResult.getConcerns();
        flexConcerns.removeAllViews();
        if (concerns != null && !concerns.isEmpty()) {
            tvNoConcerns.setVisibility(View.GONE);
            flexConcerns.setVisibility(View.VISIBLE);
            for (String concern : concerns) {
                TextView tag = new TextView(requireContext());
                tag.setText(concern);
                tag.setTextSize(12f);
                tag.setTextColor(Color.WHITE);
                tag.setBackgroundResource(R.drawable.bg_tag_primary);
                FlexboxLayout.LayoutParams lp = new FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        FlexboxLayout.LayoutParams.WRAP_CONTENT
                );
                int margin = (int) (6 * getResources().getDisplayMetrics().density);
                lp.setMargins(0, 0, margin, margin);
                tag.setLayoutParams(lp);
                int padH = (int) (10 * getResources().getDisplayMetrics().density);
                int padV = (int) (4 * getResources().getDisplayMetrics().density);
                tag.setPadding(padH, padV, padH, padV);
                flexConcerns.addView(tag);
            }
        } else {
            tvNoConcerns.setVisibility(View.VISIBLE);
            flexConcerns.setVisibility(View.GONE);
        }
    }

    private void bindScore(CircularProgressIndicator indicator, TextView label, int score) {
        if (score >= 0) {
            indicator.setProgressCompat(score, true);
            label.setText(String.valueOf(score));
        } else {
            indicator.setProgressCompat(0, false);
            label.setText("-");
        }
    }

    private void setDesc(TextView tv, int score, String high, String mid, String low, boolean higherIsBetter) {
        if (score < 0) {
            tv.setText("-");
            return;
        }
        boolean good = higherIsBetter ? (score >= 70) : (score <= 30);
        boolean bad = higherIsBetter ? (score <= 30) : (score >= 70);
        
        if (good) {
            tv.setText(higherIsBetter ? high : low);
        } else if (bad) {
            tv.setText(higherIsBetter ? low : high);
        } else {
            tv.setText(mid);
        }
    }

    private String safeStr(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
