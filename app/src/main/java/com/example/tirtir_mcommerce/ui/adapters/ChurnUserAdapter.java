package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

public class ChurnUserAdapter extends RecyclerView.Adapter<ChurnUserAdapter.ViewHolder> {

    public static class ChurnUser {
        public String id;
        public String name;
        public String email;
        public String segment;
        public int r, f, m;

        public ChurnUser(String id, String name, String email, String segment, int r, int f, int m) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.segment = segment;
            this.r = r;
            this.f = f;
            this.m = m;
        }
    }

    private final Context context;
    private final List<ChurnUser> users;
    private final OnUserActionListener voucherListener;
    private final OnUserActionListener fcmListener;

    public interface OnUserActionListener {
        void onAction(ChurnUser user);
    }

    public ChurnUserAdapter(Context context, List<ChurnUser> users, OnUserActionListener voucherListener, OnUserActionListener fcmListener) {
        this.context = context;
        this.users = users;
        this.voucherListener = voucherListener;
        this.fcmListener = fcmListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_churn_user, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChurnUser user = users.get(position);
        holder.tvName.setText(user.name);
        holder.tvEmail.setText(user.email);
        holder.chipSegment.setText(user.segment);
        holder.tvR.setText("R: " + user.r);
        holder.tvF.setText("F: " + user.f);
        holder.tvM.setText("M: " + user.m);

        // Color coding for segments
        int color;
        switch (user.segment.toLowerCase()) {
            case "champion": color = Color.parseColor("#4CAF50"); break;
            case "loyal": color = Color.parseColor("#2196F3"); break;
            case "at risk": color = Color.parseColor("#FF9800"); break;
            case "churned": color = Color.parseColor("#F44336"); break;
            default: color = Color.parseColor("#9E9E9E"); break;
        }
        holder.chipSegment.setChipBackgroundColorResource(android.R.color.transparent);
        holder.chipSegment.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));

        holder.btnVoucher.setOnClickListener(v -> {
            if (voucherListener != null) voucherListener.onAction(user);
        });
        holder.btnFCM.setOnClickListener(v -> {
            if (fcmListener != null) fcmListener.onAction(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvR, tvF, tvM;
        Chip chipSegment;
        MaterialButton btnVoucher, btnFCM;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvChurnUserName);
            tvEmail = itemView.findViewById(R.id.tvChurnUserEmail);
            tvR = itemView.findViewById(R.id.tvScoreR);
            tvF = itemView.findViewById(R.id.tvScoreF);
            tvM = itemView.findViewById(R.id.tvScoreM);
            chipSegment = itemView.findViewById(R.id.chipSegment);
            btnVoucher = itemView.findViewById(R.id.btnSendVoucher);
            btnFCM = itemView.findViewById(R.id.btnSendFCM);
        }
    }
}
