package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Campaign;
import com.example.tirtir_mcommerce.model.MarketingActivity;
import com.example.tirtir_mcommerce.model.MarketingOverviewResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.AdminCartRecoveryActivity;
import com.example.tirtir_mcommerce.ui.activities.AdminChurnActivity;
import com.example.tirtir_mcommerce.ui.activities.AdminFlashSaleActivity;
import com.example.tirtir_mcommerce.ui.activities.VoucherWalletActivity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminMarketingFragment extends Fragment {

    private TextView tvRevenueRecovered, tvAtRiskCustomers, tvVouchersUsed, tvConversionRate;
    private RecyclerView rvCampaigns, rvMarketingFeeds;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_marketing, container, false);

        tvRevenueRecovered = view.findViewById(R.id.tvRevenueRecovered);
        tvAtRiskCustomers = view.findViewById(R.id.tvAtRiskCustomers);
        tvVouchersUsed = view.findViewById(R.id.tvVouchersUsed);
        tvConversionRate = view.findViewById(R.id.tvConversionRate);

        rvCampaigns = view.findViewById(R.id.rvCampaigns);
        rvCampaigns.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        rvMarketingFeeds = view.findViewById(R.id.rvMarketingFeeds);
        rvMarketingFeeds.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));

        // Tools Setup
        view.findViewById(R.id.btnToolFlashSale).setOnClickListener(v -> 
            startActivity(new Intent(getContext(), AdminFlashSaleActivity.class)));

        view.findViewById(R.id.btnToolRecovery).setOnClickListener(v -> 
            startActivity(new Intent(getContext(), AdminCartRecoveryActivity.class)));
        
        view.findViewById(R.id.btnToolRetention).setOnClickListener(v -> 
            startActivity(new Intent(getContext(), AdminChurnActivity.class)));
        
        view.findViewById(R.id.btnToolVouchers).setOnClickListener(v -> {
            // Usually we'd go to an AdminVouchersActivity, but fallback to VoucherWalletActivity
            startActivity(new Intent(getContext(), VoucherWalletActivity.class));
        });

        loadMarketingData();

        return view;
    }

    private void loadMarketingData() {
        ApiService apiService = RetrofitClient.getAuthClient(getContext()).create(ApiService.class);
        apiService.getMarketingOverview().enqueue(new Callback<ApiResponse<MarketingOverviewResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<MarketingOverviewResponse>> call, Response<ApiResponse<MarketingOverviewResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    MarketingOverviewResponse data = response.body().getData();
                    updateUI(data);
                } else {
                    Toast.makeText(getContext(), "Failed to load marketing data", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<MarketingOverviewResponse>> call, Throwable t) {
                Log.e("Marketing", "Error: " + t.getMessage());
                Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(MarketingOverviewResponse data) {
        if (data.getInsights() != null) {
            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            tvRevenueRecovered.setText(format.format(data.getInsights().getRevenueRecovered()));
            tvAtRiskCustomers.setText(data.getInsights().getAtRiskUsers() + " users");
            tvVouchersUsed.setText(data.getInsights().getVouchersUsed() + " codes");
            tvConversionRate.setText(data.getInsights().getConversionRate() + "%");
        }

        if (data.getCampaigns() != null) {
            rvCampaigns.setAdapter(new CampaignAdapter(data.getCampaigns()));
        }

        if (data.getActivities() != null) {
            rvMarketingFeeds.setAdapter(new MarketingFeedAdapter(data.getActivities()));
        }
    }

    // ==========================================
    // Adapters
    // ==========================================

    private class CampaignAdapter extends RecyclerView.Adapter<CampaignAdapter.ViewHolder> {
        private List<Campaign> campaigns;

        public CampaignAdapter(List<Campaign> campaigns) {
            this.campaigns = campaigns;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_campaign, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Campaign c = campaigns.get(position);
            holder.tvCampaignTitle.setText(c.getTitle());
            holder.tvStatus.setText(c.getStatus());

            int progress = 0;
            if (c.getTargetRevenue() > 0) {
                progress = (int) ((c.getCurrentRevenue() / c.getTargetRevenue()) * 100);
            }
            holder.pbCampaign.setProgress(progress);
            
            String progressText = "Progress: " + (c.getCurrentRevenue() / 1000000) + "M / " + (c.getTargetRevenue() / 1000000) + "M";
            holder.tvProgressText.setText(progressText);
            holder.tvProgressPercent.setText(progress + "%");
        }

        @Override
        public int getItemCount() {
            return campaigns.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCampaignTitle, tvStatus, tvProgressText, tvProgressPercent;
            ProgressBar pbCampaign;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCampaignTitle = itemView.findViewById(R.id.tvCampaignTitle);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvProgressText = itemView.findViewById(R.id.tvProgressText);
                tvProgressPercent = itemView.findViewById(R.id.tvProgressPercent);
                pbCampaign = itemView.findViewById(R.id.pbCampaign);
            }
        }
    }

    private class MarketingFeedAdapter extends RecyclerView.Adapter<MarketingFeedAdapter.ViewHolder> {
        private List<MarketingActivity> activities;

        public MarketingFeedAdapter(List<MarketingActivity> activities) {
            this.activities = activities;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_marketing_feed, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MarketingActivity a = activities.get(position);
            holder.tvFeedTitle.setText(a.getTitle());
            holder.tvFeedTarget.setText(a.getTargetOrStatus());

            GradientDrawable dot = (GradientDrawable) holder.vStatusDot.getBackground();
            
            if ("success".equalsIgnoreCase(a.getType())) {
                holder.tvFeedStatus.setText("Success");
                holder.tvFeedStatus.setTextColor(Color.parseColor("#4CAF50"));
                dot.setColor(Color.parseColor("#4CAF50"));
            } else if ("system".equalsIgnoreCase(a.getType())) {
                holder.tvFeedStatus.setText("System");
                holder.tvFeedStatus.setTextColor(Color.parseColor("#5f5e5e"));
                dot.setColor(Color.parseColor("#8b0000")); // Primary container
            } else {
                holder.tvFeedStatus.setText("Draft");
                holder.tvFeedStatus.setTextColor(Color.parseColor("#5f5e5e"));
                dot.setColor(Color.parseColor("#5f5e5e"));
            }
        }

        @Override
        public int getItemCount() {
            return activities.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFeedTitle, tvFeedTarget, tvFeedStatus;
            View vStatusDot;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFeedTitle = itemView.findViewById(R.id.tvFeedTitle);
                tvFeedTarget = itemView.findViewById(R.id.tvFeedTarget);
                tvFeedStatus = itemView.findViewById(R.id.tvFeedStatus);
                vStatusDot = itemView.findViewById(R.id.vStatusDot);
            }
        }
    }
}
