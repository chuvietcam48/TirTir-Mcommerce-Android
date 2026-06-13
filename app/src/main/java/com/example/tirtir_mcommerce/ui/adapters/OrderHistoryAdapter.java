package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder> {

    public interface Listener {
        void onDownloadInvoice(OrderResponse order);
    }

    private List<OrderResponse> orders;
    private final Listener listener;
    private final SimpleDateFormat displayDateFormat =
            new SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.ENGLISH);

    public OrderHistoryAdapter(List<OrderResponse> orders, Listener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    public void setOrders(List<OrderResponse> orders) {
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
        OrderResponse order = orders.get(position);
        String id = order.getId() == null ? "Pending ID" : order.getId();
        String shortId = id.length() > 10 ? id.substring(id.length() - 10).toUpperCase(Locale.ENGLISH) : id;
        holder.tvOrderCode.setText("Order #" + shortId);
        holder.tvOrderStatus.setText(localizeStatus(order.getStatus()));
        holder.tvOrderDate.setText(formatDate(order.getCreatedAt()));
        holder.tvOrderTotal.setText(PriceUtils.formatPriceVnd(order.getTotalPrice()));
        boolean hasInvoice = order.getInvoiceUrl() != null && !order.getInvoiceUrl().trim().isEmpty();
        holder.btnDownloadPdf.setVisibility(hasInvoice ? View.VISIBLE : View.GONE);
        holder.btnDownloadPdf.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadInvoice(order);
        });
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

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) return "Date unavailable";
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = parser.parse(value);
                if (parsed != null) return displayDateFormat.format(parsed);
            } catch (ParseException ignored) {
                // Try the next supported API date format.
            }
        }
        return value;
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderCode, tvOrderStatus, tvOrderDate, tvOrderTotal;
        com.google.android.material.button.MaterialButton btnDownloadPdf;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
            btnDownloadPdf = itemView.findViewById(R.id.btnDownloadPDF);
        }
    }
}
