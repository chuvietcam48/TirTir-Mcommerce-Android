package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.ui.activities.ProductDetailActivity;
import com.example.tirtir_mcommerce.ui.activities.WishlistActivity;
import com.example.tirtir_mcommerce.ui.adapters.ProductAdapter;
import com.example.tirtir_mcommerce.viewmodel.CartViewModel;
import com.example.tirtir_mcommerce.viewmodel.ProductViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ShopFragment — Full product catalog with search and category filter.
 *
 * Accessible via "View All" on HomeFragment.
 * Uses ProductViewModel (MVVM) — no direct Retrofit calls.
 * Offline fallback: SQLite cache via ProductViewModel.
 */
public class ShopFragment extends Fragment {

    private static final String ARG_CATEGORY = "initial_category";
    private static final String ARG_QUERY = "initial_query";

    public static ShopFragment newInstance(String category) {
        return newInstance(category, "");
    }

    public static ShopFragment newInstance(String category, String query) {
        ShopFragment f = new ShopFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        args.putString(ARG_QUERY, query);
        f.setArguments(args);
        return f;
    }

    private ProductViewModel productViewModel;
    private CartViewModel cartViewModel;

    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private EditText etSearch;
    private ChipGroup chipGroupCategory;
    private TextView tvProductCount;
    private View layoutLoadingShop;
    private View layoutEmptyShop;
    private View layoutErrorShop;
    private TextView tvOfflineBanner;

    private List<Product> allProducts = new ArrayList<>();
    private String currentSearch = "";
    private String currentCategory = "All";
    private String currentSkinType = "All Skin Types";

    private static final int STATE_LOADING = 0;
    private static final int STATE_CONTENT = 1;
    private static final int STATE_EMPTY   = 2;
    private static final int STATE_ERROR   = 3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        cartViewModel    = new ViewModelProvider(requireActivity()).get(CartViewModel.class);

        Bundle args = getArguments();
        if (args != null && args.getString(ARG_CATEGORY) != null) {
            currentCategory = args.getString(ARG_CATEGORY);
            currentSearch = args.getString(ARG_QUERY, "");
        }

        // Bind views
        recyclerView      = view.findViewById(R.id.recyclerViewProducts);
        etSearch          = view.findViewById(R.id.etSearch);
        chipGroupCategory = view.findViewById(R.id.chipGroupCategory);
        tvProductCount    = view.findViewById(R.id.tvProductCount);
        layoutLoadingShop = view.findViewById(R.id.layoutLoadingShop);
        layoutEmptyShop   = view.findViewById(R.id.layoutEmptyShop);
        layoutErrorShop   = view.findViewById(R.id.layoutErrorShop);
        tvOfflineBanner   = view.findViewById(R.id.tvOfflineBanner);
        if (etSearch != null && !currentSearch.isEmpty()) etSearch.setText(currentSearch);

        // Wishlist shortcut
        View btnWishlist = view.findViewById(R.id.btnShopWishlist);
        if (btnWishlist != null) {
            btnWishlist.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WishlistActivity.class)));
        }

        View btnShopBack = view.findViewById(R.id.btnShopBack);
        if (btnShopBack != null) {
            btnShopBack.setOnClickListener(v -> requireActivity().onBackPressed());
        }

        // RecyclerView — tap card → Product Detail
        adapter = new ProductAdapter(getContext(), new ArrayList<>(), product -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            String pid = product.getProductId() != null ? product.getProductId() : product.getId();
            intent.putExtra("PRODUCT_ID",               pid);
            intent.putExtra("PRODUCT_NAME",             product.getName());
            intent.putExtra("PRODUCT_PRICE",            product.getPrice());
            intent.putExtra("PRODUCT_SALE_PRICE",       product.getSalePrice());
            intent.putExtra("PRODUCT_CATEGORY",         product.getCategory());
            intent.putExtra("PRODUCT_STOCK",            product.getStockQuantity());
            intent.putExtra("PRODUCT_IMAGE",            product.getThumbnailImages());
            if (product.getGalleryImages() != null) {
                intent.putStringArrayListExtra("PRODUCT_GALLERY", new ArrayList<>(product.getGalleryImages()));
            }
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerView.setAdapter(adapter);

        // Search — filter on every keystroke
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearch = s.toString();
                    applyFilter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        View btnFilters = view.findViewById(R.id.btnSort);
        if (btnFilters != null) {
            btnFilters.setOnClickListener(v -> {
                String[] skinTypes = {"All Skin Types", "Dry", "Oily", "Combination", "Sensitive", "Acne-prone"};
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Filter by Skin Type")
                    .setItems(skinTypes, (dialog, which) -> {
                        currentSkinType = skinTypes[which];
                        applyFilter();
                    }).show();
            });
        }

        // Retry button wiring
        if (layoutErrorShop != null) {
            View btnRetry = layoutErrorShop.findViewById(R.id.btnShopRetry);
            if (btnRetry != null) {
                btnRetry.setOnClickListener(v -> {
                    showState(STATE_LOADING);
                    productViewModel.loadProducts();
                });
            }
        }

        showState(STATE_LOADING);
        observeViewModels();
        productViewModel.loadProducts();
    }

    // ===========================
    // OBSERVE VIEWMODELS
    // ===========================

    private void observeViewModels() {
        productViewModel.productsLiveData.observe(getViewLifecycleOwner(), products -> {
            if (products != null && !products.isEmpty()) {
                allProducts = new ArrayList<>(products);
                buildCategoryChips(allProducts);
                applyFilter();
            } else if (products != null) {
                showState(STATE_EMPTY);
            }
        });

        productViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (Boolean.TRUE.equals(isLoading) && allProducts.isEmpty()) {
                showState(STATE_LOADING);
            }
        });

        productViewModel.errorMessage.observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.isEmpty()) return;
            if (allProducts.isEmpty()) {
                showState(STATE_ERROR);
            } else {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // Wire offline banner (previously a TODO)
        productViewModel.isOfflineMode.observe(getViewLifecycleOwner(), isOffline -> {
            if (tvOfflineBanner != null) {
                tvOfflineBanner.setVisibility(Boolean.TRUE.equals(isOffline) ? View.VISIBLE : View.GONE);
            }
        });

        cartViewModel.cartMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===========================
    // CATEGORY CHIPS
    // ===========================

    private void buildCategoryChips(List<Product> products) {
        if (chipGroupCategory == null) return;
        chipGroupCategory.removeAllViews();

        // Collect unique categories from live data, "All" always first
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");
        if (!"All".equalsIgnoreCase(currentCategory)) categories.add(currentCategory);
        for (Product p : products) {
            if (p.getCategory() != null && !p.getCategory().trim().isEmpty()) {
                categories.add(p.getCategory().trim());
            }
        }

        // ColorStateList: checked → brand red, unchecked → white
        int[][] states    = {{android.R.attr.state_checked}, {}};
        int[] bgColors    = {0xFFC62828, 0xFFFFFFFF};
        int[] textColors  = {0xFFFFFFFF, 0xFF111111};
        ColorStateList chipBgList   = new ColorStateList(states, bgColors);
        ColorStateList chipTextList = new ColorStateList(states, textColors);

        for (String cat : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals(currentCategory));
            chip.setChipBackgroundColor(chipBgList);
            chip.setTextColor(chipTextList);
            chip.setChipStrokeColor(ColorStateList.valueOf(0xFFE5E5E5));
            chip.setChipStrokeWidth(2f);
            chip.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    currentCategory = cat;
                    applyFilter();
                }
            });
            chipGroupCategory.addView(chip);
        }
    }

    // ===========================
    // FILTER LOGIC
    // ===========================

    private void applyFilter() {
        List<Product> result = new ArrayList<>(allProducts);

        // Category filter
        if (!"All".equals(currentCategory)) {
            Iterator<Product> it = result.iterator();
            while (it.hasNext()) {
                Product p = it.next();
                if (!matchesCategoryFilter(p, currentCategory)) it.remove();
            }
        }

        // Skin Type filter
        if (!"All Skin Types".equals(currentSkinType)) {
            Iterator<Product> it = result.iterator();
            while (it.hasNext()) {
                Product p = it.next();
                String target = p.getSkinTypeTarget() != null ? p.getSkinTypeTarget() : "";
                if (!target.toLowerCase().contains(currentSkinType.toLowerCase())) it.remove();
            }
        }

        // Search filter (name or category contains query)
        String q = currentSearch.toLowerCase().trim();
        if (!q.isEmpty()) {
            Iterator<Product> it = result.iterator();
            while (it.hasNext()) {
                Product p = it.next();
                String name = p.getName() != null ? p.getName().toLowerCase() : "";
                String cat  = p.getCategory() != null ? p.getCategory().toLowerCase() : "";
                if (!name.contains(q) && !cat.contains(q)) it.remove();
            }
        }

        adapter.updateData(result);
        if (tvProductCount != null) tvProductCount.setText(result.size() + " Products");
        showState(result.isEmpty() ? STATE_EMPTY : STATE_CONTENT);
    }

    private boolean matchesCategoryFilter(Product product, String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(java.util.Locale.ENGLISH).trim();
        if (needle.isEmpty() || "all".equals(needle)) return true;
        String searchable = ((product.getCategory() == null ? "" : product.getCategory()) + " "
                + (product.getCategorySlug() == null ? "" : product.getCategorySlug()) + " "
                + (product.getName() == null ? "" : product.getName()) + " "
                + (product.getProductSlug() == null ? "" : product.getProductSlug()) + " "
                + (product.getMainConcern() == null ? "" : product.getMainConcern()))
                .toLowerCase(java.util.Locale.ENGLISH);

        if (searchable.contains(needle)) return true;
        if ("moisturizer".equals(needle)) {
            return searchable.contains("cream") || searchable.contains("lotion")
                    || searchable.contains("moisture");
        }
        if ("sunscreen".equals(needle)) {
            return searchable.contains("sun") || searchable.contains("spf");
        }
        return false;
    }

    // ===========================
    // STATE MANAGEMENT
    // ===========================

    private void showState(int state) {
        if (layoutLoadingShop != null)
            layoutLoadingShop.setVisibility(state == STATE_LOADING ? View.VISIBLE : View.GONE);
        if (recyclerView != null)
            recyclerView.setVisibility(state == STATE_CONTENT ? View.VISIBLE : View.GONE);
        if (layoutEmptyShop != null)
            layoutEmptyShop.setVisibility(state == STATE_EMPTY ? View.VISIBLE : View.GONE);
        if (layoutErrorShop != null)
            layoutErrorShop.setVisibility(state == STATE_ERROR ? View.VISIBLE : View.GONE);
    }
}
