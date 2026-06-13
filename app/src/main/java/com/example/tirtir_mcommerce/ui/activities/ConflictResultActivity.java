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

        bindIngredients(getIntent().getStringArrayListExtra("INGREDIENTS"));
        bindConflicts(new ArrayList<>());
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
                "Ready to add both productIds once API returns price, image, and shade data.",
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
}
