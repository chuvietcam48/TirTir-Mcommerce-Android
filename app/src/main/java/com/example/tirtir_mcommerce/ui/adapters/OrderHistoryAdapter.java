package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder> {

    public static class MockOrder {
        public String code;
        public String status;
        public String date;
        public double total;

        public MockOrder(String code, String status, String date, double total) {
            this.code = code;
            this.status = status;
            this.date = date;
            this.total = total;
        }
    }

    private List<MockOrder> orders;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

    public OrderHistoryAdapter(List<MockOrder> orders) {
        this.orders = orders;
    }

    public void setOrders(List<MockOrder> orders) {
        this.orders = orders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_history, parent, false);
        return new OrderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        MockOrder order = orders.get(position);
        holder.tvOrderCode.setText(order.code);
        holder.tvOrderStatus.setText(localizeStatus(order.status));
        holder.tvOrderDate.setText(order.date);
        holder.tvOrderTotal.setText(PriceUtils.formatPriceVnd(order.total));
    }

    @Override
    public int getItemCount() {
        return orders == null ? 0 : orders.size();
    }

    private String localizeStatus(String status) {
        if (status == null) return "Processing";
        if ("Pending".equalsIgnoreCase(status) || "Processing".equalsIgnoreCase(status)) {
            return "Processing";
        }
        if ("Shipping".equalsIgnoreCase(status)) {
            return "Shipping";
        }
        if ("Delivered".equalsIgnoreCase(status)) {
            return "Delivered";
        }
        return status;
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderCode, tvOrderStatus, tvOrderDate, tvOrderTotal;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
        }
    }
}
