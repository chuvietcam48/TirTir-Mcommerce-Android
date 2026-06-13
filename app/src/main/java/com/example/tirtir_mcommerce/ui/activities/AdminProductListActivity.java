package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.AdminProductAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProductListActivity extends AppCompatActivity implements AdminProductAdapter.OnAdminProductActionListener {

    private RecyclerView rvAdminProducts;
    private ProgressBar progressAdminProducts;
    private FloatingActionButton fabAddProduct;
    private AdminProductAdapter adapter;
    private ApiService apiService;
    private final List<Product> allProducts = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_product_list);

        Toolbar toolbar = findViewById(R.id.toolbarAdminProducts);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Product Management");
        }

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);

        rvAdminProducts = findViewById(R.id.rvAdminProducts);
        progressAdminProducts = findViewById(R.id.progressAdminProducts);
        fabAddProduct = findViewById(R.id.fabAddProduct);
        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.searchViewAdminProducts);

        rvAdminProducts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminProductAdapter(this, new ArrayList<>(), this);
        rvAdminProducts.setAdapter(adapter);

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterProducts(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterProducts(newText);
                return true;
            }
        });

        fabAddProduct.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminProductFormActivity.class);
            startActivity(intent);
        });
    }

    private void filterProducts(String query) {
        List<Product> filtered = new ArrayList<>();
        String normalized = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ENGLISH);
        for (Product product : allProducts) {
            String name = product.getName() == null ? "" : product.getName();
            String category = product.getCategory() == null ? "" : product.getCategory();
            String sku = product.getProductId() == null ? "" : product.getProductId();
            if (normalized.isEmpty()
                    || name.toLowerCase(java.util.Locale.ENGLISH).contains(normalized)
                    || category.toLowerCase(java.util.Locale.ENGLISH).contains(normalized)
                    || sku.toLowerCase(java.util.Locale.ENGLISH).contains(normalized)) {
                filtered.add(product);
            }
        }
        adapter.updateData(filtered);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void loadProducts() {
        progressAdminProducts.setVisibility(View.VISIBLE);
        apiService.getProducts(1000, System.currentTimeMillis()).enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                progressAdminProducts.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> products = response.body().getData();
                    if (products != null) {
                        allProducts.clear();
                        allProducts.addAll(products);
                        adapter.updateData(new ArrayList<>(allProducts));
                    }
                } else {
                    Toast.makeText(AdminProductListActivity.this, "Unable to load products", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                progressAdminProducts.setVisibility(View.GONE);
                Toast.makeText(AdminProductListActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onEdit(Product product) {
        Intent intent = new Intent(this, AdminProductFormActivity.class);
        intent.putExtra("PRODUCT_ID", product.getId());
        intent.putExtra("PRODUCT_SKU", product.getProductId());
        intent.putExtra("PRODUCT_NAME", product.getName());
        intent.putExtra("PRODUCT_PRICE", product.getPrice());
        intent.putExtra("PRODUCT_STOCK", product.getStockQuantity());
        intent.putExtra("PRODUCT_DESC", product.getDescriptionShort());
        intent.putExtra("PRODUCT_CATEGORY", product.getCategory());
        intent.putExtra("PRODUCT_IMAGE", product.getThumbnailImages());
        startActivity(intent);
    }

    @Override
    public void onDelete(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Deactivate product")
                .setMessage("Deactivate " + product.getName() + "?")
                .setPositiveButton("Deactivate", (dialog, which) -> executeDelete(product.getId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeDelete(String productId) {
        progressAdminProducts.setVisibility(View.VISIBLE);
        String pid = productId;
        apiService.deleteProduct(pid).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AdminProductListActivity.this, "Product deactivated", Toast.LENGTH_SHORT).show();
                    loadProducts();
                } else {
                    progressAdminProducts.setVisibility(View.GONE);
                    Toast.makeText(AdminProductListActivity.this, "Unable to deactivate product", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                progressAdminProducts.setVisibility(View.GONE);
                Toast.makeText(AdminProductListActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onToggleActive(Product product, boolean isActive) {
        apiService.toggleProductActive(product.getId()).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(AdminProductListActivity.this, "Unable to update product status", Toast.LENGTH_SHORT).show();
                    loadProducts(); // revert state
                }
            }

            @Override
            public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                Toast.makeText(AdminProductListActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                loadProducts(); // revert state
            }
        });
    }
}
