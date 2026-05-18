package com.example.tirtir_mcommerce.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // NẾU CHẠY MÁY ẢO ANDROID STUDIO (Emulator): Dùng 10.0.2.2 thay cho localhost
    // Chú ý: Đổi số 5001 thành cái cổng mà Web Node.js cũ của bạn đang chạy
    private static final String BASE_URL = "http://10.0.2.2:5001/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}