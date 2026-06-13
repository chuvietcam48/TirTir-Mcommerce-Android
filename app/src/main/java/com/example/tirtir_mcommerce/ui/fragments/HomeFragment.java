package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.ui.activities.ChatActivity;
import com.example.tirtir_mcommerce.ui.activities.IngredientScanActivity;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.activities.SkinAnalysisActivity;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SCR-13 HomeFragment — Danh sách sản phẩm
 *
 * API-first (TASK 2):
 * - Primary: GET /api/v1/products?limit=1000 via ProductRepository
 * - Fallback: SQLite cache → MockProductFallbackProvider (last resort)
 *
 * Category chips (TASK 3):
 * - Built DYNAMICALLY from API response categories[] + unique Category fields
 * - NOT hardcoded (no more Cushion/Toner/Serum as only options)
 * - "No products found" only shown after successful load with empty filter result
 *
 * Cold-start UX (TASK 8):
 * - Loading state shows "Loading TirTir products... Server may take 30–60s to wake up"
 * - Cache shown immediately if available while API refreshes
 * - Retry button on failure with no cache
 * - Empty state only shown AFTER load completes
 *
 * Sprint 1.2 — Task A
 */
public class HomeFragment extends Fragment {

    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private ProgressBar progressProducts;
    private LinearLayout layoutEmptyProducts;
    private LinearLayout layoutLoadingState;
    private LinearLayout layoutErrorState;
    private TextView tvLoadingMessage;
    private TextView tvErrorMessage;
    private Button btnRetry;
    private SearchView searchViewProducts;
    private com.google.android.material.chip.ChipGroup chipGroupSkinTypeFilter;
    private android.widget.Spinner spinnerCategory;

    private ProductRepository productRepository;
    private List<Product> fullProductList = new ArrayList<>();
    private String currentSearchQuery = "";
    private String currentSkinTypeFilter = "All";
    private String currentCategoryFilter = "All";

    // Generated chip IDs to maintain selection state
    private static final int CHIP_ID_BASE = 9000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        rvProducts           = view.findViewById(R.id.rvProducts);
        progressProducts     = view.findViewById(R.id.progressProducts);
        layoutEmptyProducts  = view.findViewById(R.id.layoutEmptyProducts);
        layoutLoadingState   = view.findViewById(R.id.layoutLoadingState);
        layoutErrorState     = view.findViewById(R.id.layoutErrorState);
        tvLoadingMessage     = view.findViewById(R.id.tvLoadingMessage);
        tvErrorMessage       = view.findViewById(R.id.tvErrorMessage);
        btnRetry             = view.findViewById(R.id.btnRetry);
        searchViewProducts   = view.findViewById(R.id.searchViewProducts);
        chipGroupSkinTypeFilter = view.findViewById(R.id.chipGroupSkinTypeFilter);
        spinnerCategory      = view.findViewById(R.id.spinnerCategory);

        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));

        adapter = new ProductAdapter(getContext(), new ArrayList<>(), product -> {
            Intent intent = new Intent(getContext(), ProductDetailActivity.class);
            // Pass all product fields needed by ProductDetailActivity
            String pid = product.getProductId() != null ? product.getProductId() : product.getId();
            intent.putExtra("PRODUCT_ID",          pid);
            intent.putExtra("PRODUCT_NAME",        product.getName());
            // Price: raw value from API (see ProductAdapter price comments)
            intent.putExtra("PRODUCT_PRICE",       product.getPrice());
            intent.putExtra("PRODUCT_SALE_PRICE",  product.getSalePrice());
            intent.putExtra("PRODUCT_CATEGORY",    product.getCategory());
            intent.putExtra("PRODUCT_IMAGE",       product.getThumbnailImages());
            intent.putExtra("PRODUCT_SKIN_TYPES",  product.getSkinTypeTarget());
            intent.putExtra("PRODUCT_INGREDIENTS", product.getKeyIngredients());
            intent.putExtra("PRODUCT_DESCRIPTION", product.getDescriptionShort());
            intent.putExtra("PRODUCT_STOCK",       product.getStockQuantity());
            if (product.getGalleryImages() != null) {
                intent.putStringArrayListExtra("PRODUCT_GALLERY", new java.util.ArrayList<>(product.getGalleryImages()));
            }
            startActivity(intent);
        });
        rvProducts.setAdapter(adapter);

        productRepository = new ProductRepository(getContext());

        setupSearch();
        setupAiActions(view);
        loadProducts();

        btnRetry.setOnClickListener(v -> loadProducts());

        return view;
    }

    private void setupAiActions(View view) {
        View btnHomeChat = view.findViewById(R.id.btnHomeChat);
        View btnHomeSkin = view.findViewById(R.id.btnHomeSkin);
        View btnHomeScan = view.findViewById(R.id.btnHomeScan);

        if (btnHomeChat != null) {
            btnHomeChat.setOnClickListener(v -> startActivity(new Intent(requireContext(), ChatActivity.class)));
        }
        if (btnHomeSkin != null) {
            btnHomeSkin.setOnClickListener(v -> startActivity(new Intent(requireContext(), SkinAnalysisActivity.class)));
        }
        if (btnHomeScan != null) {
            btnHomeScan.setOnClickListener(v -> startActivity(new Intent(requireContext(), IngredientScanActivity.class)));
        }
    }

    // ===========================
    // PRODUCT LOADING
    // ===========================

    /**
     * API-first product load with cold-start UX.
     * Shows loading message during Render wake-up (can take 30–60s).
     * Shows cached products immediately if available.
     */
    private void loadProducts() {
        showLoadingState();

        // If SQLite cache has data, show it immediately while API refreshes
        // (happens on subsequent opens — first open shows spinner only)
        productRepository.fetchProducts(products -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                hideAllStates();
                if (products != null && !products.isEmpty()) {
                    fullProductList = new ArrayList<>(products);
                    setupCategorySpinner(products);
                    applyFilters();
                    rvProducts.setVisibility(View.VISIBLE);
                } else {
                    showEmptyState("No products found");
                }
            });
        }, error -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                hideAllStates();
                if (fullProductList.isEmpty()) {
                    // No cached data — show retry
                    showErrorState("Connection issue. The server may still be waking up.\n" + error);
                } else {
                    // Have cached data already shown — just keep it
                    rvProducts.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // ===========================
    // UI STATE HELPERS
    // ===========================

    private void showLoadingState() {
        progressProducts.setVisibility(View.VISIBLE);
        layoutLoadingState.setVisibility(View.VISIBLE);
        layoutErrorState.setVisibility(View.GONE);
        layoutEmptyProducts.setVisibility(View.GONE);
        // Don't hide rvProducts if we have cached data to show
    }

    private void hideAllStates() {
        progressProducts.setVisibility(View.GONE);
        layoutLoadingState.setVisibility(View.GONE);
        layoutErrorState.setVisibility(View.GONE);
        layoutEmptyProducts.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        layoutErrorState.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.GONE);
        if (tvErrorMessage != null) tvErrorMessage.setText(message);
    }

    private void showEmptyState(String message) {
        layoutEmptyProducts.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.GONE);
    }

    // ===========================
    // CATEGORY CHIPS (DYNAMIC)
    // ===========================

    private void setupCategorySpinner(List<Product> products) {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        
        List<ProductResponse.CategoryItem> apiCategories = productRepository.getLastKnownCategories();
        if (apiCategories != null) {
            for (ProductResponse.CategoryItem item : apiCategories) {
                if (item != null && item.getName() != null && !item.getName().trim().isEmpty()) {
                    categories.add(capitalize(item.getName().trim()));
                }
            }
        }
        
        if (products != null) {
            for (Product p : products) {
                if (p != null && p.getCategory() != null && !p.getCategory().trim().isEmpty()) {
                    categories.add(capitalize(p.getCategory().trim()));
                }
            }
        }
        
        List<String> categoryList = new ArrayList<>(categories);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, categoryList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(spinnerAdapter);
        
        spinnerCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                currentCategoryFilter = categoryList.get(position);
                applyFilters();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // ===========================
    // SEARCH & FILTER
    // ===========================

    private void setupSearch() {
        searchViewProducts.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query.toLowerCase().trim();
                applyFilters();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText.toLowerCase().trim();
                applyFilters();
                return false;
            }
        });

        chipGroupSkinTypeFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = group.getCheckedChipId();
            if (checkedId == R.id.chipFilterAll) currentSkinTypeFilter = "All";
            else if (checkedId == R.id.chipFilterOily) currentSkinTypeFilter = "Oily";
            else if (checkedId == R.id.chipFilterDry) currentSkinTypeFilter = "Dry";
            else if (checkedId == R.id.chipFilterCombo) currentSkinTypeFilter = "Combination";
            else if (checkedId == R.id.chipFilterSensitive) currentSkinTypeFilter = "Sensitive";
            else currentSkinTypeFilter = "All";
            applyFilters();
        });
    }

    /**
     * Filter products by search query + selected category chip.
     *
     * "No products found" is ONLY shown when:
     * - API loaded successfully AND
     * - active filter/search produces no matching results
     *
     * It is NOT shown during loading or API failure.
     */
    private void applyFilters() {
        List<Product> filteredList = new ArrayList<>();
        for (Product product : fullProductList) {
            boolean matchesSearch = true;
            boolean matchesCategory = true;
            boolean matchesSkinType = true;

            if (!currentSearchQuery.isEmpty()) {
                String name = product.getName() != null ? product.getName().toLowerCase() : "";
                String cat  = product.getCategory() != null ? product.getCategory().toLowerCase() : "";
                String desc = product.getDescriptionShort() != null ? product.getDescriptionShort().toLowerCase() : "";
                matchesSearch = name.contains(currentSearchQuery)
                        || cat.contains(currentSearchQuery)
                        || desc.contains(currentSearchQuery);
            }

            if (!"All".equalsIgnoreCase(currentCategoryFilter)) {
                String cat = product.getCategory() != null ? product.getCategory() : "";
                String catSlug = product.getCategorySlug() != null ? product.getCategorySlug() : "";
                matchesCategory = cat.equalsIgnoreCase(currentCategoryFilter)
                        || catSlug.equalsIgnoreCase(currentCategoryFilter)
                        || cat.toLowerCase().contains(currentCategoryFilter.toLowerCase());
            }
            
            if (!"All".equalsIgnoreCase(currentSkinTypeFilter)) {
                String skinTarget = product.getSkinTypeTarget() != null ? product.getSkinTypeTarget() : "";
                String normalized = skinTarget.toLowerCase();
                String selected = currentSkinTypeFilter.toLowerCase();
                if ("combination".equals(selected)) {
                    matchesSkinType = normalized.contains("combination") || normalized.contains("combo");
                } else {
                    matchesSkinType = normalized.contains(selected);
                }
            }

            if (matchesSearch && matchesCategory && matchesSkinType) {
                filteredList.add(product);
            }
        }

        if (filteredList.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            layoutEmptyProducts.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            layoutEmptyProducts.setVisibility(View.GONE);
            adapter.updateData(filteredList);
        }
    }
}
