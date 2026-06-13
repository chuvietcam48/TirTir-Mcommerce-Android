package com.example.tirtir_mcommerce.ui.fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.WishlistContentProvider;
import com.example.tirtir_mcommerce.ui.activities.WishlistActivity;
import com.example.tirtir_mcommerce.ui.adapters.WishlistAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * WishlistFragment - Màn hình danh sách sản phẩm yêu thích.
 *
 * Android Components được thể hiện (cho Báo cáo):
 * - ContentProvider: Query WishlistContentProvider để lấy danh sách wishlist từ SQLite
 * - Fragment: Giao diện danh sách wishlist
 *
 * Cách hoạt động:
 * 1. Query WishlistContentProvider qua ContentResolver
 * 2. Hiển thị danh sách trong RecyclerView
 * 3. Cho phép xóa item (xóa qua ContentProvider → cập nhật UI)
 *
 * URI: content://com.example.tirtir_mcommerce.provider/wishlist
 *
 * Sprint 1.1 - Task: SQLite Logic / Wishlist DB
 */
public class WishlistFragment extends Fragment {

    private RecyclerView recyclerWishlist;
    private LinearLayout layoutEmptyWishlist;
    private TextView tvWishlistCount;
    private ImageButton btnBack;

    private WishlistAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wishlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupRecyclerView();
        loadWishlistFromContentProvider();

        btnBack.setOnClickListener(v -> {
            if (requireActivity() instanceof WishlistActivity) {
                requireActivity().finish();
            } else {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    private void bindViews(View view) {
        recyclerWishlist = view.findViewById(R.id.recyclerWishlist);
        layoutEmptyWishlist = view.findViewById(R.id.layoutEmptyWishlist);
        tvWishlistCount = view.findViewById(R.id.tvWishlistCount);
        btnBack = view.findViewById(R.id.btnBack);
    }

    private void setupRecyclerView() {
        recyclerWishlist.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    // ===========================
    // CONTENT PROVIDER QUERY
    // ===========================

    /**
     * Query WishlistContentProvider để lấy toàn bộ danh sách wishlist.
     *
     * URI: content://com.example.tirtir_mcommerce.provider/wishlist
     * Columns: _id, product_id, product_name, product_image, product_price
     */
    private void loadWishlistFromContentProvider() {
        List<WishlistAdapter.WishlistItem> wishlistItems = new ArrayList<>();

        Uri contentUri = WishlistContentProvider.CONTENT_URI;
        String sortOrder = WishlistContentProvider.COL_ADDED_AT + " DESC";

        Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(
                    contentUri,
                    null,           // Lấy tất cả cột
                    null,           // Không filter
                    null,
                    sortOrder       // Sắp xếp theo thời gian thêm mới nhất
            );

            if (cursor != null && cursor.moveToFirst()) {
                // Lấy index cột một lần để tránh gọi nhiều lần
                int idxId = cursor.getColumnIndexOrThrow(WishlistContentProvider.COL_ID);
                int idxProductId = cursor.getColumnIndexOrThrow(WishlistContentProvider.COL_PRODUCT_ID);
                int idxName = cursor.getColumnIndexOrThrow(WishlistContentProvider.COL_PRODUCT_NAME);
                int idxImage = cursor.getColumnIndexOrThrow(WishlistContentProvider.COL_PRODUCT_IMAGE);
                int idxPrice = cursor.getColumnIndexOrThrow(WishlistContentProvider.COL_PRODUCT_PRICE);

                do {
                    long id = cursor.getLong(idxId);
                    String productId = cursor.getString(idxProductId);
                    String productName = cursor.getString(idxName);
                    String productImage = cursor.getString(idxImage);
                    double productPrice = cursor.getDouble(idxPrice);

                    wishlistItems.add(new WishlistAdapter.WishlistItem(
                            id, productId, productName, productImage, productPrice
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Unable to load wishlist: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) cursor.close();
        }

        displayWishlist(wishlistItems);
    }

    /**
     * Hiển thị danh sách hoặc empty state tùy theo số lượng item.
     */
    private void displayWishlist(List<WishlistAdapter.WishlistItem> items) {
        updateCount(items.size());

        if (items.isEmpty()) {
            recyclerWishlist.setVisibility(View.GONE);
            layoutEmptyWishlist.setVisibility(View.VISIBLE);
            return;
        }

        recyclerWishlist.setVisibility(View.VISIBLE);
        layoutEmptyWishlist.setVisibility(View.GONE);

        adapter = new WishlistAdapter(requireContext(), items, this::removeWishlistItem);
        recyclerWishlist.setAdapter(adapter);
    }

    // ===========================
    // REMOVE ITEM
    // ===========================

    /**
     * Xóa item khỏi Wishlist qua ContentProvider.
     * URI: content://com.example.tirtir_mcommerce.provider/wishlist/{id}
     *
     * @param item     Item cần xóa
     * @param position Vị trí trong RecyclerView
     */
    private void removeWishlistItem(WishlistAdapter.WishlistItem item, int position) {
        Uri deleteUri = Uri.withAppendedPath(
                WishlistContentProvider.CONTENT_URI,
                String.valueOf(item.id)
        );

        int rowsDeleted = requireContext().getContentResolver().delete(deleteUri, null, null);

        if (rowsDeleted > 0) {
            adapter.removeItem(position);
            updateCount(adapter.getItemCount());
            Toast.makeText(getContext(), "Removed from wishlist", Toast.LENGTH_SHORT).show();

            // Hiện empty state nếu hết item
            if (adapter.getItemCount() == 0) {
                recyclerWishlist.setVisibility(View.GONE);
                layoutEmptyWishlist.setVisibility(View.VISIBLE);
            }
        } else {
            Toast.makeText(getContext(), "Unable to remove product", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCount(int count) {
        tvWishlistCount.setText(count + " sản phẩm");
    }
}
