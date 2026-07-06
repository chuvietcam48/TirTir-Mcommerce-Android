package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.BarcodeScanActivity;
import com.example.tirtir_mcommerce.ui.activities.VoucherWalletActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoyaltyFragment extends Fragment {

    private ImageButton btnBack;
    private ImageButton btnHowItWorks;
    private CardView cardLoyaltyBadge;
    private LinearLayout layoutCardBackground;
    private TextView tvLoyaltyTierLabel;
    private TextView tvLoyaltyPointsMain;
    private ProgressBar pbLoyaltyUpgrade;
    private TextView tvLoyaltyNextTierLabel;
    private MaterialButton btnRedeemVoucher;
    private MaterialButton btnScanBarcode;
    private RecyclerView rvPointHistory;

    private PointHistoryAdapter adapter;
    private List<Map<String, Object>> historyList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_loyalty, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setListeners();
        setupRecyclerView();
        loadLoyaltyData();
    }

    private void bindViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        btnHowItWorks = view.findViewById(R.id.btnHowItWorks);
        cardLoyaltyBadge = view.findViewById(R.id.cardLoyaltyBadge);
        layoutCardBackground = view.findViewById(R.id.layoutCardBackground);
        tvLoyaltyTierLabel = view.findViewById(R.id.tvLoyaltyTierLabel);
        tvLoyaltyPointsMain = view.findViewById(R.id.tvLoyaltyPointsMain);
        pbLoyaltyUpgrade = view.findViewById(R.id.pbLoyaltyUpgrade);
        tvLoyaltyNextTierLabel = view.findViewById(R.id.tvLoyaltyNextTierLabel);
        btnRedeemVoucher = view.findViewById(R.id.btnRedeemVoucher);
        btnScanBarcode = view.findViewById(R.id.btnScanBarcode);
        rvPointHistory = view.findViewById(R.id.rvPointHistory);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().finish();
            }
        });

        if (btnHowItWorks != null) {
            btnHowItWorks.setOnClickListener(v -> showHowItWorksDialog());
        }

        btnScanBarcode.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(requireContext(), BarcodeScanActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Barcode scan not available", Toast.LENGTH_SHORT).show();
            }
        });

        btnRedeemVoucher.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(requireContext(), VoucherWalletActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                showRedeemOptionsDialog();
            }
        });
    }

    private void showHowItWorksDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("How It Works")
                .setMessage(
                    "EARN POINTS\n" +
                    "• Scan purchase barcode: +10 pts\n" +
                    "• Every $1 spent: +1 pt\n" +
                    "• Birthday bonus: +50 pts\n\n" +
                    "REDEEM VOUCHERS\n" +
                    "• 100 pts → 5% discount\n" +
                    "• 200 pts → 10% discount\n" +
                    "• 500 pts → 25% discount\n\n" +
                    "MEMBERSHIP TIERS\n" +
                    "🥉 Bronze  0–99 pts\n" +
                    "🥈 Silver  100–299 pts — 1.5× pts\n" +
                    "🥇 Gold   300–599 pts — 2× pts\n" +
                    "💎 Platinum  600+ pts — 3× pts"
                )
                .setPositiveButton("Got it", null)
                .show();
    }

    private void setupRecyclerView() {
        adapter = new PointHistoryAdapter(historyList);
        rvPointHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvPointHistory.setAdapter(adapter);
    }

    private void loadLoyaltyData() {
        ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        api.getLoyaltyDetails().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded() || response.body() == null || response.body().getData() == null) return;
                Map<String, Object> data = response.body().getData();
                updateUI(data);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Unable to load loyalty data", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateUI(Map<String, Object> data) {
        int points = asInt(data.get("loyaltyPoints"));
        int toNext = asInt(data.get("pointsToNextTier"));
        String tier = String.valueOf(data.getOrDefault("loyaltyTier", "Bronze"));
        String nextTier = String.valueOf(data.getOrDefault("nextTier", "Silver"));

        tvLoyaltyPointsMain.setText(String.valueOf(points));
        tvLoyaltyTierLabel.setText(tier.toUpperCase());

        // Update Tier Colors
        updateCardBackgroundColor(tier);

        // Progress Bar
        int max = Math.max(points + toNext, 1);
        pbLoyaltyUpgrade.setMax(max);
        pbLoyaltyUpgrade.setProgress(points);

        if (toNext > 0) {
            tvLoyaltyNextTierLabel.setText(toNext + " more points to reach " + nextTier);
        } else {
            tvLoyaltyNextTierLabel.setText("You've reached the highest tier!");
        }

        // Populate history
        Object historyObj = data.get("history");
        if (historyObj instanceof List) {
            historyList.clear();
            for (Object obj : (List<?>) historyObj) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) obj;
                    historyList.add(item);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void updateCardBackgroundColor(String tier) {
        String colorHex = "#CD7F32"; // Fallback Bronze
        if (tier != null) {
            switch (tier.toLowerCase()) {
                case "silver":
                    colorHex = "#C0C0C0";
                    break;
                case "gold":
                    colorHex = "#FFD700";
                    break;
                case "platinum":
                    colorHex = "#8A2BE2";
                    break;
                case "bronze":
                default:
                    colorHex = "#CD7F32";
                    break;
            }
        }
        try {
            layoutCardBackground.setBackgroundColor(Color.parseColor(colorHex));
        } catch (Exception e) {
            layoutCardBackground.setBackgroundColor(Color.parseColor("#CD7F32"));
        }
    }

    private void showRedeemOptionsDialog() {
        String[] options = {
                "100 pts → 5% Discount Voucher",
                "200 pts → 10% Discount Voucher",
                "500 pts → 25% Discount Voucher"
        };
        int[] pointValues = {100, 200, 500};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Redeem Points for Voucher")
                .setItems(options, (dialog, which) -> {
                    int pts = pointValues[which];
                    confirmRedemption(pts);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRedemption(int pts) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Redemption")
                .setMessage("Use " + pts + " points to redeem a voucher?")
                .setPositiveButton("Redeem", (dialog, which) -> performRedemption(pts))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performRedemption(int pts) {
        ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        Map<String, Integer> body = new HashMap<>();
        body.put("ptsRequired", pts);

        api.redeemPoints(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Map<String, Object> respData = response.body().getData();
                    String code = String.valueOf(respData.getOrDefault("voucherCode", ""));
                    showRedeemSuccessDialog(code);
                    loadLoyaltyData();
                } else {
                    String errorMsg = "Redemption failed";
                    if (response.code() == 400) {
                        errorMsg = "Not enough points to redeem";
                    }
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Unable to connect to server", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showRedeemSuccessDialog(String code) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Redemption Successful!")
                .setMessage("Your voucher code:\n\n" + code + "\n\nValid for 30 days.")
                .setPositiveButton("Done", null)
                .show();
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    // ===========================
    // RECYCLERVIEW ADAPTER
    // ===========================

    private static class PointHistoryAdapter extends RecyclerView.Adapter<PointHistoryAdapter.HistoryViewHolder> {

        private final List<Map<String, Object>> list;

        PointHistoryAdapter(List<Map<String, Object>> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_point_history, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            Map<String, Object> item = list.get(position);
            String source = String.valueOf(item.getOrDefault("source", ""));
            int finalPoints = 0;
            if (item.containsKey("finalPoints")) {
                Object fp = item.get("finalPoints");
                if (fp instanceof Number) {
                    finalPoints = ((Number) fp).intValue();
                }
            } else if (item.containsKey("pointsDeducted")) {
                // REDEEM_VOUCHER stores pointsDeducted
                Object pd = item.get("pointsDeducted");
                if (pd instanceof Number) {
                    finalPoints = -((Number) pd).intValue();
                }
            }

            // Description
            if ("SCAN_BARCODE".equals(source)) {
                holder.tvPointDescription.setText("Barcode scan");
                holder.ivPointIcon.setImageResource(android.R.drawable.ic_input_add);
            } else if ("REDEEM_VOUCHER".equals(source)) {
                holder.tvPointDescription.setText("Voucher redemption");
                holder.ivPointIcon.setImageResource(android.R.drawable.ic_menu_send);
            } else {
                holder.tvPointDescription.setText("Loyalty transaction");
                holder.ivPointIcon.setImageResource(android.R.drawable.ic_dialog_info);
            }

            // Points
            if (finalPoints > 0) {
                holder.tvPointValue.setText("+" + finalPoints);
                holder.tvPointValue.setTextColor(Color.parseColor("#2E7D32")); // Success color
            } else {
                holder.tvPointValue.setText(String.valueOf(finalPoints));
                holder.tvPointValue.setTextColor(Color.parseColor("#D32F2F")); // Error color
            }

            // Date
            String createdAt = String.valueOf(item.getOrDefault("createdAt", ""));
            holder.tvPointDate.setText(formatIsoDate(createdAt));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private String formatIsoDate(String isoDate) {
            if (isoDate == null || isoDate.length() < 16) return String.valueOf(isoDate);
            try {
                // "2026-06-19T08:15:30.000Z" -> "19/06/2026 08:15"
                String yyyy = isoDate.substring(0, 4);
                String mm = isoDate.substring(5, 7);
                String dd = isoDate.substring(8, 10);
                String time = isoDate.substring(11, 16);
                return dd + "/" + mm + "/" + yyyy + " " + time;
            } catch (Exception e) {
                return isoDate;
            }
        }

        static class HistoryViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPointIcon;
            TextView tvPointDescription;
            TextView tvPointDate;
            TextView tvPointValue;

            HistoryViewHolder(View itemView) {
                super(itemView);
                ivPointIcon = itemView.findViewById(R.id.ivPointIcon);
                tvPointDescription = itemView.findViewById(R.id.tvPointDescription);
                tvPointDate = itemView.findViewById(R.id.tvPointDate);
                tvPointValue = itemView.findViewById(R.id.tvPointValue);
            }
        }
    }
}
