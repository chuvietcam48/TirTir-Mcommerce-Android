package com.example.tirtir_mcommerce.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProductFormActivity extends AppCompatActivity {

    private ImageView ivProductImagePicker;
    private TextInputEditText etProductName, etProductPrice, etProductCategory, etProductDescription;
    private MaterialButton btnSaveProduct;
    private ProgressBar progressProductForm;

    private String productId = null;
    private Uri selectedImageUri = null;
    private ApiService apiService;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    Glide.with(this).load(selectedImageUri).into(ivProductImagePicker);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_product_form);

        Toolbar toolbar = findViewById(R.id.toolbarAdminProductForm);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);

        ivProductImagePicker = findViewById(R.id.ivProductImagePicker);
        etProductName = findViewById(R.id.etProductName);
        etProductPrice = findViewById(R.id.etProductPrice);
        etProductCategory = findViewById(R.id.etProductCategory);
        etProductDescription = findViewById(R.id.etProductDescription);
        btnSaveProduct = findViewById(R.id.btnSaveProduct);
        progressProductForm = findViewById(R.id.progressProductForm);

        // Check if editing
        if (getIntent().hasExtra("PRODUCT_ID")) {
            productId = getIntent().getStringExtra("PRODUCT_ID");
            etProductName.setText(getIntent().getStringExtra("PRODUCT_NAME"));
            etProductPrice.setText(String.valueOf(getIntent().getDoubleExtra("PRODUCT_PRICE", 0)));
            etProductCategory.setText(getIntent().getStringExtra("PRODUCT_CATEGORY"));
            etProductDescription.setText(getIntent().getStringExtra("PRODUCT_DESC"));
            
            String imageUrl = getIntent().getStringExtra("PRODUCT_IMAGE");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                if (!imageUrl.startsWith("http")) imageUrl = "https://tirtir-project.onrender.com/" + imageUrl;
                Glide.with(this).load(imageUrl).into(ivProductImagePicker);
            }
            getSupportActionBar().setTitle("Sửa sản phẩm");
        } else {
            getSupportActionBar().setTitle("Thêm sản phẩm mới");
        }

        ivProductImagePicker.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        btnSaveProduct.setOnClickListener(v -> saveProduct());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void saveProduct() {
        String name = etProductName.getText() != null ? etProductName.getText().toString().trim() : "";
        String priceStr = etProductPrice.getText() != null ? etProductPrice.getText().toString().trim() : "0";
        String category = etProductCategory.getText() != null ? etProductCategory.getText().toString().trim() : "";
        String desc = etProductDescription.getText() != null ? etProductDescription.getText().toString().trim() : "";

        if (name.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên và giá", Toast.LENGTH_SHORT).show();
            return;
        }

        progressProductForm.setVisibility(View.VISIBLE);
        btnSaveProduct.setEnabled(false);

        Map<String, RequestBody> data = new HashMap<>();
        data.put("name", RequestBody.create(MediaType.parse("text/plain"), name));
        data.put("price", RequestBody.create(MediaType.parse("text/plain"), priceStr));
        data.put("category", RequestBody.create(MediaType.parse("text/plain"), category));
        data.put("descriptionShort", RequestBody.create(MediaType.parse("text/plain"), desc));

        MultipartBody.Part imagePart = null;
        if (selectedImageUri != null) {
            try {
                InputStream is = getContentResolver().openInputStream(selectedImageUri);
                if (is != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] dataBytes = new byte[16384];
                    while ((nRead = is.read(dataBytes, 0, dataBytes.length)) != -1) {
                        buffer.write(dataBytes, 0, nRead);
                    }
                    byte[] bytes = buffer.toByteArray();
                    RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), bytes);
                    imagePart = MultipartBody.Part.createFormData("image", "upload.jpg", reqFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Callback<ApiResponse<Product>> callback = new Callback<ApiResponse<Product>>() {
            @Override
            public void onResponse(Call<ApiResponse<Product>> call, Response<ApiResponse<Product>> response) {
                progressProductForm.setVisibility(View.GONE);
                btnSaveProduct.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminProductFormActivity.this, "Lưu thành công", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AdminProductFormActivity.this, "Lưu thất bại: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Product>> call, Throwable t) {
                progressProductForm.setVisibility(View.GONE);
                btnSaveProduct.setEnabled(true);
                Toast.makeText(AdminProductFormActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        };

        if (productId == null) {
            // Create
            if (imagePart == null) {
                // Must have image for new product in this simple implementation
                // Actually, API might allow without, we just pass null
            }
            apiService.createProduct(imagePart, data).enqueue(callback);
        } else {
            // Update
            apiService.updateProduct(productId, imagePart, data).enqueue(callback);
        }
    }
}
