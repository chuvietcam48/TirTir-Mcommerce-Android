package com.example.tirtir_mcommerce.ui.activities;

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
import com.example.tirtir_mcommerce.ui.adapters.ConflictAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class ConflictResultActivity extends AppCompatActivity {
    private ConflictAdapter adapter;
    private TextView tvSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conflict_result);

        Toolbar toolbar = findViewById(R.id.toolbarConflictResult);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSummary = findViewById(R.id.tvConflictSummary);
        RecyclerView rvConflicts = findViewById(R.id.rvConflicts);
        rvConflicts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConflictAdapter();
        rvConflicts.setAdapter(adapter);

        ArrayList<String> ingredients = getIntent().getStringArrayListExtra("INGREDIENTS");
        bindIngredients(ingredients);
        bindConflicts(buildKnownConflicts(ingredients));
        if (getIntent().getBooleanExtra("IS_DEMO_OCR", false)) {
            tvSummary.setText("Demo scan · Product-specific results will appear when live recognition is available.");
            tvSummary.setVisibility(View.VISIBLE);
        }
        bindAddBothAction();
    }

    private void bindAddBothAction() {
        MaterialButton btnAddBoth = findViewById(R.id.btnAddBothToCart);
        String firstProductId = getIntent().getStringExtra("PRODUCT_ID");
        String secondProductId = getIntent().getStringExtra("SECOND_PRODUCT_ID");
        boolean hasTwoProducts = firstProductId != null && !firstProductId.isEmpty()
                && secondProductId != null && !secondProductId.isEmpty();
        btnAddBoth.setVisibility(hasTwoProducts ? View.VISIBLE : View.GONE);
        btnAddBoth.setOnClickListener(v -> Toast.makeText(this,
                "Product details are still loading. Please try again.",
                Toast.LENGTH_SHORT).show());
    }

    private void bindIngredients(ArrayList<String> ingredients) {
        ChipGroup group = findViewById(R.id.chipGroupIngredients);
        group.removeAllViews();
        if (ingredients == null || ingredients.isEmpty()) return;
        for (String ingredient : ingredients) {
            Chip chip = new Chip(this);
            chip.setText(ingredient);
            chip.setChipBackgroundColorResource(R.color.tirtir_red_surface);
            group.addView(chip);
        }
    }

    private void bindConflicts(List<ConflictAdapter.IngredientConflict> conflicts) {
        adapter.submitList(conflicts);
        if (conflicts == null || conflicts.isEmpty()) {
            tvSummary.setVisibility(View.VISIBLE);
        } else {
            tvSummary.setVisibility(View.GONE);
        }
    }

    private List<ConflictAdapter.IngredientConflict> buildKnownConflicts(List<String> ingredients) {
        List<ConflictAdapter.IngredientConflict> conflicts = new ArrayList<>();
        if (containsIngredient(ingredients, "retinol")
                && containsIngredient(ingredients, "glycolic")) {
            conflicts.add(new ConflictAdapter.IngredientConflict(
                    "Retinol",
                    "Glycolic Acid",
                    "Using both in the same routine may increase dryness and irritation. Alternate nights or ask a dermatologist.",
                    "HIGH"));
        }
        return conflicts;
    }

    private boolean containsIngredient(List<String> ingredients, String query) {
        if (ingredients == null) return false;
        for (String ingredient : ingredients) {
            if (ingredient != null && ingredient.toLowerCase().contains(query)) return true;
        }
        return false;
    }
}
