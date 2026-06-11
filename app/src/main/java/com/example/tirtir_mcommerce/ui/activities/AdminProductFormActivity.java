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

import java.util.ArrayList;
import java.util.List;

public class AdminProductFormActivity extends AppCompatActivity {

    private TextInputEditText etProductName, etProductPrice, etProductBrand, etProductStock, etProductShadeHex, etProductDescription, etProductIngredients;
    private View viewShadePreview;
    private android.widget.Spinner spinnerCategory;
    private com.google.android.material.chip.ChipGroup chipGroupSkinType;
    private MaterialButton btnSaveProduct, btnDeleteProduct, btnSelectImages;
    private ProgressBar progressProductForm;

    private String productId = null;
    private List<Uri> selectedImageUris = new ArrayList<>();
    private ApiService apiService;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    if (result.getData().getClipData() != null) {
                        int count = result.getData().getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            selectedImageUris.add(result.getData().getClipData().getItemAt(i).getUri());
                        }
                    } else if (result.getData().getData() != null) {
                        selectedImageUris.add(result.getData().getData());
                    }
                    updateImageGrid();
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

        bindViews();
        setupCategorySpinner();
        setupShadePreview();

        // Check if editing
        if (getIntent().hasExtra("PRODUCT_ID")) {
            productId = getIntent().getStringExtra("PRODUCT_ID");
            loadProductData();
            getSupportActionBar().setTitle("Sửa sản phẩm");
            btnDeleteProduct.setVisibility(View.VISIBLE);
        } else {
            getSupportActionBar().setTitle("Thêm sản phẩm mới");
            btnDeleteProduct.setVisibility(View.GONE);
        }

        btnSelectImages.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            imagePickerLauncher.launch(Intent.createChooser(intent, "Chọn ảnh sản phẩm"));
        });

        btnSaveProduct.setOnClickListener(v -> saveProduct());
        btnDeleteProduct.setOnClickListener(v -> confirmDelete());
    }

    private void bindViews() {
        etProductName = findViewById(R.id.etProductName);
        etProductBrand = findViewById(R.id.etProductBrand);
        etProductPrice = findViewById(R.id.etProductPrice);
        etProductStock = findViewById(R.id.etProductStock);
        etProductShadeHex = findViewById(R.id.etProductShadeHex);
        etProductDescription = findViewById(R.id.etProductDescription);
        etProductIngredients = findViewById(R.id.etProductIngredients);
        viewShadePreview = findViewById(R.id.viewShadePreview);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        chipGroupSkinType = findViewById(R.id.chipGroupSkinType);
        btnSaveProduct = findViewById(R.id.btnSaveProduct);
        btnDeleteProduct = findViewById(R.id.btnDeleteProduct);
        btnSelectImages = findViewById(R.id.btnSelectImages);
        progressProductForm = findViewById(R.id.progressProductForm);
    }

    private void setupCategorySpinner() {
        String[] categories = {"Toner", "Serum", "Cream", "Cushion", "Mask", "Others"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    private void setupShadePreview() {
        etProductShadeHex.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int color = android.graphics.Color.parseColor(s.toString());
                    viewShadePreview.setBackgroundColor(color);
                } catch (Exception e) {
                    viewShadePreview.setBackgroundColor(android.graphics.Color.LTGRAY);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void loadProductData() {
        etProductName.setText(getIntent().getStringExtra("PRODUCT_NAME"));
        etProductPrice.setText(String.valueOf(getIntent().getDoubleExtra("PRODUCT_PRICE", 0)));
        // Mock loading for brand, stock etc if needed
    }

    private void updateImageGrid() {
        androidx.gridlayout.widget.GridLayout grid = findViewById(R.id.gridProductImages);
        grid.removeAllViews();
        for (Uri uri : selectedImageUris) {
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new android.view.ViewGroup.LayoutParams(250, 250));
            iv.setPadding(8, 8, 8, 8);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(uri).into(iv);
            grid.addView(iv);
            if (grid.getChildCount() >= 6) break;
        }
    }

    private void saveProduct() {
        String name = etProductName.getText().toString().trim();
        String price = etProductPrice.getText().toString().trim();

        if (name.isEmpty() || price.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên và giá", Toast.LENGTH_SHORT).show();
            return;
        }

        progressProductForm.setVisibility(View.VISIBLE);
        btnSaveProduct.setEnabled(false);

        // Simulated save logic for UI Task
        new android.os.Handler().postDelayed(() -> {
            progressProductForm.setVisibility(View.GONE);
            btnSaveProduct.setEnabled(true);
            Toast.makeText(this, "Đã lưu sản phẩm (Simulated)", Toast.LENGTH_SHORT).show();
            finish();
        }, 1500);
    }

    private void confirmDelete() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này?")
                .setPositiveButton("Xóa", (dialog, which) -> finish())
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
