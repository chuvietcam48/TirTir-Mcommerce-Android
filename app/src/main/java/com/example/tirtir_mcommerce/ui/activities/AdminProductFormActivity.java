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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
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
            getSupportActionBar().setTitle("Edit Product");
            btnDeleteProduct.setVisibility(View.VISIBLE);
        } else {
            getSupportActionBar().setTitle("Add Product");
            btnDeleteProduct.setVisibility(View.GONE);
        }

        btnSelectImages.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            imagePickerLauncher.launch(Intent.createChooser(intent, "Choose product images"));
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
        etProductStock.setText(String.valueOf(getIntent().getIntExtra("PRODUCT_STOCK", 0)));
        etProductDescription.setText(getIntent().getStringExtra("PRODUCT_DESC"));
    }

    private void updateImageGrid() {
        android.widget.GridLayout grid = findViewById(R.id.gridProductImages);
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
            Toast.makeText(this, "Product name and price are required", Toast.LENGTH_SHORT).show();
            return;
        }

        String stock = etProductStock.getText() == null ? "" : etProductStock.getText().toString().trim();
        if (stock.isEmpty()) {
            etProductStock.setError("Stock quantity is required");
            return;
        }

        progressProductForm.setVisibility(View.VISIBLE);
        btnSaveProduct.setEnabled(false);

        Map<String, RequestBody> fields = buildProductFields(name, price, stock);
        MultipartBody.Part thumbnail = buildThumbnailPart();
        Call<Product> request = productId == null
                ? apiService.createProduct(thumbnail, fields)
                : apiService.updateProduct(productId, thumbnail, fields);

        request.enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> call, Response<Product> response) {
                setSaving(false);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminProductFormActivity.this,
                            productId == null ? "Product created" : "Product updated",
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AdminProductFormActivity.this,
                            "Unable to save product (" + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Product> call, Throwable t) {
                setSaving(false);
                Toast.makeText(AdminProductFormActivity.this,
                        "Connection error. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Deactivate product")
                .setMessage("This product will be hidden from the storefront.")
                .setPositiveButton("Deactivate", (dialog, which) -> deleteProduct())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Map<String, RequestBody> buildProductFields(String name, String price, String stock) {
        Map<String, RequestBody> fields = new LinkedHashMap<>();
        String category = String.valueOf(spinnerCategory.getSelectedItem());
        String generatedProductId = productId == null
                ? "APP-" + System.currentTimeMillis()
                : getIntent().getStringExtra("PRODUCT_SKU");

        if (generatedProductId != null && !generatedProductId.isEmpty()) {
            fields.put("Product_ID", textBody(generatedProductId));
        }
        fields.put("Name", textBody(name));
        fields.put("Price", textBody(price));
        fields.put("Category", textBody(category));
        fields.put("Stock_Quantity", textBody(stock));
        fields.put("Description_Short", textBody(valueOf(etProductDescription)));
        fields.put("Full_Description", textBody(valueOf(etProductDescription)));
        fields.put("Key_Ingredients", textBody(valueOf(etProductIngredients)));
        fields.put("Brand", textBody(valueOf(etProductBrand)));
        fields.put("Skin_Type_Target", textBody(selectedSkinTypes()));
        fields.put("isActive", textBody("true"));
        return fields;
    }

    private RequestBody textBody(String value) {
        return RequestBody.create(MediaType.parse("text/plain"),
                value == null ? "" : value);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String selectedSkinTypes() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < chipGroupSkinType.getChildCount(); i++) {
            View child = chipGroupSkinType.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip
                    && ((com.google.android.material.chip.Chip) child).isChecked()) {
                selected.add(((com.google.android.material.chip.Chip) child).getText().toString());
            }
        }
        return android.text.TextUtils.join(", ", selected);
    }

    private MultipartBody.Part buildThumbnailPart() {
        if (selectedImageUris.isEmpty()) return null;
        Uri uri = selectedImageUris.get(0);
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return null;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            String mime = getContentResolver().getType(uri);
            RequestBody body = RequestBody.create(
                    MediaType.parse(mime == null ? "image/jpeg" : mime),
                    output.toByteArray());
            return MultipartBody.Part.createFormData(
                    "thumbnail",
                    "product-" + System.currentTimeMillis() + ".jpg",
                    body);
        } catch (Exception error) {
            Toast.makeText(this, "Unable to read the selected image", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void deleteProduct() {
        if (productId == null) return;
        setSaving(true);
        apiService.deleteProduct(productId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                setSaving(false);
                if (response.isSuccessful()) {
                    Toast.makeText(AdminProductFormActivity.this, "Product deactivated", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AdminProductFormActivity.this, "Unable to deactivate product", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                setSaving(false);
                Toast.makeText(AdminProductFormActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setSaving(boolean saving) {
        progressProductForm.setVisibility(saving ? View.VISIBLE : View.GONE);
        btnSaveProduct.setEnabled(!saving);
        btnDeleteProduct.setEnabled(!saving);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
