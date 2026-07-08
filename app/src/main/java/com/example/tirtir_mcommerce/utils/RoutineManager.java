package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tirtir_mcommerce.model.Product;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages routine quiz state + client-side product recommendation.
 * All recommendations use the live TirTir product catalog fetched from the API.
 * No hardcoded product IDs or names.
 */
public class RoutineManager {

    private static final String PREFS      = "tirtir_routine_v2";
    private static final String KEY_DONE       = "quiz_done";
    private static final String KEY_AI_RESULT  = "ai_routine_json";
    static final String KEY_SKIN_TYPE           = "skin_type";
    static final String KEY_CONCERNS            = "concerns";
    static final String KEY_GOALS               = "goals";
    static final String KEY_CURRENT             = "current_routine";
    static final String KEY_LEVEL               = "routine_level";

    private final SharedPreferences prefs;

    public RoutineManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    public void saveQuizResult(String skinType, Set<String> concerns,
                               Set<String> goals, String currentRoutine, String routineLevel) {
        prefs.edit()
                .putBoolean(KEY_DONE, true)
                .putString(KEY_SKIN_TYPE, skinType)
                .putStringSet(KEY_CONCERNS, new HashSet<>(concerns))
                .putStringSet(KEY_GOALS, new HashSet<>(goals))
                .putString(KEY_CURRENT, currentRoutine)
                .putString(KEY_LEVEL, routineLevel)
                .apply();
    }

    public void saveAiRoutineResult(String jsonString) {
        prefs.edit().putString(KEY_AI_RESULT, jsonString).apply();
    }

    public String getSavedAiRoutineResult() {
        return prefs.getString(KEY_AI_RESULT, null);
    }

    public void resetQuiz() {
        prefs.edit().clear().apply();
    }

    public boolean isQuizDone() { return prefs.getBoolean(KEY_DONE, false); }
    public String getSkinType()  { return prefs.getString(KEY_SKIN_TYPE, "Normal"); }
    public Set<String> getConcerns() { return prefs.getStringSet(KEY_CONCERNS, new HashSet<>()); }
    public Set<String> getGoals()    { return prefs.getStringSet(KEY_GOALS, new HashSet<>()); }
    public String getRoutineLevel()  { return prefs.getString(KEY_LEVEL, "basic"); }

    // ── Routine Step model ───────────────────────────────────────────────────

    public static class RoutineStep {
        public final String timeOfDay;  // "AM" | "PM"
        public final String stepName;   // "Cleanser", "Toner", etc.
        public final int    order;
        public Product product;
        public String  reason;
        public String  tag;

        public RoutineStep(String timeOfDay, String stepName, int order) {
            this.timeOfDay = timeOfDay;
            this.stepName  = stepName;
            this.order     = order;
        }
    }

    // ── Routine generation ───────────────────────────────────────────────────

    public List<RoutineStep> generateRoutine(List<Product> allProducts) {
        String       skinType = getSkinType();
        Set<String>  concerns = getConcerns();
        Set<String>  goals    = getGoals();
        String       level    = getRoutineLevel();

        List<RoutineStep> am = buildAMSteps(level);
        List<RoutineStep> pm = buildPMSteps(level);

        for (RoutineStep s : am) assignProduct(s, allProducts, skinType, concerns, goals);
        for (RoutineStep s : pm) assignProduct(s, allProducts, skinType, concerns, goals);

        List<RoutineStep> result = new ArrayList<>();
        result.addAll(am);
        result.addAll(pm);
        return result;
    }

    private List<RoutineStep> buildAMSteps(String level) {
        List<RoutineStep> s = new ArrayList<>();
        s.add(new RoutineStep("AM", "Cleanser", 1));
        if (!"minimal".equals(level)) s.add(new RoutineStep("AM", "Toner", 2));
        if ("full".equals(level))     s.add(new RoutineStep("AM", "Serum", 3));
        s.add(new RoutineStep("AM", "Moisturizer", s.size() + 1));
        s.add(new RoutineStep("AM", "Sunscreen",   s.size() + 1));
        return s;
    }

    private List<RoutineStep> buildPMSteps(String level) {
        List<RoutineStep> s = new ArrayList<>();
        s.add(new RoutineStep("PM", "Cleanser", 1));
        if (!"minimal".equals(level)) s.add(new RoutineStep("PM", "Toner", 2));
        if ("full".equals(level))     s.add(new RoutineStep("PM", "Serum", 3));
        s.add(new RoutineStep("PM", "Moisturizer", s.size() + 1));
        return s;
    }

    private void assignProduct(RoutineStep step, List<Product> all,
                               String skinType, Set<String> concerns, Set<String> goals) {
        Product best = null;
        int     bestScore = -1;
        for (Product p : all) {
            if (!matchesStep(p, step.stepName)) continue;
            int score = score(p, skinType, concerns, goals);
            if (score > bestScore) { bestScore = score; best = p; }
        }
        step.product = best;
        step.reason  = buildReason(step.stepName, skinType, concerns);
        step.tag     = buildTag(skinType, concerns);
    }

    private boolean matchesStep(Product p, String step) {
        String cat  = lc(p.getCategory());
        String slug = lc(p.getCategorySlug());
        String name = lc(p.getName());
        switch (step) {
            case "Cleanser":
                return cat.contains("cleanser") || cat.contains("wash") || cat.contains("foam")
                        || slug.contains("cleanser") || name.contains("cleanser") || name.contains("foam");
            case "Toner":
                return (cat.contains("toner") || cat.contains("essence") || slug.contains("toner"))
                        && !cat.contains("set") && !name.contains("set");
            case "Serum":
                return (cat.contains("serum") || cat.contains("ampoule") || slug.contains("serum"))
                        && !cat.contains("set");
            case "Moisturizer":
                return (cat.contains("cream") || cat.contains("moisturizer")
                        || slug.contains("cream") || name.contains("cream") || name.contains("moisturizer"))
                        && !cat.contains("set") && !cat.contains("sunscreen");
            case "Sunscreen":
                return cat.contains("sunscreen") || cat.contains("sun") || slug.contains("sunscreen")
                        || name.contains("sun") || name.contains("spf");
            default:
                return false;
        }
    }

    private int score(Product p, String skinType, Set<String> concerns, Set<String> goals) {
        int s = 0;
        String target      = lc(p.getSkinTypeTarget());
        String mainConcern = lc(p.getMainConcern());
        String ingredients = lc(p.getKeyIngredients());
        String name        = lc(p.getName());

        if (target.contains(lc(skinType))) s += 3;
        if (target.contains("all skin") || target.contains("all types")) s += 1;

        for (String c : concerns) {
            switch (c) {
                case "acne":
                    if (mainConcern.contains("acne") || ingredients.contains("salicylic")
                            || ingredients.contains("niacinamide")) s += 2;
                    break;
                case "dark_spots":
                    if (mainConcern.contains("bright") || mainConcern.contains("dark")
                            || ingredients.contains("vitamin c") || ingredients.contains("niacinamide")) s += 2;
                    break;
                case "dry_flaky":
                    if (mainConcern.contains("hydrat") || ingredients.contains("hyaluronic")
                            || ingredients.contains("ceramide")) s += 2;
                    break;
                case "dull":
                    if (mainConcern.contains("glow") || mainConcern.contains("bright")
                            || ingredients.contains("vitamin c")) s += 2;
                    break;
                case "aging":
                    if (mainConcern.contains("aging") || mainConcern.contains("firm")
                            || ingredients.contains("retinol") || ingredients.contains("peptide")) s += 2;
                    break;
                case "irritation":
                    if (mainConcern.contains("sooth") || mainConcern.contains("calm")
                            || ingredients.contains("centella") || ingredients.contains("cica")) s += 2;
                    if (name.contains("matcha")) s += 1;
                    break;
                case "pores":
                    if (ingredients.contains("niacinamide") || mainConcern.contains("pore")) s += 2;
                    break;
                case "excess_oil":
                    if (target.contains("oily") || mainConcern.contains("oil control")) s += 2;
                    break;
            }
        }
        for (String g : goals) {
            switch (g) {
                case "hydration":
                    if (mainConcern.contains("hydrat") || ingredients.contains("hyaluronic")) s += 1; break;
                case "brightening":
                    if (mainConcern.contains("bright") || ingredients.contains("vitamin c")) s += 1; break;
                case "anti_acne":
                    if (mainConcern.contains("acne") || ingredients.contains("salicylic")) s += 1; break;
                case "anti_aging":
                    if (mainConcern.contains("aging") || ingredients.contains("retinol")) s += 1; break;
                case "sun_protection":
                    if (name.contains("spf") || name.contains("sun")) s += 1; break;
            }
        }
        if ("sensitive".equalsIgnoreCase(skinType)) {
            if (name.contains("matcha") || mainConcern.contains("calm") || ingredients.contains("centella")) s += 2;
        }
        return s;
    }

    private String buildReason(String step, String skinType, Set<String> concerns) {
        switch (step) {
            case "Cleanser":
                if (concerns.contains("acne") || "Oily".equalsIgnoreCase(skinType))
                    return "Removes excess oil and helps prevent breakouts.";
                if ("Sensitive".equalsIgnoreCase(skinType))
                    return "Soft formula that cleanses without irritating skin.";
                return "Removes impurities and preps skin for the next steps.";
            case "Toner":
                if (concerns.contains("dry_flaky") || "Dry".equalsIgnoreCase(skinType))
                    return "Replenishes moisture right after cleansing.";
                if (concerns.contains("acne"))
                    return "Balances pH and helps reduce breakout frequency.";
                return "Balances skin and boosts absorption of serums.";
            case "Serum":
                if (concerns.contains("dark_spots") || concerns.contains("dull"))
                    return "Targets dark spots and brightens overall skin tone.";
                if (concerns.contains("acne"))
                    return "Concentrated actives to fight blemishes and calm skin.";
                if (concerns.contains("aging"))
                    return "Firms and smooths skin with targeted active ingredients.";
                return "Delivers concentrated actives for your key concern.";
            case "Moisturizer":
                if ("Oily".equalsIgnoreCase(skinType))
                    return "Lightweight formula that hydrates without clogging pores.";
                if ("Dry".equalsIgnoreCase(skinType))
                    return "Rich texture to deeply nourish and lock in moisture.";
                return "Seals in moisture and supports the skin barrier.";
            case "Sunscreen":
                return "Essential daily SPF to prevent UV damage and dark spots.";
            default:
                return "Selected based on your skin profile.";
        }
    }

    private String buildTag(String skinType, Set<String> concerns) {
        if (concerns.contains("acne"))       return "Anti-acne";
        if (concerns.contains("dark_spots")) return "Brightening";
        if (concerns.contains("irritation")) return "Sensitive skin friendly";
        if ("Oily".equalsIgnoreCase(skinType))   return "For oily skin";
        if ("Dry".equalsIgnoreCase(skinType))    return "For dry skin";
        if ("Sensitive".equalsIgnoreCase(skinType)) return "Sensitive-safe";
        return "Hydrating";
    }

    // ── Combo detection ──────────────────────────────────────────────────────

    public List<Product> findCombos(List<Product> all) {
        List<Product> combos = new ArrayList<>();
        for (Product p : all) {
            String cat  = lc(p.getCategory());
            String name = lc(p.getName());
            if (cat.contains("set") || cat.contains("kit") || cat.contains("duo")
                    || name.contains("set") || name.contains("duo") || name.contains("kit")) {
                combos.add(p);
            }
        }
        return combos;
    }

    private String lc(String s) { return s == null ? "" : s.toLowerCase(Locale.ENGLISH); }
}
