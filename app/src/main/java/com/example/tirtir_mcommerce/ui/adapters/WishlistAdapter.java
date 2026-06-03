package com.example.tirtir_mcommerce.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WishlistAdapter - RecyclerView Adapter cho danh sách Wishlist.
 *
 * Mỗi item được load từ SQLite thông qua WishlistContentProvider.
 * Dữ liệu được truyền vào dưới dạng List<WishlistItem>.
 *
 * Sprint 1.1 - Task: SQLite Logic / Wishlist DB (ContentProvider)
 */
public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.WishlistViewHolder> {

    // ===========================
    // DATA MODEL
    // ===========================

    /**
     * Model đơn giản cho một item Wishlist.
     * Mapping trực tiếp từ cột SQLite trong WishlistContentProvider.
     */
    public static class WishlistItem {
        public final long id;
        public final String productId;
        public final String productName;
        public final String productImage;
        public final double productPrice;

        public WishlistItem(long id, String productId, String productName,
                            String productImage, double productPrice) {
            this.id = id;
            this.productId = productId;
            this.productName = productName;
            this.productImage = productImage;
            this.productPrice = productPrice;
        }
    }

    // ===========================
    // CALLBACK INTERFACE
    // ===========================

    public interface OnRemoveClickListener {
        void onRemove(WishlistItem item, int position);
    }

    // ===========================
    // FIELDS
    // ===========================

    private final Context context;
    private final List<WishlistItem> items;
    private final OnRemoveClickListener removeListener;
    private final NumberFormat currencyFormat;

    public WishlistAdapter(Context context, List<WishlistItem> items, OnRemoveClickListener removeListener) {
        this.context = context;
        this.items = new ArrayList<>(items);
        this.removeListener = removeListener;
        this.currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    }

    // ===========================
    // RECYCLERVIEW METHODS
    // ===========================

    @NonNull
    @Override
    public WishlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_wishlist, parent, false);
        return new WishlistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WishlistViewHolder holder, int position) {
        WishlistItem item = items.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Xóa item khỏi danh sách UI (sau khi đã xóa qua ContentProvider).
     */
    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size());
        }
    }

    public List<WishlistItem> getItems() {
        return items;
    }

    // ===========================
    // VIEW HOLDER
    // ===========================

    class WishlistViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imgProduct;
        private final TextView tvName;
        private final TextView tvPrice;
        private final ImageButton btnRemove;

        WishlistViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgWishlistProduct);
            tvName = itemView.findViewById(R.id.tvWishlistProductName);
            tvPrice = itemView.findViewById(R.id.tvWishlistProductPrice);
            btnRemove = itemView.findViewById(R.id.btnRemoveWishlist);
        }

        void bind(WishlistItem item, int position) {
            tvName.setText(item.productName != null ? item.productName : "Sản phẩm");

            // Format giá tiền
            tvPrice.setText(currencyFormat.format(item.productPrice) + " đ");

            // Load ảnh bằng Glide
            if (item.productImage != null && !item.productImage.isEmpty()) {
                Glide.with(context)
                        .load(item.productImage)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(imgProduct);
            } else {
                imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Xóa item
            btnRemove.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRemove(item, getAdapterPosition());
                }
            });
        }
    }
}
