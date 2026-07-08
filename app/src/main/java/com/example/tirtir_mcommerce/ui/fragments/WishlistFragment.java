package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.tirtir_mcommerce.MainActivity;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.WishlistItem;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.repository.WishlistRepository;
import com.example.tirtir_mcommerce.ui.adapters.WishlistAdapter;
import com.example.tirtir_mcommerce.ui.fragments.CartFragment;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WishlistFragment extends Fragment implements WishlistAdapter.OnItemClickListener {

    private static final String TAG = "WishlistFragment";

    private SwipeRefreshLayout swipeRefreshWishlist;
    private RecyclerView rvWishlist;
    private LinearLayout layoutEmptyState;
    private MaterialButton btnStartShopping;
    private ImageButton btnBack;
    private ImageButton btnCart;

    private WishlistAdapter adapter;
    private WishlistRepository wishlistRepository;
    private CartRepository cartRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);
        wishlistRepository = new WishlistRepository(requireContext());
        cartRepository = new CartRepository(requireContext());
        initViews(view);
        setupRecyclerView();
        loadWishlistLocal();
        fetchWishlistFromServer(); // optionally fetch latest from server
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWishlistLocal();
    }

    private void initViews(View view) {
        swipeRefreshWishlist = view.findViewById(R.id.swipeRefreshWishlist);
        rvWishlist = view.findViewById(R.id.rvWishlist);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        btnStartShopping = view.findViewById(R.id.btnStartShopping);
        btnBack = view.findViewById(R.id.btnBack);
        btnCart = view.findViewById(R.id.btnCart);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        
        btnCart.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new CartFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        btnStartShopping.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).setActiveTab(R.id.navTabHome);
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new HomeFragment())
                        .commit();
            }
        });

        swipeRefreshWishlist.setOnRefreshListener(this::fetchWishlistFromServer);
    }

    private void setupRecyclerView() {
        adapter = new WishlistAdapter(requireContext(), this);
        rvWishlist.setAdapter(adapter);
    }

    private void loadWishlistLocal() {
        List<WishlistItem> items = wishlistRepository.getLocalWishlist();
        adapter.setItems(items);
        updateEmptyState(items.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            rvWishlist.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvWishlist.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void fetchWishlistFromServer() {
        swipeRefreshWishlist.setRefreshing(true);
        ApiService apiService = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        apiService.getWishlist().enqueue(new Callback<ApiResponse<List<Product>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Product>>> call, Response<ApiResponse<List<Product>>> response) {
                swipeRefreshWishlist.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<Product> products = response.body().getData();
                    // In a real robust offline-first app, we would reconcile local & remote.
                    // For now, we update local db.
                    // Since local wishlist has user interactions, maybe sync up is better.
                    // Let's just load local for now.
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Product>>> call, Throwable t) {
                swipeRefreshWishlist.setRefreshing(false);
                Log.e(TAG, "Failed to fetch wishlist", t);
            }
        });
    }

    @Override
    public void onRemoveClick(WishlistItem item, int position) {
        adapter.removeItem(position);
        updateEmptyState(adapter.getItemCount() == 0);
        wishlistRepository.removeProductFromWishlist(item.getProductId());
        wishlistRepository.syncAllItemsToServer(null, null);
    }

    @Override
    public void onAddToCartClick(WishlistItem item) {
        CartItem cartItem = new CartItem(
                item.getProductId(),
                item.getProductName(),
                item.getThumbnail(),
                item.getPrice(),
                1,
                ""
        );
        
        cartRepository.addToCartLocal(cartItem);
        cartRepository.syncItemToServer(cartItem, null, null);
        Toast.makeText(requireContext(), "Added to cart", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProductClick(WishlistItem item) {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity.class);
        intent.putExtra("PRODUCT_ID", item.getProductId());
        startActivity(intent);
    }
}
