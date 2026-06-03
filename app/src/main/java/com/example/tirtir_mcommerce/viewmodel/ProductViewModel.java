package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.repository.ProductRepository;

import java.util.List;

/**
 * ProductViewModel — MVVM layer cho danh sách sản phẩm.
 *
 * LiveData:
 * - productsLiveData: danh sách sản phẩm (từ API hoặc SQLite cache)
 * - isLoading: trạng thái đang tải
 * - errorMessage: thông báo lỗi
 *
 * Sprint 1.2 — Task A: Retrofit Logic / Fetch Data API
 */
public class ProductViewModel extends AndroidViewModel {

    private final ProductRepository productRepository;

    public final MutableLiveData<List<Product>> productsLiveData = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isOfflineMode = new MutableLiveData<>(false);

    public ProductViewModel(@NonNull Application application) {
        super(application);
        productRepository = new ProductRepository(application.getApplicationContext());
    }

    /**
     * Load danh sách sản phẩm.
     * Online → Retrofit → cache SQLite.
     * Offline → SQLite cache.
     */
    public void loadProducts() {
        isLoading.setValue(true);
        productRepository.fetchProducts(
                products -> {
                    isLoading.postValue(false);
                    productsLiveData.postValue(products);
                },
                error -> {
                    isLoading.postValue(false);
                    isOfflineMode.postValue(true);
                    errorMessage.postValue(error);
                }
        );
    }
}
