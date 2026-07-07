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
    private TextView tvCartSubtotal, tvShippingFee, tvCartTotal;
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
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        layoutEmptyCart = view.findViewById(R.id.layoutEmptyCart);
        cardCartSummary = view.findViewById(R.id.cardCartSummary);

        View btnContinueShopping = view.findViewById(R.id.btnContinueShopping);
        if (btnContinueShopping != null) {
            btnContinueShopping.setOnClickListener(v -> navigateHome());
        }

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            });
        }

        View btnMore = view.findViewById(R.id.btnMoreOptions);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                Toast.makeText(getContext(), "More options clicked", Toast.LENGTH_SHORT).show();
            });
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
        tvShippingFee.setText("Calculated");
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
        ((TextView) sheet.findViewById(R.id.tvCartVariantPrice)).setText(PriceUtils.formatPriceUsd(item.getPrice()));
        
        android.widget.ImageView ivProduct = sheet.findViewById(R.id.ivCartVariantProduct);
        com.bumptech.glide.Glide.with(requireContext())
            .load(com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(item.getThumbnail()))
            .placeholder(R.drawable.ic_product_placeholder)
            .into(ivProduct);

        LinearLayout group = sheet.findViewById(R.id.layoutCartVariants);
        View progress = sheet.findViewById(R.id.progressCartVariants);
        TextView tvSelectedShade = sheet.findViewById(R.id.tvCartVariantSelectedShade);
        String[] selected = {item.getShade() == null || item.getShade().trim().isEmpty() ? "Standard" : item.getShade()};
        tvSelectedShade.setText("Selected: " + selected[0]);

        final int[] quantity = {Math.max(1, item.getQuantity())};
        TextView tvQuantity = sheet.findViewById(R.id.tvCartVariantQuantity);
        tvQuantity.setText(String.valueOf(quantity[0]));

        sheet.findViewById(R.id.btnCartVariantDecrease).setOnClickListener(v -> {
            if (quantity[0] > 1) quantity[0]--;
            tvQuantity.setText(String.valueOf(quantity[0]));
        });

        sheet.findViewById(R.id.btnCartVariantIncrease).setOnClickListener(v -> {
            if (quantity[0] < 99) quantity[0]++;
            tvQuantity.setText(String.valueOf(quantity[0]));
        });

        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getProductById(item.getProductId()).enqueue(new Callback<ProductDetailResponse>() {
            @Override
            public void onResponse(Call<ProductDetailResponse> call, Response<ProductDetailResponse> response) {
                Product product = response.isSuccessful() && response.body() != null ? response.body().getProduct() : null;
                String parentId = product == null ? null : product.getParentId();
                loadVariants(api, item, parentId, group, progress, selected, tvSelectedShade);
            }

            @Override
            public void onFailure(Call<ProductDetailResponse> call, Throwable t) {
                loadVariants(api, item, null, group, progress, selected, tvSelectedShade);
            }
        });

        sheet.findViewById(R.id.btnCancelCartVariant).setOnClickListener(v -> dialog.dismiss());

        sheet.findViewById(R.id.btnConfirmCartVariant).setOnClickListener(v -> {
            item.setShade(selected[0]);
            item.setQuantity(quantity[0]);
            cartRepository.updateShade(item.getProductId(), selected[0], quantity[0]);
            cartAdapter.notifyItemChanged(position);
            updateCartSummary();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void loadVariants(ApiService api, CartItem item, String parentId, LinearLayout group,
                              View progress, String[] selected, TextView tvSelectedShade) {
        String productId = parentId == null || parentId.trim().isEmpty() ? item.getProductId() : null;
        api.getShades(productId, parentId, 100).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    // Fallback to single static variant if none found
                    addVariantCircle(group, selected[0], null, true, selected, tvSelectedShade);
                    return;
                }
                group.removeAllViews();
                
                boolean foundSelected = false;
                for (Map<String, Object> shade : response.body()) {
                    Object nameValue = shade.get("Shade_Name");
                    if (nameValue == null) nameValue = shade.get("Shade_Code");
                    String name = nameValue == null ? "Shade" : String.valueOf(nameValue);
                    
                    Object hexValue = shade.get("Hex_Code");
                    if (hexValue == null) hexValue = shade.get("shade_color_hex");
                    String hexCode = hexValue == null ? null : String.valueOf(hexValue);
                    
                    if (name.equals(selected[0])) foundSelected = true;
                    
                    addVariantCircle(group, name, hexCode, name.equals(selected[0]), selected, tvSelectedShade);
                }
                
                // If the selected shade wasn't in the list but the list is populated, pick the first one
                if (!foundSelected && group.getChildCount() > 0) {
                    group.getChildAt(0).performClick();
                }
            }

            @Override public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                addVariantCircle(group, selected[0], null, true, selected, tvSelectedShade);
            }
        });
    }

    private void addVariantCircle(LinearLayout group, String name, String hexCode, boolean isSelected, String[] selected, TextView tvSelectedShade) {
        float density = getResources().getDisplayMetrics().density;
        
        android.widget.FrameLayout frame = new android.widget.FrameLayout(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            (int) (56 * density), (int) (56 * density));
        lp.setMarginEnd((int) (12 * density));
        frame.setLayoutParams(lp);
        
        // Inner Color Circle
        android.widget.TextView colorCircle = new android.widget.TextView(requireContext());
        android.widget.FrameLayout.LayoutParams ivLp = new android.widget.FrameLayout.LayoutParams(
            (int) (48 * density), (int) (48 * density));
        ivLp.gravity = android.view.Gravity.CENTER;
        colorCircle.setLayoutParams(ivLp);
        colorCircle.setGravity(android.view.Gravity.CENTER);
        colorCircle.setTextSize(10f);
        colorCircle.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Extract short code for text inside (e.g. "21N" from "21N Ivory")
        String shortCode = name;
        if (name.contains(" ")) {
            shortCode = name.substring(0, name.indexOf(" "));
        }
        if (shortCode.length() > 4) shortCode = shortCode.substring(0, 4);
        colorCircle.setText(shortCode);
        
        int color = android.graphics.Color.LTGRAY;
        if (hexCode != null && !hexCode.trim().isEmpty()) {
            if (!hexCode.startsWith("#")) hexCode = "#" + hexCode;
            try { color = android.graphics.Color.parseColor(hexCode); } catch (Exception ignored) {}
        }
        
        // Determine text color based on background luminance
        double luminance = 0.2126 * android.graphics.Color.red(color) + 0.7152 * android.graphics.Color.green(color) + 0.0722 * android.graphics.Color.blue(color);
        if (luminance > 128) {
            colorCircle.setTextColor(android.graphics.Color.BLACK);
        } else {
            colorCircle.setTextColor(android.graphics.Color.WHITE);
        }
        
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(color);
        colorCircle.setBackground(drawable);
        frame.addView(colorCircle);
        
        // Outer Ring
        android.widget.ImageView ring = new android.widget.ImageView(requireContext());
        ring.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        android.graphics.drawable.GradientDrawable ringDrawable = new android.graphics.drawable.GradientDrawable();
        ringDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ringDrawable.setColor(android.graphics.Color.TRANSPARENT);
        
        if (isSelected) {
            ringDrawable.setStroke((int) (2 * density), android.graphics.Color.parseColor("#A12E2B")); // Primary red
        } else {
            ringDrawable.setStroke(0, android.graphics.Color.TRANSPARENT);
        }
        ring.setImageDrawable(ringDrawable);
        frame.addView(ring);
        
        // Badge (Top Right)
        android.widget.ImageView badge = new android.widget.ImageView(requireContext());
        android.widget.FrameLayout.LayoutParams badgeLp = new android.widget.FrameLayout.LayoutParams(
            (int) (16 * density), (int) (16 * density));
        badgeLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        badgeLp.setMargins(0, (int) (2 * density), (int) (2 * density), 0);
        badge.setLayoutParams(badgeLp);
        
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        badgeBg.setColor(android.graphics.Color.parseColor("#A12E2B"));
        badge.setBackground(badgeBg);
        badge.setImageResource(R.drawable.ic_check); // Requires a white check icon
        badge.setPadding((int)(3*density), (int)(3*density), (int)(3*density), (int)(3*density));
        badge.setColorFilter(android.graphics.Color.WHITE);
        badge.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        frame.addView(badge);
        
        frame.setTag(name);
        frame.setOnClickListener(v -> {
            selected[0] = name;
            tvSelectedShade.setText("Selected: " + name);
            
            // Update all views in group
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.FrameLayout childFrame = (android.widget.FrameLayout) group.getChildAt(i);
                boolean childSelected = childFrame.getTag().equals(name);
                
                android.widget.ImageView childRing = (android.widget.ImageView) childFrame.getChildAt(1);
                android.graphics.drawable.GradientDrawable cd = (android.graphics.drawable.GradientDrawable) childRing.getDrawable();
                cd.setStroke(childSelected ? (int) (2 * density) : 0, childSelected ? android.graphics.Color.parseColor("#A12E2B") : android.graphics.Color.TRANSPARENT);
                
                android.widget.ImageView childBadge = (android.widget.ImageView) childFrame.getChildAt(2);
                childBadge.setVisibility(childSelected ? View.VISIBLE : View.GONE);
            }
        });
        
        group.addView(frame);
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
