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
    private java.util.function.Consumer<OrderResponse> openListener;
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

    public void setOpenListener(java.util.function.Consumer<OrderResponse> openListener) {
        this.openListener = openListener;
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
        holder.tvOrderCode.setText("Order #" + displayOrderId(id));
        holder.tvOrderStatus.setText(localizeStatus(order.getStatus()));
        holder.tvOrderDate.setText(formatDate(order.getCreatedAt()));
        holder.tvOrderTotal.setText(PriceUtils.formatPriceUsd(order.getTotalPrice()));
        holder.tvOrderPayment.setText(formatPayment(order.getPaymentMethod()));
        boolean hasInvoice = order.getInvoiceUrl() != null && !order.getInvoiceUrl().trim().isEmpty();
        holder.btnDownloadPdf.setVisibility(hasInvoice ? View.VISIBLE : View.GONE);
        holder.btnDownloadPdf.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadInvoice(order);
        });
        View.OnClickListener openClick = v -> {
            if (openListener != null) openListener.accept(order);
        };
        holder.itemView.setOnClickListener(openClick);
        holder.btnViewDetails.setOnClickListener(openClick);
    }

    @Override
    public int getItemCount() {
        return orders == null ? 0 : orders.size();
    }

    private String localizeStatus(String status) {
        if (status == null) return "Processing";
        if ("Confirmed".equalsIgnoreCase(status)) {
            return "Confirmed";
        }
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

    private String displayOrderId(String id) {
        if (id == null || id.trim().isEmpty()) return "Pending";
        if (id.startsWith("ORD-LOCAL-")) {
            return id.substring("ORD-LOCAL-".length());
        }
        return id.length() > 12 ? id.substring(id.length() - 12).toUpperCase(Locale.ENGLISH) : id;
    }

    private String formatPayment(String method) {
        if (method == null || method.trim().isEmpty()) return "Pending";
        if ("CARD".equalsIgnoreCase(method)) return "Card";
        if ("VNPAY".equalsIgnoreCase(method)) return "VNPAY";
        if ("MOMO".equalsIgnoreCase(method)) return "MoMo";
        return method;
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
        TextView tvOrderCode, tvOrderStatus, tvOrderDate, tvOrderTotal, tvOrderPayment;
        com.google.android.material.button.MaterialButton btnDownloadPdf, btnViewDetails;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
            tvOrderPayment = itemView.findViewById(R.id.tvOrderPayment);
            btnDownloadPdf = itemView.findViewById(R.id.btnDownloadPDF);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
        }
    }
}
