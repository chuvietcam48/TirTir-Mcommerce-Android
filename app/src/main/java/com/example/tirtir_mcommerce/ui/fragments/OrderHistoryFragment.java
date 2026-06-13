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
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * SCR-18 Order history backed by GET /api/v1/orders/my-orders.
 */
public class OrderHistoryFragment extends Fragment {

    private RecyclerView rvOrderHistory;
    private LinearLayout layoutEmptyOrders;
    private ProgressBar progressOrders;
    private TextView tvEmptyOrdersMessage;
    private Button btnRetryOrders;

    private OrderRepository orderRepository;
    private com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter adapter;
    private List<OrderResponse> allOrders = new ArrayList<>();

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

        adapter = new com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter(
                new ArrayList<>(),
                order -> {
                    long downloadId = orderRepository.downloadInvoicePdf(order.getId(), order.getInvoiceUrl());
                    android.widget.Toast.makeText(requireContext(),
                            downloadId >= 0 ? "Invoice download started" : "Unable to start invoice download",
                            android.widget.Toast.LENGTH_SHORT).show();
                });
        rvOrderHistory.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        rvOrderHistory.setAdapter(adapter);

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

    private void loadOrders(String status) {
        if (!"All".equals(status) && !allOrders.isEmpty()) {
            displayFilteredOrders(status);
            return;
        }

        showLoading(true);
        orderRepository.getMyOrders(orders -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                allOrders = orders == null ? new ArrayList<>() : orders;
                displayFilteredOrders(status);
            });
        }, error -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                btnRetryOrders.setVisibility(View.VISIBLE);
                showEmptyState(error);
            });
        });
    }

    private void displayFilteredOrders(String status) {
        List<OrderResponse> filtered = new ArrayList<>();
        for (OrderResponse order : allOrders) {
            if ("All".equals(status) || statusMatches(status, order.getStatus())) {
                filtered.add(order);
            }
        }
        btnRetryOrders.setVisibility(View.GONE);
        if (filtered.isEmpty()) {
            showEmptyState("No orders match this status yet.");
            return;
        }
        adapter.setOrders(filtered);
        layoutEmptyOrders.setVisibility(View.GONE);
        rvOrderHistory.setVisibility(View.VISIBLE);
    }

    private boolean statusMatches(String filter, String value) {
        if (value == null) return false;
        if ("Shipping".equals(filter)) {
            return "Shipping".equalsIgnoreCase(value) || "Shipped".equalsIgnoreCase(value);
        }
        return filter.equalsIgnoreCase(value);
    }
}
