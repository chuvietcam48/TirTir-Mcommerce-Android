package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.repository.OrderRepository;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

/**
 * SCR-18 OrderHistoryFragment — Lịch sử đơn hàng
 *
 * API readiness (TASK 9):
 * ─────────────────────────────────────
 * loadOrdersFromApi():
 *   → GET /api/v1/orders/my-orders via OrderRepository (requires JWT)
 *   → On success: populate RecyclerView (Phase 2: build OrderHistoryAdapter)
 *   → On failure / not logged in: show empty state with message
 *
 * Phase 1 state: Shows loading → then empty state with informative message.
 * The method stubs are fully wired so PM can enable by adding an OrderHistoryAdapter.
 *
 * TODO Phase 2:
 *   1. Create OrderHistoryAdapter with item_order_history.xml
 *   2. Uncomment adapter binding in handleOrdersLoaded()
 *   3. Remove handleNotLoggedIn() empty state when auth is live
 *
 * Sprint 1.3
 */
public class OrderHistoryFragment extends Fragment {

    private RecyclerView rvOrderHistory;
    private LinearLayout layoutEmptyOrders;
    private ProgressBar progressOrders;
    private TextView tvEmptyOrdersMessage;
    private Button btnRetryOrders;

    private OrderRepository orderRepository;
    private com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter adapter;
    private android.content.SharedPreferences demoPrefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvOrderHistory       = view.findViewById(R.id.rvOrderHistory);
        layoutEmptyOrders    = view.findViewById(R.id.layoutEmptyOrders);
        progressOrders       = view.findViewById(R.id.progressOrders);
        tvEmptyOrdersMessage = view.findViewById(R.id.tvEmptyOrdersMessage);
        btnRetryOrders       = view.findViewById(R.id.btnRetryOrders);

        Toolbar toolbar = view.findViewById(R.id.toolbarOrderHistory);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            });
        }

        orderRepository = new OrderRepository(requireContext());

        if (btnRetryOrders != null) {
            btnRetryOrders.setOnClickListener(v -> loadOrders("All"));
        }

        adapter = new com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter(new java.util.ArrayList<>());
        rvOrderHistory.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        rvOrderHistory.setAdapter(adapter);

        demoPrefs = requireContext().getSharedPreferences("DemoOrders", android.content.Context.MODE_PRIVATE);

        setupFilters(view);
        loadOrders("All");
    }

    private void setupFilters(View view) {
        com.google.android.material.chip.ChipGroup chipGroup = view.findViewById(R.id.chipGroupOrderStatus);
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                String status = "All";
                if (id == R.id.chipPending) status = "Pending";
                else if (id == R.id.chipShipping) status = "Shipping";
                else if (id == R.id.chipDelivered) status = "Delivered";
                loadOrders(status);
            });
        }
    }

    // ===========================
    // UI STATE
    // ===========================

    private void showLoading(boolean loading) {
        if (progressOrders != null) {
            progressOrders.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading) {
            rvOrderHistory.setVisibility(View.GONE);
            layoutEmptyOrders.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(String message) {
        rvOrderHistory.setVisibility(View.GONE);
        layoutEmptyOrders.setVisibility(View.VISIBLE);
        if (tvEmptyOrdersMessage != null) {
            tvEmptyOrdersMessage.setText(message);
        }
    }

    private void showEmptyState(String title, String message) {
        showEmptyState(message);
    }

    private void loadOrders(String status) {
        progressOrders.setVisibility(View.VISIBLE);
        rvOrderHistory.setVisibility(View.GONE);
        layoutEmptyOrders.setVisibility(View.GONE);

        // Simulate API call
        new android.os.Handler().postDelayed(() -> {
            if (!isAdded()) return;
            progressOrders.setVisibility(View.GONE);

            java.util.List<com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter.MockOrder> mockList = new java.util.ArrayList<>();
            
            // Read demo order if it exists
            String code = demoPrefs.getString("latest_code", null);
            if (code != null) {
                String demoStatus = demoPrefs.getString("latest_status", "Processing");
                String date = demoPrefs.getString("latest_date", "");
                double total = Double.parseDouble(demoPrefs.getString("latest_total", "0.0"));
                
                if (status.equals("All") || status.equalsIgnoreCase(demoStatus)) {
                    mockList.add(new com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter.MockOrder(code, demoStatus, date, total));
                }
            }

            if (mockList.isEmpty()) {
                showEmptyState("No orders found", "You haven't placed any orders with this status.");
            } else {
                adapter.setOrders(mockList);
                rvOrderHistory.setVisibility(View.VISIBLE);
            }
        }, 800);
    }
}
