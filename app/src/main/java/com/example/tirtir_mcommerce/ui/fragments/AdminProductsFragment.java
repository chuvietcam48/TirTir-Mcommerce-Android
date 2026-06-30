package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.AdminProductAdapter;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProductsFragment extends Fragment implements AdminProductAdapter.OnAdminProductActionListener {

    private RecyclerView rvProducts;
    private ProgressBar pbProducts;
    private TextView tvProductCount;
    private EditText etSearch;
    private ChipGroup cgCategory;
    private FloatingActionButton fabAddProduct;

    private AdminProductAdapter adapter;
    private ApiService apiService;
    private final List<Product> allProducts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_products, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        apiService = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        
        rvProducts = view.findViewById(R.id.rvAdminProducts);
        pbProducts = view.findViewById(R.id.pbAdminProducts);
        tvProductCount = view.findViewById(R.id.tvAdminProductCount);
        etSearch = view.findViewById(R.id.etAdminSearchProduct);
        cgCategory = view.findViewById(R.id.cgAdminCategory);
        fabAddProduct = view.findViewById(R.id.fabAddProduct);

        rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminProductAdapter(requireContext(), new ArrayList<>(), this);
        rvProducts.setAdapter(adapter);

        setupListeners();
        loadProducts();
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        cgCategory.setOnCheckedChangeListener((group, checkedId) -> filterProducts());

        fabAddProduct.setOnClickListener(v -> {
            // Open editor dialog for new product
            AdminProductEditorDialog dialog = AdminProductEditorDialog.newInstance(null);
            dialog.show(getChildFragmentManager(), "AdminProductEditor");
        });
    }

    private void loadProducts() {
        if (!isAdded()) return;
        pbProducts.setVisibility(View.VISIBLE);
        apiService.getProducts(1000, System.currentTimeMillis()).enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                if (!isAdded()) return;
                pbProducts.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> products = response.body().getData();
                    if (products != null) {
                        allProducts.clear();
                        allProducts.addAll(products);
                        filterProducts(); // initial render
                    }
                } else {
                    Toast.makeText(requireContext(), "Unable to load products", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                if (!isAdded()) return;
                pbProducts.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterProducts() {
        String query = etSearch.getText().toString().trim().toLowerCase(java.util.Locale.ENGLISH);
        
        // Determine category from chips (assuming hardcoded layout for now)
        String category = "All";
        int checkedId = cgCategory.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            com.google.android.material.chip.Chip chip = cgCategory.findViewById(checkedId);
            if (chip != null) category = chip.getText().toString();
        }

        List<Product> filtered = new ArrayList<>();
        for (Product product : allProducts) {
            boolean matchesSearch = true;
            boolean matchesCat = true;

            if (!query.isEmpty()) {
                String name = product.getName() == null ? "" : product.getName().toLowerCase(java.util.Locale.ENGLISH);
                String sku = product.getProductId() == null ? "" : product.getProductId().toLowerCase(java.util.Locale.ENGLISH);
                matchesSearch = name.contains(query) || sku.contains(query);
            }

            if (!category.equalsIgnoreCase("All")) {
                String pCat = product.getCategory() == null ? "" : product.getCategory();
                matchesCat = pCat.equalsIgnoreCase(category) || pCat.toLowerCase(java.util.Locale.ENGLISH).contains(category.toLowerCase(java.util.Locale.ENGLISH));
            }

            if (matchesSearch && matchesCat) {
                filtered.add(product);
            }
        }

        tvProductCount.setText(category.toUpperCase() + " (" + filtered.size() + ")");
        adapter.updateData(filtered);
    }

    @Override
    public void onEdit(Product product) {
        AdminProductEditorDialog dialog = AdminProductEditorDialog.newInstance(product);
        dialog.show(getChildFragmentManager(), "AdminProductEditor");
    }
}
