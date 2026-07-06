package com.example.tirtir_mcommerce.ui.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.AdminNotification;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AdminNotificationAdapter extends RecyclerView.Adapter<AdminNotificationAdapter.ViewHolder> {

    private List<AdminNotification> notifications;

    public AdminNotificationAdapter(List<AdminNotification> notifications) {
        this.notifications = notifications;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminNotification notif = notifications.get(position);

        holder.tvTimeLabel.setText(notif.getTime());
        holder.tvMessageBody.setText(notif.getMessage());
        holder.tvCategoryTag.setText(notif.getType().name());

        // Reset visibility
        holder.viewTypeDot.setVisibility(View.VISIBLE);
        holder.ivTypeIcon.setVisibility(View.GONE);
        holder.btnPrimaryAction.setVisibility(View.GONE);
        holder.btnSecondaryAction.setVisibility(View.GONE);
        holder.btnTextLinkAction.setVisibility(View.GONE);
        holder.btnCloseSystemNotif.setVisibility(View.GONE);

        // Styling based on Type
        GradientDrawable tagBg = (GradientDrawable) holder.tvCategoryTag.getBackground().mutate();
        GradientDrawable dotBg = (GradientDrawable) holder.viewTypeDot.getBackground().mutate();

        switch (notif.getType()) {
            case INVENTORY:
                dotBg.setColor(Color.parseColor("#610000")); // Primary
                tagBg.setColor(Color.parseColor("#ffdad4")); // Primary Fixed
                holder.tvCategoryTag.setTextColor(Color.parseColor("#610000"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnPrimaryAction.setText(notif.getPrimaryAction());
                    holder.btnPrimaryAction.setBackgroundColor(Color.parseColor("#610000"));
                    holder.btnPrimaryAction.setTextColor(Color.WHITE);
                }
                break;
            case SALES:
                holder.viewTypeDot.setVisibility(View.GONE);
                tagBg.setColor(Color.parseColor("#e2dfde")); // Secondary Container
                holder.tvCategoryTag.setTextColor(Color.parseColor("#636262"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnPrimaryAction.setText(notif.getPrimaryAction());
                    holder.btnPrimaryAction.setBackgroundColor(Color.TRANSPARENT);
                    holder.btnPrimaryAction.setTextColor(Color.parseColor("#610000"));
                    holder.btnPrimaryAction.setStrokeColorResource(R.color.tirtir_red_dark);
                    holder.btnPrimaryAction.setStrokeWidth(2);
                }
                break;
            case SECURITY:
                dotBg.setColor(Color.parseColor("#ba1a1a")); // Error
                tagBg.setColor(Color.parseColor("#ffdad6"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#ba1a1a"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnTextLinkAction.setVisibility(View.VISIBLE);
                    holder.btnTextLinkAction.setText(notif.getPrimaryAction());
                }
                if (notif.getSecondaryAction() != null) {
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText(notif.getSecondaryAction());
                }
                break;
            case FEEDBACK:
                dotBg.setColor(Color.parseColor("#5f5e5e")); // Secondary
                tagBg.setColor(Color.parseColor("#eeeeee"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#5f5e5e"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnTextLinkAction.setVisibility(View.VISIBLE);
                    holder.btnTextLinkAction.setText(notif.getPrimaryAction());
                }
                if (notif.getSecondaryAction() != null) {
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText(notif.getSecondaryAction());
                }
                break;
            case SYSTEM:
                holder.viewTypeDot.setVisibility(View.GONE);
                holder.ivTypeIcon.setVisibility(View.VISIBLE);
                tagBg.setColor(Color.parseColor("#414343"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#afafb0"));
                holder.btnCloseSystemNotif.setVisibility(View.VISIBLE);
                break;
        }

        // Handle mark as read fading
        if (notif.isRead()) {
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewTypeDot;
        ImageView ivTypeIcon, btnCloseSystemNotif;
        TextView tvCategoryTag, tvTimeLabel, tvMessageBody, btnSecondaryAction, btnTextLinkAction;
        MaterialButton btnPrimaryAction;

        ViewHolder(View view) {
            super(view);
            viewTypeDot = view.findViewById(R.id.viewTypeDot);
            ivTypeIcon = view.findViewById(R.id.ivTypeIcon);
            tvCategoryTag = view.findViewById(R.id.tvCategoryTag);
            tvTimeLabel = view.findViewById(R.id.tvTimeLabel);
            tvMessageBody = view.findViewById(R.id.tvMessageBody);
            btnPrimaryAction = view.findViewById(R.id.btnPrimaryAction);
            btnSecondaryAction = view.findViewById(R.id.btnSecondaryAction);
            btnTextLinkAction = view.findViewById(R.id.btnTextLinkAction);
            btnCloseSystemNotif = view.findViewById(R.id.btnCloseSystemNotif);
        }
    }
}
