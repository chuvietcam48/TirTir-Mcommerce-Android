package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.AdminVoucher;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminVoucherDetailActivity extends AppCompatActivity {
    private static final String TAG = "AdminVoucherDetail";
    public static final String EXTRA_VOUCHER_ID = "extra_voucher_id";

    private EditText etVoucherCode, etDescription, etDiscountValue, etTotalLimit, etLimitPerUser, etMinPurchase, etValidTo;
    private RadioGroup rgDiscountType;
    private RadioButton rbPercentage, rbFixed, rbFreeShip;
    private Button btnSave, btnCancel;
    private ImageButton btnDelete;
    private TextView tvTitle;

    private ApiService apiService;
    private String voucherId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_voucher_detail);

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        
        if (getIntent().hasExtra(EXTRA_VOUCHER_ID)) {
            voucherId = getIntent().getStringExtra(EXTRA_VOUCHER_ID);
        }

        initViews();
        setupListeners();

        if (voucherId != null) {
            tvTitle.setText("Edit Voucher");
            btnDelete.setVisibility(View.VISIBLE);
            loadVoucherDetails();
        } else {
            tvTitle.setText("Create Voucher");
            btnDelete.setVisibility(View.GONE);
        }
    }

    private void initViews() {
        etVoucherCode = findViewById(R.id.etVoucherCode);
        etDescription = findViewById(R.id.etDescription);
        etDiscountValue = findViewById(R.id.etDiscountValue);
        etTotalLimit = findViewById(R.id.etTotalLimit);
        etLimitPerUser = findViewById(R.id.etLimitPerUser);
        etMinPurchase = findViewById(R.id.etMinPurchase);
        etValidTo = findViewById(R.id.etValidTo);
        
        rgDiscountType = findViewById(R.id.rgDiscountType);
        rbPercentage = findViewById(R.id.rbPercentage);
        rbFixed = findViewById(R.id.rbFixed);
        rbFreeShip = findViewById(R.id.rbFreeShip);
        
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        btnDelete = findViewById(R.id.btnDelete);
        tvTitle = findViewById(R.id.tvTitle);
        
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveVoucher());
        btnDelete.setOnClickListener(v -> confirmDelete());
        
        rgDiscountType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbFreeShip) {
                etDiscountValue.setText("0");
                etDiscountValue.setEnabled(false);
            } else {
                etDiscountValue.setEnabled(true);
            }
        });
    }

    private void loadVoucherDetails() {
        apiService.getAdminVoucherById(voucherId).enqueue(new Callback<ApiResponse<AdminVoucher>>() {
            @Override
            public void onResponse(Call<ApiResponse<AdminVoucher>> call, Response<ApiResponse<AdminVoucher>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AdminVoucher v = response.body().getData();
                    
                    etVoucherCode.setText(v.getCode());
                    if (v.getDescription() != null) etDescription.setText(v.getDescription());
                    etDiscountValue.setText(String.valueOf(v.getDiscountValue()));
                    etTotalLimit.setText(String.valueOf(v.getUsageLimit()));
                    etLimitPerUser.setText(String.valueOf(v.getLimitPerUser()));
                    etMinPurchase.setText(String.valueOf(v.getMinOrderValue()));
                    
                    if (v.getValidTo() != null) {
                        etValidTo.setText(v.getValidTo().substring(0, 10)); // Extract YYYY-MM-DD
                    }
                    
                    if ("fixed".equals(v.getDiscountType())) {
                        rbFixed.setChecked(true);
                    } else if ("free_ship".equals(v.getDiscountType())) {
                        rbFreeShip.setChecked(true);
                    } else {
                        rbPercentage.setChecked(true);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AdminVoucher>> call, Throwable t) {
                Toast.makeText(AdminVoucherDetailActivity.this, "Failed to load voucher", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveVoucher() {
        String code = etVoucherCode.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String discountValueStr = etDiscountValue.getText().toString().trim();
        String totalLimitStr = etTotalLimit.getText().toString().trim();
        String limitPerUserStr = etLimitPerUser.getText().toString().trim();
        String minPurchaseStr = etMinPurchase.getText().toString().trim();
        String validToStr = etValidTo.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            etVoucherCode.setError("Code required");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("description", desc);
        body.put("discountValue", TextUtils.isEmpty(discountValueStr) ? 0 : Double.parseDouble(discountValueStr));
        body.put("usageLimit", TextUtils.isEmpty(totalLimitStr) ? 100 : Integer.parseInt(totalLimitStr));
        body.put("limitPerUser", TextUtils.isEmpty(limitPerUserStr) ? 1 : Integer.parseInt(limitPerUserStr));
        body.put("minOrderValue", TextUtils.isEmpty(minPurchaseStr) ? 0 : Double.parseDouble(minPurchaseStr));
        
        if (!TextUtils.isEmpty(validToStr)) {
            body.put("validTo", validToStr);
        } else if (voucherId == null) {
            // Need a default future date if creating
            body.put("validTo", "2024-12-31T23:59:59.000Z");
        }
        
        if (rbFixed.isChecked()) body.put("discountType", "fixed");
        else if (rbFreeShip.isChecked()) body.put("discountType", "free_ship");
        else body.put("discountType", "percentage");

        if (voucherId == null) {
            // Create
            apiService.createAdminVoucher(body).enqueue(new Callback<ApiResponse<AdminVoucher>>() {
                @Override
                public void onResponse(Call<ApiResponse<AdminVoucher>> call, Response<ApiResponse<AdminVoucher>> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(AdminVoucherDetailActivity.this, "Created successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(AdminVoucherDetailActivity.this, "Create failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<AdminVoucher>> call, Throwable t) {
                    Toast.makeText(AdminVoucherDetailActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Update
            apiService.updateAdminVoucher(voucherId, body).enqueue(new Callback<ApiResponse<AdminVoucher>>() {
                @Override
                public void onResponse(Call<ApiResponse<AdminVoucher>> call, Response<ApiResponse<AdminVoucher>> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(AdminVoucherDetailActivity.this, "Updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(AdminVoucherDetailActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<AdminVoucher>> call, Throwable t) {
                    Toast.makeText(AdminVoucherDetailActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Voucher")
            .setMessage("Are you sure you want to delete this voucher? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                apiService.deleteAdminVoucher(voucherId).enqueue(new Callback<ApiResponse<Object>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(AdminVoucherDetailActivity.this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(AdminVoucherDetailActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                        Toast.makeText(AdminVoucherDetailActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
