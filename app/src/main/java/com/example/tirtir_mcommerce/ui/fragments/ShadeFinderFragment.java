package com.example.tirtir_mcommerce.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.ShadeMatchResult;
import com.example.tirtir_mcommerce.ui.adapters.CushionMatchAdapter;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab 1: Virtual Shade Finder Result.
 * Hiển thị kết quả từ API /api/v1/shades/match.
 * matchScore → matchPercent = 100 * exp(-matchScore / 7)
 */
public class ShadeFinderFragment extends Fragment {

    private static final String ARG_SHADE_RESULTS = "shade_results";
    private static final String ARG_SKIN_HEX = "skin_hex";

    private List<ShadeMatchResult> shadeResults;
    private String skinHex;

    // UI
    private ImageView imgTopMatchProduct;
    private TextView tvTopMatchName, tvTopMatchShade, tvTopMatchPercent;
    private View viewTopMatchSwatch;
    private MaterialButton btnTopMatchAddToCart;
    private TextView tvOtherMatchesHeader;
    private RecyclerView rvShadeMatches;
    private TextView tvShadeEmpty;

    public static ShadeFinderFragment newInstance(List<ShadeMatchResult> results, String skinHex) {
        ShadeFinderFragment fragment = new ShadeFinderFragment();
        Bundle args = new Bundle();
        // Truyền qua static fields thay vì serialization để tránh overhead
        fragment.shadeResults = results;
        fragment.skinHex = skinHex;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shade_finder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        populateData();
    }

    public void updateData(List<ShadeMatchResult> results, String skinHex) {
        this.shadeResults = results;
        this.skinHex = skinHex;
        if (getView() != null) populateData();
    }

    private void bindViews(View view) {
        imgTopMatchProduct   = view.findViewById(R.id.imgTopMatchProduct);
        tvTopMatchName       = view.findViewById(R.id.tvTopMatchName);
        tvTopMatchShade      = view.findViewById(R.id.tvTopMatchShade);
        tvTopMatchPercent    = view.findViewById(R.id.tvTopMatchPercent);
        viewTopMatchSwatch   = view.findViewById(R.id.viewTopMatchSwatch);
        btnTopMatchAddToCart = view.findViewById(R.id.btnTopMatchAddToCart);
        tvOtherMatchesHeader = view.findViewById(R.id.tvOtherMatchesHeader);
        rvShadeMatches       = view.findViewById(R.id.rvShadeMatches);
        tvShadeEmpty         = view.findViewById(R.id.tvShadeEmpty);
    }

    private void populateData() {
        if (shadeResults == null || shadeResults.isEmpty()) {
            tvShadeEmpty.setVisibility(View.VISIBLE);
            tvOtherMatchesHeader.setVisibility(View.GONE);
            return;
        }

        tvShadeEmpty.setVisibility(View.GONE);

        // Top match (item đầu tiên — API trả về đã sorted theo matchScore tăng dần)
        ShadeMatchResult top = shadeResults.get(0);
        tvTopMatchName.setText(top.getProductName() != null ? top.getProductName() : "Shade Match");
        tvTopMatchShade.setText("Shade: " + (top.getShadeName() != null ? top.getShadeName() : "—"));
        tvTopMatchPercent.setText(top.getMatchPercent() + "% Match");

        // Shade swatch color
        String hex = top.getShadeHex() != null ? top.getShadeHex() : skinHex;
        if (hex != null && !hex.isEmpty()) {
            try {
                viewTopMatchSwatch.setBackgroundColor(Color.parseColor(hex));
            } catch (Exception ignored) { }
        }

        // Product image
        if (top.getImageUrl() != null && !top.getImageUrl().isEmpty()) {
            Glide.with(requireContext()).load(top.getImageUrl())
                    .placeholder(R.drawable.ic_skin).into(imgTopMatchProduct);
        }

        // Add to cart — top match
        btnTopMatchAddToCart.setOnClickListener(v -> addToCart(top));

        // Other matches (items 1..N) — dùng CushionMatchAdapter
        List<CushionMatchAdapter.CushionMatch> others = new ArrayList<>();
        for (int i = 1; i < shadeResults.size(); i++) {
            ShadeMatchResult item = shadeResults.get(i);
            String id = item.getProductId() != null ? item.getProductId() : "";
            double price = item.getDisplayPrice();
            String shadeHex = item.getShadeHex() != null ? item.getShadeHex() : (skinHex != null ? skinHex : "#E9B5A5");
            others.add(new CushionMatchAdapter.CushionMatch(
                    id,
                    item.getProductName(),
                    item.getImageUrl(),
                    shadeHex,
                    item.getQualityLabel(),
                    price
            ));
        }

        if (!others.isEmpty()) {
            tvOtherMatchesHeader.setVisibility(View.VISIBLE);
            CushionMatchAdapter otherAdapter = new CushionMatchAdapter(match -> addCushionToCart(match));
            rvShadeMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvShadeMatches.setAdapter(otherAdapter);
            otherAdapter.submitList(others);
        } else {
            tvOtherMatchesHeader.setVisibility(View.GONE);
        }
    }

    private void addToCart(ShadeMatchResult result) {
        if (result.getProductId() == null || result.getProductId().isEmpty()) {
            Toast.makeText(requireContext(),
                    "This product is temporarily unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        CartItem item = new CartItem(
                result.getProductId(),
                result.getProductName(),
                result.getImageUrl(),
                result.getDisplayPrice(), 1,
                result.getShadeName()
        );
        DatabaseHelper.getInstance(requireContext()).insertOrUpdateCartItem(item);
        Toast.makeText(requireContext(), "Added to cart!", Toast.LENGTH_SHORT).show();
    }

    private void addCushionToCart(CushionMatchAdapter.CushionMatch match) {
        if (match.productId == null || match.productId.isEmpty()) {
            Toast.makeText(requireContext(),
                    "This recommendation is temporarily unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseHelper.getInstance(requireContext()).insertOrUpdateCartItem(
                new CartItem(match.productId, match.name, match.imageUrl, match.price, 1, match.shadeHex));
        Toast.makeText(requireContext(), "Added to cart!", Toast.LENGTH_SHORT).show();
    }
}
