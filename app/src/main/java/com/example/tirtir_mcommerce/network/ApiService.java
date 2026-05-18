package com.example.tirtir_mcommerce.network;

import com.example.tirtir_mcommerce.model.ProductResponse; // Chú ý import cái mới
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("api/v1/products") // Link của bạn đã chuẩn xác!
    Call<ProductResponse> getProducts(); // Đổi List<Product> thành ProductResponse
}