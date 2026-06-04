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

        rvOrderHistory.setLayoutManager(new LinearLayoutManager(getContext()));

        orderRepository = new OrderRepository(requireContext());

        if (btnRetryOrders != null) {
            btnRetryOrders.setOnClickListener(v -> loadOrdersFromApi());
        }

        loadOrdersFromApi();
    }

    // ===========================
    // API CALL (TASK 9)
    // ===========================

    /**
     * Loads order history from GET /api/v1/orders/my-orders.
     * Requires valid JWT (user must be logged in).
     *
     * Phase 1: Backend not ready → shows empty state.
     * Phase 2: Wire result into OrderHistoryAdapter.
     */
    private void loadOrdersFromApi() {
        // Check login state first
        SharedPrefsManager prefs = new SharedPrefsManager(requireContext());
        if (!prefs.isLoggedIn()) {
            handleNotLoggedIn();
            return;
        }

        showLoading(true);

        orderRepository.getMyOrders(
                orders -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        showLoading(false);
                        if (orders == null || orders.isEmpty()) {
                            showEmptyState("No orders yet.\nStart shopping to see your history here.");
                        } else {
                            // TODO Phase 2: bind to OrderHistoryAdapter
                            // adapter.submitList(orders);
                            // rvOrderHistory.setVisibility(View.VISIBLE);
                            // layoutEmptyOrders.setVisibility(View.GONE);
                            showEmptyState(orders.size() + " orders found (display coming in Phase 2)");
                        }
                    });
                },
                error -> {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        showLoading(false);
                        // API not ready (backend order endpoint may not exist yet)
                        showEmptyState("No orders yet.\nComplete your first purchase to see history.");
                    });
                }
        );
    }

    private void handleNotLoggedIn() {
        showLoading(false);
        showEmptyState("Please log in to view your order history.");
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
}
