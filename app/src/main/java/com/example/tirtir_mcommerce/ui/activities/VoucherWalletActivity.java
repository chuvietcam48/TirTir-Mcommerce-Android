package com.example.tirtir_mcommerce.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VoucherWalletActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView tvUserPoints;
    private RecyclerView rvRedeemLevels;
    private RecyclerView rvVouchers;
    private TextView tvNoVouchers;

    private int userPoints = 0;
    private List<Map<String, Object>> vouchersList = new ArrayList<>();
    private List<RedeemLevel> redeemLevels = new ArrayList<>();

    private VoucherAdapter voucherAdapter;
    private RedeemLevelAdapter redeemAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voucher_wallet);

        bindViews();
        setListeners();
        setupRedeemLevels();
        setupRecyclerViews();
        loadData();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        tvUserPoints = findViewById(R.id.tvUserPoints);
        rvRedeemLevels = findViewById(R.id.rvRedeemLevels);
        rvVouchers = findViewById(R.id.rvVouchers);
        tvNoVouchers = findViewById(R.id.tvNoVouchers);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRedeemLevels() {
        redeemLevels.add(new RedeemLevel(100, 5, "Voucher giảm giá 5%"));
        redeemLevels.add(new RedeemLevel(200, 10, "Voucher giảm giá 10%"));
        redeemLevels.add(new RedeemLevel(500, 25, "Voucher giảm giá 25%"));
    }

    private void setupRecyclerViews() {
        // Redeem levels list
        redeemAdapter = new RedeemLevelAdapter(redeemLevels, this::onRedeemLevelClicked);
        rvRedeemLevels.setLayoutManager(new LinearLayoutManager(this));
        rvRedeemLevels.setAdapter(redeemAdapter);

        // Vouchers list
        voucherAdapter = new VoucherAdapter(vouchersList, this::onCopyClicked);
        rvVouchers.setLayoutManager(new LinearLayoutManager(this));
        rvVouchers.setAdapter(voucherAdapter);
    }

    private void loadData() {
        ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        
        // 1. Load User Points
        api.getLoyaltyDetails().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    userPoints = asInt(response.body().getData().get("loyaltyPoints"));
                    tvUserPoints.setText(userPoints + " điểm");
                    redeemAdapter.updateUserPoints(userPoints);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                // Silent fallback or logs
            }
        });

        // 2. Load Vouchers Wallet
        api.getWallet().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call, Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    vouchersList.clear();
                    vouchersList.addAll(response.body().getData());
                    voucherAdapter.notifyDataSetChanged();
                    
                    if (vouchersList.isEmpty()) {
                        tvNoVouchers.setVisibility(View.VISIBLE);
                        rvVouchers.setVisibility(View.GONE);
                    } else {
                        tvNoVouchers.setVisibility(View.GONE);
                        rvVouchers.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (!isFinishing()) {
                    Toast.makeText(VoucherWalletActivity.this, "Không thể kết nối ví voucher", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void onRedeemLevelClicked(RedeemLevel level) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xác Nhận Đổi Điểm")
                .setMessage("Bạn muốn đổi " + level.pointsRequired + " điểm để nhận voucher?")
                .setPositiveButton("Đổi", (dialog, which) -> {
                    performRedemption(level.pointsRequired);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performRedemption(int pts) {
        ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        Map<String, Integer> body = new HashMap<>();
        body.put("ptsRequired", pts);

        api.redeemPoints(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (isFinishing()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    String code = String.valueOf(response.body().getData().getOrDefault("voucherCode", ""));
                    new MaterialAlertDialogBuilder(VoucherWalletActivity.this)
                            .setTitle("Đổi Thành Công!")
                            .setMessage("Mã voucher mới của bạn:\n\n" + code)
                            .setPositiveButton("Đóng", (d, w) -> loadData())
                            .show();
                } else {
                    Toast.makeText(VoucherWalletActivity.this, "Đổi điểm thất bại", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isFinishing()) {
                    Toast.makeText(VoucherWalletActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void onCopyClicked(String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Voucher Code", code);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã sao chép mã voucher vào bộ nhớ tạm", Toast.LENGTH_SHORT).show();
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    // ===========================
    // REDEEM LEVEL MODEL
    // ===========================

    private static class RedeemLevel {
        final int pointsRequired;
        final int discountPercent;
        final String label;

        RedeemLevel(int pointsRequired, int discountPercent, String label) {
            this.pointsRequired = pointsRequired;
            this.discountPercent = discountPercent;
            this.label = label;
        }
    }

    // ===========================
    // REDEEM LEVEL ADAPTER
    // ===========================

    private interface OnRedeemClickListener {
        void onRedeemClicked(RedeemLevel level);
    }

    private static class RedeemLevelAdapter extends RecyclerView.Adapter<RedeemLevelAdapter.RedeemViewHolder> {

        private final List<RedeemLevel> list;
        private final OnRedeemClickListener listener;
        private int userPoints = 0;

        RedeemLevelAdapter(List<RedeemLevel> list, OnRedeemClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        void updateUserPoints(int points) {
            this.userPoints = points;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RedeemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_redeem_level, parent, false);
            return new RedeemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RedeemViewHolder holder, int position) {
            RedeemLevel item = list.get(position);
            holder.tvRedeemReward.setText(item.label);
            holder.tvRedeemPointsRequired.setText("Yêu cầu: " + item.pointsRequired + " điểm");

            // Button is always enabled and visible
            holder.btnRedeem.setEnabled(true);
            holder.btnRedeem.setAlpha(1.0f);

            holder.btnRedeem.setOnClickListener(v -> {
                if (userPoints < item.pointsRequired) {
                    Toast.makeText(v.getContext(), "Bạn không đủ điểm để nhận voucher này.", Toast.LENGTH_SHORT).show();
                } else {
                    if (listener != null) {
                        listener.onRedeemClicked(item);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class RedeemViewHolder extends RecyclerView.ViewHolder {
            TextView tvRedeemReward;
            TextView tvRedeemPointsRequired;
            MaterialButton btnRedeem;

            RedeemViewHolder(View itemView) {
                super(itemView);
                tvRedeemReward = itemView.findViewById(R.id.tvRedeemReward);
                tvRedeemPointsRequired = itemView.findViewById(R.id.tvRedeemPointsRequired);
                btnRedeem = itemView.findViewById(R.id.btnRedeem);
            }
        }
    }

    // ===========================
    // VOUCHER ADAPTER
    // ===========================

    private interface OnCopyClickListener {
        void onCopyClicked(String code);
    }

    private static class VoucherAdapter extends RecyclerView.Adapter<VoucherAdapter.VoucherViewHolder> {

        private final List<Map<String, Object>> list;
        private final OnCopyClickListener listener;

        VoucherAdapter(List<Map<String, Object>> list, OnCopyClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voucher, parent, false);
            return new VoucherViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VoucherViewHolder holder, int position) {
            Map<String, Object> item = list.get(position);
            String code = String.valueOf(item.getOrDefault("code", ""));
            int discountPercent = 0;
            if (item.containsKey("discountPct")) {
                Object dp = item.get("discountPct");
                if (dp instanceof Number) {
                    discountPercent = ((Number) dp).intValue();
                }
            }
            String validTo = String.valueOf(item.getOrDefault("validTo", ""));

            holder.tvVoucherCode.setText(code);
            holder.tvVoucherDiscountPercent.setText(discountPercent + "%");
            holder.tvVoucherExpiry.setText("Hạn dùng: " + formatExpiry(validTo));

            holder.btnCopyVoucher.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCopyClicked(code);
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private String formatExpiry(String rawExpiry) {
            if (rawExpiry == null || rawExpiry.length() < 10) return String.valueOf(rawExpiry);
            try {
                // "2026-06-19T..." -> "19/06/2026"
                String yyyy = rawExpiry.substring(0, 4);
                String mm = rawExpiry.substring(5, 7);
                String dd = rawExpiry.substring(8, 10);
                return dd + "/" + mm + "/" + yyyy;
            } catch (Exception e) {
                return rawExpiry;
            }
        }

        static class VoucherViewHolder extends RecyclerView.ViewHolder {
            TextView tvVoucherDiscountPercent;
            TextView tvVoucherCode;
            TextView tvVoucherExpiry;
            MaterialButton btnCopyVoucher;

            VoucherViewHolder(View itemView) {
                super(itemView);
                tvVoucherDiscountPercent = itemView.findViewById(R.id.tvVoucherDiscountPercent);
                tvVoucherCode = itemView.findViewById(R.id.tvVoucherCode);
                tvVoucherExpiry = itemView.findViewById(R.id.tvVoucherExpiry);
                btnCopyVoucher = itemView.findViewById(R.id.btnCopyVoucher);
            }
        }
    }
}
