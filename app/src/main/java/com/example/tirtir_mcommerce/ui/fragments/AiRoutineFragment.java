package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.EditText;
import android.app.AlertDialog;
import java.util.Calendar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.RoutineStep;
import com.example.tirtir_mcommerce.network.ApiConfig;
import com.example.tirtir_mcommerce.repository.CartRepository;
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
    private View btnAddAllToCart;
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

        View layoutActions = view.findViewById(R.id.layoutRoutineActions);
        View btnReminder = view.findViewById(R.id.btnSetReminder);
        View btnCommunity = view.findViewById(R.id.btnApplyCommunity);
        btnAddAllToCart = view.findViewById(R.id.btnAddAllToCart);

        btnReminder.setOnClickListener(v -> setReminder());
        btnCommunity.setOnClickListener(v -> showCommunityDialog());
        btnAddAllToCart.setOnClickListener(v -> addAllToCart());

        adapter = new RoutineStepAdapter(
                this::onStepSkipToggled,
                this::onAddToCart,
                this::onProductClicked
        );
        rvRoutineSteps.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRoutineSteps.setAdapter(adapter);

        if (routineSteps != null) populateData();

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                adapter.swapItems(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Do nothing
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                routineSteps = adapter.getReorderedList();
                DatabaseHelper.getInstance(requireContext()).updateRoutineSteps(routineSteps);
            }
        });
        itemTouchHelper.attachToRecyclerView(rvRoutineSteps);
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
        if (getView() != null) {
            getView().findViewById(R.id.layoutRoutineActions).setVisibility(View.VISIBLE);
            btnAddAllToCart.setVisibility(View.VISIBLE);
        }
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

        String imgUrl = step.getImageUrl() != null ? ApiConfig.resolveMediaUrl(step.getImageUrl()) : "";
        CartItem item = new CartItem(
                step.getProductId(),
                step.getProductName(),
                imgUrl,
                step.getDisplayPrice(), 1, null
        );
        // Fix: Use CartRepository to both save locally AND sync to server/Firebase
        CartRepository repository = new CartRepository(requireContext());
        repository.addToCartLocal(item);
        repository.syncItemToServer(item, null, error -> {});
        Toast.makeText(requireContext(), step.getStepName() + " product added to cart!",
                Toast.LENGTH_SHORT).show();
    }

    private void onProductClicked(RoutineStep step) {
        if (step.getProductId() == null || step.getProductId().isEmpty()) {
            Toast.makeText(requireContext(), "Product details unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", step.getProductId());
        startActivity(intent);
    }

    private void addAllToCart() {
        if (routineSteps == null || routineSteps.isEmpty()) return;
        
        CartRepository repository = new CartRepository(requireContext());
        int addedCount = 0;
        
        for (RoutineStep step : routineSteps) {
            if (!step.isSkipped() && step.getProductId() != null && !step.getProductId().isEmpty()) {
                String imgUrl = step.getImageUrl() != null ? ApiConfig.resolveMediaUrl(step.getImageUrl()) : "";
                CartItem item = new CartItem(
                        step.getProductId(),
                        step.getProductName(),
                        imgUrl,
                        step.getDisplayPrice(), 1, null
                );
                repository.addToCartLocal(item);
                repository.syncItemToServer(item, null, error -> {});
                addedCount++;
            }
        }
        
        if (addedCount > 0) {
            Toast.makeText(requireContext(), "Added " + addedCount + " products to cart!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "No products to add (all skipped or unavailable)", Toast.LENGTH_SHORT).show();
        }
    }

    private void setReminder() {
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), com.example.tirtir_mcommerce.receivers.RoutineReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 0);
        
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
        Toast.makeText(requireContext(), "Daily reminder set for 8:00 PM", Toast.LENGTH_SHORT).show();
    }

    private void showCommunityDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Enter Community Routine ID");

        new AlertDialog.Builder(requireContext())
                .setTitle("Apply Community Routine")
                .setView(input)
                .setPositiveButton("Apply", (dialog, which) -> {
                    String id = input.getText().toString().trim();
                    if (!id.isEmpty()) applyCommunityRoutine(id);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyCommunityRoutine(String id) {
        ApiService apiService = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        apiService.getCommunityRoutine(id).enqueue(new Callback<ApiResponse<List<RoutineStep>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RoutineStep>>> call, Response<ApiResponse<List<RoutineStep>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    updateData(response.body().getData());
                    DatabaseHelper.getInstance(requireContext()).updateRoutineSteps(routineSteps);
                    Toast.makeText(requireContext(), "Community routine applied!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Routine not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<RoutineStep>>> call, Throwable t) {
                Toast.makeText(requireContext(), "Failed to fetch routine", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
