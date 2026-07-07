package com.example.tirtir_mcommerce.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.network.ApiConfig;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import androidx.appcompat.widget.AppCompatButton;
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

    public interface OptionClickListener {
        void onOptionClick(int adapterPosition, String option);
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

<<<<<<< Updated upstream
    /** Structured support-menu row: icon + title + description + chevron. */
    public static class MenuOption {
        public final String emoji;
        public final String title;
        public final String description;
        public MenuOption(String emoji, String title, String description) {
            this.emoji = emoji;
            this.title = title;
            this.description = description;
=======
    public static class ProductContext {
        public final String productId;
        public final String name;
        public final String image;
        public final String price;
        public final String rating;
        public ProductContext(String id, String name, String image, String price, String rating) {
            this.productId = id;
            this.name = name;
            this.image = image;
            this.price = price;
            this.rating = rating;
>>>>>>> Stashed changes
        }
    }

    public static class ChatMessage {
        public final boolean fromUser;
        public final String text;
        public final String timestamp;
        public final List<RecommendedProduct> recommendations;
        public final boolean isSystem;
        public final boolean isOptions;
        public final List<ChatAction> actions;
        public final List<String> options;
<<<<<<< Updated upstream
        public final boolean isMenu;
        public final String menuHeader;
        public final List<MenuOption> menuOptions;
=======
        public final ProductContext productContext;
>>>>>>> Stashed changes

        /** Normal user/bot message (no OOD actions). */
        public ChatMessage(boolean fromUser, String text, String timestamp,
                           List<RecommendedProduct> recommendations) {
            this(fromUser, text, timestamp, recommendations, null);
        }

        /** Normal user/bot message with optional OOD action chips and optional product context. */
        public ChatMessage(boolean fromUser, String text, String timestamp,
                           List<RecommendedProduct> recommendations,
                           List<ChatAction> actions,
                           ProductContext productContext) {
            this.fromUser = fromUser;
            this.text = text;
            this.timestamp = timestamp;
            this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
            this.isSystem = false;
            this.isOptions = false;
            this.actions = actions != null ? actions : new ArrayList<>();
            this.options = new ArrayList<>();
<<<<<<< Updated upstream
            this.isMenu = false;
            this.menuHeader = "";
            this.menuOptions = new ArrayList<>();
=======
            this.productContext = productContext;
        }

        public ChatMessage(boolean fromUser, String text, String timestamp,
                           List<RecommendedProduct> recommendations,
                           List<ChatAction> actions) {
            this(fromUser, text, timestamp, recommendations, actions, null);
>>>>>>> Stashed changes
        }

        /** System divider note (centered text). */
        private ChatMessage(String systemText) {
            this.fromUser = false;
            this.text = systemText;
            this.timestamp = "";
            this.recommendations = new ArrayList<>();
            this.isSystem = true;
            this.isOptions = false;
            this.actions = new ArrayList<>();
            this.options = new ArrayList<>();
<<<<<<< Updated upstream
            this.isMenu = false;
            this.menuHeader = "";
            this.menuOptions = new ArrayList<>();
=======
            this.productContext = null;
>>>>>>> Stashed changes
        }

        /** Mode/guided options selection message. */
        private ChatMessage(List<String> options) {
            this.fromUser = false;
            this.text = "";
            this.timestamp = "";
            this.recommendations = new ArrayList<>();
            this.isSystem = false;
            this.isOptions = true;
            this.actions = new ArrayList<>();
            this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
<<<<<<< Updated upstream
            this.isMenu = false;
            this.menuHeader = "";
            this.menuOptions = new ArrayList<>();
        }

        /** Structured support menu message. */
        private ChatMessage(String header, List<MenuOption> menuOptions) {
            this.fromUser = false;
            this.text = "";
            this.timestamp = "";
            this.recommendations = new ArrayList<>();
            this.isSystem = false;
            this.isOptions = false;
            this.actions = new ArrayList<>();
            this.options = new ArrayList<>();
            this.isMenu = true;
            this.menuHeader = header != null ? header : "";
            this.menuOptions = menuOptions != null ? new ArrayList<>(menuOptions) : new ArrayList<>();
=======
            this.productContext = null;
>>>>>>> Stashed changes
        }

        public static ChatMessage system(String text) {
            return new ChatMessage(text);
        }

        public static ChatMessage options(List<String> opts) {
            return new ChatMessage(opts);
        }

        public static ChatMessage menu(String header, List<MenuOption> items) {
            return new ChatMessage(header, items);
        }
    }

    // ── View type constants ───────────────────────────────────────────────────

    private static final int TYPE_SYSTEM  = 0;
    private static final int TYPE_USER    = 1;
    private static final int TYPE_BOT     = 2;
    private static final int TYPE_OPTIONS = 3;
    private static final int TYPE_MENU    = 4;

    // ── Adapter state ─────────────────────────────────────────────────────────

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ProductChipListener productChipListener;
    private final ActionChipListener  actionChipListener;
    private final OptionClickListener optionClickListener;

    public ChatMessageAdapter(ProductChipListener productChipListener,
                              ActionChipListener  actionChipListener,
                              OptionClickListener optionClickListener) {
        this.productChipListener = productChipListener;
        this.actionChipListener  = actionChipListener;
        this.optionClickListener = optionClickListener;
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

    /** Replace the options message at `position` with a plain user-bubble echo. */
    public void collapseOptions(int position, String chosenLabel) {
        if (position < 0 || position >= messages.size()) return;
        messages.remove(position);
        notifyItemRemoved(position);
    }

    public String getLastMessageTimestamp() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (!m.isSystem && !m.isOptions && m.timestamp != null && !m.timestamp.isEmpty()) {
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
        if (m.isMenu)    return TYPE_MENU;
        if (m.isOptions) return TYPE_OPTIONS;
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
            case TYPE_OPTIONS:
                return new OptionsViewHolder(inf.inflate(R.layout.item_options_message, parent, false));
            case TYPE_MENU:
                return new MenuViewHolder(inf.inflate(R.layout.item_menu_message, parent, false));
            default:
                return new BotViewHolder(inf.inflate(R.layout.item_bubble_bot, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if      (holder instanceof MenuViewHolder)    ((MenuViewHolder) holder).bind(message);
        else if (holder instanceof OptionsViewHolder) ((OptionsViewHolder) holder).bind(message);
        else if (holder instanceof UserViewHolder)    ((UserViewHolder) holder).bind(message);
        else if (holder instanceof SystemViewHolder)  ((SystemViewHolder) holder).bind(message);
        else                                           ((BotViewHolder) holder).bind(message);
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
        private final View layoutProductCard;
        private final ImageView ivProductThumb;
        private final TextView tvProductName;
        private final TextView tvProductPrice;
        private final TextView tvProductRating;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage   = itemView.findViewById(R.id.tvUserMessage);
            tvTimestamp = itemView.findViewById(R.id.tvUserTimestamp);
            layoutProductCard = itemView.findViewById(R.id.layoutUserProductCard);
            ivProductThumb    = itemView.findViewById(R.id.ivProductThumb);
            tvProductName     = itemView.findViewById(R.id.tvProductName);
            tvProductPrice    = itemView.findViewById(R.id.tvProductPrice);
            tvProductRating   = itemView.findViewById(R.id.tvProductRating);
        }
        void bind(ChatMessage message) {
            tvMessage.setText(message.text);
            tvTimestamp.setText(message.timestamp);

            if (layoutProductCard != null) {
                if (message.productContext != null) {
                    layoutProductCard.setVisibility(View.VISIBLE);
                    if (tvProductName != null) tvProductName.setText(message.productContext.name);
                    if (tvProductPrice != null) tvProductPrice.setText(message.productContext.price + " đ");
                    if (tvProductRating != null) tvProductRating.setText(message.productContext.rating);
                    if (ivProductThumb != null && message.productContext.image != null) {
                        String primaryUrl = message.productContext.image;
                        String fallbackUrl = ApiConfig.resolveMediaFallbackUrl(primaryUrl);
                        
                        Glide.with(itemView.getContext())
                             .load(primaryUrl)
                             .error(
                                 Glide.with(itemView.getContext())
                                      .load(fallbackUrl)
                                      .placeholder(R.drawable.ic_tirtir_logo)
                             )
                             .placeholder(R.drawable.ic_tirtir_logo)
                             .into(ivProductThumb);
                    }
                } else {
                    layoutProductCard.setVisibility(View.GONE);
                }
            }
        }
    }

    class OptionsViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout llOptionButtons;

        OptionsViewHolder(@NonNull View itemView) {
            super(itemView);
            llOptionButtons = itemView.findViewById(R.id.llOptionButtons);
        }

        void bind(ChatMessage message) {
            llOptionButtons.removeAllViews();
            for (String option : message.options) {
                // Use AppCompatButton — MaterialButton overrides custom backgrounds via backgroundTint
                AppCompatButton btn = new AppCompatButton(itemView.getContext());

                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dpToPx(28));
                bg.setColor(0xFFFFFFFF);
                bg.setStroke((int) dpToPx(1.5f), 0xFF8B0000);
                btn.setBackground(bg);
                btn.setTextColor(0xFF8B0000);
                btn.setTextSize(14f);
                btn.setAllCaps(false);

                int ph = (int) dpToPx(20);
                int pv = (int) dpToPx(13);
                btn.setPadding(ph, pv, ph, pv);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, (int) dpToPx(10));
                btn.setLayoutParams(params);

                btn.setText(option);
                btn.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_ID && optionClickListener != null) {
                        optionClickListener.onOptionClick(pos, option);
                    }
                });

                llOptionButtons.addView(btn);
            }
        }

        private float dpToPx(float dp) {
            return dp * itemView.getContext().getResources().getDisplayMetrics().density;
        }
    }

    /** Shopee/TikTok-style structured support menu: card with icon+title+description rows. */
    class MenuViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMenuHeader;
        private final LinearLayout llMenuContainer;

        MenuViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMenuHeader    = itemView.findViewById(R.id.tvMenuHeader);
            llMenuContainer = itemView.findViewById(R.id.llMenuContainer);
        }

        void bind(ChatMessage message) {
            if (message.menuHeader.isEmpty()) {
                tvMenuHeader.setVisibility(View.GONE);
            } else {
                tvMenuHeader.setVisibility(View.VISIBLE);
                tvMenuHeader.setText(message.menuHeader);
            }

            // Card background: white, rounded, soft burgundy-tinted stroke
            GradientDrawable card = new GradientDrawable();
            card.setShape(GradientDrawable.RECTANGLE);
            card.setCornerRadius(dp(16));
            card.setColor(0xFFFFFFFF);
            card.setStroke((int) dp(1), 0xFFEBDCDF);
            llMenuContainer.setBackground(card);
            llMenuContainer.setElevation(dp(1));
            llMenuContainer.removeAllViews();

            android.content.Context ctx = itemView.getContext();
            for (int i = 0; i < message.menuOptions.size(); i++) {
                MenuOption item = message.menuOptions.get(i);

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding((int) dp(14), (int) dp(12), (int) dp(12), (int) dp(12));
                android.util.TypedValue tv = new android.util.TypedValue();
                ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                row.setForeground(ContextCompat.getDrawable(ctx, tv.resourceId));

                // Leading icon
                TextView tvIcon = new TextView(ctx);
                tvIcon.setText(item.emoji);
                tvIcon.setTextSize(17f);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        (int) dp(30), ViewGroup.LayoutParams.WRAP_CONTENT);
                tvIcon.setLayoutParams(iconLp);
                row.addView(tvIcon);

                // Title + description
                LinearLayout colText = new LinearLayout(ctx);
                colText.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textLp.setMarginStart((int) dp(4));
                colText.setLayoutParams(textLp);

                TextView tvTitle = new TextView(ctx);
                tvTitle.setText(item.title);
                tvTitle.setTextColor(0xFF2B2D2D);
                tvTitle.setTextSize(14.5f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                colText.addView(tvTitle);

                if (item.description != null && !item.description.isEmpty()) {
                    TextView tvDesc = new TextView(ctx);
                    tvDesc.setText(item.description);
                    tvDesc.setTextColor(0xFF9B8E90);
                    tvDesc.setTextSize(12f);
                    LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    descLp.topMargin = (int) dp(1.5f);
                    tvDesc.setLayoutParams(descLp);
                    colText.addView(tvDesc);
                }
                row.addView(colText);

                // Trailing chevron
                TextView tvChevron = new TextView(ctx);
                tvChevron.setText("›");
                tvChevron.setTextColor(0xFFC4B2B6);
                tvChevron.setTextSize(20f);
                tvChevron.setPadding((int) dp(6), 0, 0, 0);
                row.addView(tvChevron);

                final String title = item.title;
                row.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && optionClickListener != null) {
                        optionClickListener.onOptionClick(pos, title);
                    }
                });
                llMenuContainer.addView(row);

                // Subtle divider between rows
                if (i < message.menuOptions.size() - 1) {
                    View divider = new View(ctx);
                    LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
                    dLp.setMarginStart((int) dp(48));
                    divider.setLayoutParams(dLp);
                    divider.setBackgroundColor(0xFFF3EAEC);
                    llMenuContainer.addView(divider);
                }
            }
        }

        private float dp(float v) {
            return v * itemView.getContext().getResources().getDisplayMetrics().density;
        }
    }

    class BotViewHolder extends RecyclerView.ViewHolder {
        private final TextView      tvMessage;
        private final TextView      tvTimestamp;
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
