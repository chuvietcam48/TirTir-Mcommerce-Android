package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.ViewHolder> {

    public static class AdminOrder {
        public String code;
        public String id;
        public String userName;
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
    private final String[] statusOptions = {"Pending", "Processing", "Shipped", "Delivered", "Cancelled"};

    public interface OnOrderActionListener {
        void onShowDetail(AdminOrder order);
        void onStatusChanged(AdminOrder order, String status);
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
        holder.tvCode.setText(order.code);
        holder.tvUser.setText("Customer: " + order.userName);
        holder.tvTotal.setText(String.format("%,.0f đ", order.total));
        holder.spinnerStatus.setOnItemSelectedListener(null);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, statusOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerStatus.setAdapter(spinnerAdapter);

        // Set current status
        for (int i = 0; i < statusOptions.length; i++) {
            if (statusOptions[i].equalsIgnoreCase(order.status)) {
                holder.spinnerStatus.setSelection(i);
                break;
            }
        }
        holder.spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initialized;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int selected, long id) {
                String status = statusOptions[selected];
                if (!initialized) {
                    initialized = true;
                    return;
                }
                if (!status.equalsIgnoreCase(order.status) && listener != null) {
                    listener.onStatusChanged(order, status);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        holder.btnDetail.setOnClickListener(v -> {
            if (listener != null) listener.onShowDetail(order);
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode, tvUser, tvTotal;
        Spinner spinnerStatus;
        MaterialButton btnDetail;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tvOrderCode);
            tvUser = itemView.findViewById(R.id.tvUserName);
            tvTotal = itemView.findViewById(R.id.tvOrderTotal);
            spinnerStatus = itemView.findViewById(R.id.spinnerOrderStatus);
            btnDetail = itemView.findViewById(R.id.btnOrderDetail);
        }
    }
}
