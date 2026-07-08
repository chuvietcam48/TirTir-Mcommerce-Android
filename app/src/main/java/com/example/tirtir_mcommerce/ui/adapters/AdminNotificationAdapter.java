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
                dotBg.setColor(holder.itemView.getContext().getColor(R.color.tirtir_red_primary));
                tagBg.setColor(Color.parseColor("#F5F5F5")); 
                holder.tvCategoryTag.setTextColor(holder.itemView.getContext().getColor(R.color.tirtir_red_primary));
                if (notif.getPrimaryAction() != null) {
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnPrimaryAction.setText(notif.getPrimaryAction());
                    holder.btnPrimaryAction.setBackgroundColor(holder.itemView.getContext().getColor(R.color.tirtir_red_primary));
                    holder.btnPrimaryAction.setTextColor(Color.WHITE);
                }
                break;
            case SALES:
                holder.viewTypeDot.setVisibility(View.GONE);
                tagBg.setColor(Color.parseColor("#EEEEEE"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#424242"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnPrimaryAction.setText(notif.getPrimaryAction());
                    holder.btnPrimaryAction.setBackgroundColor(Color.TRANSPARENT);
                    holder.btnPrimaryAction.setTextColor(holder.itemView.getContext().getColor(R.color.tirtir_red_primary));
                    holder.btnPrimaryAction.setStrokeColorResource(R.color.tirtir_red_primary);
                    holder.btnPrimaryAction.setStrokeWidth(2);
                }
                break;
            case SECURITY:
                dotBg.setColor(holder.itemView.getContext().getColor(R.color.tirtir_error));
                tagBg.setColor(Color.parseColor("#F5F5F5"));
                holder.tvCategoryTag.setTextColor(holder.itemView.getContext().getColor(R.color.tirtir_error));
                if (notif.getPrimaryAction() != null) {
                    holder.btnTextLinkAction.setVisibility(View.VISIBLE);
                    holder.btnTextLinkAction.setText(notif.getPrimaryAction());
                    holder.btnTextLinkAction.setTextColor(holder.itemView.getContext().getColor(R.color.tirtir_error));
                }
                if (notif.getSecondaryAction() != null) {
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText(notif.getSecondaryAction());
                }
                break;
            case FEEDBACK:
                dotBg.setColor(Color.parseColor("#757575"));
                tagBg.setColor(Color.parseColor("#F5F5F5"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#757575"));
                if (notif.getPrimaryAction() != null) {
                    holder.btnTextLinkAction.setVisibility(View.VISIBLE);
                    holder.btnTextLinkAction.setText(notif.getPrimaryAction());
                    holder.btnTextLinkAction.setTextColor(holder.itemView.getContext().getColor(R.color.tirtir_red_primary));
                }
                if (notif.getSecondaryAction() != null) {
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText(notif.getSecondaryAction());
                }
                break;
            case SYSTEM:
                holder.viewTypeDot.setVisibility(View.GONE);
                holder.ivTypeIcon.setVisibility(View.VISIBLE);
                tagBg.setColor(Color.parseColor("#F5F5F5"));
                holder.tvCategoryTag.setTextColor(Color.parseColor("#9E9E9E"));
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
