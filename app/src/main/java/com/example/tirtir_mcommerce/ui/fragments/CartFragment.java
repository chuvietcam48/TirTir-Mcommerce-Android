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

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.ui.activities.CheckoutActivity;
import com.example.tirtir_mcommerce.ui.adapters.CartAdapter;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartFragment extends Fragment implements CartAdapter.CartListener {

    private RecyclerView rvCartItems;
    private TextView tvCartSubtotal, tvShippingFee, tvCartTotal;
    private Button btnCheckout;
    private LinearLayout layoutEmptyCart;
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList;

    private DatabaseHelper databaseHelper;

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
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
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnCheckout = view.findViewById(R.id.btnCheckout);
        layoutEmptyCart = view.findViewById(R.id.layoutEmptyCart);

        loadCartData();

        btnCheckout.setOnClickListener(v -> {
            if (cartItemList.isEmpty()) {
                Toast.makeText(getContext(), "Cart is empty!", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(getContext(), CheckoutActivity.class);
                intent.putExtra("CART_TOTAL", currentTotal);
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

        updateCartSummary();
    }

    private void updateCartSummary() {
        if (cartItemList.isEmpty()) {
            layoutEmptyCart.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            tvCartSubtotal.setText("0 đ");
            tvShippingFee.setText("0 đ");
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

        currentTotal = subtotal + shippingFee;

        tvCartSubtotal.setText(currencyFormat.format(subtotal) + " đ");
        tvShippingFee.setText(currencyFormat.format(shippingFee) + " đ");
        tvCartTotal.setText(currencyFormat.format(currentTotal) + " đ");
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
}
