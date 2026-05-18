package com.example.tirtir_mcommerce;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Import 3 file mà bạn đã tạo ở các bước trước
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Đoạn này của bạn giữ nguyên để giao diện tràn viền đẹp
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ==========================================
        // BẮT ĐẦU GỌI API LẤY DỮ LIỆU TỪ MONGODB VỀ
        // ==========================================
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        apiService.getProducts().enqueue(new Callback<ProductResponse>() {
            @Override
            public void onResponse(Call<ProductResponse> call, Response<ProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // KHUI THÙNG NÈ: Lấy cái ruột "data" ra và nhét vào List
                    List<Product> productList = response.body().getData();

                    if (productList != null && !productList.isEmpty()) {
                        Log.d("TIRTIR_API", "🎉 Thành công! Sản phẩm đầu tiên là: " + productList.get(0).getName());
                        Log.d("TIRTIR_API", "📦 Tổng số sản phẩm lấy được: " + productList.size());
                    }
                } else {
                    Log.e("TIRTIR_API", "⚠️ Web từ chối! Mã lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ProductResponse> call, Throwable t) {
                Log.e("TIRTIR_API", "❌ Lỗi: " + t.getMessage());
            }
        });
    }
}
