package com.example.tirtir_mcommerce.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.AdminVoucher;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminVoucherAdapter extends RecyclerView.Adapter<AdminVoucherAdapter.VoucherViewHolder> {

    private List<AdminVoucher> vouchers;

    public AdminVoucherAdapter(List<AdminVoucher> vouchers) {
        this.vouchers = vouchers;
    }

    public void setVouchers(List<AdminVoucher> vouchers) {
        this.vouchers = vouchers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_voucher, parent, false);
        return new VoucherViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoucherViewHolder holder, int position) {
        AdminVoucher voucher = vouchers.get(position);
        
        holder.tvVoucherCode.setText(voucher.getCode());
        
        // Format Discount
        if ("fixed".equals(voucher.getDiscountType())) {
            holder.tvDiscountValue.setText("$" + (int) voucher.getDiscountValue());
            holder.tvDiscountLabel.setText("FIXED");
        } else if ("free_ship".equals(voucher.getDiscountType())) {
            holder.tvDiscountValue.setText("FREE");
            holder.tvDiscountLabel.setText("SHIP");
        } else {
            holder.tvDiscountValue.setText((int) voucher.getDiscountValue() + "%");
            holder.tvDiscountLabel.setText("OFF");
        }
        
        // Format Date
        holder.tvExpiry.setText("Expiry: " + formatDate(voucher.getValidTo()));
        
        // Progress Bar
        int max = voucher.getUsageLimit() > 0 ? voucher.getUsageLimit() : 100;
        int current = voucher.getUsedCount();
        holder.pbUsage.setMax(max);
        holder.pbUsage.setProgress(current);
        holder.tvUsageText.setText(current + "/" + max + " used");
        
        holder.itemView.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.tirtir_mcommerce.ui.activities.AdminVoucherDetailActivity.class);
            intent.putExtra(com.example.tirtir_mcommerce.ui.activities.AdminVoucherDetailActivity.EXTRA_VOUCHER_ID, voucher.getId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return vouchers != null ? vouchers.size() : 0;
    }
    
    private String formatDate(String isoDate) {
        if (isoDate == null) return "Ongoing";
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            Date date = inFormat.parse(isoDate);
            SimpleDateFormat outFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            return outFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return isoDate;
        }
    }

    static class VoucherViewHolder extends RecyclerView.ViewHolder {
        TextView tvVoucherCode, tvExpiry, tvDiscountValue, tvDiscountLabel, tvUsageText;
        ProgressBar pbUsage;

        public VoucherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVoucherCode = itemView.findViewById(R.id.tvVoucherCode);
            tvExpiry = itemView.findViewById(R.id.tvExpiry);
            tvDiscountValue = itemView.findViewById(R.id.tvDiscountValue);
            tvDiscountLabel = itemView.findViewById(R.id.tvDiscountLabel);
            tvUsageText = itemView.findViewById(R.id.tvUsageText);
            pbUsage = itemView.findViewById(R.id.pbUsage);
        }
    }
}
