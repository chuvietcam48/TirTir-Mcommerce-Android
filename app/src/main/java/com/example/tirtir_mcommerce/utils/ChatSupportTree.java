package com.example.tirtir_mcommerce.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Structured Beauty Advisor decision tree (Shopee/TikTok-style guided support).
 *
 * TODO(backend): This tree is a TEMPORARY frontend mirror of the chat dataset,
 * used while the Render backend deploy is pending. Once the backend is deployed
 * and seeded, these nodes must be moved into the `chatCategories` collection
 * (fields: id, parentId, title, description, icon, level, isLeaf, intentCode)
 * and leaf answers served via POST /chat with `intentCode`. Keep intentCodes
 * here in sync with `suggestedQuestions` seed data.
 */
public final class ChatSupportTree {

    /** Backend-compatible node: mirrors chatCategories document shape. */
    public static class Node {
        public final String emoji;       // icon
        public final String title;
        public final String desc;        // description
        public final String question;    // guided question shown as submenu header
        public final String intentCode;  // dataset intent (null for local-only)
        public final String answer;      // TODO(backend): local fallback until dataset serves this
        public final List<Node> children;

        Node(String emoji, String title, String desc, String question,
             String intentCode, String answer, List<Node> children) {
            this.emoji = emoji; this.title = title; this.desc = desc;
            this.question = question; this.intentCode = intentCode;
            this.answer = answer; this.children = children;
        }

        public boolean isLeaf() { return children == null || children.isEmpty(); }
    }

    private static Node leaf(String emoji, String title, String desc, String intentCode, String answer) {
        return new Node(emoji, title, desc, null, intentCode, answer, null);
    }

    private static Node branch(String emoji, String title, String desc, String question, Node... children) {
        return new Node(emoji, title, desc, question, null, null, Arrays.asList(children));
    }

    private ChatSupportTree() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static List<Node> buildTree() {
        List<Node> root = new ArrayList<>();
        root.add(skincareRoutine());
        root.add(productRecommendation());
        root.add(ingredientSafety());
        root.add(ordersSupport());
        root.add(accountSupport());
        return root;
    }

    // ── 1. Skincare Routine ───────────────────────────────────────────────────

    private static Node skincareRoutine() {
        Node morning = branch("☀️", "Morning Routine", "Steps for your morning skincare",
                "Which morning routine do you need?",
                leaf("🧼", "Basic AM routine", "The essential morning steps", "routine_morning_order",
                        "Basic morning routine:\n\n1. Gentle cleanser\n2. Toner\n3. Serum (vitamin C or hydrating)\n4. Moisturizer\n5. Sunscreen SPF30+ (never skip!)\n\nTip: 3–4 simple steps done daily beat a 10-step routine done sometimes."),
                leaf("🛢️", "AM routine for oily skin", "Shine control from the start", "routine_am_oily",
                        "Morning routine for oily skin:\n\n1. Foaming/gel cleanser\n2. Niacinamide or BHA toner\n3. Niacinamide serum (controls sebum)\n4. Oil-free gel moisturizer\n5. Matte-finish fluid sunscreen\n\nAvoid heavy creams and facial oils in the morning."),
                leaf("🏜️", "AM routine for dry skin", "Lock in hydration all day", "routine_am_dry",
                        "Morning routine for dry skin:\n\n1. Cream or milk cleanser (or just rinse)\n2. Hydrating toner with hyaluronic acid\n3. Hyaluronic acid serum on damp skin\n4. Rich moisturizer with ceramides\n5. Hydrating cream sunscreen\n\nApply moisturizer while skin is still slightly damp."),
                leaf("🌸", "AM routine for sensitive skin", "Gentle, low-irritation morning care", "routine_am_sensitive",
                        "Morning routine for sensitive skin:\n\n1. Fragrance-free gentle cleanser (or water rinse)\n2. Soothing toner (centella, no alcohol)\n3. Light hydrating serum\n4. Ceramide moisturizer\n5. Mineral (zinc oxide) sunscreen\n\nKeep it minimal — fewer products, fewer triggers."),
                leaf("🧴", "AM routine with sunscreen", "How sunscreen fits your routine", "routine_am_spf",
                        "Sunscreen always goes LAST in your morning skincare:\n\nCleanser → Toner → Serum → Moisturizer → Sunscreen\n\n• Use SPF30+ daily, SPF50+ outdoors\n• Wait 1–2 minutes after moisturizer before applying\n• Use about 2 finger-lengths for face and neck\n• Reapply every 2–3 hours when outside"));

        Node night = branch("🌙", "Night Routine", "Evening repair and treatment order",
                "Which night routine do you need?",
                leaf("🧼", "Basic PM routine", "The essential evening steps", "routine_night_order",
                        "Basic night routine:\n\n1. Makeup remover / cleansing oil\n2. Water-based cleanser (double cleanse)\n3. Toner\n4. Treatment serum\n5. Moisturizer or night cream\n\nNight is when skin repairs — never sleep with makeup on."),
                leaf("🎯", "PM routine for acne-prone skin", "Treat breakouts overnight", "routine_pm_acne",
                        "Night routine for acne-prone skin:\n\n1. Double cleanse (oil + gel cleanser)\n2. BHA (salicylic acid) toner 2–3×/week\n3. Niacinamide or spot treatment serum\n4. Light, non-comedogenic gel moisturizer\n\nDon't layer too many actives at once — start slow and be consistent."),
                leaf("🏜️", "PM routine for dry skin", "Overnight deep hydration", "routine_pm_dry",
                        "Night routine for dry skin:\n\n1. Cleansing balm + gentle cleanser\n2. Hydrating toner (apply in layers)\n3. Hyaluronic acid serum\n4. Rich night cream with ceramides\n5. Optional: facial oil as the last step\n\n2–3 nights a week, add a hydrating sleeping mask."),
                leaf("🌸", "PM routine for sensitive skin", "Calm and repair overnight", "routine_pm_sensitive",
                        "Night routine for sensitive skin:\n\n1. Gentle single cleanse (fragrance-free)\n2. Soothing toner with centella\n3. Barrier-repair serum (ceramide, panthenol)\n4. Simple moisturizer, no fragrance\n\nSkip exfoliating acids while skin feels reactive."),
                leaf("💉", "PM routine with treatment/serum", "Using actives at night safely", "routine_pm_treatment",
                        "Using treatment serums at night:\n\n1. Cleanse thoroughly\n2. Toner\n3. ONE active treatment (retinol, AHA/BHA, or vitamin C — not all together)\n4. Wait 1–2 minutes\n5. Moisturizer to seal and buffer irritation\n\nAlternate actives on different nights (e.g. retinol Mon/Thu, AHA Sat)."));

        Node productOrder = branch("🧴", "Product Order", "Which product goes first?",
                "Which order question do you have?",
                leaf("💧", "Serum before or after moisturizer?", "The classic layering question", "routine_serum_before_moisturizer",
                        "Serum should be used BEFORE moisturizer.\n\nCorrect order:\n1. Cleanser\n2. Toner\n3. Serum (smallest molecules, absorbs deepest)\n4. Moisturizer (locks everything in)\n5. Sunscreen (AM only)\n\nWhy: serums are lightweight and penetrate deeper. Moisturizer creates a barrier — applied first, it would block the serum."),
                leaf("💦", "Toner before or after serum?", "Prep vs treat", "order_toner_serum",
                        "Toner goes BEFORE serum.\n\nOrder: Cleanser → Toner → Serum\n\nToner rebalances your skin's pH and adds a first layer of hydration, preparing skin to absorb the serum better. Apply serum while skin is still damp from toner for best absorption."),
                leaf("☀️", "Sunscreen before or after moisturizer?", "The final AM step", "order_spf_moisturizer",
                        "Sunscreen goes AFTER moisturizer — it is always the LAST skincare step in the morning.\n\nOrder: Serum → Moisturizer → Sunscreen\n\nSunscreen needs to sit on top of skin to form its protective film. Applying moisturizer over it dilutes and breaks that film."),
                leaf("🌙", "Can I use serum at night?", "Serums around the clock", "order_serum_night",
                        "Yes — night is actually the BEST time for many serums.\n\n• Retinol, AHA/BHA, peptide serums: night only or mainly\n• Hyaluronic acid, niacinamide: fine morning and night\n• Vitamin C: usually morning, but night works too\n\nAt night skin is in repair mode and there's no sunlight to break down actives."),
                leaf("🧴", "Can I use moisturizer after serum?", "Sealing in your actives", "order_moist_after_serum",
                        "Yes — you SHOULD use moisturizer after serum.\n\n1. Apply serum\n2. Wait 30–60 seconds to absorb\n3. Apply moisturizer on top\n\nMoisturizer seals the serum in and prevents water loss. Skipping it can leave the serum evaporating off your skin."));

        Node bySkinType = branch("💧", "Routine by Skin Type", "A routine matched to your skin",
                "What is your skin type?",
                skinTypeLevels("🛢️", "Oily skin", "oily"),
                skinTypeLevels("🏜️", "Dry skin", "dry"),
                skinTypeLevels("🔄", "Combination skin", "combination"),
                skinTypeLevels("🌸", "Sensitive skin", "sensitive"),
                skinTypeLevels("😊", "Normal skin", "normal"),
                leaf("🤔", "I'm not sure", "Find out your skin type first", "ai_scan_how_to_use",
                        "No problem! The fastest way to find your skin type:\n\n📸 Use TIRTIR AI Skin Scan\n1. Open the AI SCAN tab below\n2. Take a selfie in natural light\n3. Get your skin type + a personalized routine\n\nOr observe your skin 1 hour after washing:\n• Shiny all over → oily\n• Tight/flaky → dry\n• Shiny T-zone only → combination\n• Easily red/itchy → sensitive"));

        Node byConcern = branch("🎯", "Routine by Concern", "Target your main skin issue",
                "What is your main concern?",
                leaf("🔴", "Acne", "Clear breakouts step by step", "concern_acne_routine",
                        "Routine for acne-prone skin:\n\nAM: gentle cleanser → niacinamide serum → oil-free moisturizer → non-comedogenic SPF\nPM: double cleanse → BHA toner (2–3×/week) → spot treatment → light gel moisturizer\n\nKey rules: don't pick, change pillowcases often, and be patient — 4–6 weeks for results."),
                leaf("😶", "Dull skin", "Bring back your glow", "concern_dull_routine",
                        "Routine for dull skin:\n\nAM: cleanser → vitamin C serum → moisturizer → SPF (UV makes dullness worse!)\nPM: double cleanse → AHA exfoliant 2–3×/week → hydrating serum → night cream\n\nDullness is usually dead skin buildup + dehydration — gentle exfoliation + hydration fixes both."),
                leaf("🟤", "Dark spots", "Fade hyperpigmentation", "concern_darkspot_routine",
                        "Routine for dark spots:\n\nAM: cleanser → vitamin C serum → moisturizer → SPF50 (essential — sun darkens spots)\nPM: cleanse → niacinamide or azelaic acid serum → moisturizer\n\nConsistency + sunscreen matter more than any single product. Expect 8–12 weeks for visible fading."),
                leaf("🕳️", "Large pores", "Refine skin texture", "concern_pores_routine",
                        "Routine for large pores:\n\nAM: gel cleanser → niacinamide serum → light moisturizer → matte SPF\nPM: double cleanse → BHA toner (unclogs pores) → hydrating serum → gel moisturizer\n\nPores can't shrink permanently, but keeping them clean + niacinamide makes them look much smaller."),
                leaf("💧", "Dryness", "Repair a thirsty skin barrier", "concern_dry_routine",
                        "Routine for dryness:\n\nAM: cream cleanser → hyaluronic acid serum on damp skin → rich moisturizer → hydrating SPF\nPM: gentle cleanse → layered hydrating toner → HA serum → ceramide night cream → optional facial oil\n\nAvoid hot water and harsh foaming cleansers — they strip your barrier."),
                leaf("🌡️", "Redness / irritation", "Calm reactive skin", "concern_redness_routine",
                        "Routine for redness & irritation:\n\n1. Stop all actives (retinol, acids) for 1–2 weeks\n2. Fragrance-free gentle cleanser\n3. Centella or panthenol soothing serum\n4. Ceramide barrier cream\n5. Mineral sunscreen only\n\nIf redness persists or burns, see a dermatologist — it could be rosacea or a damaged barrier."));

        return branch("🌿", "Skincare Routine", "Build or understand your skincare routine",
                "What would you like help with?",
                morning, night, productOrder, bySkinType, byConcern);
    }

    /** Guided Q&A: skin type → routine level → generated answer. */
    private static Node skinTypeLevels(String emoji, String title, String type) {
        return branch(emoji, title, "Routines for " + type + " skin",
                "What routine level do you want?",
                leaf("1️⃣", "Minimal routine", "3 quick daily steps", "routine_" + type + "_minimal", routineAnswer(type, 0)),
                leaf("2️⃣", "Basic routine", "The recommended core routine", "routine_" + type + "_basic", routineAnswer(type, 1)),
                leaf("3️⃣", "Full routine", "Complete multi-step care", "routine_" + type + "_full", routineAnswer(type, 2)));
    }

    private static String routineAnswer(String type, int level) {
        String cleanser, serum, moisturizer, spf, tip;
        switch (type) {
            case "oily":
                cleanser = "Foaming/gel cleanser"; serum = "Niacinamide serum";
                moisturizer = "Oil-free gel moisturizer"; spf = "Matte fluid SPF30+";
                tip = "Don't skip moisturizer — dehydrated oily skin produces MORE oil."; break;
            case "dry":
                cleanser = "Cream cleanser"; serum = "Hyaluronic acid serum (on damp skin)";
                moisturizer = "Rich ceramide cream"; spf = "Hydrating cream SPF30+";
                tip = "Layer hydration: apply products on slightly damp skin."; break;
            case "combination":
                cleanser = "Balanced gel cleanser"; serum = "Niacinamide serum";
                moisturizer = "Lightweight lotion"; spf = "Fluid SPF30+";
                tip = "Treat zones differently: mattify the T-zone, hydrate the cheeks."; break;
            case "sensitive":
                cleanser = "Fragrance-free gentle cleanser"; serum = "Centella soothing serum";
                moisturizer = "Ceramide barrier cream"; spf = "Mineral (zinc oxide) SPF30+";
                tip = "Always patch test new products behind your ear for 24h first."; break;
            default: // normal
                cleanser = "Gentle daily cleanser"; serum = "Vitamin C (AM) or hydrating serum";
                moisturizer = "Balanced moisturizer"; spf = "SPF30+ of your preferred texture";
                tip = "Lucky you! Focus on consistency and sun protection."; break;
        }
        StringBuilder sb = new StringBuilder();
        String cap = type.substring(0, 1).toUpperCase() + type.substring(1);
        if (level == 0) {
            sb.append("Minimal routine for ").append(type).append(" skin (3 steps):\n\n")
              .append("AM & PM:\n1. ").append(cleanser).append("\n2. ").append(moisturizer)
              .append("\n3. ").append(spf).append(" (AM only)\n");
        } else if (level == 1) {
            sb.append("Basic routine for ").append(type).append(" skin:\n\n")
              .append("AM:\n1. ").append(cleanser).append("\n2. Toner\n3. ").append(serum)
              .append("\n4. ").append(moisturizer).append("\n5. ").append(spf)
              .append("\n\nPM:\n1. Double cleanse\n2. Toner\n3. ").append(serum)
              .append("\n4. ").append(moisturizer).append("\n");
        } else {
            sb.append("Full routine for ").append(type).append(" skin:\n\n")
              .append("AM:\n1. ").append(cleanser).append("\n2. Toner\n3. Essence\n4. ").append(serum)
              .append("\n5. Eye cream\n6. ").append(moisturizer).append("\n7. ").append(spf)
              .append("\n\nPM:\n1. Cleansing oil/balm\n2. ").append(cleanser)
              .append("\n3. Toner\n4. Essence\n5. Treatment serum\n6. Eye cream\n7. Night cream")
              .append("\n\nWeekly: exfoliate 1–2×, mask 2–3×\n");
        }
        sb.append("\n💡 ").append(cap).append(" skin tip: ").append(tip);
        return sb.toString();
    }

    // ── 2. Product Recommendation ─────────────────────────────────────────────

    private static Node productRecommendation() {
        Node byType = branch("💧", "By Skin Type", "Products matched to your skin",
                "What is your skin type?",
                leaf("🛢️", "Oily skin products", "Oil control picks", "product_rec_skin_type",
                        "Best product types for oily skin:\n\n• Foaming/gel cleanser\n• Niacinamide or BHA toner\n• Niacinamide serum\n• Oil-free gel moisturizer\n• Matte fluid sunscreen\n\n🛍️ In the app: Shop → filter by category, or run AI Scan for personalized picks."),
                leaf("🏜️", "Dry skin products", "Deep hydration picks", "product_rec_skin_type",
                        "Best product types for dry skin:\n\n• Cream/milk cleanser\n• Hydrating toner (hyaluronic acid)\n• HA or ceramide serum\n• Rich cream moisturizer\n• Hydrating cream sunscreen\n\n🛍️ In the app: Shop → filter by category, or run AI Scan for personalized picks."),
                leaf("🔄", "Combination skin products", "Balanced picks", "product_rec_skin_type",
                        "Best product types for combination skin:\n\n• Balanced gel cleanser\n• Alcohol-free hydrating toner\n• Niacinamide serum\n• Lightweight lotion/gel-cream\n• Fluid sunscreen\n\n🛍️ In the app: Shop → filter by category, or run AI Scan for personalized picks."),
                leaf("🌸", "Sensitive skin products", "Gentle, fragrance-free picks", "product_rec_skin_type",
                        "Best product types for sensitive skin:\n\n• Fragrance-free gentle cleanser\n• Centella soothing toner\n• Barrier-repair serum (ceramide/panthenol)\n• Simple ceramide moisturizer\n• Mineral (zinc oxide) sunscreen\n\n🛍️ In the app: Shop → filter by category, or run AI Scan for personalized picks."));

        String[][] concerns = {
                {"🔴", "Acne", "acne", "salicylic acid (BHA), niacinamide, tea tree"},
                {"😶", "Dullness", "dullness", "vitamin C, AHA (glycolic/lactic acid), niacinamide"},
                {"🟤", "Dark spots", "dark spots", "vitamin C, niacinamide, azelaic acid"},
                {"🕳️", "Large pores", "large pores", "niacinamide, BHA (salicylic acid)"},
                {"💧", "Dryness", "dryness", "hyaluronic acid, ceramides, squalane"},
                {"🌡️", "Redness", "redness", "centella asiatica, panthenol, madecassoside"},
        };
        Node[] concernNodes = new Node[concerns.length];
        for (int i = 0; i < concerns.length; i++) {
            String[] c = concerns[i];
            concernNodes[i] = branch(c[0], c[1], "Products that target " + c[2],
                    "What product type do you prefer?",
                    concernTypeLeaf(c[1], c[2], c[3], "Toner / Essence"),
                    concernTypeLeaf(c[1], c[2], c[3], "Serum"),
                    concernTypeLeaf(c[1], c[2], c[3], "Moisturizer"),
                    concernTypeLeaf(c[1], c[2], c[3], "Sunscreen"),
                    concernTypeLeaf(c[1], c[2], c[3], "Combo / Set"));
        }
        Node byConcern = branch("🎯", "By Skin Concern", "Solve your main skin issue",
                "What is your main concern?", concernNodes);

        Node byCategory = branch("🧴", "By Product Category", "Browse by product type",
                "Which product category?",
                leaf("🧼", "Cleanser", "First step of every routine", "product_cat_cleanser",
                        "Choosing a cleanser:\n\n• Oily skin → foaming/gel cleanser\n• Dry skin → cream or milk cleanser\n• Sensitive → fragrance-free, no SLS\n• All types → low pH (5–6) is ideal\n\n🛍️ Browse: Shop → Cleanser category in the app."),
                leaf("✨", "Toner / Essence", "Hydrate and prep skin", "product_toner_essence",
                        "Toner vs Essence:\n\n• Toner: first layer after cleansing, rebalances pH, light hydration\n• Essence: more concentrated, deeper hydration, applied after toner\n\nOrder: Toner → Essence → Serum\n\n🛍️ Browse: Shop → Toner category in the app."),
                leaf("💉", "Serum", "Targeted concentrated treatment", "product_cat_serum",
                        "Choosing a serum by goal:\n\n• Brightening → vitamin C\n• Oil/pores → niacinamide\n• Hydration → hyaluronic acid\n• Anti-aging → retinol, peptides\n• Acne → BHA, niacinamide\n\n🛍️ Browse: Shop → Serum category in the app."),
                leaf("🧴", "Moisturizer", "Seal in hydration", "product_cat_moisturizer",
                        "Choosing a moisturizer:\n\n• Oily → oil-free gel\n• Dry → rich cream with ceramides\n• Combination → lightweight lotion\n• Sensitive → simple, fragrance-free\n\n🛍️ Browse: Shop → Cream category in the app."),
                leaf("☀️", "Sunscreen", "Daily UV protection", "product_sunscreen_recommend",
                        "Choosing sunscreen:\n\n• SPF30+ daily, SPF50+ outdoors\n• Oily → matte fluid/gel\n• Dry → hydrating cream\n• Sensitive → mineral (zinc oxide)\n\nIt's the #1 anti-aging product — wear it every day.\n\n🛍️ Browse: Shop → Sunscreen in the app."),
                leaf("💄", "Cushion / Makeup", "Foundation & color — try AI Scan", "product_recommend_cushion",
                        "For cushion and makeup, shade matching matters most!\n\n📸 Use AI Scan (tab below) to analyze your skin tone, then check:\n• Mask Fit Red Cushion — high coverage, oily/combination skin\n• Blur Fit Cushion — natural finish, dry/normal skin\n\n💡 You can also try AR TRY-ON on any cushion product page.")),
             bySets = branch("🎁", "Combo / Set Recommendation", "Curated sets & better value",
                "Which combo are you looking for?",
                leaf("🧼", "Basic skincare combo", "Starter essentials set", "promo_combo_sets",
                        "Basic skincare combo (great for beginners):\n\n• Gentle cleanser + Toner + Moisturizer\n• Everything you need for a 3-step routine\n• Usually 10–20% cheaper than buying separately\n\n🛍️ See current sets: Promotions tab → Combo deals."),
                leaf("💧", "Hydration combo", "For dry, thirsty skin", "promo_combo_sets",
                        "Hydration combo:\n\n• Hydrating toner + HA serum + ceramide cream\n• Designed to layer moisture for dry skin\n\n🛍️ See current sets: Promotions tab → Combo deals."),
                leaf("✨", "Brightening combo", "Glow & even skin tone", "promo_combo_sets",
                        "Brightening combo:\n\n• Vitamin C serum + brightening toner + SPF\n• Fades dullness and dark spots — SPF included because sun undoes brightening\n\n🛍️ See current sets: Promotions tab → Combo deals."),
                leaf("🌸", "Sensitive skin combo", "Gentle barrier-care set", "promo_combo_sets",
                        "Sensitive skin combo:\n\n• Fragrance-free cleanser + centella serum + barrier cream\n• Everything tested for reactive skin\n\n🛍️ See current sets: Promotions tab → Combo deals."),
                leaf("💰", "Best value combo", "Biggest savings right now", "promo_combo_sets",
                        "Best value combos:\n\n• Check the Promotions tab for current flash deals\n• Cushion + refill sets typically save the most\n• Holiday gift sets offer premium value\n\n🛍️ Go to: Promotions tab → sort by discount."));

        return branch("✨", "Product Recommendation", "Find products based on skin type or concern",
                "How would you like to find products?",
                byType, byConcern, byCategory, bySets);
    }

    private static Node concernTypeLeaf(String concernTitle, String concern, String ingredients, String productType) {
        String emoji;
        switch (productType) {
            case "Toner / Essence": emoji = "✨"; break;
            case "Serum":           emoji = "💉"; break;
            case "Moisturizer":     emoji = "🧴"; break;
            case "Sunscreen":       emoji = "☀️"; break;
            default:                emoji = "🎁"; break;
        }
        String code = "rec_" + concern.replaceAll("\\s+", "_") + "_" + productType.toLowerCase().replaceAll("[^a-z]+", "_");
        String answer;
        if (productType.equals("Combo / Set")) {
            answer = "For " + concern + ", a combo set is a smart pick:\n\n"
                    + "• Look for sets featuring: " + ingredients + "\n"
                    + "• Sets are formulated to work together and cost less\n\n"
                    + "🛍️ In the app: Promotions tab → Combo deals, or Shop and filter by your concern.";
        } else {
            answer = "For " + concern + ", choose a " + productType.toLowerCase() + " with:\n\n"
                    + "• Key ingredients: " + ingredients + "\n"
                    + "• Apply consistently for 4–8 weeks to see results\n"
                    + (productType.equals("Sunscreen")
                        ? "• SPF is essential — UV worsens " + concern + "\n"
                        : "• Pair with daily SPF to protect your progress\n")
                    + "\n🛍️ In the app: Shop → " + productType + " category. For personalized picks, try AI Scan!";
        }
        return leaf(emoji, productType, productType + " for " + concern, code, answer);
    }

    // ── 3. Ingredient Safety ──────────────────────────────────────────────────

    private static Node ingredientSafety() {
        Node combination = branch("🧪", "Product Combination", "Check what layers safely",
                "Which combination do you want to check?",
                leaf("💧", "Serum + moisturizer", "The everyday pair", "ingredient_serum_moisturizer_combine",
                        "Serum + moisturizer: ✅ SAFE — and recommended!\n\nDon't mix them in your hand. Apply in order:\n1. Serum first\n2. Wait 30–60 seconds\n3. Moisturizer on top\n\nMixing directly dilutes the serum and can break both formulas."),
                leaf("💦", "Toner + serum", "Prep then treat", "ingredient_toner_serum_combine",
                        "Toner + serum: ✅ SAFE — a classic pairing.\n\nOrder: toner first, serum right after while skin is damp.\n\nOne caution: if BOTH are exfoliating (AHA/BHA toner + acid serum), use them on different days to avoid over-exfoliation."),
                leaf("☀️", "Moisturizer + sunscreen", "The morning finish", "ingredient_moist_spf_combine",
                        "Moisturizer + sunscreen: ✅ SAFE — the standard AM finish.\n\n1. Moisturizer first\n2. Wait 1–2 minutes to absorb\n3. Sunscreen last\n\nNever mix them together — it breaks the sunscreen's protective film."),
                leaf("💉", "Multiple serums", "Layering more than one active", "ingredient_multiple_serums",
                        "Multiple serums: ⚠️ DEPENDS on the combination.\n\n✅ Safe together: hyaluronic acid + niacinamide + peptides\n⚠️ Use with care: vitamin C + niacinamide (fine in modern formulas)\n❌ Avoid same routine: retinol + AHA/BHA, vitamin C + retinol\n\nRule: max 2 serums per routine, thinnest texture first."),
                leaf("⚗️", "Treatment every day?", "How often to use actives", "ingredient_treatment_daily",
                        "Can you use treatments daily? Depends on the active:\n\n• Niacinamide, hyaluronic acid: ✅ daily, AM & PM\n• Vitamin C: ✅ daily (mornings)\n• AHA/BHA: ⚠️ 2–3×/week, work up slowly\n• Retinol: ⚠️ start 1–2×/week, increase over a month\n\nIf skin stings, flakes, or reddens — cut back frequency."));

        Node sensitive = branch("🌸", "Sensitive Skin", "Ingredient safety for reactive skin",
                "What do you need to know?",
                leaf("🚫", "Ingredients to avoid", "Common irritation triggers", "ingredient_sensitive_skin",
                        "Sensitive skin — ingredients to AVOID:\n\n❌ Fragrance (parfum) & essential oils\n❌ Denatured alcohol\n❌ SLS/SLES sulfates\n❌ High-strength AHA/BHA\n❌ Menthol / peppermint\n\n✅ Look for instead: centella, ceramides, panthenol, hyaluronic acid, zinc oxide SPF."),
                leaf("✅", "Products suitable for sensitive skin", "Safe picks", "product_rec_skin_type",
                        "Products that suit sensitive skin:\n\n• Fragrance-free gentle cleanser\n• Alcohol-free centella toner\n• Ceramide/panthenol barrier serum\n• Simple moisturizer (short ingredient list)\n• Mineral sunscreen (zinc oxide)\n\n🛍️ In the app: Shop → filter, or run AI Scan for personalized safe picks."),
                leaf("🧪", "How to test a new product", "Patch testing 101", "ingredient_patch_test",
                        "How to patch test a new product:\n\n1. Apply a small amount behind your ear or inner forearm\n2. Wait 24–48 hours\n3. No redness/itch? Try it on a small face area for 2–3 days\n4. Then use normally\n\nIntroduce ONE new product at a time, one week apart."),
                leaf("🆘", "What to do if irritation happens", "Rescue steps", "ingredient_irritation_help",
                        "If your skin gets irritated:\n\n1. STOP all actives immediately (acids, retinol, vitamin C)\n2. Rinse with cool water, no scrubbing\n3. Use only: gentle cleanser + ceramide moisturizer\n4. Mineral SPF if going out\n5. Resume products one at a time after 1–2 weeks\n\n⚠️ See a dermatologist if it burns, swells, or lasts over a week."));

        Node acne = branch("🎯", "Acne-prone Skin", "Ingredient safety for breakout-prone skin",
                "What do you need to know?",
                leaf("🚫", "Ingredients to avoid", "Pore-clogging ingredients", "ingredient_acne_safe",
                        "Acne-prone skin — ingredients to AVOID:\n\n❌ Coconut oil (highly comedogenic)\n❌ Lanolin\n❌ Isopropyl myristate\n❌ Heavy silicones & mineral oil in rich creams\n❌ Fragrance (can inflame active acne)\n\n✅ Safe & helpful: salicylic acid (BHA), niacinamide, azelaic acid, zinc, tea tree.\n\n💡 Check products at CosDNA/INCIDecoder before buying."),
                leaf("✅", "Safe routine for acne-prone skin", "A breakout-friendly routine", "routine_pm_acne",
                        "Safe routine for acne-prone skin:\n\nAM: gentle gel cleanser → niacinamide serum → oil-free gel moisturizer → non-comedogenic SPF\nPM: double cleanse → BHA 2–3×/week → light moisturizer\n\nGolden rules: never skip moisturizer, introduce actives slowly, don't combine BHA + retinol on the same night."),
                leaf("✨", "Brightening products with acne?", "Mixing goals safely", "ingredient_brightening_acne",
                        "Can you use brightening products with acne? ✅ YES, carefully:\n\n• Niacinamide: perfect — treats acne AND brightens\n• Azelaic acid: fades acne marks + fights breakouts\n• Vitamin C: fine, but start low strength (10%)\n⚠️ Avoid layering vitamin C + BHA in the same routine — alternate AM/PM instead."),
                leaf("🧴", "Moisturizer with acne?", "Yes — here's why", "ingredient_moisturizer_acne",
                        "Should you moisturize acne-prone skin? ✅ ABSOLUTELY.\n\nSkipping moisturizer makes skin produce MORE oil, worsening breakouts. Acne treatments also dry skin out — moisturizer keeps your barrier strong.\n\nChoose: oil-free, non-comedogenic gel moisturizer.\nAvoid: heavy creams with coconut oil or lanolin."));

        return branch("🔬", "Ingredient Safety", "Check compatibility and ingredient concerns",
                "What would you like to check?",
                combination, sensitive, acne);
    }

    // ── 4. Orders & Shopping Support ──────────────────────────────────────────

    private static Node ordersSupport() {
        Node status = branch("📍", "Order Status", "Track your orders",
                "Which orders do you want to check?",
                leaf("💳", "Check paid orders", "Orders you've paid for", "order_check_status",
                        "To check your PAID orders:\n\n1. Go to Profile tab → My Orders\n2. Select the \"Processing\" or \"Shipping\" tab\n3. Tap any order for tracking details\n\nPaid orders show: payment confirmed → packing → handed to courier → delivering."),
                leaf("⏳", "Check pending orders", "Awaiting payment or confirmation", "order_check_status",
                        "To check PENDING orders:\n\n1. Profile tab → My Orders\n2. Select the \"Pending\" tab\n3. Complete payment if needed, or wait for shop confirmation\n\n⚠️ Unpaid orders are auto-cancelled after 24 hours."),
                leaf("✅", "Check delivered orders", "Completed order history", "order_check_status",
                        "To check DELIVERED orders:\n\n1. Profile tab → My Orders\n2. Select the \"Delivered\" tab\n3. Tap an order to review items, reorder, or leave a review\n\n💡 Reviewing a delivered product earns you +50 loyalty points!"));

        Node cart = branch("🛒", "Cart / Wishlist", "Manage your cart and saved items",
                "What do you need help with?",
                leaf("➕", "Add product to cart", "How to add items", "cart_add_product",
                        "To add a product to your cart:\n\n1. Open any product page\n2. Choose shade/option if required\n3. Tap \"Add to Cart\"\n4. The cart icon (top right) shows your item count\n\nFrom the cart you can adjust quantity before checkout."),
                leaf("💝", "Move wishlist item to cart", "From saved to purchased", "cart_wishlist_to_cart",
                        "To move a wishlist item to your cart:\n\n1. Profile tab → My Wishlist\n2. Tap the item you want\n3. On its product page, tap \"Add to Cart\"\n\n💡 Wishlist items stay saved even after adding to cart."),
                leaf("🗑️", "Remove item from cart", "Clean up your cart", "cart_remove_item",
                        "To remove an item from your cart:\n\n1. Tap the cart icon (top right)\n2. Swipe left on the item, or tap the trash icon\n3. Confirm removal\n\n💡 Not ready to buy? Tap the heart first to save it to your wishlist before removing."),
                leaf("❤️", "View wishlist", "See your saved products", "cart_wishlist_save_for_later",
                        "To view your wishlist:\n\n1. Go to Profile tab\n2. Tap \"My Wishlist\"\n3. Tap any item to view or buy it\n\nAdd items by tapping the ❤️ icon on any product page."));

        Node changes = branch("❌", "Cancellation / Change", "Cancel or modify an order",
                "What change do you need?",
                leaf("🚫", "Cancel an order", "How and when you can cancel", "order_cancel",
                        "To cancel an order:\n\n✅ Possible while the order is NOT yet packed.\n\n1. Profile tab → My Orders\n2. Select the order\n3. Tap \"Cancel Order\" and choose a reason\n\n❌ Already shipped? You can't cancel — but you can refuse delivery or request a return after receiving.\n\nNeed help? Contact staff or call the hotline."),
                leaf("📍", "Change delivery address", "Update where it ships", "order_change_address",
                        "To change a delivery address:\n\n• Order NOT shipped yet: contact TIRTIR Staff immediately via chat — we'll update it with the courier\n• Order already shipping: address can't be changed; you may refuse delivery and reorder\n\nFor future orders: Profile → Addresses → edit your default address."),
                leaf("📱", "Change phone number", "Update contact for delivery", "order_change_phone",
                        "To change the phone number on an order:\n\n1. Contact TIRTIR Staff via chat (fastest)\n2. Provide your order ID + new phone number\n3. We'll update it with the delivery partner\n\nFor your account: Profile → Edit Profile → update phone number."),
                leaf("💸", "Refund / return request", "Return policy & process", "order_return_refund",
                        "Returns & refunds:\n\n✅ Within 7 days if: product defective, wrong item, or damaged in transit\n❌ Not eligible: opened/used products (unless defective), past 7 days\n\nProcess:\n1. Photo the product + packaging\n2. Contact staff via chat with your order ID\n3. Refund processed in 1–3 business days after approval"));

        Node promos = branch("🎁", "Promotion / Combo", "Deals, vouchers and savings",
                "What are you looking for?",
                leaf("🎟️", "Apply voucher", "How to use a discount code", "promotion_current_vouchers",
                        "To apply a voucher:\n\n1. Add products to cart → Checkout\n2. Tap the \"Voucher\" field\n3. Select an available voucher or enter a code\n4. Discount applies instantly to your total\n\n⚠️ Check minimum order value and expiry date. One voucher per order."),
                leaf("📦", "Check combo discount", "Bundle savings", "promo_combo_sets",
                        "Combo discounts:\n\n• Combos are pre-bundled sets priced 10–20% below buying separately\n• Find them in the Promotions tab → Combo section\n• Discount is already included — no code needed\n\n💡 Cushion + refill combos usually save the most."),
                leaf("🔍", "Find best deal", "Current top savings", "promotion_current_vouchers",
                        "To find the best current deals:\n\n1. Open the Promotions tab\n2. Check Flash Sale (time-limited, deepest cuts)\n3. Compare combo sets vs single products\n4. Stack: sale price + voucher at checkout when allowed\n\n💎 Loyalty members get extra member-only offers!"),
                leaf("⭐", "Top offers", "What's hot right now", "promotion_current_vouchers",
                        "Today's top offer types:\n\n• 🔥 Flash Sales — biggest % off, limited time\n• 🎁 Combo sets — bundled savings\n• 🎟️ New-user voucher — first order discount\n• 💎 Loyalty rewards — redeem points for discounts\n\nCheck the Promotions tab for live offers!"));

        return branch("📦", "Orders & Shopping Support", "Order, cart, wishlist, combo, and promotion help",
                "What do you need help with?",
                status, cart, changes, promos);
    }

    // ── 5. Account & Other Support ────────────────────────────────────────────

    private static Node accountSupport() {
        Node account = branch("👤", "Account", "Manage your TIRTIR account",
                "What would you like to do?",
                leaf("✏️", "Update profile", "Edit your personal info", "account_update_profile",
                        "To update your profile:\n\n1. Profile tab → tap your name/avatar\n2. Tap \"Edit Profile\"\n3. Update name, photo, skin type, birthday\n4. Save\n\n💡 Keeping your skin profile updated improves product recommendations."),
                leaf("🔑", "Change password", "Keep your account secure", "account_change_password",
                        "To change your password:\n\n1. Profile tab → Settings/Security\n2. Tap \"Change Password\"\n3. Enter current password, then the new one twice\n4. Save\n\nForgot it? Log out and tap \"Forgot password\" on the login screen."),
                leaf("🚪", "Logout", "Sign out of your account", "account_logout",
                        "To log out:\n\n1. Go to Profile tab\n2. Scroll to the bottom\n3. Tap \"SIGN OUT\"\n\nYour cart and wishlist are saved to your account and will be restored when you log back in."),
                leaf("📋", "View personal information", "See your account details", "account_view_info",
                        "To view your personal information:\n\n1. Profile tab → tap your name/avatar\n2. Your profile shows: name, email, phone, addresses, skin profile\n3. Tap \"Edit Profile\" to make changes\n\nYour data is private and used only to improve your shopping experience."));

        Node aiScan = branch("📸", "AI Scan", "About the AI skin analysis feature",
                "What would you like to know?",
                leaf("⚙️", "How AI Scan works", "The analysis process", "ai_scan_how_to_use",
                        "How AI Scan works:\n\n1. Open the AI SCAN tab (bottom center)\n2. Take a selfie or pick a photo — natural light, no makeup\n3. AI analyzes skin type, hydration, tone, concerns\n4. Get a personalized routine + product suggestions\n\nThe whole process takes about 30 seconds."),
                leaf("🔍", "Can AI Scan detect skin type?", "What it can identify", "ai_scan_results",
                        "Yes! AI Scan detects:\n\n• Skin type: oily / dry / combination / normal / sensitive\n• Hydration score (0–100)\n• Sebum level\n• Tone evenness & dark spots\n• Wrinkle score\n\nResults are for reference — for medical concerns, consult a dermatologist."),
                leaf("⏰", "When should I use AI Scan?", "Best timing for accuracy", "ai_scan_when",
                        "Best times to use AI Scan:\n\n• Morning after cleansing, before skincare\n• In natural daylight (near a window)\n• Without makeup\n\nRe-scan every 2–4 weeks to track progress. Avoid scanning right after workouts or hot showers — redness skews results."),
                leaf("⚖️", "AI Scan vs Routine", "Which feature to use", "ai_scan_vs_routine",
                        "AI Scan vs Routine tab:\n\n📸 AI Scan: analyzes your CURRENT skin state → detects type & concerns → recommends products\n📋 Routine tab: your saved daily skincare plan → step-by-step schedule you follow\n\nBest flow: run AI Scan first → save its suggestions into your Routine."));

        Node hotline = branch("☎️", "Hotline / Staff", "Talk to a human",
                "How would you like to reach us?",
                leaf("📞", "Call Hotline", "Speak with support now", null, null),
                leaf("👩‍💼", "Chat with TIRTIR Staff", "Message a human support member", null, null),
                leaf("🕐", "Business hours / availability", "When we're available", "hotline_contact_staff",
                        "TIRTIR support availability:\n\n📞 Hotline: 8:00–22:00 daily (including weekends)\n💬 Staff chat: 8:00–22:00, response within 30 minutes\n📧 Out-of-hours messages are answered the next morning\n\nFor urgent order issues, the hotline is fastest."));

        return branch("👤", "Account & Other Support", "Account, AI Scan, hotline, and general support",
                "What do you need help with?",
                account, aiScan, hotline);
    }
}
