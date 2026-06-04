package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.google.android.material.chip.ChipGroup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView rvProducts;
    private ProductAdapter adapter;
    private ProgressBar progressProducts;
    private LinearLayout layoutEmptyProducts;
    private SearchView searchViewProducts;
    private ChipGroup chipGroupCategory;

    private ProductRepository productRepository;
    private List<Product> fullProductList = new ArrayList<>();
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        rvProducts = view.findViewById(R.id.rvProducts);
        progressProducts = view.findViewById(R.id.progressProducts);
        layoutEmptyProducts = view.findViewById(R.id.layoutEmptyProducts);
        searchViewProducts = view.findViewById(R.id.searchViewProducts);
        chipGroupCategory = view.findViewById(R.id.chipGroupCategory);

        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));

        adapter = new ProductAdapter(getContext(), new ArrayList<>(), product -> {
            Intent intent = new Intent(getContext(), ProductDetailActivity.class);
            intent.putExtra("PRODUCT_ID", product.getProductId() != null ? product.getProductId() : product.getId());
            intent.putExtra("PRODUCT_NAME", product.getName());
            intent.putExtra("PRODUCT_PRICE", product.getPrice());
            intent.putExtra("PRODUCT_CATEGORY", product.getCategory());
            intent.putExtra("PRODUCT_IMAGE", product.getThumbnailImages());
            intent.putExtra("PRODUCT_SKIN_TYPES", product.getSkinTypeTarget());
            intent.putExtra("PRODUCT_INGREDIENTS", product.getKeyIngredients());
            intent.putExtra("PRODUCT_DESCRIPTION", product.getDescriptionShort());
            intent.putExtra("PRODUCT_STOCK", product.getStockQuantity());
            startActivity(intent);
        });
        rvProducts.setAdapter(adapter);

        productRepository = new ProductRepository(getContext());

        setupSearchAndFilter();
        loadProducts();

        return view;
    }

    private void loadProducts() {
        progressProducts.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.GONE);
        layoutEmptyProducts.setVisibility(View.GONE);

        productRepository.fetchProducts(products -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                progressProducts.setVisibility(View.GONE);
                if (products != null && !products.isEmpty()) {
                    fullProductList = products;
                    applyFilters();
                } else {
                    layoutEmptyProducts.setVisibility(View.VISIBLE);
                }
            });
        }, error -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                progressProducts.setVisibility(View.GONE);
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                layoutEmptyProducts.setVisibility(View.VISIBLE);
            });
        });
    }

    private void setupSearchAndFilter() {
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

        chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            applyFilters();
        });
    }

    private void applyFilters() {
        List<Product> filteredList = new ArrayList<>();
        
        // Let's get the selected chip text properly
        int checkedChipId = chipGroupCategory.getCheckedChipId();
        if (checkedChipId != View.NO_ID) {
            com.google.android.material.chip.Chip chip = chipGroupCategory.findViewById(checkedChipId);
            if (chip != null) {
                currentCategoryFilter = chip.getText().toString();
            }
        } else {
            currentCategoryFilter = "All";
        }

        for (Product product : fullProductList) {
            boolean matchesSearch = true;
            boolean matchesCategory = true;

            if (!currentSearchQuery.isEmpty()) {
                String name = product.getName() != null ? product.getName().toLowerCase() : "";
                String cat = product.getCategory() != null ? product.getCategory().toLowerCase() : "";
                matchesSearch = name.contains(currentSearchQuery) || cat.contains(currentSearchQuery);
            }

            if (!currentCategoryFilter.equalsIgnoreCase("All")) {
                String cat = product.getCategory() != null ? product.getCategory() : "";
                matchesCategory = cat.equalsIgnoreCase(currentCategoryFilter) || cat.contains(currentCategoryFilter);
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
