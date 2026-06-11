package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;

import java.util.List;

public class CartRecoveryAdapter extends RecyclerView.Adapter<CartRecoveryAdapter.ViewHolder> {

    public static class CartRecoveryItem {
        public String name;
        public String email;
        public String status;
        public String summary;
        public double value;
        public String time;

        public CartRecoveryItem(String name, String email, String status, String summary, double value, String time) {
            this.name = name;
            this.email = email;
            this.status = status;
            this.summary = summary;
            this.value = value;
            this.time = time;
        }
    }

    private final Context context;
    private final List<CartRecoveryItem> items;

    public CartRecoveryAdapter(Context context, List<CartRecoveryItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_cart_recovery, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartRecoveryItem item = items.get(position);
        holder.tvName.setText(item.name);
        holder.tvEmail.setText(item.email);
        holder.tvStatus.setText(item.status);
        holder.tvSummary.setText("Sản phẩm: " + item.summary);
        holder.tvValue.setText(String.format("Giá trị: %,.0f đ", item.value));
        holder.tvTime.setText(item.time);

        if (item.status.equalsIgnoreCase("Recovered")) {
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#C62828"));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvStatus, tvSummary, tvValue, tvTime;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRecoveryUserName);
            tvEmail = itemView.findViewById(R.id.tvRecoveryEmail);
            tvStatus = itemView.findViewById(R.id.tvRecoveryStatus);
            tvSummary = itemView.findViewById(R.id.tvRecoveryCartSummary);
            tvValue = itemView.findViewById(R.id.tvRecoveryCartValue);
            tvTime = itemView.findViewById(R.id.tvRecoveryTime);
        }
    }
}
