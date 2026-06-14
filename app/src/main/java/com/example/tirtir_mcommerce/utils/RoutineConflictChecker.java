package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.database.Cursor;

import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.Product;

import java.util.ArrayList;
import java.util.List;

public class RoutineConflictChecker {

    public static class ConflictResult {
        public final String productA;
        public final String productB;
        public final String ingredientA;
        public final String ingredientB;
        public final String reason;
        public final String severity;

        public ConflictResult(String productA, String productB, String ingredientA, String ingredientB, String reason, String severity) {
            this.productA = productA;
            this.productB = productB;
            this.ingredientA = ingredientA;
            this.ingredientB = ingredientB;
            this.reason = reason;
            this.severity = severity;
        }
    }

    public static List<ConflictResult> checkConflicts(Context context, List<Product> routineProducts) {
        List<ConflictResult> conflicts = new ArrayList<>();
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);

        // Simple cross-check of ingredients.
        // Assuming products have ingredients in description_short or a specific field. 
        // For this demo, we'll map product categories or names to common ingredients if ingredients field is null.
        for (int i = 0; i < routineProducts.size(); i++) {
            for (int j = i + 1; j < routineProducts.size(); j++) {
                Product pA = routineProducts.get(i);
                Product pB = routineProducts.get(j);

                List<String> ingredientsA = extractIngredients(pA);
                List<String> ingredientsB = extractIngredients(pB);

                for (String ingA : ingredientsA) {
                    Cursor cursor = dbHelper.searchConflicts(ingA);
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            String ingB = cursor.getString(cursor.getColumnIndexOrThrow("ingredient_b"));
                            if (containsIngredient(ingredientsB, ingB)) {
                                conflicts.add(new ConflictResult(
                                        pA.getName(), pB.getName(),
                                        ingA, ingB,
                                        cursor.getString(cursor.getColumnIndexOrThrow("reason")),
                                        cursor.getString(cursor.getColumnIndexOrThrow("severity"))
                                ));
                            }
                        } while (cursor.moveToNext());
                        cursor.close();
                    }
                }
            }
        }
        return conflicts;
    }

    private static boolean containsIngredient(List<String> list, String target) {
        for (String ing : list) {
            if (ing.toLowerCase().contains(target.toLowerCase())) return true;
        }
        return false;
    }

    private static List<String> extractIngredients(Product p) {
        List<String> list = new ArrayList<>();
        String text = (p.getName() + " " + (p.getDescriptionShort() != null ? p.getDescriptionShort() : "")).toLowerCase();
        
        if (text.contains("retinol")) list.add("Retinol");
        if (text.contains("vitamin c") || text.contains("ascorbic")) list.add("Vitamin C");
        if (text.contains("aha") || text.contains("glycolic") || text.contains("lactic")) list.add("AHA");
        if (text.contains("bha") || text.contains("salicylic")) list.add("BHA");
        if (text.contains("niacinamide")) list.add("Niacinamide");

        return list;
    }
}
