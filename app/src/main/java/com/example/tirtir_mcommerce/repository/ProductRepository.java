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
 * Nguồn dữ liệu ưu tiên theo thứ tự:
 * 1. Gọi Retrofit GET /api/v1/products?limit=1000 (online) — PRIMARY SOURCE
 * 2. Nếu thất bại + có SQLite cache → trả về cache (offline fallback)
 * 3. Nếu API fail + không có cache → dùng MockProductFallbackProvider (last resort)
 *
 * QUAN TRỌNG:
 * - SQLite chỉ dùng làm cache + cart_items, KHÔNG phải nguồn sự thật chính
 * - Mock data chỉ được dùng khi cả 2 (API + cache) đều thất bại
 * - MongoDB backend via API là nguồn sự thật chính
 *
 * Sprint 1.2 — Task A: Retrofit Logic / Fetch Data API
 */
public class ProductRepository {

    private static final String TAG = "ProductRepository";
    private static final int DEFAULT_LIMIT = 1000;

    private final Context context;
    private final DatabaseHelper dbHelper;

    // Lưu tạm thời categories list từ API response gần nhất
    private List<ProductResponse.CategoryItem> lastKnownCategories;

    public ProductRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
    }

    // ===========================
    // FETCH PRODUCTS (API-FIRST)
    // ===========================

    /**
     * Lấy danh sách sản phẩm từ API hoặc SQLite cache.
     *
     * @param onSuccess  Callback nhận List<Product>
     * @param onError    Callback nhận String message lỗi
     */
    public void fetchProducts(Consumer<List<Product>> onSuccess, Consumer<String> onError) {
        fetchProducts(DEFAULT_LIMIT, onSuccess, onError);
    }

    /**
     * Lấy danh sách sản phẩm từ API với limit tuỳ chọn.
     *
     * @param limit     Số lượng sản phẩm tối đa
     * @param onSuccess Callback nhận List<Product>
     * @param onError   Callback nhận String message lỗi
     */
    public void fetchProducts(int limit,
                              Consumer<List<Product>> onSuccess,
                              Consumer<String> onError) {
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        // Show cached products immediately so Home is usable while Render wakes up.
        try {
            List<Product> cached = dbHelper.getAllProducts();
            if (cached != null && !cached.isEmpty()) {
                Log.d(TAG, "Warm cache: showing " + cached.size() + " products before API refresh");
                onSuccess.accept(cached);
            }
        } catch (Exception e) {
            Log.w(TAG, "Warm cache unavailable: " + e.getMessage());
        }

        // PRIMARY: Gọi API backend (MongoDB qua Node.js)
        apiService.getProducts(limit, System.currentTimeMillis()).enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> products = response.body().getData();

                    // Lưu categories để HomeFragment có thể build chip filter
                    lastKnownCategories = response.body().getCategories();

                    if (products != null && !products.isEmpty()) {
                        // Cache vào SQLite để dùng offline sau
                        try {
                            dbHelper.insertProducts(products);
                            Log.d(TAG, "API success: " + products.size() + " products cached to SQLite");
                        } catch (Exception e) {
                            Log.e(TAG, "SQLite cache error: " + e.getMessage());
                        }
                        onSuccess.accept(products);
                    } else {
                        // API trả về rỗng → fallback
                        Log.w(TAG, "API returned empty product list");
                        fallbackToSqlite(onSuccess, onError, "API trả về danh sách rỗng");
                    }
                } else {
                    Log.e(TAG, "API error: HTTP " + response.code());
                    fallbackToSqlite(onSuccess, onError, "Lỗi server: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                Log.w(TAG, "Network failure (Render cold start?): " + t.getMessage());
                fallbackToSqlite(onSuccess, onError,
                        "Không có kết nối — đang tải dữ liệu đã lưu...");
            }
        });
    }

    /**
     * FALLBACK 1: Đọc dữ liệu từ SQLite cache khi mất mạng hoặc API lỗi.
     * SQLite là cache offline, KHÔNG phải nguồn sự thật.
     */
    private void fallbackToSqlite(Consumer<List<Product>> onSuccess,
                                   Consumer<String> onError,
                                   String reason) {
        try {
            List<Product> cached = dbHelper.getAllProducts();
            if (cached != null && !cached.isEmpty()) {
                Log.d(TAG, "SQLite fallback: loaded " + cached.size() + " cached products");
                onSuccess.accept(cached);
            } else {
                Log.w(TAG, "SQLite cache empty. No mock fallback available.");
                onError.accept(reason + " — không có dữ liệu offline");
            }
        } catch (Exception e) {
            Log.e(TAG, "SQLite read error: " + e.getMessage());
            onError.accept(reason + " (SQLite lỗi: " + e.getMessage() + ")");
        }
    }

    // ===========================
    // CATEGORIES
    // ===========================

    /**
     * Trả về danh mục từ API response gần nhất (dùng để build chip filter).
     * Null nếu chưa fetch lần nào.
     */
    public List<ProductResponse.CategoryItem> getLastKnownCategories() {
        return lastKnownCategories;
    }
}
