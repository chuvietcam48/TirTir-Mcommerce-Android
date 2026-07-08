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

import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * SCR-18 Order history backed by GET /api/v1/orders/my-orders.
 */
public class OrderHistoryFragment extends Fragment {

    private RecyclerView rvOrderHistory;
    private LinearLayout layoutEmptyOrders;
    private ProgressBar progressOrders;
    private TextView tvEmptyOrdersMessage, tvOrderHistorySummary;
    private Button btnRetryOrders;

    private OrderRepository orderRepository;
    private com.example.tirtir_mcommerce.ui.adapters.OrderHistoryAdapter adapter;
    private List<OrderResponse> allOrders = new ArrayList<>();
    private String currentFilterStatus = "All";
    
    private FirebaseFirestore firestore;
    private ListenerRegistration ordersListener;
    private SharedPrefsManager prefsManager;

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
        tvOrderHistorySummary = view.findViewById(R.id.tvOrderHistorySummary);
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
        firestore = FirebaseFirestore.getInstance();
        prefsManager = new SharedPrefsManager(requireContext());

        if (btnRetryOrders != null) {
            btnRetryOrders.setOnClickListener(v -> loadOrders(currentFilterStatus));
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
        adapter.setOpenListener(order -> {
            android.content.Intent intent = new android.content.Intent(requireContext(),
                    com.example.tirtir_mcommerce.ui.activities.OrderDetailActivity.class);
            intent.putExtra("ORDER_ID", order.getId());
            startActivity(intent);
        });

        setupFilters(view);
        loadOrders(currentFilterStatus);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
        }
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
                currentFilterStatus = status;
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
                setupFirestoreListener(); // start listening for real-time updates
            });
        }, error -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                showLoading(false);
                if (btnRetryOrders != null) btnRetryOrders.setVisibility(View.VISIBLE);
                showEmptyState(error);
            });
        });
    }

    private void setupFirestoreListener() {
        if (ordersListener != null) return; // Already listening
        String uid = prefsManager.getFirebaseUid();
        if (uid == null || uid.isEmpty()) return;

        ordersListener = firestore.collection("users").document(uid).collection("orders")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    
                    boolean changed = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED || dc.getType() == DocumentChange.Type.MODIFIED) {
                            String orderId = dc.getDocument().getId();
                            String newStatus = dc.getDocument().getString("status");
                            
                            for (OrderResponse order : allOrders) {
                                if (order.getId() != null && order.getId().equals(orderId)) {
                                    if (!order.getStatus().equals(newStatus)) {
                                        order.setStatus(newStatus);
                                        changed = true;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    if (changed) {
                        displayFilteredOrders(currentFilterStatus);
                    }
                });
    }

    private void displayFilteredOrders(String status) {
        List<OrderResponse> filtered = new ArrayList<>();
        for (OrderResponse order : allOrders) {
            if ("All".equals(status) || statusMatches(status, order.getStatus())) {
                filtered.add(order);
            }
        }
        if (btnRetryOrders != null) btnRetryOrders.setVisibility(View.GONE);
        updateSummary(filtered.size());
        if (filtered.isEmpty()) {
            showEmptyState("All".equals(status)
                    ? "Your TIRTIR orders will appear here after checkout."
                    : "No orders match this status yet.");
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
        if ("Pending".equals(filter)) {
            return "Pending".equalsIgnoreCase(value)
                    || "Processing".equalsIgnoreCase(value)
                    || "Confirmed".equalsIgnoreCase(value);
        }
        return filter.equalsIgnoreCase(value);
    }

    private void updateSummary(int count) {
        if (tvOrderHistorySummary == null) return;
        if (count == 0) {
            tvOrderHistorySummary.setText("Your recent TIRTIR orders");
        } else if (count == 1) {
            tvOrderHistorySummary.setText("1 order in your history");
        } else {
            tvOrderHistorySummary.setText(count + " orders in your history");
        }
    }
}
