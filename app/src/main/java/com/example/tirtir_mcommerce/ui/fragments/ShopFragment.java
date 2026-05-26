package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ShopFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private DatabaseHelper dbHelper;
    private List<Product> productList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shop, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerViewProducts);
        
        // Khởi tạo Adapter với mảng rỗng trước
        adapter = new ProductAdapter(getContext(), productList);
        recyclerView.setAdapter(adapter);

        dbHelper = DatabaseHelper.getInstance(getContext());

        fetchProducts();

        return view;
    }

    private void fetchProducts() {
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        apiService.getProducts().enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> newProducts = response.body().getData();
                    if (newProducts != null && !newProducts.isEmpty()) {
                        // Cập nhật giao diện
                        adapter.updateData(newProducts);
                        
                        // Lưu xuống Database SQLite để dành xài offline
                        try {
                            dbHelper.insertProducts(newProducts);
                        } catch (Exception e) {
                            Log.e("SHOP", "Lỗi lưu SQLite: " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Lỗi mạng, đang lấy dữ liệu Offline", Toast.LENGTH_SHORT).show();
                
                // Lấy dữ liệu từ SQLite nếu rớt mạng
                List<Product> offlineData = dbHelper.getAllProducts();
                if (offlineData != null && !offlineData.isEmpty()) {
                    adapter.updateData(offlineData);
                } else {
                    Toast.makeText(getContext(), "Không có dữ liệu Offline", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
