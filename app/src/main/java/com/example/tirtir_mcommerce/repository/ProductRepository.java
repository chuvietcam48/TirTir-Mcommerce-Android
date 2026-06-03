package com.example.tirtir_mcommerce.repository;

import android.content.Context;
import android.util.Log;

import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.List;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ProductRepository — nguồn dữ liệu duy nhất cho Product.
 *
 * Ưu tiên:
 * 1. Gọi Retrofit GET /api/v1/products (online)
 * 2. Nếu thất bại → fallback SQLite cache (offline)
 * 3. Sau khi fetch online → cache vào SQLite để dùng offline sau
 *
 * Sprint 1.2 — Task A: Retrofit Logic / Fetch Data API
 */
public class ProductRepository {

    private static final String TAG = "ProductRepository";

    private final Context context;
    private final DatabaseHelper dbHelper;

    public ProductRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
    }

    // ===========================
    // FETCH PRODUCTS
    // ===========================

    /**
     * Lấy danh sách sản phẩm từ API (online) hoặc SQLite cache (offline).
     *
     * @param onSuccess callback nhận List<Product>
     * @param onError   callback nhận String message lỗi
     */
    public void fetchProducts(Consumer<List<Product>> onSuccess, Consumer<String> onError) {
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        apiService.getProducts().enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> products = response.body().getData();
                    if (products != null && !products.isEmpty()) {
                        // Cache vào SQLite để dùng offline
                        try {
                            dbHelper.insertProducts(products);
                            Log.d(TAG, "Cached " + products.size() + " products to SQLite");
                        } catch (Exception e) {
                            Log.e(TAG, "Cache error: " + e.getMessage());
                        }
                        onSuccess.accept(products);
                    } else {
                        // Response rỗng — thử fallback offline
                        fallbackToOffline(onSuccess, onError, "Dữ liệu API trống");
                    }
                } else {
                    fallbackToOffline(onSuccess, onError, "Lỗi server: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                Log.w(TAG, "Network error, falling back to offline: " + t.getMessage());
                fallbackToOffline(onSuccess, onError, "Không có kết nối mạng");
            }
        });
    }

    /**
     * Fallback: lấy dữ liệu từ SQLite cache khi mất mạng.
     */
    private void fallbackToOffline(Consumer<List<Product>> onSuccess,
                                   Consumer<String> onError,
                                   String reason) {
        List<Product> cached = dbHelper.getAllProducts();
        if (cached != null && !cached.isEmpty()) {
            Log.d(TAG, "Offline mode: loaded " + cached.size() + " products from SQLite");
            onSuccess.accept(cached);
        } else {
            onError.accept(reason + " — không có dữ liệu offline");
        }
    }
}