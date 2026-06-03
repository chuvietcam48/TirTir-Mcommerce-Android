package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.example.tirtir_mcommerce.viewmodel.CartViewModel;
import com.example.tirtir_mcommerce.viewmodel.ProductViewModel;

import java.util.ArrayList;

/**
 * ShopFragment — Màn hình danh sách sản phẩm.
 *
 * Sprint 1.2 — Task A: Retrofit Logic / Fetch Data API
 * - Observe ProductViewModel (MVVM pattern, không gọi Retrofit trực tiếp)
 * - Hiển thị sản phẩm với Glide images qua ProductAdapter
 * - Offline fallback: SQLite cache
 *
 * Sprint 1.2 — Task B: Offline Cart
 * - Click "Thêm vào giỏ" → CartViewModel.addToCart()
 * - Online: lưu SQLite + sync API; Offline: lưu SQLite, auto sync khi có mạng
 */
public class ShopFragment extends Fragment {

    private ProductViewModel productViewModel;
    private CartViewModel cartViewModel;

    private RecyclerView recyclerView;
    private ProductAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Init ViewModels
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        cartViewModel = new ViewModelProvider(requireActivity()).get(CartViewModel.class);

        // Bind Views
        recyclerView = view.findViewById(R.id.recyclerViewProducts);
        // progressBar và tvOfflineBanner sẽ được bind khi FE layout có các view này

        // Setup RecyclerView — 2 cột grid
        adapter = new ProductAdapter(getContext(), new ArrayList<>(), this::onAddToCartClicked);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerView.setAdapter(adapter);

        // Observe ViewModel
        observeViewModels();

        // Load dữ liệu
        productViewModel.loadProducts();
    }

    // ===========================
    // OBSERVE VIEWMODELS
    // ===========================

    private void observeViewModels() {
        // Products LiveData
        productViewModel.productsLiveData.observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                adapter.updateData(products);
            }
        });

        // Loading state — sẽ bỏ sung khi FE có ProgressBar trong layout
        productViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            // TODO: progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE)
        });

        // Error / offline mode
        productViewModel.errorMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // Offline banner — sẽ bỏ sung khi FE có tvOfflineBanner trong layout
        productViewModel.isOfflineMode.observe(getViewLifecycleOwner(), isOffline -> {
            // TODO: tvOfflineBanner.setVisibility(isOffline ? View.VISIBLE : View.GONE)
        });

        // Cart messages
        cartViewModel.cartMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===========================
    // CART ACTION
    // ===========================

    /**
     * Callback từ ProductAdapter khi user bấm "Thêm vào giỏ".
     * Delegate sang CartViewModel để xử lý online/offline.
     */
    private void onAddToCartClicked(Product product) {
        cartViewModel.addToCart(product, 1);
    }
}
