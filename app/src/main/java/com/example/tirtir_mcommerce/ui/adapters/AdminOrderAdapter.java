package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tirtir_mcommerce.R;
import java.util.List;

public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.ViewHolder> {

    public static class AdminOrder {
        public String code;
        public String id;
        public String userName;
        public String userEmail; // New field for UI
        public double total;
        public String status;
        public String address;
        public double shippingFee;
        public String orderTime;
        public String products;

        public AdminOrder(String id, String code, String userName, double total, String status, String address, double shippingFee, String orderTime, String products) {
            this.id = id;
            this.code = code;
            this.userName = userName;
            this.userEmail = userName.toLowerCase().replace(" ", ".") + "@example.com"; // Mock email for now
            this.total = total;
            this.status = status;
            this.address = address;
            this.shippingFee = shippingFee;
            this.orderTime = orderTime;
            this.products = products;
        }
    }

    private final Context context;
    private final List<AdminOrder> orders;
    private final OnOrderActionListener listener;

    public interface OnOrderActionListener {
        void onShowDetail(AdminOrder order);
        void onStatusChanged(AdminOrder order, String status); // Could be hooked to the edit/ship buttons
    }

    public AdminOrderAdapter(Context context, List<AdminOrder> orders, OnOrderActionListener listener) {
        this.context = context;
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_admin_order, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminOrder order = orders.get(position);
        holder.tvOrderId.setText(order.code != null ? order.code : "#TR-" + order.id.substring(Math.max(0, order.id.length() - 5)));
        holder.tvCustomerName.setText(order.userName);
        holder.tvCustomerEmail.setText(order.userEmail);
        holder.tvTotalPrice.setText(String.format("$%,.2f", order.total));

        // Style the status badge
        String currentStatus = order.status != null ? order.status : "Pending";
        holder.tvOrderStatus.setText(currentStatus);
        
        int badgeColor = Color.parseColor("#F3F4F6"); // Default Gray
        int textColor = Color.parseColor("#4B5563");
        
        if (currentStatus.equalsIgnoreCase("pending")) {
            badgeColor = Color.parseColor("#FEF3C7"); // Amber bg
            textColor = Color.parseColor("#D97706");
        } else if (currentStatus.equalsIgnoreCase("processing")) {
            badgeColor = Color.parseColor("#DBEAFE"); // Blue bg
            textColor = Color.parseColor("#2563EB");
        } else if (currentStatus.equalsIgnoreCase("shipped")) {
            badgeColor = Color.parseColor("#E0E7FF"); // Indigo bg
            textColor = Color.parseColor("#4F46E5");
        } else if (currentStatus.equalsIgnoreCase("delivered")) {
            badgeColor = Color.parseColor("#F0FDF4"); // Green bg
            textColor = Color.parseColor("#15803D");
        } else if (currentStatus.equalsIgnoreCase("cancelled")) {
            badgeColor = Color.parseColor("#FEE2E2"); // Red bg
            textColor = Color.parseColor("#DC2626");
        }

        // Apply background tint to badge
        holder.layoutOrderStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));
        holder.tvOrderStatus.setTextColor(textColor);
        holder.viewStatusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(textColor));

        // Setup actions
        holder.btnOrderActionView.setOnClickListener(v -> {
            if (listener != null) listener.onShowDetail(order);
        });
        
        holder.btnOrderActionShip.setOnClickListener(v -> {
            if (listener != null && !currentStatus.equalsIgnoreCase("shipped")) {
                listener.onStatusChanged(order, "Shipped");
            }
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvCustomerName, tvCustomerEmail, tvTotalPrice, tvOrderStatus;
        LinearLayout layoutOrderStatus;
        View viewStatusDot;
        ImageButton btnOrderActionShip, btnOrderActionView, btnOrderActionEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvCustomerEmail = itemView.findViewById(R.id.tvCustomerEmail);
            tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            layoutOrderStatus = itemView.findViewById(R.id.layoutOrderStatus);
            viewStatusDot = itemView.findViewById(R.id.viewStatusDot);
            
            btnOrderActionShip = itemView.findViewById(R.id.btnOrderActionShip);
            btnOrderActionView = itemView.findViewById(R.id.btnOrderActionView);
            btnOrderActionEdit = itemView.findViewById(R.id.btnOrderActionEdit);
        }
    }
}
