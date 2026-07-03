package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.database.DatabaseHelper;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.RoutineRecommendRequest;
import com.example.tirtir_mcommerce.model.RoutineStep;
import com.example.tirtir_mcommerce.model.ShadeMatchRequest;
import com.example.tirtir_mcommerce.model.ShadeMatchResult;
import com.example.tirtir_mcommerce.model.SkinAnalysisResult;
import com.example.tirtir_mcommerce.model.SkinProfile;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.fragments.AiRoutineFragment;
import com.example.tirtir_mcommerce.ui.fragments.ShadeFinderFragment;
import com.example.tirtir_mcommerce.ui.fragments.SkinReportFragment;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.example.tirtir_mcommerce.utils.PriceUtils;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SCR-35: SkinResultActivity — Kết quả phân tích da.
 *
 * Luồng gọi API (Parallel pattern với AtomicInteger):
 * 1. Gọi song song: API 1 (shadeMatch) + API 2 (analyzeSkin)
 * 2. Khi cả 2 xong → gọi API 3 (recommendRoutine)
 * 3. Lưu offline nếu user chưa đăng nhập
 * 4. Cập nhật 3 tabs UI
 *
 * Extras từ SkinAnalysisActivity:
 * - EXTRA_IMAGE_BASE64: ảnh JPEG encode base64
 * - EXTRA_AVG_R/G/B: màu trung bình từ 15-frame history
 * - EXTRA_IS_DEMO: boolean — chạy demo mode
 */
public class SkinResultActivity extends AppCompatActivity {

    private static final String TAG = "SkinResultActivity";

    // Intent extras keys
    public static final String EXTRA_IMAGE_BASE64 = "IMAGE_BASE64";
    public static final String EXTRA_AVG_R        = "AVG_R";
    public static final String EXTRA_AVG_G        = "AVG_G";
    public static final String EXTRA_AVG_B        = "AVG_B";
    public static final String EXTRA_IS_DEMO      = "IS_DEMO";

    // Tabs
    private static final String[] TAB_TITLES = {"Shade Finder", "Skin Report", "AI Routine"};

    // Fragments (giữ tham chiếu để update data sau khi API trả về)
    private ShadeFinderFragment shadeFinderFragment;
    private SkinReportFragment  skinReportFragment;
    private AiRoutineFragment   aiRoutineFragment;

    // UI
    private View loadingOverlay;
    private TextView tvLoadingStatus;
    private TextView tvLoadingSubStatus;

    // API
    private ApiService apiService;
    private final Gson gson = new Gson();

    // Kết quả tạm giữ để gọi API 3
    private SkinAnalysisResult analysisResult;
    private List<ShadeMatchResult> shadeResults;

    // Parallel counter: 2 → 0 thì gọi API 3
    private final AtomicInteger pendingApiCount = new AtomicInteger(2);

    // Input data
    private int avgR, avgG, avgB;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_result);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbarSkinResult);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Loading overlay
        loadingOverlay     = findViewById(R.id.loadingOverlay);
        tvLoadingStatus    = findViewById(R.id.tvLoadingStatus);
        tvLoadingSubStatus = findViewById(R.id.tvLoadingSubStatus);

        // Setup ViewPager2 + Tabs
        setupViewPager();

        // Extract input data
        String analysisJson = getIntent().getStringExtra("SKIN_ANALYSIS_JSON");
        String matchesJson = getIntent().getStringExtra("SHADE_MATCHES_JSON");
        avgR = getIntent().getIntExtra(EXTRA_AVG_R, 216);
        avgG = getIntent().getIntExtra(EXTRA_AVG_G, 160);
        avgB = getIntent().getIntExtra(EXTRA_AVG_B, 135);
        boolean isDemo = getIntent().getBooleanExtra(EXTRA_IS_DEMO, false);

        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);

        if (isDemo) {
            loadDemoData();
        } else if (analysisJson != null && matchesJson != null) {
            showLoading(true, "Building your routine...", "Almost done!");
            analysisResult = gson.fromJson(analysisJson, SkinAnalysisResult.class);
            Type listType = new TypeToken<List<ShadeMatchResult>>(){}.getType();
            shadeResults = gson.fromJson(matchesJson, listType);
            
            String skinHex = analysisResult != null ? analysisResult.getSkinHex() : null;
            
            // Cập nhật UI của Tab 1 và Tab 2 trước
            shadeFinderFragment.updateData(shadeResults, skinHex);
            skinReportFragment.updateData(analysisResult, -1, -1, -1,
                    analysisResult != null ? analysisResult.computeItaAngle() : Double.NaN);

            // Preload shade match images for faster display
            preloadShadeImages(shadeResults);

            callRecommendRoutineApi();
        } else {
            loadDemoData();
        }
        
        MobileAds.initialize(this, initializationStatus -> {});
        AdView mAdView = findViewById(R.id.adViewSkinResult);
        if (mAdView != null) {
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        }
    }

    // ===========================
    // VIEWPAGER SETUP
    // ===========================

    private void setupViewPager() {
        // Khởi tạo fragments
        shadeFinderFragment = ShadeFinderFragment.newInstance(null, null);
        skinReportFragment  = SkinReportFragment.newInstance();
        aiRoutineFragment   = AiRoutineFragment.newInstance();

        ViewPager2 viewPager = findViewById(R.id.viewPagerSkinResult);
        viewPager.setAdapter(new SkinResultPagerAdapter(this));
        viewPager.setOffscreenPageLimit(3); // Giữ tất cả fragments alive

        TabLayout tabLayout = findViewById(R.id.tabLayoutSkinResult);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    private class SkinResultPagerAdapter extends FragmentStateAdapter {
        SkinResultPagerAdapter(FragmentActivity fa) { super(fa); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return shadeFinderFragment;
                case 1: return skinReportFragment;
                case 2: return aiRoutineFragment;
                default: return skinReportFragment;
            }
        }

        @Override
        public int getItemCount() { return 3; }
    }

    // ===========================
    // ROUTINE API CALL
    // ===========================

    /**
     * API 3 — gọi sau khi cả 2 API parallel đã xong.
     */
    private void callRecommendRoutineApi() {
        if (analysisResult == null) {
            onAllApisComplete(new ArrayList<>());
            return;
        }

        String skinType   = safeStr(analysisResult.getSkinType(), "Normal");
        String skinTone   = safeStr(analysisResult.getSkinTone(), "Medium");
        String undertone  = safeStr(analysisResult.getUndertone(), "Neutral");
        List<String> concerns = analysisResult.getConcerns() != null
                ? analysisResult.getConcerns() : new ArrayList<>();

        // Lấy sản phẩm match tốt nhất (đầu tiên trong list)
        String topProductId = "";
        String topShadeName = "";
        if (shadeResults != null && !shadeResults.isEmpty()) {
            ShadeMatchResult top = shadeResults.get(0);
            topProductId = top.getProductId() != null ? top.getProductId() : "";
            topShadeName = top.getShadeName() != null ? top.getShadeName() : "";
        }

        RoutineRecommendRequest request = new RoutineRecommendRequest(
                skinType, skinTone, undertone, concerns, topProductId, topShadeName
        );

        apiService.recommendRoutine(request).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                List<RoutineStep> steps = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null) {
                    steps = parseRoutineSteps(response.body().getData());
                } else {
                    Log.w(TAG, "Recommend routine API failed (HTTP " + response.code() + "), using fallback");
                    steps = buildDemoRoutineSteps(skinType);
                }
                onAllApisComplete(steps);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Log.w(TAG, "Recommend routine API unavailable", t);
                onAllApisComplete(buildDemoRoutineSteps(
                        analysisResult != null ? analysisResult.getSkinType() : "Normal"
                ));
            }
        });
    }

    /**
     * Tất cả 3 API đã xong.
     * 1. Cập nhật Tab 3 UI
     * 2. Lưu offline nếu guest
     * 3. Ẩn loading
     */
    private void onAllApisComplete(List<RoutineStep> routineSteps) {
        // Lưu offline nếu guest
        saveOfflineIfGuest(routineSteps);

        runOnUiThread(() -> {
            aiRoutineFragment.updateData(routineSteps);
            showLoading(false, null, null);
        });
    }

    // ===========================
    // OFFLINE SAVE (Guest mode)
    // ===========================

    private void saveOfflineIfGuest(List<RoutineStep> routineSteps) {
        SharedPrefsManager prefs = new SharedPrefsManager(this);
        if (!prefs.isLoggedIn()) {
            // Guest mode → lưu vào SQLite
            SkinProfile profile = new SkinProfile(
                    null,           // userId = null cho guest
                    analysisResult,
                    shadeResults != null ? shadeResults : new ArrayList<>(),
                    routineSteps
            );
            long rowId = DatabaseHelper.getInstance(this).saveSkinProfile(profile);
            if (rowId > 0) {
                prefs.setPendingSkinProfileSync(true);
                Log.i(TAG, "Skin profile saved offline (row " + rowId + ") for later sync");
            }
        }
        // Nếu đã đăng nhập → API backend tự lưu, không cần làm gì thêm
    }

    // ===========================
    // IMAGE PRELOADING
    // ===========================

    /**
     * Preload shade match product images vào Glide cache.
     * Khi user chuyển sang Shade Finder tab, ảnh đã sẵn trong cache → hiển thị ngay.
     */
    private void preloadShadeImages(List<ShadeMatchResult> matches) {
        if (matches == null || isFinishing()) return;
        for (ShadeMatchResult match : matches) {
            String url = match.getImageUrl();
            if (url != null && !url.isEmpty()) {
                String resolved = com.example.tirtir_mcommerce.network.ApiConfig.resolveMediaUrl(url);
                Glide.with(getApplicationContext())
                        .load(resolved)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .preload();
            }
        }
    }

    // ===========================
    // PARSING HELPERS
    // ===========================

    @SuppressWarnings("unchecked")
    private SkinAnalysisResult parseAnalysisResult(Map<String, Object> data) {
        SkinAnalysisResult result = new SkinAnalysisResult();
        result.setSkinTone(stringVal(data.get("skinTone")));
        result.setUndertone(stringVal(data.get("undertone")));
        result.setSkinType(stringVal(data.get("skinType")));
        result.setSkinHex(stringVal(data.get("skinHex")));

        Object confidence = data.get("confidence");
        if (confidence instanceof Number) {
            result.setConfidence(((Number) confidence).doubleValue());
        }

        Object concernsObj = data.get("concerns");
        if (concernsObj instanceof List) {
            List<String> concerns = new ArrayList<>();
            for (Object c : (List<?>) concernsObj) {
                if (c != null) concerns.add(c.toString());
            }
            result.setConcerns(concerns);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<RoutineStep> parseRoutineSteps(Map<String, Object> data) {
        List<RoutineStep> steps = new ArrayList<>();
        Object routineObj = data.get("routine");
        if (!(routineObj instanceof List)) return steps;

        int stepIndex = 1;
        for (Object item : (List<?>) routineObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> itemMap = (Map<String, Object>) item;

            RoutineStep step = new RoutineStep();
            step.setStep(stepIndex++);
            
            // The API returns step name as 'step' (e.g. "Cleanser")
            step.setStepName(stringVal(itemMap.get("step")));
            if (step.getStepName().isEmpty()) {
                step.setStepName(stringVal(itemMap.get("stepName")));
            }
            
            step.setProductName(stringVal(itemMap.get("product_name")));
            if (step.getProductName().isEmpty()) {
                step.setProductName(stringVal(itemMap.get("productName")));
            }

            step.setDescription(stringVal(itemMap.get("reason")));

            Object productObj = itemMap.get("product");
            if (productObj instanceof Map) {
                Map<String, Object> productMap = (Map<String, Object>) productObj;
                step.setProductId(stringVal(productMap.get("Product_ID")));
                
                Object price = productMap.get("Price");
                if (price instanceof Number) step.setPrice(((Number) price).doubleValue());
                
                String imgUrl = stringVal(productMap.get("Thumbnail_Images"));
                // Prepend base URL if it's a relative path
                if (!imgUrl.isEmpty() && !imgUrl.startsWith("http")) {
                    imgUrl = "https://tirtir-project.onrender.com/" + imgUrl;
                }
                step.setImageUrl(imgUrl);
            } else {
                step.setProductId(stringVal(itemMap.get("productId")));
                step.setImageUrl(stringVal(itemMap.get("imageUrl")));
                Object price = itemMap.get("price");
                if (price instanceof Number) step.setPrice(((Number) price).doubleValue());
            }

            Object hBoost = itemMap.get("hydrationBoost");
            if (hBoost instanceof Number) step.setHydrationBoost(((Number) hBoost).intValue());
            else step.setHydrationBoost(3);

            Object tBoost = itemMap.get("textureBoost");
            if (tBoost instanceof Number) step.setTextureBoost(((Number) tBoost).intValue());
            else step.setTextureBoost(2);

            steps.add(step);
        }
        return steps;
    }

    // ===========================
    // FALLBACK / DEMO DATA
    // ===========================

    /**
     * Client-side fallback cho Shade Match:
     * Dựa vào avgR, avgG, avgB để phân loại shade mô phỏng.
     */
    private List<ShadeMatchResult> buildClientSideShadeMatches() {
        List<ShadeMatchResult> results = new ArrayList<>();
        // {shadeName, hex, matchScore, productName, price, imageUrl, productId}
        String[][] shades = {
            {"21N Ivory", "#ebc5a1", "3.2", "Mask Fit Red Cushion", "35.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg", "cushion-21n"},
            {"23N Sand", "#ebbf98", "6.5", "Mask Fit Red Cushion", "35.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg", "cushion-23n"},
            {"24N Latte", "#e4b58e", "8.0", "Mask Fit Aura Cushion", "38.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Aura-Cushion.jpg", "cushion-24n"},
            {"27N Camel", "#e5b98b", "11.0", "Mask Fit Red Cushion", "35.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg", "cushion-27n"},
            {"33N Macchiato", "#d3a177", "15.0", "Mask Fit All-Cover Cushion", "36.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-All-Cover-Cushion.jpg", "cushion-33n"}
        };
        for (String[] shade : shades) {
            ShadeMatchResult r = new ShadeMatchResult();
            r.setShadeName(shade[0]);
            r.setShadeHex(shade[1]);
            r.setMatchScore(Double.parseDouble(shade[2]));
            r.setProductName(shade[3]);
            r.setPrice(Double.parseDouble(shade[4]));
            r.setImageUrl(shade[5]);
            r.setProductId(shade[6]);
            results.add(r);
        }
        return results;
    }

    private SkinAnalysisResult buildDemoAnalysisResult() {
        SkinAnalysisResult result = new SkinAnalysisResult();
        result.setSkinTone("Medium");
        result.setUndertone("Neutral-warm");
        result.setSkinType("Combination");
        result.setSkinHex("#D8A087");
        result.setConfidence(85.0);
        List<String> concerns = new ArrayList<>();
        concerns.add("Visible Pores");
        concerns.add("Uneven Tone");
        result.setConcerns(concerns);
        return result;
    }

    private List<RoutineStep> buildDemoRoutineSteps(String skinType) {
        List<RoutineStep> steps = new ArrayList<>();
        // {step, stepName, productName, description, hydration, texture, price, imageUrl}
        String[][] routineData = {
            {"1", "Cleanser", "Hydro Boost Enzyme Cleansing Balm",
                    "Gentle cleansing is essential for " + skinType + " skin to remove impurities without over-stripping.",
                    "3", "2", "25.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Hydro-Boost-Enzyme-Cleansing-Balm.jpg"},
            {"2", "Toner", "Matcha Skin Toner",
                    "Rebalances skin pH and prepares " + skinType + " skin for better absorption of subsequent products.",
                    "5", "3", "22.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Matcha-Skin-Toner.jpg"},
            {"3", "Serum", "Matcha Calming Duo Set",
                    "Targets concerns with concentrated active ingredients.",
                    "7", "4", "45.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Matcha-Calming-Duo-Set.jpg"},
            {"4", "Cream", "Matcha Calming Cream",
                    "Locks in moisture and creates a protective barrier suited for " + skinType + " skin.",
                    "8", "3", "28.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Matcha-Calming-Cream.jpg"},
            {"5", "Cushion", "Tirtir Mask Fit Red Cushion",
                    "Provides coverage and sun protection while giving a natural, dewy finish.",
                    "4", "2", "35.00",
                    "https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg"}
        };
        for (String[] data : routineData) {
            RoutineStep step = new RoutineStep();
            step.setStep(Integer.parseInt(data[0]));
            step.setStepName(data[1]);
            step.setProductName(data[2]);
            step.setDescription(data[3]);
            step.setHydrationBoost(Integer.parseInt(data[4]));
            step.setTextureBoost(Integer.parseInt(data[5]));
            step.setPrice(Double.parseDouble(data[6]));
            step.setImageUrl(data[7]);
            step.setProductId("tirtir-" + data[1].toLowerCase());
            steps.add(step);
        }
        return steps;
    }

    // ===========================
    // DEMO MODE
    // ===========================

    private void loadDemoData() {
        analysisResult = buildDemoAnalysisResult();
        shadeResults   = buildClientSideShadeMatches();
        List<RoutineStep> routine = buildDemoRoutineSteps("Combination");

        shadeFinderFragment.updateData(shadeResults, analysisResult.getSkinHex());
        skinReportFragment.updateData(analysisResult, 76, 68, 72,
                analysisResult.computeItaAngle());
        aiRoutineFragment.updateData(routine);
        showLoading(false, null, null);
    }

    // ===========================
    // UI HELPERS
    // ===========================

    private void showLoading(boolean show, String status, String subStatus) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && status != null)    tvLoadingStatus.setText(status);
        if (show && subStatus != null) tvLoadingSubStatus.setText(subStatus);
    }

    private void updateLoadingStatus(String status, String subStatus) {
        tvLoadingStatus.setText(status);
        tvLoadingSubStatus.setText(subStatus);
    }

    private String stringVal(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private String safeStr(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
