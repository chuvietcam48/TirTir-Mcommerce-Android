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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_product_list);

        Toolbar toolbar = findViewById(R.id.toolbarAdminProducts);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Quản lý sản phẩm");
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
        // Simple search filtering
        List<Product> filtered = new ArrayList<>();
        // In real app, we might search via API or filter current list
        // For S2.2 UI, we just toast or filter if list is already loaded
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
                        adapter.updateData(products);
                    }
                } else {
                    Toast.makeText(AdminProductListActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                progressAdminProducts.setVisibility(View.GONE);
                Toast.makeText(AdminProductListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onEdit(Product product) {
        Intent intent = new Intent(this, AdminProductFormActivity.class);
        intent.putExtra("PRODUCT_ID", product.getId());
        intent.putExtra("PRODUCT_NAME", product.getName());
        intent.putExtra("PRODUCT_PRICE", product.getPrice());
        intent.putExtra("PRODUCT_DESC", product.getDescriptionShort());
        intent.putExtra("PRODUCT_CATEGORY", product.getCategory());
        intent.putExtra("PRODUCT_IMAGE", product.getThumbnailImages());
        startActivity(intent);
    }

    @Override
    public void onDelete(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc muốn xóa sản phẩm " + product.getName() + "?")
                .setPositiveButton("Xóa", (dialog, which) -> executeDelete(product.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void executeDelete(String productId) {
        progressAdminProducts.setVisibility(View.VISIBLE);
        String pid = productId;
        apiService.deleteProduct(pid).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AdminProductListActivity.this, "Xóa thành công", Toast.LENGTH_SHORT).show();
                    loadProducts();
                } else {
                    progressAdminProducts.setVisibility(View.GONE);
                    Toast.makeText(AdminProductListActivity.this, "Xóa thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                progressAdminProducts.setVisibility(View.GONE);
                Toast.makeText(AdminProductListActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onToggleActive(Product product, boolean isActive) {
        apiService.toggleProductActive(product.getId()).enqueue(new Callback<ApiResponse<Product>>() {
            @Override
            public void onResponse(Call<ApiResponse<Product>> call, Response<ApiResponse<Product>> response) {
                if (!response.isSuccessful()) {
                    Toast.makeText(AdminProductListActivity.this, "Thay đổi trạng thái thất bại", Toast.LENGTH_SHORT).show();
                    loadProducts(); // revert state
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Product>> call, Throwable t) {
                Toast.makeText(AdminProductListActivity.this, "Lỗi mạng", Toast.LENGTH_SHORT).show();
                loadProducts(); // revert state
            }
        });
    }
}
