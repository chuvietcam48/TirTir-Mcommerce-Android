package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;

import java.util.ArrayList;
import java.util.List;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ScanHistoryViewHolder> {

    public interface Listener {
        void onOpen(ScanHistoryItem item);
    }

    public static class ScanHistoryItem {
        public final String productName;
        public final String date;
        public final String previewIngredients;
        public final ArrayList<String> ingredients;

        public ScanHistoryItem(String productName, String date, String previewIngredients,
                               ArrayList<String> ingredients) {
            this.productName = productName;
            this.date = date;
            this.previewIngredients = previewIngredients;
            this.ingredients = ingredients;
        }
    }

    private final List<ScanHistoryItem> items = new ArrayList<>();
    private final Listener listener;

    public ScanHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<ScanHistoryItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScanHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan_history, parent, false);
        return new ScanHistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanHistoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ScanHistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDate;
        private final TextView tvPreview;

        ScanHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvScanProductName);
            tvDate = itemView.findViewById(R.id.tvScanDate);
            tvPreview = itemView.findViewById(R.id.tvScanPreviewIngredients);
            itemView.findViewById(R.id.btnOpenScanResult).setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onOpen(items.get(getAdapterPosition()));
                }
            });
        }

        void bind(ScanHistoryItem item) {
            tvName.setText(item.productName);
            tvDate.setText(item.date);
            tvPreview.setText(item.previewIngredients);
        }
    }
}
