package com.example.tirtir_mcommerce.ui.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminProductEditorDialog extends BottomSheetDialogFragment {

    private Product product;
    private ApiService apiService;

    private EditText etName, etCategory, etDesc, etSku;
    private SwitchCompat switchActive, switchPublic;
    private ImageView ivThumb;
    private TextView tvTitle;
    private ImageButton btnDelete, btnClose;
    private Button btnSave, btnDiscard;

    // Variants
    private LinearLayout layoutVariantsContainer, layoutVariantList;
    private Button btnAddVariant;
    private List<VariantItem> variantsList = new ArrayList<>();
    private VariantItem currentExtractingVariant = null;

    // Description Images
    private android.widget.GridLayout layoutDescImages;
    private TextView btnAddDescImage;
    private List<String> descImagesList = new ArrayList<>();

    // Activity Launchers
    private ActivityResultLauncher<Intent> colorExtractorLauncher;

    private static class VariantItem {
        String id;
        String name;
        String hex;
        View view;
        EditText etName;
        EditText etHex;
        View viewColor;
    }

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
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        if (getArguments() != null) {
            String json = getArguments().getString("product_json");
            if (json != null) {
                product = new Gson().fromJson(json, Product.class);
            }
        }

        colorExtractorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        extractColorFromUri(uri);
                    }
                }
        );
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
        etDesc = view.findViewById(R.id.etEditorDesc);
        etSku = view.findViewById(R.id.etEditorSku);
        
        ivThumb = view.findViewById(R.id.ivEditorThumb);
        switchActive = view.findViewById(R.id.switchEditorActive);
        switchPublic = view.findViewById(R.id.switchEditorPublic);
        
        btnDiscard = view.findViewById(R.id.btnEditorDiscard);
        btnSave = view.findViewById(R.id.btnEditorSave);

        layoutVariantsContainer = view.findViewById(R.id.layoutVariantsContainer);
        layoutVariantList = view.findViewById(R.id.layoutVariantList);
        btnAddVariant = view.findViewById(R.id.btnAddVariant);

        layoutDescImages = view.findViewById(R.id.layoutDescImages);
        btnAddDescImage = view.findViewById(R.id.btnAddDescImage);
    }

    private void populateData() {
        if (product != null) {
            tvTitle.setText("Edit Catalog Entry");
            etName.setText(product.getName());
            etCategory.setText(product.getCategory());
            etDesc.setText(product.getDescriptionShort());
            
            if (etSku != null) {
                etSku.setText(product.getProductId() != null ? product.getProductId() : product.getId());
            }
            
            switchActive.setChecked(product.isActive());
            if (switchPublic != null) {
                switchPublic.setChecked("Published".equalsIgnoreCase(product.getStatus()));
            }
            btnDelete.setVisibility(View.VISIBLE);
            
            if (ivThumb != null) {
                String imgUrl = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(product.getThumbnailImages());
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    Glide.with(this).load(imgUrl).into(ivThumb);
                } else {
                    ivThumb.setImageResource(R.drawable.ic_product_placeholder);
                }
            }

            // Fetch variants from API
            if (product.getProductId() != null) {
                fetchVariants(product.getProductId());
            } else {
                loadLegacyHex();
            }
            
            // Populate desc images
            if (product.getDescriptionImages() != null) {
                for (String url : product.getDescriptionImages()) {
                    addDescImageRow(url);
                }
            }
        } else {
            tvTitle.setText("New Product Listing");
            btnDelete.setVisibility(View.GONE);
            switchActive.setChecked(true);
            if (switchPublic != null) switchPublic.setChecked(true);
            
            addVariantRow(null, "", "");
        }
        
        updateCategoryVisibility();
    }

    private void loadLegacyHex() {
        String hex = product != null ? product.getShadeColorHex() : null;
        if (hex != null && !hex.isEmpty()) {
            if (hex.startsWith("#")) hex = hex.substring(1);
            addVariantRow(null, "Default", hex);
        } else {
            addVariantRow(null, "", "");
        }
    }

    private void fetchVariants(String productId) {
        apiService.getShades(productId, null, 100).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    for (Map<String, Object> shade : response.body()) {
                        String id = shade.get("_id") != null ? shade.get("_id").toString() : null;
                        String name = shade.get("Shade_Name") != null ? shade.get("Shade_Name").toString() : "";
                        String hex = shade.get("Hex_Code") != null ? shade.get("Hex_Code").toString() : "";
                        if (hex.startsWith("#")) hex = hex.substring(1);
                        addVariantRow(id, name, hex);
                    }
                } else {
                    loadLegacyHex(); // Fallback if no shades found in DB
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                loadLegacyHex();
            }
        });
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> dismiss());
        btnDiscard.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> saveProduct());
        btnDelete.setOnClickListener(v -> {
            if (product != null) deleteProduct();
        });

        btnAddVariant.setOnClickListener(v -> addVariantRow(null, "", ""));
        btnAddDescImage.setOnClickListener(v -> Toast.makeText(getContext(), "Adding Description Image (Simulated)", Toast.LENGTH_SHORT).show());
        
        etCategory.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateCategoryVisibility();
            }
        });
    }

    private void updateCategoryVisibility() {
        String cat = etCategory.getText().toString().trim().toLowerCase();
        if (cat.contains("skincare")) {
            layoutVariantsContainer.setVisibility(View.GONE);
        } else {
            layoutVariantsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void addVariantRow(String id, String name, String hex) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_admin_product_variant, layoutVariantList, false);
        
        VariantItem item = new VariantItem();
        item.id = id;
        item.view = row;
        item.etName = row.findViewById(R.id.etVariantName);
        item.etHex = row.findViewById(R.id.etVariantHex);
        item.viewColor = row.findViewById(R.id.viewVariantColor);
        
        ImageButton btnExtract = row.findViewById(R.id.btnExtractColor);
        ImageButton btnRemove = row.findViewById(R.id.btnRemoveVariant);
        
        item.etName.setText(name);
        item.etHex.setText(hex);
        updateColorPreview(item.viewColor, hex);
        
        item.etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateColorPreview(item.viewColor, s.toString());
            }
        });
        
        btnExtract.setOnClickListener(v -> {
            currentExtractingVariant = item;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            colorExtractorLauncher.launch(intent);
        });
        
        btnRemove.setOnClickListener(v -> {
            layoutVariantList.removeView(row);
            variantsList.remove(item);
        });
        
        layoutVariantList.addView(row);
        variantsList.add(item);
    }

    private void addDescImageRow(String url) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(getContext());
        int size = (int) (100 * getResources().getDisplayMetrics().density);
        android.widget.GridLayout.LayoutParams frameParams = new android.widget.GridLayout.LayoutParams();
        frameParams.width = size;
        frameParams.height = size;
        frameParams.setMargins(0, 0, (int) (12 * getResources().getDisplayMetrics().density), (int) (12 * getResources().getDisplayMetrics().density));
        frame.setLayoutParams(frameParams);

        android.widget.ImageView iv = new android.widget.ImageView(getContext());
        android.widget.FrameLayout.LayoutParams ivParams = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        iv.setLayoutParams(ivParams);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundResource(R.drawable.bg_rounded_border);
        iv.setClipToOutline(true);

        Glide.with(this)
             .load(com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(url))
             .placeholder(R.drawable.ic_product_placeholder)
             .into(iv);

        android.widget.ImageButton btnRemove = new android.widget.ImageButton(getContext());
        android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        btnParams.setMargins(0, (int)(8 * getResources().getDisplayMetrics().density), (int)(8 * getResources().getDisplayMetrics().density), 0);
        btnRemove.setLayoutParams(btnParams);
        
        btnRemove.setImageResource(R.drawable.ic_close);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(android.graphics.Color.parseColor("#80000000"));
        btnRemove.setBackground(bg);
        btnRemove.setPadding(16, 16, 16, 16);
        btnRemove.setColorFilter(android.graphics.Color.WHITE);
        
        btnRemove.setOnClickListener(v -> {
            layoutDescImages.removeView(frame);
            descImagesList.remove(url);
        });

        frame.addView(iv);
        frame.addView(btnRemove);

        layoutDescImages.addView(frame);
        descImagesList.add(url);
    }

    private void updateColorPreview(View view, String hex) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(8 * view.getContext().getResources().getDisplayMetrics().density); // 8dp
        shape.setStroke((int) (1 * view.getContext().getResources().getDisplayMetrics().density), Color.parseColor("#DDDDDD"));

        if (hex != null) {
            hex = hex.trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
        }

        if (hex == null || hex.length() != 6) {
            shape.setColor(Color.parseColor("#E0E0E0"));
        } else {
            try {
                int color = Color.parseColor("#" + hex);
                shape.setColor(color);
            } catch (IllegalArgumentException e) {
                shape.setColor(Color.parseColor("#E0E0E0"));
            }
        }
        view.setBackgroundTintList(null);
        view.setBackground(shape);
    }

    private void extractColorFromUri(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), uri);
            Palette.from(bitmap).generate(palette -> {
                if (palette != null && currentExtractingVariant != null) {
                    // Prefer dominant or vibrant swatch
                    Palette.Swatch swatch = palette.getDominantSwatch();
                    if (swatch == null) swatch = palette.getVibrantSwatch();
                    
                    if (swatch != null) {
                        int color = swatch.getRgb();
                        String hexColor = String.format("%06X", (0xFFFFFF & color));
                        currentExtractingVariant.etHex.setText(hexColor);
                        Toast.makeText(getContext(), "Color extracted successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Could not extract color", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to read image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProduct() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", etName.getText().toString());
        data.put("category", etCategory.getText().toString());
        data.put("descriptionShort", etDesc.getText().toString());
        data.put("isActive", switchActive.isChecked());
        
        if (etSku != null) data.put("productId", etSku.getText().toString());
        if (switchPublic != null) data.put("status", switchPublic.isChecked() ? "Published" : "Draft");
        
        // Variants extraction
        if (layoutVariantsContainer.getVisibility() == View.VISIBLE) {
            JSONArray variantsArray = new JSONArray();
            for (VariantItem item : variantsList) {
                String vName = item.etName.getText().toString().trim();
                String vHex = item.etHex.getText().toString().trim();
                
                if (!vName.isEmpty()) {
                    try {
                        JSONObject vObj = new JSONObject();
                        if (item.id != null) vObj.put("_id", item.id);
                        vObj.put("Shade_Name", vName);
                        vObj.put("Shade_Code", vName.replaceAll("\\s+", "").toUpperCase());
                        if (vHex.length() == 6) {
                            vObj.put("Hex_Code", "#" + vHex);
                        }
                        variantsArray.put(vObj);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            if (variantsArray.length() > 0) {
                data.put("shades", variantsArray.toString());
            }
        }

        Map<String, okhttp3.RequestBody> requestData = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            requestData.put(entry.getKey(), okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), String.valueOf(entry.getValue())));
        }
        okhttp3.MultipartBody.Part dummyThumb = okhttp3.MultipartBody.Part.createFormData("dummy", "dummy");

        if (product == null) {
            // CREATE new product
            requestData.put("productId", okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), etSku.getText().toString().isEmpty() ? "NEW-" + System.currentTimeMillis() : etSku.getText().toString()));
            apiService.createProduct(dummyThumb, requestData).enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> call, Response<Product> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Product created!", Toast.LENGTH_SHORT).show();
                        dismiss();
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
