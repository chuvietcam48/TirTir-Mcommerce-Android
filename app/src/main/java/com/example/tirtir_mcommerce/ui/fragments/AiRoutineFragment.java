package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.RoutineStep;
import com.example.tirtir_mcommerce.ui.adapters.RoutineStepAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 3: AI Routine.
 * Hiển thị danh sách các bước skincare AI đề xuất từ API /api/ai/recommend-routine.
 *
 * Tính năng Skin Evolution:
 * - Tổng hydration boost = tổng hydrationBoost của tất cả steps không bị skip
 * - Tổng texture boost = tổng textureBoost của tất cả steps không bị skip
 * - Khi user skip 1 bước → hydration -3%, texture -2% (theo spec)
 * - Hiển thị forecast ở panel đầu trang
 */
public class AiRoutineFragment extends Fragment {

    private List<RoutineStep> routineSteps;

    // UI
    private TextView tvHydrationForecast;
    private TextView tvTextureForecast;
    private RecyclerView rvRoutineSteps;
    private TextView tvRoutineEmpty;
    private RoutineStepAdapter adapter;

    // Base scores tính từ tất cả steps (không skip)
    private int baseHydration = 0;
    private int baseTexture = 0;

    public static AiRoutineFragment newInstance() {
        return new AiRoutineFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_routine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvHydrationForecast = view.findViewById(R.id.tvHydrationForecast);
        tvTextureForecast   = view.findViewById(R.id.tvTextureForecast);
        rvRoutineSteps      = view.findViewById(R.id.rvRoutineSteps);
        tvRoutineEmpty      = view.findViewById(R.id.tvRoutineEmpty);

        adapter = new RoutineStepAdapter(
                this::onStepSkipToggled,
                this::onAddToCart
        );
        rvRoutineSteps.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRoutineSteps.setAdapter(adapter);

        if (routineSteps != null) populateData();
    }

    /**
     * Được gọi từ SkinResultActivity khi API 3 trả về thành công.
     */
    public void updateData(List<RoutineStep> steps) {
        this.routineSteps = steps;
        computeBaseScores();
        if (getView() != null) populateData();
    }

    private void computeBaseScores() {
        baseHydration = 0;
        baseTexture = 0;
        if (routineSteps == null) return;
        for (RoutineStep step : routineSteps) {
            baseHydration += step.getHydrationBoost();
            baseTexture   += step.getTextureBoost();
        }
    }

    private void populateData() {
        if (routineSteps == null || routineSteps.isEmpty()) {
            tvRoutineEmpty.setVisibility(View.VISIBLE);
            rvRoutineSteps.setVisibility(View.GONE);
            return;
        }

        tvRoutineEmpty.setVisibility(View.GONE);
        rvRoutineSteps.setVisibility(View.VISIBLE);
        adapter.submitList(new ArrayList<>(routineSteps));
        updateEvolutionForecast();
    }

    /**
     * Cập nhật Skin Evolution khi user toggle skip.
     * Theo spec: mỗi bước skip → Hydration -3%, Texture -2%
     */
    private void onStepSkipToggled(RoutineStep step, boolean skipped) {
        updateEvolutionForecast();
    }

    private void updateEvolutionForecast() {
        if (routineSteps == null || tvHydrationForecast == null) return;

        int skippedCount = 0;
        for (RoutineStep step : routineSteps) {
            if (step.isSkipped()) skippedCount++;
        }

        // Theo spec: mỗi bước skip → Hydration -3%, Texture -2%
        int hydrationPenalty = skippedCount * 3;
        int texturePenalty   = skippedCount * 2;

        int finalHydration = Math.max(0, baseHydration - hydrationPenalty);
        int finalTexture   = Math.max(0, baseTexture - texturePenalty);

        tvHydrationForecast.setText("+" + finalHydration + "%");
        tvTextureForecast.setText("+" + finalTexture + "%");
    }

    /**
     * Thêm sản phẩm vào giỏ hàng local (offline cart).
     * SkinResultActivity sẽ handle server sync nếu user đã đăng nhập.
     */
    private void onAddToCart(RoutineStep step) {
        if (step.getProductId() == null || step.getProductId().isEmpty()) {
            Toast.makeText(requireContext(),
                    "This product is temporarily unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        CartItem item = new CartItem(
                step.getProductId(),
                step.getProductName(),
                step.getImageUrl(),
                step.getDisplayPrice(), 1, null
        );
        DatabaseHelper.getInstance(requireContext()).insertOrUpdateCartItem(item);
        Toast.makeText(requireContext(), step.getStepName() + " product added to cart!",
                Toast.LENGTH_SHORT).show();
    }
}
