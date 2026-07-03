package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.example.tirtir_mcommerce.ui.activities.ChatActivity;
import com.example.tirtir_mcommerce.ui.activities.IngredientScanActivity;
import com.example.tirtir_mcommerce.ui.activities.NotificationCenterActivity;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.activities.SkinAnalysisActivity;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.example.tirtir_mcommerce.ui.fragments.ShopFragment;
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
    private TextView tvCartBadge;
    private LinearLayout layoutSearch;
    private LinearLayout containerCategories;
    private EditText etHomeSearch;
    private View cardSearchSuggestions;
    private LinearLayout containerSearchSuggestions;

    // Banner CTA
    private View btnShopCollection;

    private ProductRepository productRepository;
    private List<Product> fullProductList = new ArrayList<>();
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";

    // Category definitions: {label, iconResId}
    private static final String[] CATEGORY_LABELS = {"Cleanser", "Serum", "Moisturizer", "Sunscreen"};
    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_category_cleanser,
            R.drawable.ic_category_serum,
            R.drawable.ic_category_moisturizer,
            R.drawable.ic_category_sunscreen
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
        com.example.tirtir_mcommerce.utils.HeaderHelper.bind(
                view, requireContext(), requireActivity().getSupportFragmentManager());
        setupCategoryRow();
        setupBestSellers();
        setupExploreAll();
        setupClickListeners(view);

        if (getArguments() != null) {
            String initialQuery = getArguments().getString(ARG_INITIAL_QUERY, "");
            if (!initialQuery.trim().isEmpty()) {
                currentSearchQuery = initialQuery.toLowerCase(Locale.ENGLISH).trim();
                if (etHomeSearch != null) etHomeSearch.setText(initialQuery);
            }
        }

        loadProducts();

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
        tvCartBadge         = view.findViewById(R.id.tvCartBadge);
        layoutSearch        = view.findViewById(R.id.layoutSearch);
        containerCategories = view.findViewById(R.id.containerCategories);
        etHomeSearch        = view.findViewById(R.id.etHomeSearch);
        cardSearchSuggestions = view.findViewById(R.id.cardSearchSuggestions);
        containerSearchSuggestions = view.findViewById(R.id.containerSearchSuggestions);
        btnShopCollection   = view.findViewById(R.id.btnShopCollection);
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
            categoryItem.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageView ivIcon = categoryItem.findViewById(R.id.ivCategoryIcon);
            TextView tvLabel = categoryItem.findViewById(R.id.tvCategoryName);

            ivIcon.setImageResource(iconRes);
            tvLabel.setText(label);

            categoryItem.setContentDescription("Filter by " + label);
            categoryItem.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, ShopFragment.newInstance(label))
                        .addToBackStack(null)
                        .commit());

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
        intent.putExtra("PRODUCT_FULL_DESCRIPTION", product.getFullDescription());
        intent.putExtra("PRODUCT_HOW_TO_USE", product.getHowToUse());
        intent.putExtra("PRODUCT_VOLUME", product.getVolumeSize());
        intent.putExtra("PRODUCT_PARENT_ID", product.getParentId());
        intent.putExtra("PRODUCT_IS_SKINCARE", product.getIsSkincare());
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
        if (btnShopCollection != null) btnShopCollection.setOnClickListener(v -> navigateToShop());

        // AI Skin Analysis banner → SkinAnalysisActivity
        View cardAI = view.findViewById(R.id.cardAISkinAnalysis);
        if (cardAI != null) cardAI.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SkinAnalysisActivity.class)));

        if (etHomeSearch != null) {
            etHomeSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ENGLISH);
                    updateSearchSuggestions();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            etHomeSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH && !currentSearchQuery.isEmpty()) {
                    hideSearchSuggestions();
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer,
                                    ShopFragment.newInstance("All", currentSearchQuery))
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
                return false;
            });
        }

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

        View btnNotifications = view.findViewById(R.id.btnNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), NotificationCenterActivity.class)));
        }

        // "SEE ALL" opens the complete Shop catalog.
        View tvViewAll = view.findViewById(R.id.tvViewAllBestSellers);
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v -> navigateToShop());
        }

        // Keep the catalog filter explicit and bounded on small screens.
        View btnFilters = view.findViewById(R.id.btnFilters);
        if (btnFilters != null) btnFilters.setOnClickListener(v -> {
            String[] filters = {"All", "Cleanser", "Serum", "Moisturizer", "Sunscreen"};
            int checked = java.util.Arrays.asList(filters).indexOf(currentCategoryFilter);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Filter products")
                    .setSingleChoiceItems(filters, Math.max(0, checked), (dialog, which) -> {
                        currentCategoryFilter = filters[which];
                        applyFilters();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    // ===========================
    // NAVIGATION HELPERS
    // ===========================

    private void navigateToShop() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, ShopFragment.newInstance("All"))
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
                    updateSearchSuggestions();
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

            if (!"All".equalsIgnoreCase(currentCategoryFilter) && !"Best Sellers".equalsIgnoreCase(currentCategoryFilter)) {
                String cat = product.getCategory() != null ? product.getCategory() : "";
                matchesCategory = cat.equalsIgnoreCase(currentCategoryFilter)
                        || cat.toLowerCase(Locale.ENGLISH).contains(
                                currentCategoryFilter.toLowerCase(Locale.ENGLISH));
            }

            if (matchesCategory) {
                filtered.add(product);
            }
        }

        // Nếu là "Best Sellers", sort theo stockQuantity (càng ít hàng càng bán chạy)
        if ("Best Sellers".equalsIgnoreCase(currentCategoryFilter)) {
            java.util.Collections.sort(filtered, (p1, p2) -> Integer.compare(p1.getStockQuantity(), p2.getStockQuantity()));
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

    private void updateSearchSuggestions() {
        if (cardSearchSuggestions == null || containerSearchSuggestions == null) return;
        containerSearchSuggestions.removeAllViews();
        if (currentSearchQuery.isEmpty() || fullProductList.isEmpty()) {
            hideSearchSuggestions();
            return;
        }

        int shown = 0;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Product product : fullProductList) {
            if (!matchesSearch(product, currentSearchQuery)) continue;

            View row = inflater.inflate(R.layout.item_search_suggestion,
                    containerSearchSuggestions, false);
            ImageView image = row.findViewById(R.id.ivSuggestionImage);
            TextView name = row.findViewById(R.id.tvSuggestionName);
            TextView meta = row.findViewById(R.id.tvSuggestionMeta);

            name.setText(product.getName() == null ? "TirTir product" : product.getName());
            double price = product.getSalePrice() > 0 ? product.getSalePrice() : product.getPrice();
            meta.setText((product.getCategory() == null ? "Beauty" : product.getCategory())
                    + "  •  " + com.example.tirtir_mcommerce.utils.PriceUtils.formatPriceUsd(
                    com.example.tirtir_mcommerce.utils.PriceUtils.normalizePrice(price)));
            String suggestionPath = product.getGalleryImages() != null
                    && !product.getGalleryImages().isEmpty()
                    ? product.getGalleryImages().get(0) : product.getThumbnailImages();
            if ((product.getGalleryImages() == null || product.getGalleryImages().isEmpty())
                    && suggestionPath != null && suggestionPath.endsWith("/thumb.webp")
                    && !suggestionPath.contains("/Main-Images/")) {
                suggestionPath = suggestionPath.substring(
                        0, suggestionPath.length() - "/thumb.webp".length())
                        + "/Main-Images/1.webp";
            }
            Glide.with(this)
                    .load(com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(suggestionPath))
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_product_placeholder)
                    .into(image);
            row.setOnClickListener(v -> {
                hideSearchSuggestions();
                openProductDetail(product);
            });
            containerSearchSuggestions.addView(row);
            shown++;
            if (shown == 5) break;
        }

        if (shown == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("No products match ‘" + currentSearchQuery + "’");
            empty.setTextColor(requireContext().getColor(R.color.tirtir_text_secondary));
            empty.setTextSize(13f);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            empty.setPadding(padding, padding, padding, padding);
            containerSearchSuggestions.addView(empty);
        }
        cardSearchSuggestions.setVisibility(View.VISIBLE);
    }

    private boolean matchesSearch(Product product, String query) {
        String name = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ENGLISH);
        String category = product.getCategory() == null ? "" : product.getCategory().toLowerCase(Locale.ENGLISH);
        String concern = product.getMainConcern() == null ? "" : product.getMainConcern().toLowerCase(Locale.ENGLISH);
        return name.contains(query) || category.contains(query) || concern.contains(query);
    }

    private void hideSearchSuggestions() {
        if (cardSearchSuggestions != null) cardSearchSuggestions.setVisibility(View.GONE);
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

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) updateCartBadge(new CartRepository(requireContext()).getCartCount());
    }
}
