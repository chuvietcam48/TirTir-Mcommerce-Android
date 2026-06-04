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
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
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
    private ChipGroup chipGroupCategory;

    private ProductRepository productRepository;
    private List<Product> fullProductList = new ArrayList<>();
    private String currentSearchQuery = "";
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
        chipGroupCategory    = view.findViewById(R.id.chipGroupCategory);

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
            startActivity(intent);
        });
        rvProducts.setAdapter(adapter);

        productRepository = new ProductRepository(getContext());

        setupSearch();
        loadProducts();

        btnRetry.setOnClickListener(v -> loadProducts());

        return view;
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
                    buildCategoryChipsFromProducts(products);
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
                    showErrorState("Could not load products.\n" + error);
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

    /**
     * Builds category filter chips DYNAMICALLY from loaded product data.
     * Source priority:
     * 1. productRepository.getLastKnownCategories() — from API categories[] field
     * 2. Unique Category values from product list
     *
     * Does NOT hardcode any category names.
     * Always includes "All" chip first.
     */
    private void buildCategoryChipsFromProducts(List<Product> products) {
        if (chipGroupCategory == null) return;
        chipGroupCategory.removeAllViews();

        // "All" chip — always first
        Chip allChip = createChip("All", CHIP_ID_BASE);
        allChip.setChecked(true);
        chipGroupCategory.addView(allChip);

        // Collect unique Category values from products
        Set<String> categories = new LinkedHashSet<>();

        // Priority 1: categories[] from API response
        List<ProductResponse.CategoryItem> apiCategories = productRepository.getLastKnownCategories();
        if (apiCategories != null) {
            for (ProductResponse.CategoryItem item : apiCategories) {
                String name = item.getName();
                if (name != null && !name.isEmpty()) {
                    // Capitalize first letter for display
                    categories.add(capitalize(name));
                }
            }
        }

        // Priority 2: Extract from Product.Category field (broad categories like Skincare, Makeup)
        for (Product p : products) {
            String cat = p.getCategory();
            if (cat != null && !cat.isEmpty()) {
                categories.add(cat);
            }
        }

        // Build chips (skip if already seen from API categories)
        int chipId = CHIP_ID_BASE + 1;
        for (String category : categories) {
            Chip chip = createChip(category, chipId++);
            chipGroupCategory.addView(chip);
        }
    }

    private Chip createChip(String label, int id) {
        Chip chip = new Chip(requireContext());
        chip.setId(id);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setChipBackgroundColorResource(R.color.chip_selector);
        chip.setTextColor(getResources().getColorStateList(R.color.chip_text_selector, null));
        chip.setRippleColorResource(R.color.tirtir_red_primary);
        return chip;
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

        chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
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
        // Determine selected category
        int checkedChipId = chipGroupCategory.getCheckedChipId();
        if (checkedChipId != View.NO_ID) {
            Chip chip = chipGroupCategory.findViewById(checkedChipId);
            if (chip != null) {
                currentCategoryFilter = chip.getText().toString();
            }
        } else {
            currentCategoryFilter = "All";
        }

        List<Product> filteredList = new ArrayList<>();
        for (Product product : fullProductList) {
            boolean matchesSearch = true;
            boolean matchesCategory = true;

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

            if (matchesSearch && matchesCategory) {
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
