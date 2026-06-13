package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.repository.ProductRepository;
import com.example.tirtir_mcommerce.ui.adapters.CushionMatchAdapter;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class SkinResultActivity extends AppCompatActivity {
    private static final String BASE_IMAGE_URL = "https://tirtir-project.onrender.com/";
    private CushionMatchAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_result);

        Toolbar toolbar = findViewById(R.id.toolbarSkinResult);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindScores();
        bindTone();

        RecyclerView rvMatches = findViewById(R.id.rvCushionMatches);
        tvEmpty = findViewById(R.id.tvCushionEmpty);
        adapter = new CushionMatchAdapter(item -> {
            if (item.productId == null || item.productId.isEmpty()) {
                Toast.makeText(this, "This recommendation is missing productId.", Toast.LENGTH_SHORT).show();
                return;
            }
            DatabaseHelper.getInstance(this).insertOrUpdateCartItem(
                    new CartItem(item.productId, item.name, item.imageUrl, item.price, 1, item.shadeHex));
            Toast.makeText(this, "Recommended cushion added to cart", Toast.LENGTH_SHORT).show();
        });
        rvMatches.setLayoutManager(new LinearLayoutManager(this));
        rvMatches.setAdapter(adapter);
        loadCushionMatches();
    }

    private void loadCushionMatches() {
        ProductRepository repository = new ProductRepository(this);
        repository.fetchProducts(products -> runOnUiThread(() -> bindMatches(products)),
                error -> runOnUiThread(() -> {
                    adapter.submitList(new ArrayList<>());
                    tvEmpty.setText("Unable to load cushion matches from API/cache.");
                    tvEmpty.setVisibility(View.VISIBLE);
                }));
    }

    private void bindMatches(List<Product> products) {
        List<CushionMatchAdapter.CushionMatch> matches = new ArrayList<>();
        String skinHex = getIntent().getStringExtra("SKIN_HEX");
        if (skinHex == null || skinHex.isEmpty()) skinHex = "#E9B5A5";

        if (products != null) {
            for (Product product : products) {
                if (product == null || !looksLikeCushion(product)) continue;
                String id = product.getProductId() != null ? product.getProductId() : product.getId();
                double price = PriceUtils.normalizePrice(product.getSalePrice() > 0 ? product.getSalePrice() : product.getPrice());
                matches.add(new CushionMatchAdapter.CushionMatch(
                        id,
                        product.getName(),
                        buildImageUrl(product.getThumbnailImages()),
                        skinHex,
                        qualityForPosition(matches.size()),
                        price
                ));
                if (matches.size() == 5) break;
            }
        }

        adapter.submitList(matches);
        tvEmpty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean looksLikeCushion(Product product) {
        String name = product.getName() == null ? "" : product.getName().toLowerCase();
        String category = product.getCategory() == null ? "" : product.getCategory().toLowerCase();
        return name.contains("cushion")
                || name.contains("foundation")
                || category.contains("cushion")
                || category.contains("makeup")
                || category.contains("base");
    }

    private String qualityForPosition(int position) {
        if (position == 0) return "Perfect";
        if (position <= 2) return "Good";
        return "Acceptable";
    }

    private String buildImageUrl(String path) {
        if (path == null || path.isEmpty()) return "";
        if (path.startsWith("http")) return path;
        return BASE_IMAGE_URL + path;
    }

    private void bindTone() {
        String hex = getIntent().getStringExtra("SKIN_HEX");
        String category = getIntent().getStringExtra("ITA_CATEGORY");
        double angle = getIntent().getDoubleExtra("ITA_ANGLE", Double.NaN);
        TextView meta = findViewById(R.id.tvSkinToneMeta);
        View swatch = findViewById(R.id.viewSkinToneSwatch);
        if (hex != null && !hex.isEmpty()) {
            try {
                swatch.setBackgroundColor(Color.parseColor(hex));
            } catch (Exception ignored) {
                swatch.setBackgroundResource(R.drawable.bg_shade_swatch);
            }
            meta.setText(hex + " | " + (category == null ? "ITA" : category) + " | " + (Double.isNaN(angle) ? "--" : String.format("%.1f", angle)));
        }
    }

    private void bindScores() {
        bindScore(R.id.progressTexture, R.id.tvTextureScore, "Texture", getIntent().getIntExtra("TEXTURE_SCORE", -1));
        bindScore(R.id.progressPores, R.id.tvPoresScore, "Pores", getIntent().getIntExtra("PORES_SCORE", -1));
        bindScore(R.id.progressHydration, R.id.tvHydrationScore, "Hydration", getIntent().getIntExtra("HYDRATION_SCORE", -1));
    }

    private void bindScore(int progressId, int textId, String label, int score) {
        CircularProgressIndicator indicator = findViewById(progressId);
        TextView text = findViewById(textId);
        if (score >= 0) {
            indicator.setProgressCompat(score, false);
            text.setText(label + " " + score);
        } else {
            indicator.setProgressCompat(0, false);
            text.setText(label);
        }
    }
}
