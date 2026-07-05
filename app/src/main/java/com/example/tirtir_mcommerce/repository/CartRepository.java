package com.example.tirtir_mcommerce.repository;

import android.content.Context;
import android.util.Log;

import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * CartRepository — Single source of truth cho giỏ hàng.
 *
 * Luồng Offline Cart:
 * 1. addToCart() → luôn lưu SQLite trước (synced=0)
 * 2. Nếu online → gọi API ngay → nếu thành công → markSynced=1
 * 3. Nếu offline → NetworkReceiver sẽ gọi syncPendingToServer() khi có mạng
 *
 * Sprint 1.2 — Task B: SQLite Logic / Offline Cart
 */
public class CartRepository {

    private static final String TAG = "CartRepository";

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CartRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
    }

    // ===========================
    // LOCAL SQLITE OPERATIONS
    // ===========================

    /** Thêm/cập nhật item vào giỏ hàng SQLite. */
    public void addToCartLocal(CartItem item) {
        dbHelper.insertOrUpdateCartItem(item);
    }

    /** Lấy toàn bộ danh sách giỏ hàng từ SQLite. */
    public List<CartItem> getCartItems() {
        return dbHelper.getCartItems();
    }

    /** Cập nhật số lượng. */
    public void updateQuantity(String productId, int newQty) {
        updateQuantity(productId, "", newQty);
    }

    public void updateQuantity(String productId, String shade, int newQty) {
        if (newQty <= 0) {
            dbHelper.removeCartItem(productId);
        } else {
            dbHelper.updateCartQuantity(productId, newQty);
        }
        syncQuantity(productId, shade, Math.max(newQty, 0));
    }

    public void updateShade(String productId, String shade, int quantity) {
        dbHelper.updateCartShade(productId, shade);
        syncQuantity(productId, shade, quantity);
    }

    /** Xóa item. */
    public void removeItem(String productId) {
        dbHelper.removeCartItem(productId);
        syncQuantity(productId, "", 0);
    }

    /** Xóa toàn bộ giỏ. */
    public void clearCart() {
        dbHelper.clearCart();
        RetrofitClient.getAuthClient(context).create(ApiService.class)
                .clearCartServer().enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                    @Override public void onFailure(Call<Void> call, Throwable t) {
                        Log.w(TAG, "Server cart clear deferred: " + t.getMessage());
                    }
                });
    }

    /** Overwrite local cart with cloud data (safely keeping synced=0). */
    public void replaceCartItemsFromCloud(List<CartItem> items) {
        dbHelper.replaceCartItemsFromCloud(items);
    }

    /** Số lượng item trong giỏ. */
    public int getCartCount() {
        return dbHelper.getCartCount();
    }

    // ===========================
    // SERVER SYNC
    // ===========================

    /**
     * Thêm item lên server (POST /api/v1/cart/add).
     * Body: { productId, quantity, shade }
     *
     * @param item      Item cần sync
     * @param onSuccess Callback khi thành công (đánh dấu synced=1 trong SQLite)
     * @param onError   Callback khi thất bại (item vẫn pending)
     */
    public void syncItemToServer(CartItem item,
                                  Runnable onSuccess,
                                  Consumer<String> onError) {
        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("productId", item.getProductId());
        body.put("quantity", item.getQuantity());
        body.put("shade", item.getShade() != null ? item.getShade() : "");

        apiService.addToCartServer(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    dbHelper.markCartItemSynced(item.getProductId());
                    Log.d(TAG, "Synced to server: " + item.getProductName());
                    if (onSuccess != null) onSuccess.run();
                } else {
                    Log.w(TAG, "Sync failed (HTTP " + response.code() + "): " + item.getProductId());
                    if (onError != null) onError.accept("Lỗi " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.w(TAG, "Sync failed (network): " + t.getMessage());
                if (onError != null) onError.accept(t.getMessage());
            }
        });
    }

    /**
     * Sync tất cả items chưa sync (synced=0) lên server.
     * Được gọi bởi NetworkReceiver khi kết nối mạng được khôi phục.
     */
    public void syncPendingToServer() {
        List<CartItem> pendingItems = dbHelper.getPendingSyncItems();
        if (pendingItems.isEmpty()) {
            Log.d(TAG, "No pending items to sync");
            return;
        }

        Log.d(TAG, "Syncing " + pendingItems.size() + " pending cart items...");
        for (CartItem item : pendingItems) {
            syncItemToServer(item,
                    () -> Log.d(TAG, "Auto-synced: " + item.getProductName()),
                    error -> Log.w(TAG, "Auto-sync failed for " + item.getProductId() + ": " + error)
            );
        }
    }

    /**
     * Syncs all pending local items and completes only when the server cart is ready.
     * Checkout uses this to avoid racing order creation against local-first cart writes.
     */
    public void syncPendingToServer(Runnable onComplete, Consumer<String> onError) {
        List<CartItem> pendingItems = dbHelper.getPendingSyncItems();
        if (pendingItems == null || pendingItems.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(pendingItems.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        for (CartItem item : pendingItems) {
            syncItemToServer(item, () -> {
                if (remaining.decrementAndGet() == 0 && !failed.get() && onComplete != null) {
                    onComplete.run();
                }
            }, error -> {
                if (failed.compareAndSet(false, true) && onError != null) {
                    onError.accept(error);
                }
            });
        }
    }

    private void syncQuantity(String productId, String shade, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("shade", shade == null ? "" : shade);
        body.put("quantity", quantity);
        RetrofitClient.getAuthClient(context).create(ApiService.class)
                .updateCartServer(body).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful() && quantity > 0) {
                            dbHelper.markCartItemSynced(productId);
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.w(TAG, "Cart quantity sync deferred: " + t.getMessage());
                    }
                });
    }

    public void syncAllItemsToServer(Runnable onSuccess, Consumer<String> onError) {
        List<CartItem> localItems = dbHelper.getCartItems();
        if (localItems == null || localItems.isEmpty()) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);
        
        java.util.List<java.util.Map<String, Object>> itemsList = new java.util.ArrayList<>();
        for (CartItem item : localItems) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("productId", item.getProductId());
            map.put("quantity", item.getQuantity());
            map.put("shade", item.getShade() != null ? item.getShade() : "");
            itemsList.add(map);
        }
        
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("items", itemsList);

        apiService.syncCart(body).enqueue(new Callback<ApiResponse<List<CartItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<CartItem>>> call, Response<ApiResponse<List<CartItem>>> response) {
                if (response.isSuccessful()) {
                    for (CartItem item : localItems) {
                        dbHelper.markCartItemSynced(item.getProductId());
                    }
                    if (onSuccess != null) onSuccess.run();
                } else {
                    if (onError != null) onError.accept("HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<CartItem>>> call, Throwable t) {
                if (onError != null) onError.accept(t.getMessage());
            }
        });
    }
}
