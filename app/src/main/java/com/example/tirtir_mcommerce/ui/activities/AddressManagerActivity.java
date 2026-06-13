package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.adapters.AddressAdapter;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.tirtir_mcommerce.model.ApiResponse;

/**
 * SCR-1A AddressManagerActivity — Quản lý địa chỉ
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────
 * loadAddressesFromApi():
 *   → GET /api/v1/users/addresses via ApiService (requires JWT)
 *   → On success: populate AddressAdapter
 *   → On failure / not logged in: show empty state
 *
 * Add/Edit/Delete: TODO Phase 2 (POST/PUT/DELETE /api/v1/users/addresses/{id})
 *
 * Sprint 1.3
 */
public class AddressManagerActivity extends AppCompatActivity {

    private RecyclerView rvAddressList;
    private LinearLayout layoutEmptyAddresses;
    private ProgressBar progressAddresses;
    private MaterialButton btnAddNewAddress;
    private AddressAdapter addressAdapter;
    private List<Address> addressList = new ArrayList<>();
    private ApiService apiService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_manager);

        Toolbar toolbar = findViewById(R.id.toolbarAddressManager);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Addresses");
        }

        rvAddressList        = findViewById(R.id.rvAddressList);
        layoutEmptyAddresses = findViewById(R.id.layoutEmptyAddresses);
        progressAddresses    = findViewById(R.id.progressAddresses);
        btnAddNewAddress     = findViewById(R.id.btnAddNewAddress);
        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);

        setupAdapter();

        btnAddNewAddress.setOnClickListener(v -> showAddressDialog(null));

        loadAddressesFromApi();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter() {
        addressAdapter = new AddressAdapter(addressList, new AddressAdapter.AddressActionListener() {
            @Override
            public void onEditAddress(Address address) {
                showAddressDialog(address);
            }

            @Override
            public void onDeleteAddress(Address address) {
                updateAddressList(apiService.deleteAddress(address.getId()), "Address deleted.");
            }

            @Override
            public void onSetDefault(Address address) {
                updateAddressList(apiService.setDefaultAddress(address.getId()), "Default address updated.");
            }
        });
        rvAddressList.setLayoutManager(new LinearLayoutManager(this));
        rvAddressList.setAdapter(addressAdapter);
    }

    // ===========================
    // API CALL (TASK 9)
    // ===========================

    /**
     * Loads addresses from GET /api/v1/users/addresses (requires JWT).
     * On success: populate RecyclerView.
     * On failure / not logged in: show empty state.
     *
     * TODO Phase 2: Add error toast with specific message per HTTP code.
     */
    private void loadAddressesFromApi() {
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        if (!prefs.isLoggedIn()) {
            showEmptyState();
            return;
        }

        showLoading(true);

        apiService.getAddresses().enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null) {
                    List<Address> loaded = response.body().getData();
                    if (loaded.isEmpty()) {
                        showEmptyState();
                    } else {
                        addressList.clear();
                        addressList.addAll(loaded);
                        addressAdapter.notifyDataSetChanged();
                        rvAddressList.setVisibility(View.VISIBLE);
                        layoutEmptyAddresses.setVisibility(View.GONE);
                    }
                } else {
                    // API returned error (endpoint may not exist yet in Phase 1)
                    showEmptyState();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                showLoading(false);
                showEmptyState();
            }
        });
    }

    private void showAddressDialog(@Nullable Address existing) {
        View content = getLayoutInflater().inflate(R.layout.dialog_address_form, null);
        TextInputEditText name = content.findViewById(R.id.etAddressName);
        TextInputEditText phone = content.findViewById(R.id.etAddressPhone);
        TextInputEditText street = content.findViewById(R.id.etAddressStreet);
        TextInputEditText ward = content.findViewById(R.id.etAddressWard);
        TextInputEditText district = content.findViewById(R.id.etAddressDistrict);
        TextInputEditText city = content.findViewById(R.id.etAddressCity);

        if (existing != null) {
            name.setText(existing.getFullName());
            phone.setText(existing.getPhone());
            street.setText(existing.getStreet());
            ward.setText(existing.getWard());
            district.setText(existing.getDistrict());
            city.setText(existing.getCity());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add address" : "Edit address")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    TextInputLayout firstError = null;
                    int[] layoutIds = {
                            R.id.tilAddressName, R.id.tilAddressPhone, R.id.tilAddressStreet,
                            R.id.tilAddressWard, R.id.tilAddressDistrict, R.id.tilAddressCity
                    };
                    TextInputEditText[] fields = {name, phone, street, ward, district, city};
                    for (int i = 0; i < fields.length; i++) {
                        TextInputLayout layout = content.findViewById(layoutIds[i]);
                        layout.setError(null);
                        if (textOf(fields[i]).isEmpty()) {
                            layout.setError("Required");
                            if (firstError == null) firstError = layout;
                        }
                    }
                    if (firstError != null) return;

                    Address address = new Address(
                            textOf(name), textOf(phone), textOf(street),
                            textOf(ward), textOf(district), textOf(city));
                    Call<ApiResponse<List<Address>>> call = existing == null
                            ? apiService.addAddress(address)
                            : apiService.updateAddress(existing.getId(), address);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    submitAddress(call, existing == null ? "Address added." : "Address updated.", dialog);
                }));
        dialog.show();
    }

    private void submitAddress(Call<ApiResponse<List<Address>>> call, String message, AlertDialog dialog) {
        call.enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getData() != null) {
                    renderAddresses(response.body().getData());
                    Toast.makeText(AddressManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Toast.makeText(AddressManagerActivity.this,
                            response.body() != null ? response.body().getMessage() : "Unable to save address.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(AddressManagerActivity.this,
                        "Connection error. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateAddressList(Call<ApiResponse<List<Address>>> call, String message) {
        showLoading(true);
        call.enqueue(new Callback<ApiResponse<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Address>>> call,
                                   Response<ApiResponse<List<Address>>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getData() != null) {
                    renderAddresses(response.body().getData());
                    Toast.makeText(AddressManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AddressManagerActivity.this,
                            "Unable to update addresses.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Address>>> call, Throwable t) {
                showLoading(false);
                Toast.makeText(AddressManagerActivity.this,
                        "Connection error. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderAddresses(List<Address> addresses) {
        addressList.clear();
        if (addresses != null) addressList.addAll(addresses);
        addressAdapter.notifyDataSetChanged();
        boolean empty = addressList.isEmpty();
        rvAddressList.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyAddresses.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    // ===========================
    // UI STATE
    // ===========================

    private void showLoading(boolean loading) {
        if (progressAddresses != null) {
            progressAddresses.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState() {
        rvAddressList.setVisibility(View.GONE);
        layoutEmptyAddresses.setVisibility(View.VISIBLE);
    }
}
