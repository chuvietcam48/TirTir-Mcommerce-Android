package com.example.tirtir_mcommerce.ui.fragments;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProductEditorDialog extends BottomSheetDialogFragment {

    private Product product;
    private ApiService apiService;

    private EditText etName, etCategory, etPrice, etStock, etShadeHex, etDesc;
    private SwitchCompat switchActive;
    private View viewShadePreview;
    private TextView tvTitle;
    private ImageButton btnDelete, btnClose;
    private Button btnSave, btnDiscard;

    public static AdminProductEditorDialog newInstance(Product product) {
        AdminProductEditorDialog fragment = new AdminProductEditorDialog();
        Bundle args = new Bundle();
        if (product != null) {
            args.putString("product_json", new Gson().toJson(product));
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog); // Make background transparent if needed
        if (getArguments() != null) {
            String json = getArguments().getString("product_json");
            if (json != null) {
                product = new Gson().fromJson(json, Product.class);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheetInternal).setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_admin_product_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        apiService = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);

        bindViews(view);
        populateData();
        setupListeners();
    }

    private void bindViews(View view) {
        tvTitle = view.findViewById(R.id.tvEditorTitle);
        btnClose = view.findViewById(R.id.btnEditorClose);
        btnDelete = view.findViewById(R.id.btnEditorDelete);
        
        etName = view.findViewById(R.id.etEditorName);
        etCategory = view.findViewById(R.id.etEditorCategory);
        etPrice = view.findViewById(R.id.etEditorPrice);
        etStock = view.findViewById(R.id.etEditorStock);
        etShadeHex = view.findViewById(R.id.etEditorShadeHex);
        etDesc = view.findViewById(R.id.etEditorDesc);
        
        viewShadePreview = view.findViewById(R.id.viewShadePreview);
        switchActive = view.findViewById(R.id.switchEditorActive);
        
        btnDiscard = view.findViewById(R.id.btnEditorDiscard);
        btnSave = view.findViewById(R.id.btnEditorSave);
    }

    private void populateData() {
        if (product != null) {
            tvTitle.setText("Edit Catalog Entry");
            etName.setText(product.getName());
            etCategory.setText(product.getCategory());
            etPrice.setText(String.valueOf(product.getPrice()));
            etStock.setText(String.valueOf(product.getStockQuantity()));
            etDesc.setText(product.getDescriptionShort());
            
            String hex = product.getShadeColorHex();
            if (hex != null && hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            etShadeHex.setText(hex);
            updateShadePreview(hex);
            
            switchActive.setChecked(product.isActive());
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            tvTitle.setText("New Product Listing");
            btnDelete.setVisibility(View.GONE);
            switchActive.setChecked(true);
            updateShadePreview("");
        }
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> dismiss());
        btnDiscard.setOnClickListener(v -> dismiss());
        
        etShadeHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateShadePreview(s.toString());
            }
        });

        btnSave.setOnClickListener(v -> saveProduct());
        
        btnDelete.setOnClickListener(v -> {
            if (product != null) deleteProduct();
        });
    }

    private void updateShadePreview(String hex) {
        if (hex == null || hex.length() != 6) {
            viewShadePreview.setBackgroundColor(Color.TRANSPARENT);
            return;
        }
        try {
            int color = Color.parseColor("#" + hex);
            viewShadePreview.setBackgroundColor(color);
        } catch (IllegalArgumentException e) {
            viewShadePreview.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void saveProduct() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", etName.getText().toString());
        data.put("category", etCategory.getText().toString());
        
        try { data.put("price", Double.parseDouble(etPrice.getText().toString())); } catch (Exception ignored) {}
        try { data.put("stockQuantity", Integer.parseInt(etStock.getText().toString())); } catch (Exception ignored) {}
        
        data.put("descriptionShort", etDesc.getText().toString());
        data.put("isActive", switchActive.isChecked());
        
        String hex = etShadeHex.getText().toString().trim();
        if (hex.length() == 6) {
            data.put("shadeColorHex", "#" + hex);
        }

        Map<String, okhttp3.RequestBody> requestData = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            requestData.put(entry.getKey(), okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), String.valueOf(entry.getValue())));
        }
        okhttp3.MultipartBody.Part dummyThumb = okhttp3.MultipartBody.Part.createFormData("dummy", "dummy");

        if (product == null) {
            // CREATE new product (Assuming default dummy values for fields missing in simple editor)
            requestData.put("productId", okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), "NEW-" + System.currentTimeMillis()));
            apiService.createProduct(dummyThumb, requestData).enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> call, Response<Product> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Product created!", Toast.LENGTH_SHORT).show();
                        dismiss(); // We should refresh parent list but for demo it's fine
                    } else {
                        Toast.makeText(getContext(), "Failed to create", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Product> call, Throwable t) {}
            });
        } else {
            // UPDATE
            apiService.updateProduct(product.getId(), dummyThumb, requestData).enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> call, Response<Product> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Product updated!", Toast.LENGTH_SHORT).show();
                        dismiss();
                    } else {
                        Toast.makeText(getContext(), "Failed to update", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Product> call, Throwable t) {}
            });
        }
    }

    private void deleteProduct() {
        apiService.deleteProduct(product.getId()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Product deactivated!", Toast.LENGTH_SHORT).show();
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }
}
