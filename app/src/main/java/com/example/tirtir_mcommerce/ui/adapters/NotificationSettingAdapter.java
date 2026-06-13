package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class NotificationSettingAdapter extends RecyclerView.Adapter<NotificationSettingAdapter.SettingViewHolder> {

    public interface Listener {
        void onSwitchChanged(NotificationSetting item, boolean enabled);
        void onTimeClick(NotificationSetting item);
    }

    public static class NotificationSetting {
        public final String key;
        public final int iconRes;
        public final String name;
        public final String description;
        public final boolean supportsTimePicker;
        public boolean enabled;
        public String time;

        public NotificationSetting(String key, int iconRes, String name, String description, boolean supportsTimePicker) {
            this.key = key;
            this.iconRes = iconRes;
            this.name = name;
            this.description = description;
            this.supportsTimePicker = supportsTimePicker;
        }
    }

    private final Listener listener;
    private final List<NotificationSetting> items = new ArrayList<>();

    public NotificationSettingAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<NotificationSetting> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_setting, parent, false);
        return new SettingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SettingViewHolder holder, int position) {
        NotificationSetting item = items.get(position);
        holder.ivIcon.setImageResource(item.iconRes);
        holder.tvName.setText(item.name);
        holder.tvDescription.setText(item.description);
        holder.switchCompat.setOnCheckedChangeListener(null);
        holder.switchCompat.setChecked(item.enabled);
        holder.switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.enabled = isChecked;
            if (listener != null) listener.onSwitchChanged(item, isChecked);
        });

        if (item.supportsTimePicker) {
            holder.btnTime.setVisibility(View.VISIBLE);
            holder.btnTime.setText(item.time == null || item.time.isEmpty() ? "Set time" : "Remind at " + item.time);
            holder.btnTime.setOnClickListener(v -> {
                if (listener != null) listener.onTimeClick(item);
            });
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTimeClick(item);
            });
        } else {
            holder.btnTime.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> holder.switchCompat.setChecked(!holder.switchCompat.isChecked()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SettingViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvDescription;
        final MaterialButton btnTime;
        final SwitchCompat switchCompat;

        SettingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivSettingIcon);
            tvName = itemView.findViewById(R.id.tvSettingName);
            tvDescription = itemView.findViewById(R.id.tvSettingDescription);
            btnTime = itemView.findViewById(R.id.btnNotificationTime);
            switchCompat = itemView.findViewById(R.id.switchNotificationSetting);
        }
    }
}
