package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.ui.activities.ChatActivity;
import com.example.tirtir_mcommerce.ui.activities.IngredientScanActivity;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.activities.SkinAnalysisActivity;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SCR-13 HomeFragment — Redesigned Home Page
 *
 * Layout:
 *  - Custom Search Bar + Cart Icon with badge
 *  - Greeting "Hello, {Name} 👋"
 *  - Category Row (Cleanser, Serum, Moisturizer, Sunscreen)
 *  - Best Sellers horizontal RecyclerView (first 6 products)
 *  - Promotional Banner "Hydra + Hyaluronic Collection"
 *  - Explore All vertical 2-column grid
 */
public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final String ARG_INITIAL_QUERY = "initial_query";

    // Best Sellers
    private RecyclerView rvBestSellers;
    private ProductAdapter bestSellersAdapter;

    // Explore All
    private RecyclerView rvProducts;
    private ProductAdapter exploreAllAdapter;

    // State views
    private ProgressBar progressProducts;
    private LinearLayout layoutEmptyProducts;
    private LinearLayout layoutLoadingState;
    private LinearLayout layoutErrorState;
    private TextView tvLoadingMessage;
    private TextView tvErrorMessage;
    private Button btnRetry;

    // Header
    private TextView tvGreetingSub;
    private TextView tvGreeting;
    private TextView tvCartBadge;
    private LinearLayout layoutSearch;
    private LinearLayout containerCategories;

    // Banner CTA
    private View btnShopCollection;

    private ProductRepository productRepository;
    private List<Product> fullProductList = new ArrayList<>();
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";

    // Category definitions: {label, iconResId}
    private static final String[] CATEGORY_LABELS = {"Cleanser", "Serum", "Moisturizer", "Sunscreen"};
    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_skin,   // Cleanser
            R.drawable.ic_routine, // Serum
            R.drawable.ic_skin,   // Moisturizer
            R.drawable.ic_scan    // Sunscreen
    };

    public static HomeFragment newInstance(String initialQuery) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_QUERY, initialQuery);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        bindViews(view);
        setupGreeting();
        setupCategoryRow();
        setupBestSellers();
        setupExploreAll();
        setupClickListeners(view);
        loadProducts();

        if (getArguments() != null) {
            String initialQuery = getArguments().getString(ARG_INITIAL_QUERY, "");
            if (!initialQuery.trim().isEmpty()) {
                currentSearchQuery = initialQuery.toLowerCase(Locale.ENGLISH).trim();
            }
        }

        return view;
    }

    // ===========================
    // BIND VIEWS
    // ===========================

    private void bindViews(View view) {
        rvBestSellers       = view.findViewById(R.id.rvBestSellers);
        rvProducts          = view.findViewById(R.id.rvProducts);
        progressProducts    = view.findViewById(R.id.progressProducts);
        layoutEmptyProducts = view.findViewById(R.id.layoutEmptyProducts);
        layoutLoadingState  = view.findViewById(R.id.layoutLoadingState);
        layoutErrorState    = view.findViewById(R.id.layoutErrorState);
        tvLoadingMessage    = view.findViewById(R.id.tvLoadingMessage);
        tvErrorMessage      = view.findViewById(R.id.tvErrorMessage);
        btnRetry            = view.findViewById(R.id.btnRetry);
        tvGreetingSub       = view.findViewById(R.id.tvGreetingSub);
        tvGreeting          = view.findViewById(R.id.tvGreeting);
        tvCartBadge         = view.findViewById(R.id.tvCartBadge);
        layoutSearch        = view.findViewById(R.id.layoutSearch);
        containerCategories = view.findViewById(R.id.containerCategories);
        btnShopCollection   = view.findViewById(R.id.btnShopCollection);
    }

    // ===========================
    // GREETING
    // ===========================

    private void setupGreeting() {
        if (tvGreeting == null) return;
        SharedPrefsManager prefs = new SharedPrefsManager(requireContext());
        User user = prefs.getCachedUser();
        
        String firstName = "Guest";
        String fullName = "Guest User";
        
        if (user != null) {
            fullName = user.getName() != null && !user.getName().isEmpty() ? user.getName() : user.getEmail();
            if (fullName != null && !fullName.isEmpty()) {
                // Extract first name only
                firstName = fullName.split(" ")[0];
                // Remove email part if no display name
                if (firstName.contains("@")) firstName = firstName.split("@")[0];
            }
        }
        
        if (tvGreetingSub != null) {
            tvGreetingSub.setText("Hi,");
        }
        tvGreeting.setText(fullName);
    }

    // ===========================
    // CATEGORY ROW
    // ===========================

    private void setupCategoryRow() {
        if (containerCategories == null) return;
        containerCategories.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            String label = CATEGORY_LABELS[i];
            int iconRes = CATEGORY_ICONS[i];

            View categoryItem = inflater.inflate(R.layout.item_category, containerCategories, false);
            ImageView ivIcon = categoryItem.findViewById(R.id.ivCategoryIcon);
            TextView tvLabel = categoryItem.findViewById(R.id.tvCategoryName);

            ivIcon.setImageResource(iconRes);
            tvLabel.setText(label);

            final String categoryFilter = label;
            categoryItem.setOnClickListener(v -> {
                currentCategoryFilter = categoryFilter;
                applyFilters();
            });

            containerCategories.addView(categoryItem);
        }
    }

    // ===========================
    // ADAPTER SETUP
    // ===========================

    private void setupBestSellers() {
        if (rvBestSellers == null) return;
        bestSellersAdapter = new ProductAdapter(
                getContext(), new ArrayList<>(), R.layout.item_product_bestseller, this::openProductDetail);
        rvBestSellers.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvBestSellers.setHasFixedSize(false);
        rvBestSellers.setAdapter(bestSellersAdapter);
    }

    private void setupExploreAll() {
        if (rvProducts == null) return;
        exploreAllAdapter = new ProductAdapter(
                getContext(), new ArrayList<>(), R.layout.item_product, this::openProductDetail);
        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvProducts.setHasFixedSize(false);
        rvProducts.setNestedScrollingEnabled(false);
        rvProducts.setAdapter(exploreAllAdapter);
    }

    private void openProductDetail(Product product) {
        Intent intent = new Intent(getContext(), ProductDetailActivity.class);
        String pid = product.getProductId() != null ? product.getProductId() : product.getId();
        intent.putExtra("PRODUCT_ID",          pid);
        intent.putExtra("PRODUCT_NAME",        product.getName());
        intent.putExtra("PRODUCT_PRICE",       product.getPrice());
        intent.putExtra("PRODUCT_SALE_PRICE",  product.getSalePrice());
        intent.putExtra("PRODUCT_CATEGORY",    product.getCategory());
        intent.putExtra("PRODUCT_IMAGE",       product.getThumbnailImages());
        intent.putExtra("PRODUCT_SKIN_TYPES",  product.getSkinTypeTarget());
        intent.putExtra("PRODUCT_INGREDIENTS", product.getKeyIngredients());
        intent.putExtra("PRODUCT_DESCRIPTION", product.getDescriptionShort());
        intent.putExtra("PRODUCT_STOCK",       product.getStockQuantity());
        if (product.getGalleryImages() != null) {
            intent.putStringArrayListExtra("PRODUCT_GALLERY",
                    new ArrayList<>(product.getGalleryImages()));
        }
        startActivity(intent);
    }

    // ===========================
    // CLICK LISTENERS
    // ===========================

    private void setupClickListeners(View view) {
        if (btnRetry != null)          btnRetry.setOnClickListener(v -> loadProducts());
        // "Shop Collection" banner → full Shop catalog
        if (btnShopCollection != null) btnShopCollection.setOnClickListener(v -> navigateToShop());

        // Search bar tap opens HomeFragment search or navigates
        if (layoutSearch != null) layoutSearch.setOnClickListener(v -> {
            // Focus a search bar or re-use SearchView logic
        });

        // Cart icon
        View btnCart = view.findViewById(R.id.btnCart);
        if (btnCart != null) btnCart.setOnClickListener(v -> {
            if (requireActivity() instanceof com.example.tirtir_mcommerce.MainActivity) {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new com.example.tirtir_mcommerce.ui.fragments.CartFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        // "View All" → open full Shop catalog with filter/search
        View tvViewAll = view.findViewById(R.id.tvViewAllBestSellers);
        if (tvViewAll != null) tvViewAll.setOnClickListener(v -> navigateToShop());

        // Filters button opens category filter (for now, resets to "All")
        View btnFilters = view.findViewById(R.id.btnFilters);
        if (btnFilters != null) btnFilters.setOnClickListener(v -> {
            currentCategoryFilter = "All";
            applyFilters();
        });
    }

    // ===========================
    // NAVIGATION HELPERS
    // ===========================

    private void navigateToShop() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new ShopFragment())
                .addToBackStack(null)
                .commit();
    }

    // ===========================
    // PRODUCT LOADING
    // ===========================

    private void loadProducts() {
        showLoadingState();

        productRepository = new ProductRepository(getContext());
        productRepository.fetchProducts(products -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                hideAllStates();
                if (products != null && !products.isEmpty()) {
                    fullProductList = new ArrayList<>(products);
                    applyFilters();
                    rvProducts.setVisibility(View.VISIBLE);
                } else {
                    showEmptyState();
                }
            });
        }, error -> {
            Log.e(TAG, "Product load failed: " + error);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                hideAllStates();
                if (fullProductList.isEmpty()) {
                    showErrorState("We could not load the catalog. Check your connection and try again.");
                } else {
                    rvProducts.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // ===========================
    // UI STATE HELPERS
    // ===========================

    private void showLoadingState() {
        if (layoutLoadingState != null) layoutLoadingState.setVisibility(View.VISIBLE);
        if (layoutErrorState != null)   layoutErrorState.setVisibility(View.GONE);
        if (layoutEmptyProducts != null) layoutEmptyProducts.setVisibility(View.GONE);
    }

    private void hideAllStates() {
        if (progressProducts != null)   progressProducts.setVisibility(View.GONE);
        if (layoutLoadingState != null) layoutLoadingState.setVisibility(View.GONE);
        if (layoutErrorState != null)   layoutErrorState.setVisibility(View.GONE);
        if (layoutEmptyProducts != null) layoutEmptyProducts.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        if (layoutErrorState != null)  layoutErrorState.setVisibility(View.VISIBLE);
        if (rvProducts != null)        rvProducts.setVisibility(View.GONE);
        if (rvBestSellers != null)     rvBestSellers.setVisibility(View.GONE);
        if (tvErrorMessage != null)    tvErrorMessage.setText(message);
    }

    private void showEmptyState() {
        if (layoutEmptyProducts != null) layoutEmptyProducts.setVisibility(View.VISIBLE);
        if (rvProducts != null)          rvProducts.setVisibility(View.GONE);
    }

    // ===========================
    // FILTER & SPLIT
    // ===========================

    /**
     * Splits products:
     *  - Best Sellers RecyclerView: first 6 products (or category match)
     *  - Explore All grid: remaining products
     */
    private void applyFilters() {
        List<Product> filtered = new ArrayList<>();
        for (Product product : fullProductList) {
            boolean matchesCategory = true;
            boolean matchesSearch = true;

            if (!"All".equalsIgnoreCase(currentCategoryFilter)) {
                String cat = product.getCategory() != null ? product.getCategory() : "";
                matchesCategory = cat.equalsIgnoreCase(currentCategoryFilter)
                        || cat.toLowerCase(Locale.ENGLISH).contains(
                                currentCategoryFilter.toLowerCase(Locale.ENGLISH));
            }

            if (!currentSearchQuery.isEmpty()) {
                String name = product.getName() != null ? product.getName().toLowerCase(Locale.ENGLISH) : "";
                String cat  = product.getCategory() != null ? product.getCategory().toLowerCase(Locale.ENGLISH) : "";
                matchesSearch = name.contains(currentSearchQuery) || cat.contains(currentSearchQuery);
            }

            if (matchesCategory && matchesSearch) {
                filtered.add(product);
            }
        }

        if (filtered.isEmpty()) {
            showEmptyState();
            return;
        }

        // Best Sellers: first 6 items
        int bestSellersCount = Math.min(6, filtered.size());
        List<Product> bestSellers = filtered.subList(0, bestSellersCount);

        // Explore All: everything
        List<Product> exploreAll = filtered;

        if (bestSellersAdapter != null) {
            bestSellersAdapter.updateData(new ArrayList<>(bestSellers));
            if (rvBestSellers != null) rvBestSellers.setVisibility(View.VISIBLE);
        }

        if (exploreAllAdapter != null) {
            exploreAllAdapter.updateData(new ArrayList<>(exploreAll));
            if (rvProducts != null) rvProducts.setVisibility(View.VISIBLE);
        }

        hideAllStates();
    }

    // ===========================
    // PUBLIC API (called by MainActivity)
    // ===========================

    /** Updates cart badge count shown in the top bar. */
    public void updateCartBadge(int count) {
        if (tvCartBadge == null || getView() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (count > 0) {
                tvCartBadge.setVisibility(View.VISIBLE);
                tvCartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
                tvCartBadge.setVisibility(View.GONE);
            }
        });
    }
}
