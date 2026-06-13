package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ProductChipListener {
        void onProductChipClick(RecommendedProduct product);
    }

    public static class RecommendedProduct {
        public final String productId;
        public final String name;

        public RecommendedProduct(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }
    }

    public static class ChatMessage {
        public final boolean fromUser;
        public final String text;
        public final String timestamp;
        public final List<RecommendedProduct> recommendations;

        public ChatMessage(boolean fromUser, String text, String timestamp, List<RecommendedProduct> recommendations) {
            this.fromUser = fromUser;
            this.text = text;
            this.timestamp = timestamp;
            this.recommendations = recommendations == null ? new ArrayList<>() : recommendations;
        }
    }

    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ProductChipListener productChipListener;

    public ChatMessageAdapter(ProductChipListener productChipListener) {
        this.productChipListener = productChipListener;
    }

    public void submitMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public int addAndReturnPosition(ChatMessage message) {
        int position = messages.size();
        addMessage(message);
        return position;
    }

    public void updateMessage(int position, ChatMessage message) {
        if (position < 0 || position >= messages.size()) return;
        messages.set(position, message);
        notifyItemChanged(position);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).fromUser ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_bubble_user, parent, false));
        }
        return new BotViewHolder(inflater.inflate(R.layout.item_bubble_bot, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else {
            ((BotViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTimestamp;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvUserMessage);
            tvTimestamp = itemView.findViewById(R.id.tvUserTimestamp);
        }

        void bind(ChatMessage message) {
            tvMessage.setText(message.text);
            tvTimestamp.setText(message.timestamp);
        }
    }

    class BotViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final FlexboxLayout flexProducts;

        BotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvBotMessage);
            tvTimestamp = itemView.findViewById(R.id.tvBotTimestamp);
            flexProducts = itemView.findViewById(R.id.flexRecommendedProducts);
        }

        void bind(ChatMessage message) {
            tvMessage.setText(message.text);
            tvTimestamp.setText(message.timestamp);
            flexProducts.removeAllViews();
            if (message.recommendations.isEmpty()) {
                flexProducts.setVisibility(View.GONE);
                return;
            }

            flexProducts.setVisibility(View.VISIBLE);
            for (RecommendedProduct product : message.recommendations) {
                Chip chip = new Chip(itemView.getContext());
                chip.setText(product.name);
                chip.setCheckable(false);
                chip.setClickable(true);
                chip.setOnClickListener(v -> {
                    if (productChipListener != null) {
                        productChipListener.onProductChipClick(product);
                    }
                });
                flexProducts.addView(chip);
            }
        }
    }
}
