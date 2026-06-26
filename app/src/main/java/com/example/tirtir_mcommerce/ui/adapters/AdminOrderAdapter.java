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
import androidx.appcompat.app.AlertDialog;
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
    private final String[] statusOptions = {"pending", "confirmed", "shipping", "delivered"};

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

        // Map backend status to UI status
        String uiStatus;
        if (order.status == null) {
            uiStatus = "pending";
        } else {
            switch (order.status.toLowerCase()) {
                case "pending": uiStatus = "pending"; break;
                case "processing": uiStatus = "confirmed"; break;
                case "shipped": uiStatus = "shipping"; break;
                case "delivered": uiStatus = "delivered"; break;
                default: uiStatus = "pending"; break;
            }
        }

        // Set spinner selection
        int selectionIndex = 0;
        for (int i = 0; i < statusOptions.length; i++) {
            if (statusOptions[i].equalsIgnoreCase(uiStatus)) {
                selectionIndex = i;
                break;
            }
        }
        holder.spinnerStatus.setSelection(selectionIndex);
        final int finalSelectionIndex = selectionIndex;

        holder.spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean initialized;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int selected, long id) {
                String selectedUiStatus = statusOptions[selected];
                if (!initialized) {
                    initialized = true;
                    return;
                }
                
                // Map UI status back to backend status
                String beStatus;
                switch (selectedUiStatus) {
                    case "pending": beStatus = "Pending"; break;
                    case "confirmed": beStatus = "Processing"; break;
                    case "shipping": beStatus = "Shipped"; break;
                    case "delivered": beStatus = "Delivered"; break;
                    default: beStatus = "Pending"; break;
                }

                if (!beStatus.equalsIgnoreCase(order.status) && listener != null) {
                    new AlertDialog.Builder(context)
                            .setTitle("Confirm Status Change")
                            .setMessage("Change order " + order.code + " status to \"" + selectedUiStatus + "\"?")
                            .setPositiveButton("Confirm", (d, w) -> listener.onStatusChanged(order, beStatus))
                            .setNegativeButton("Cancel", (d, w) -> {
                                int pos = holder.getAdapterPosition();
                                if (pos >= 0) {
                                    notifyItemChanged(pos);
                                } else {
                                    holder.spinnerStatus.setOnItemSelectedListener(null);
                                    holder.spinnerStatus.setSelection(finalSelectionIndex);
                                }
                            })
                            .show();
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
