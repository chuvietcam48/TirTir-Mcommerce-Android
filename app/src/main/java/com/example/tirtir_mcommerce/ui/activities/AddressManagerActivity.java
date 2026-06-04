package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

        setupAdapter();

        btnAddNewAddress.setOnClickListener(v -> {
            // TODO Phase 2: Open AddressFormActivity or bottom sheet dialog
            Toast.makeText(this, "Add address — coming in Phase 2", Toast.LENGTH_SHORT).show();
        });

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
                // TODO Phase 2: PUT /api/v1/users/addresses/{id}
                Toast.makeText(AddressManagerActivity.this, "Edit address — Phase 2", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteAddress(Address address) {
                // TODO Phase 2: DELETE /api/v1/users/addresses/{id}
                Toast.makeText(AddressManagerActivity.this, "Delete address — Phase 2", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSetDefault(Address address) {
                // TODO Phase 2: PATCH /api/v1/users/addresses/{id}/set-default
                Toast.makeText(AddressManagerActivity.this, "Set default — Phase 2", Toast.LENGTH_SHORT).show();
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

        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
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
