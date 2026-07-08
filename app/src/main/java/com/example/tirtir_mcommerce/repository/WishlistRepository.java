package com.example.tirtir_mcommerce.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.WishlistItem;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WishlistRepository {
    private static final String TAG = "WishlistRepository";
    private final DatabaseHelper dbHelper;
    private final Context context;

    public WishlistRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
    }

    public List<WishlistItem> getLocalWishlist() {
        List<WishlistItem> items = new ArrayList<>();
        Cursor cursor = context.getContentResolver().query(
                com.example.tirtir_mcommerce.WishlistContentProvider.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    WishlistItem item = new WishlistItem();
                    item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(com.example.tirtir_mcommerce.WishlistContentProvider.COL_ID)));
                    item.setProductId(cursor.getString(cursor.getColumnIndexOrThrow(com.example.tirtir_mcommerce.WishlistContentProvider.COL_PRODUCT_ID)));
                    item.setProductName(cursor.getString(cursor.getColumnIndexOrThrow(com.example.tirtir_mcommerce.WishlistContentProvider.COL_PRODUCT_NAME)));
                    item.setThumbnail(cursor.getString(cursor.getColumnIndexOrThrow(com.example.tirtir_mcommerce.WishlistContentProvider.COL_PRODUCT_IMAGE)));
                    
                    String priceStr = cursor.getString(cursor.getColumnIndexOrThrow(com.example.tirtir_mcommerce.WishlistContentProvider.COL_PRODUCT_PRICE));
                    // Try to parse price if it has currency symbols, or just keep it as 0 if complex
                    double price = 0;
                    try {
                        String cleanPrice = priceStr.replaceAll("[^\\d.]", "");
                        if (!cleanPrice.isEmpty()) price = Double.parseDouble(cleanPrice);
                    } catch (Exception e) {}
                    item.setPrice(price);
                    
                    item.setSynced(1); // Assume synced for CP
                    item.setSubtitle("Favorite item");
                    items.add(item);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return items;
    }

    public boolean isProductInWishlist(String productId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                "wishlist_items",
                new String[]{"id"},
                "product_id=?",
                new String[]{productId},
                null, null, null
        );
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) cursor.close();
        return exists;
    }

    public void addProductToWishlist(Product product) {
        if (isProductInWishlist(product.getProductId())) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("product_id", product.getProductId());
        values.put("product_name", product.getName());
        values.put("thumbnail", product.getThumbnailImages());
        values.put("price", product.getPrice());
        values.put("synced", 0); // Not synced initially
        values.put("added_at", System.currentTimeMillis() / 1000);

        db.insert("wishlist_items", null, values);
    }

    public void removeProductFromWishlist(String productId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("wishlist_items", "product_id=?", new String[]{productId});
    }

    public void syncAllItemsToServer(@Nullable Runnable onSuccess, @Nullable Consumer<String> onError) {
        List<WishlistItem> localItems = getLocalWishlist();
        if (localItems == null || localItems.isEmpty()) {
            // Nothing to sync locally, maybe server is empty too. Let's sync what we have (empty list).
            syncWithBackend(new ArrayList<>(), onSuccess, onError);
            return;
        }

        List<String> productIds = new ArrayList<>();
        for (WishlistItem item : localItems) {
            productIds.add(item.getProductId());
        }

        syncWithBackend(productIds, () -> {
            // Mark all as synced
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("synced", 1);
            db.update("wishlist_items", values, null, null);
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    private void syncWithBackend(List<String> productIds, @Nullable Runnable onSuccess, @Nullable Consumer<String> onError) {
        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("products", productIds);

        apiService.syncWishlist(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    if (onSuccess != null) onSuccess.run();
                } else {
                    if (onError != null) onError.accept("Backend sync failed");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                if (onError != null) onError.accept("Network error during sync: " + t.getMessage());
            }
        });
    }
}
