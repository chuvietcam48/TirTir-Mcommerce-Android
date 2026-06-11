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
import com.example.tirtir_mcommerce.ui.activities.CheckoutActivity;
import com.example.tirtir_mcommerce.ui.adapters.CartAdapter;

import com.example.tirtir_mcommerce.utils.PriceUtils;

import java.util.List;

public class CartFragment extends Fragment implements CartAdapter.CartListener {

    private RecyclerView rvCartItems;
    private TextView tvCartSubtotal, tvShippingFee, tvTaxFee, tvCartTotal;
    private Button btnCheckout;
    private LinearLayout layoutEmptyCart;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;

    private DatabaseHelper databaseHelper;

    private final double shippingFee = 30000; // Mock shipping fee
    private double currentTotal = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);

        databaseHelper = DatabaseHelper.getInstance(getContext());

        rvCartItems = view.findViewById(R.id.rvCartItems);
        tvCartSubtotal = view.findViewById(R.id.tvCartSubtotal);
        tvShippingFee = view.findViewById(R.id.tvShippingFee);
        tvTaxFee = view.findViewById(R.id.tvTaxFee);
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        layoutEmptyCart = view.findViewById(R.id.layoutEmptyCart);

        loadCartData();

        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(getContext(), "Cart is empty!", Toast.LENGTH_SHORT).show();
            } else {
                double subtotal = getSubtotal();
                Intent intent = new Intent(getContext(), CheckoutActivity.class);
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
            tvCartSubtotal.setText("0 đ");
            tvShippingFee.setText("0 đ");
            tvTaxFee.setText("0 đ");
            tvCartTotal.setText("0 đ");
            currentTotal = 0;
            return;
        }

        layoutEmptyCart.setVisibility(View.GONE);
        rvCartItems.setVisibility(View.VISIBLE);

        double subtotal = 0;
        for (CartItem item : cartItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }

        double tax = subtotal * 0.10;
        currentTotal = subtotal + tax + shippingFee;

        tvCartSubtotal.setText(PriceUtils.formatPriceVnd(subtotal));
        tvShippingFee.setText(PriceUtils.formatPriceVnd(shippingFee));
        tvTaxFee.setText(PriceUtils.formatPriceVnd(tax));
        tvCartTotal.setText(PriceUtils.formatPriceVnd(currentTotal));
    }

    @Override
    public void onQuantityChanged(int position, int newQuantity) {
        CartItem item = cartItemList.get(position);
        item.setQuantity(newQuantity);
        databaseHelper.updateCartQuantity(item.getProductId(), newQuantity);
        cartAdapter.notifyItemChanged(position);
        updateCartSummary();
    }

    @Override
    public void onRemoveItem(int position) {
        CartItem item = cartItemList.get(position);
        databaseHelper.removeCartItem(item.getProductId());
        cartItemList.remove(position);
        cartAdapter.notifyItemRemoved(position);
        updateCartSummary();
    }

    private double getSubtotal() {
        double subtotal = 0;
        for (CartItem item : cartItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }
        return subtotal;
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
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
                    ColorDrawable background = new ColorDrawable(Color.parseColor("#C62828"));
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
}
