package com.example.tirtir_mcommerce.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Campaign;

import java.util.List;

public class AdminCampaignAdapter extends RecyclerView.Adapter<AdminCampaignAdapter.ViewHolder> {
    private List<Campaign> campaigns;

    public AdminCampaignAdapter(List<Campaign> campaigns) {
        this.campaigns = campaigns;
    }

    public void setCampaigns(List<Campaign> campaigns) {
        this.campaigns = campaigns;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_campaign, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Campaign campaign = campaigns.get(position);
        holder.tvCampaignTitle.setText(campaign.getTitle());
        holder.tvStatus.setText(campaign.getStatus());
        
        if (campaign.getEndDate() != null && campaign.getEndDate().length() >= 10) {
            holder.tvCampaignEndDate.setText("Ends: " + campaign.getEndDate().substring(0, 10));
        } else {
            holder.tvCampaignEndDate.setText("Ends: N/A");
        }

        double current = campaign.getCurrentRevenue();
        double target = campaign.getTargetRevenue() > 0 ? campaign.getTargetRevenue() : 1; // avoid / 0
        int progress = (int) ((current / target) * 100);

        holder.tvProgressText.setText(String.format("Progress: $%.0f / $%.0f", current, target));
        holder.tvProgressPercent.setText(Math.min(progress, 100) + "%");
        holder.pbCampaign.setProgress(Math.min(progress, 100));
    }

    @Override
    public int getItemCount() {
        return campaigns != null ? campaigns.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCampaignTitle, tvCampaignEndDate, tvStatus, tvProgressText, tvProgressPercent;
        ProgressBar pbCampaign;

        ViewHolder(View itemView) {
            super(itemView);
            tvCampaignTitle = itemView.findViewById(R.id.tvCampaignTitle);
            tvCampaignEndDate = itemView.findViewById(R.id.tvCampaignEndDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProgressText = itemView.findViewById(R.id.tvProgressText);
            tvProgressPercent = itemView.findViewById(R.id.tvProgressPercent);
            pbCampaign = itemView.findViewById(R.id.pbCampaign);
        }
    }
}
