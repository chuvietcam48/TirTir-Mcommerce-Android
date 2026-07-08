package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
            new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);

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
        Context context = holder.itemView.getContext();

        String id = order.getId() == null ? "Pending ID" : order.getId();
        holder.tvOrderCode.setText("Order #" + displayOrderId(id));
        holder.tvOrderStatus.setText(localizeStatus(order.getStatus()));
        holder.tvOrderDate.setText(formatDate(order.getCreatedAt()));
        holder.tvOrderTotal.setText(PriceUtils.formatPriceUsd(order.getTotalPrice()));

        // Populate inner items
        holder.layoutOrderItems.removeAllViews();
        List<OrderResponse.OrderItemResponse> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            int displayCount = Math.min(items.size(), 2);
            for (int i = 0; i < displayCount; i++) {
                OrderResponse.OrderItemResponse item = items.get(i);
                View productView = LayoutInflater.from(context).inflate(R.layout.item_order_history_product, holder.layoutOrderItems, false);
                
                ImageView ivProductImage = productView.findViewById(R.id.ivProductImage);
                TextView tvProductName = productView.findViewById(R.id.tvProductName);
                TextView tvProductSubtitle = productView.findViewById(R.id.tvProductSubtitle);
                TextView tvQuantity = productView.findViewById(R.id.tvQuantity);

                tvProductName.setText(item.getName() != null ? item.getName() : "Unknown Product");
                tvProductSubtitle.setText(item.getSubtitle() != null && !item.getSubtitle().isEmpty() ? item.getSubtitle() : "Original");
                tvQuantity.setText("x" + item.getQuantity());

                if (item.getThumbnail() != null && !item.getThumbnail().isEmpty()) {
                    String primaryUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(item.getThumbnail());
                    String fallbackUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaFallbackUrl(item.getThumbnail());
                    
                    android.util.Log.d("TirTirImg", "[OrderAdapter] " + item.getName() + " -> Primary: " + primaryUrl + " | Fallback: " + fallbackUrl);

                    if (!fallbackUrl.isEmpty()) {
                        Glide.with(context)
                                .load(primaryUrl)
                                .placeholder(R.drawable.ic_product_placeholder)
                                .error(Glide.with(context).load(fallbackUrl).placeholder(R.drawable.ic_product_placeholder))
                                .into(ivProductImage);
                    } else {
                        Glide.with(context)
                                .load(primaryUrl)
                                .placeholder(R.drawable.ic_product_placeholder)
                                .into(ivProductImage);
                    }
                }

                holder.layoutOrderItems.addView(productView);
            }

            if (items.size() > 2) {
                holder.layoutMoreItems.setVisibility(View.VISIBLE);
                holder.tvMoreItemsCount.setText("+" + (items.size() - 2) + " MORE ITEM" + (items.size() - 2 > 1 ? "S" : ""));
            } else {
                holder.layoutMoreItems.setVisibility(View.GONE);
            }
        } else {
            holder.layoutMoreItems.setVisibility(View.GONE);
        }

        View.OnClickListener openClick = v -> {
            if (openListener != null) openListener.accept(order);
        };
        holder.itemView.setOnClickListener(openClick);
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
        if ("Cancelled".equalsIgnoreCase(status) || "Canceled".equalsIgnoreCase(status)) {
            return "Canceled";
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
            }
        }
        return value;
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderCode, tvOrderStatus, tvOrderDate, tvOrderTotal;
        LinearLayout layoutOrderItems;
        LinearLayout layoutMoreItems;
        TextView tvMoreItemsCount;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
            layoutOrderItems = itemView.findViewById(R.id.layoutOrderItems);
            layoutMoreItems = itemView.findViewById(R.id.layoutMoreItems);
            tvMoreItemsCount = itemView.findViewById(R.id.tvMoreItemsCount);
        }
    }
}
