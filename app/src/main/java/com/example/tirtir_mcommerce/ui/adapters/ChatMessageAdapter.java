package com.example.tirtir_mcommerce.ui.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── Listener interfaces ───────────────────────────────────────────────────

    public interface ProductChipListener {
        void onProductChipClick(RecommendedProduct product);
    }

    public interface ActionChipListener {
        void onActionChipClick(ChatAction action);
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public static class RecommendedProduct {
        public final String productId;
        public final String name;
        public RecommendedProduct(String productId, String name) {
            this.productId = productId;
            this.name = name;
        }
    }

    public static class ChatAction {
        public final String type;  // choose_topic | contact_staff | call_hotline
        public final String label;
        public ChatAction(String type, String label) {
            this.type = type;
            this.label = label;
        }
    }

    public static class ChatMessage {
        public final boolean fromUser;
        public final String text;
        public final String timestamp;
        public final List<RecommendedProduct> recommendations;
        public final boolean isSystem;
        public final List<ChatAction> actions;

        /** Normal user/bot message. */
        public ChatMessage(boolean fromUser, String text, String timestamp,
                           List<RecommendedProduct> recommendations) {
            this(fromUser, text, timestamp, recommendations, null);
        }

        /** Normal user/bot message with optional OOD action chips. */
        public ChatMessage(boolean fromUser, String text, String timestamp,
                           List<RecommendedProduct> recommendations,
                           List<ChatAction> actions) {
            this.fromUser = fromUser;
            this.text = text;
            this.timestamp = timestamp;
            this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
            this.isSystem = false;
            this.actions = actions != null ? actions : new ArrayList<>();
        }

        /** System divider note (centered text). */
        private ChatMessage(String systemText) {
            this.fromUser = false;
            this.text = systemText;
            this.timestamp = "";
            this.recommendations = new ArrayList<>();
            this.isSystem = true;
            this.actions = new ArrayList<>();
        }

        public static ChatMessage system(String text) {
            return new ChatMessage(text);
        }
    }

    // ── View type constants ───────────────────────────────────────────────────

    private static final int TYPE_SYSTEM = 0;
    private static final int TYPE_USER   = 1;
    private static final int TYPE_BOT    = 2;

    // ── Adapter state ─────────────────────────────────────────────────────────

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ProductChipListener productChipListener;
    private final ActionChipListener  actionChipListener;

    public ChatMessageAdapter(ProductChipListener productChipListener,
                              ActionChipListener actionChipListener) {
        this.productChipListener = productChipListener;
        this.actionChipListener  = actionChipListener;
    }

    // ── List mutation ─────────────────────────────────────────────────────────

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

    public String getLastMessageTimestamp() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (!m.isSystem && m.timestamp != null && !m.timestamp.isEmpty()) {
                return m.timestamp;
            }
        }
        return "";
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = messages.get(position);
        if (m.isSystem)  return TYPE_SYSTEM;
        if (m.fromUser)  return TYPE_USER;
        return TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_USER:
                return new UserViewHolder(inf.inflate(R.layout.item_bubble_user, parent, false));
            case TYPE_SYSTEM:
                return new SystemViewHolder(inf.inflate(R.layout.item_bubble_system, parent, false));
            default:
                return new BotViewHolder(inf.inflate(R.layout.item_bubble_bot, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder)   ((UserViewHolder) holder).bind(message);
        else if (holder instanceof SystemViewHolder) ((SystemViewHolder) holder).bind(message);
        else ((BotViewHolder) holder).bind(message);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvText;
        SystemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvSystemMessage);
        }
        void bind(ChatMessage message) {
            if (tvText != null) tvText.setText(message.text);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage   = itemView.findViewById(R.id.tvUserMessage);
            tvTimestamp = itemView.findViewById(R.id.tvUserTimestamp);
        }
        void bind(ChatMessage message) {
            tvMessage.setText(message.text);
            tvTimestamp.setText(message.timestamp);
        }
    }

    class BotViewHolder extends RecyclerView.ViewHolder {
        private final TextView     tvMessage;
        private final TextView     tvTimestamp;
        private final FlexboxLayout flexProducts;
        private final FlexboxLayout flexActions;

        BotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage    = itemView.findViewById(R.id.tvBotMessage);
            tvTimestamp  = itemView.findViewById(R.id.tvBotTimestamp);
            flexProducts = itemView.findViewById(R.id.flexRecommendedProducts);
            flexActions  = itemView.findViewById(R.id.flexBotActions);
        }

        void bind(ChatMessage message) {
            tvMessage.setText(message.text);
            tvTimestamp.setText(message.timestamp);

            // Product recommendation chips
            flexProducts.removeAllViews();
            if (message.recommendations.isEmpty()) {
                flexProducts.setVisibility(View.GONE);
            } else {
                flexProducts.setVisibility(View.VISIBLE);
                for (RecommendedProduct product : message.recommendations) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(product.name);
                    chip.setCheckable(false);
                    chip.setClickable(true);
                    chip.setOnClickListener(v -> {
                        if (productChipListener != null) productChipListener.onProductChipClick(product);
                    });
                    flexProducts.addView(chip);
                }
            }

            // OOD action chips (choose_topic / contact_staff / call_hotline)
            if (flexActions == null) return;
            flexActions.removeAllViews();
            if (message.actions == null || message.actions.isEmpty()) {
                flexActions.setVisibility(View.GONE);
            } else {
                flexActions.setVisibility(View.VISIBLE);
                for (ChatAction action : message.actions) {
                    Chip chip = new Chip(itemView.getContext());
                    chip.setText(action.label);
                    chip.setCheckable(false);
                    chip.setClickable(true);
                    chip.setChipBackgroundColor(
                            ColorStateList.valueOf(
                                    ContextCompat.getColor(itemView.getContext(), R.color.tirtir_red_primary)));
                    chip.setTextColor(0xFFFFFFFF);
                    chip.setChipStrokeWidth(0f);
                    chip.setOnClickListener(v -> {
                        if (actionChipListener != null) actionChipListener.onActionChipClick(action);
                    });
                    flexActions.addView(chip);
                }
            }
        }
    }
}
