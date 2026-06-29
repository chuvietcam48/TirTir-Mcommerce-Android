package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.ProductDetailResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.CheckoutActivity;
import com.example.tirtir_mcommerce.ui.adapters.CartAdapter;
import com.example.tirtir_mcommerce.repository.CartRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CartFragment extends Fragment implements CartAdapter.CartListener {

    private RecyclerView rvCartItems;
    private TextView tvCartSubtotal, tvShippingFee, tvTaxFee, tvCartTotal;
    private Button btnCheckout;
    private LinearLayout layoutEmptyCart;
    private View cardCartSummary;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;

    private DatabaseHelper databaseHelper;
    private CartRepository cartRepository;

    private double currentTotal = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);

        databaseHelper = DatabaseHelper.getInstance(getContext());
        cartRepository = new CartRepository(requireContext());

        rvCartItems = view.findViewById(R.id.rvCartItems);
        tvCartSubtotal = view.findViewById(R.id.tvCartSubtotal);
        tvShippingFee = view.findViewById(R.id.tvShippingFee);
        tvTaxFee = view.findViewById(R.id.tvTaxFee);
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        layoutEmptyCart = view.findViewById(R.id.layoutEmptyCart);
        cardCartSummary = view.findViewById(R.id.cardCartSummary);

        View btnContinueShopping = view.findViewById(R.id.btnContinueShopping);
        if (btnContinueShopping != null) {
            btnContinueShopping.setOnClickListener(v -> navigateHome());
        }

        loadCartData();

        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(getContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();
            } else {
                double subtotal = getSubtotal();
                Intent intent = new Intent(requireContext(), CheckoutActivity.class);
                intent.putExtra("CART_SUBTOTAL", subtotal);
                startActivity(intent);
            }
        });

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbarCart);
        if (toolbar != null) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            });
        }

        return view;
    }

    private void loadCartData() {
        cartItemList = databaseHelper.getCartItems();
        
        cartAdapter = new CartAdapter(getContext(), cartItemList, this);
        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCartItems.setAdapter(cartAdapter);

        setupSwipeToDelete();

        updateCartSummary();
    }

    private void updateCartSummary() {
        if (cartItemList.isEmpty()) {
            layoutEmptyCart.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            if (cardCartSummary != null) cardCartSummary.setVisibility(View.GONE);
            btnCheckout.setEnabled(false);
            tvCartSubtotal.setText("$0.00");
            tvShippingFee.setText("$0.00");
            tvTaxFee.setText("$0.00");
            tvCartTotal.setText("$0.00");
            currentTotal = 0;
            return;
        }

        layoutEmptyCart.setVisibility(View.GONE);
        rvCartItems.setVisibility(View.VISIBLE);
        if (cardCartSummary != null) cardCartSummary.setVisibility(View.VISIBLE);
        btnCheckout.setEnabled(true);

        double subtotal = 0;
        for (CartItem item : cartItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }

        currentTotal = subtotal;

        tvCartSubtotal.setText(PriceUtils.formatPriceUsd(subtotal));
        tvShippingFee.setText("Calculated at checkout");
        tvTaxFee.setText("Included");
        tvCartTotal.setText(PriceUtils.formatPriceUsd(currentTotal));
    }

    @Override
    public void onQuantityChanged(int position, int newQuantity) {
        CartItem item = cartItemList.get(position);
        item.setQuantity(newQuantity);
        cartRepository.updateQuantity(item.getProductId(), item.getShade(), newQuantity);
        cartAdapter.notifyItemChanged(position);
        updateCartSummary();
    }

    @Override
    public void onRemoveItem(int position) {
        CartItem item = cartItemList.get(position);
        cartRepository.removeItem(item.getProductId());
        cartItemList.remove(position);
        cartAdapter.notifyItemRemoved(position);
        updateCartSummary();
    }

    @Override
    public void onEditVariant(int position) {
        if (position < 0 || position >= cartItemList.size()) return;
        CartItem item = cartItemList.get(position);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_cart_variant, null, false);
        dialog.setContentView(sheet);
        ((TextView) sheet.findViewById(R.id.tvCartVariantProduct)).setText(item.getProductName());
        ChipGroup group = sheet.findViewById(R.id.chipsCartVariants);
        View progress = sheet.findViewById(R.id.progressCartVariants);
        String[] selected = {item.getShade() == null || item.getShade().trim().isEmpty() ? "Standard" : item.getShade()};
        addVariantChip(group, selected[0], true, selected);

        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getProductById(item.getProductId()).enqueue(new Callback<ProductDetailResponse>() {
            @Override
            public void onResponse(Call<ProductDetailResponse> call, Response<ProductDetailResponse> response) {
                Product product = response.isSuccessful() && response.body() != null ? response.body().getProduct() : null;
                String parentId = product == null ? null : product.getParentId();
                loadVariants(api, item, parentId, group, progress, selected);
            }

            @Override
            public void onFailure(Call<ProductDetailResponse> call, Throwable t) {
                loadVariants(api, item, null, group, progress, selected);
            }
        });

        sheet.findViewById(R.id.btnConfirmCartVariant).setOnClickListener(v -> {
            item.setShade(selected[0]);
            cartRepository.updateShade(item.getProductId(), selected[0], item.getQuantity());
            cartAdapter.notifyItemChanged(position);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void loadVariants(ApiService api, CartItem item, String parentId, ChipGroup group,
                              View progress, String[] selected) {
        String productId = parentId == null || parentId.trim().isEmpty() ? item.getProductId() : null;
        api.getShades(productId, parentId, 100).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) return;
                group.removeAllViews();
                for (Map<String, Object> shade : response.body()) {
                    Object nameValue = shade.get("Shade_Name");
                    if (nameValue == null) nameValue = shade.get("Shade_Code");
                    String name = nameValue == null ? "Shade" : String.valueOf(nameValue);
                    addVariantChip(group, name, name.equals(selected[0]), selected);
                }
                if (group.getCheckedChipId() == View.NO_ID && group.getChildCount() > 0) {
                    ((Chip) group.getChildAt(0)).setChecked(true);
                }
            }

            @Override public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
            }
        });
    }

    private void addVariantChip(ChipGroup group, String name, boolean checked, String[] selected) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(name);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) selected[0] = name;
        });
        group.addView(chip);
    }

    private double getSubtotal() {
        double subtotal = 0;
        for (CartItem item : cartItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }
        return subtotal;
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int swipeDir) {
                int position = viewHolder.getAdapterPosition();
                onRemoveItem(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    ColorDrawable background = new ColorDrawable(Color.parseColor("#D32F2F"));
                    Drawable icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
                    int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconBottom = iconTop + icon.getIntrinsicHeight();

                    if (dX > 0) { // Swiping to the right
                        int iconLeft = itemView.getLeft() + iconMargin;
                        int iconRight = iconLeft + icon.getIntrinsicWidth();
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        background.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + ((int) dX), itemView.getBottom());
                    } else if (dX < 0) { // Swiping to the left
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = iconRight - icon.getIntrinsicWidth();
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        background.setBounds(itemView.getRight() + ((int) dX), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    } else { // view is unSwiped
                        background.setBounds(0, 0, 0, 0);
                        icon.setBounds(0, 0, 0, 0);
                    }
                    background.draw(c);
                    icon.draw(c);
                }
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(rvCartItems);
    }

    private void navigateHome() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit();
    }
}
