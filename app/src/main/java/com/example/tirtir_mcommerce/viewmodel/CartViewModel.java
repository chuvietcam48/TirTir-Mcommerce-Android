package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.NetworkReceiver;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.repository.CartRepository;

import java.util.List;

/**
 * CartViewModel — MVVM layer cho giỏ hàng.
 *
 * Logic addToCart():
 * 1. Luôn lưu SQLite trước (synced=0) → UX không bị chặn
 * 2. Kiểm tra mạng:
 *    - Online: sync lên server ngay → nếu thành công mark synced=1
 *    - Offline: giữ synced=0, NetworkReceiver sẽ sync khi có mạng
 *
 * Sprint 1.2 — Task B: SQLite Logic / Offline Cart
 */
public class CartViewModel extends AndroidViewModel {

    private final CartRepository cartRepository;
    private final Context appContext;

    public final MutableLiveData<List<CartItem>> cartItemsLiveData = new MutableLiveData<>();
    public final MutableLiveData<Integer> cartCountLiveData = new MutableLiveData<>(0);
    public final MutableLiveData<String> cartMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public CartViewModel(@NonNull Application application) {
        super(application);
        this.appContext = application.getApplicationContext();
        cartRepository = new CartRepository(appContext);
        refreshCart();
    }

    // ===========================
    // ADD TO CART
    // ===========================

    /**
     * Thêm sản phẩm vào giỏ hàng.
     *
     * Flow:
     * 1. Lưu SQLite ngay (offline-first)
     * 2. Nếu online: sync lên server
     * 3. Nếu offline: hiện Toast "sẽ đồng bộ khi có mạng"
     *
     * @param product  Sản phẩm cần thêm
     * @param quantity Số lượng
     */
    public void addToCart(Product product, int quantity) {
        // Tạo CartItem từ Product
        CartItem cartItem = new CartItem(
                product.getId(),
                product.getName(),
                product.getThumbnailImages(),
                product.getSalePrice() > 0 ? product.getSalePrice() : product.getPrice(),
                quantity,
                "" // shade mặc định rỗng
        );

        // Bước 1: Lưu SQLite ngay (đảm bảo offline luôn hoạt động)
        cartRepository.addToCartLocal(cartItem);
        refreshCart();

        // Bước 2: Kiểm tra mạng và sync
        boolean isOnline = NetworkReceiver.isNetworkAvailable(appContext);

        if (isOnline) {
            // Online: sync lên server ngay
            cartRepository.syncItemToServer(cartItem,
                    () -> {
                        // Sync thành công
                        cartMessage.postValue("✅ Đã thêm " + product.getName() + " vào giỏ");
                        refreshCart();
                    },
                    error -> {
                        // Sync thất bại (item vẫn ở SQLite, sẽ retry sau)
                        cartMessage.postValue("Đã lưu offline, sẽ đồng bộ sau");
                    }
            );
        } else {
            // Offline: thông báo sẽ đồng bộ sau
            cartMessage.postValue("📦 Đã thêm vào giỏ (offline) — sẽ đồng bộ khi có mạng");
        }
    }

    // ===========================
    // CART MANAGEMENT
    // ===========================

    /** Load/refresh danh sách giỏ hàng từ SQLite. */
    public void refreshCart() {
        List<CartItem> items = cartRepository.getCartItems();
        cartItemsLiveData.postValue(items);
        cartCountLiveData.postValue(cartRepository.getCartCount());
    }

    /** Cập nhật số lượng item. */
    public void updateQuantity(String productId, int newQty) {
        cartRepository.updateQuantity(productId, newQty);
        refreshCart();
    }

    /** Xóa item khỏi giỏ. */
    public void removeItem(String productId) {
        cartRepository.removeItem(productId);
        refreshCart();
        cartMessage.postValue("Đã xóa khỏi giỏ hàng");
    }

    /** Xóa toàn bộ giỏ hàng (sau khi đặt hàng thành công). */
    public void clearCart() {
        cartRepository.clearCart();
        refreshCart();
    }

    /** Lấy số lượng hiện tại từ SQLite (không cần network). */
    public int getCartCount() {
        return cartRepository.getCartCount();
    }
}
