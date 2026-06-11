package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.adapters.OnboardingPagerAdapter;
import com.example.tirtir_mcommerce.ui.adapters.OnboardingPagerAdapter.OnboardingPage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Arrays;
import java.util.List;

/**
 * SCR-01 OnboardingActivity
 *
 * Shown only on first launch (controlled by SplashActivity).
 * Features:
 * - ViewPager2 with 3 pages using TirTir brand illustrations (NOT Android robot)
 * - TabLayout dot indicator
 * - Skip button (top right) → LoginActivity
 * - Next button advances pages; becomes "Get Started" on final page
 * - "Already have account" link → LoginActivity
 *
 * Sprint S0.1
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabDots;
    private MaterialButton btnGetStarted;
    private TextView tvSkip;
    private TextView tvOnboardingLogin;

    private static final int PAGE_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager        = findViewById(R.id.viewPagerOnboarding);
        tabDots          = findViewById(R.id.tabDots);
        btnGetStarted    = findViewById(R.id.btnGetStarted);
        tvSkip           = findViewById(R.id.tvSkip);
        tvOnboardingLogin = findViewById(R.id.tvOnboardingLogin);

        setupViewPager();
        setupButtons();
    }

    private void setupViewPager() {
        // TirTir branded onboarding pages — no Android robot icon
        List<OnboardingPage> pages = Arrays.asList(
                new OnboardingPage(
                        R.drawable.ic_onboarding_1,
                        "Khám phá mỹ phẩm TirTir",
                        "Hàng ngàn sản phẩm skincare cao cấp được tuyển chọn cho từng loại da của bạn."
                ),
                new OnboardingPage(
                        R.drawable.ic_onboarding_2,
                        "Phân tích da thông minh",
                        "Nhận gợi ý sản phẩm phù hợp dựa trên loại da và nhu cầu chăm sóc của bạn."
                ),
                new OnboardingPage(
                        R.drawable.ic_onboarding_3,
                        "Đặt hàng dễ dàng & nhanh chóng",
                        "Giao hàng tận nơi, theo dõi đơn hàng và chăm sóc khách hàng 24/7."
                )
        );

        OnboardingPagerAdapter adapter = new OnboardingPagerAdapter(pages);
        viewPager.setAdapter(adapter);

        // Connect dot indicators to ViewPager2
        new TabLayoutMediator(tabDots, viewPager, (tab, position) -> {}).attach();

        // Update button text when page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == PAGE_COUNT - 1) {
                    btnGetStarted.setText("Bắt đầu ngay");
                } else {
                    btnGetStarted.setText("Tiếp theo");
                }
            }
        });
    }

    private void setupButtons() {
        // Next / Get Started button
        btnGetStarted.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < PAGE_COUNT - 1) {
                // Advance to next page
                viewPager.setCurrentItem(currentItem + 1, true);
            } else {
                // Final page — go to login
                goToLogin();
            }
        });

        // Skip button — skip all pages, go to login
        tvSkip.setOnClickListener(v -> goToLogin());

        // Already have account link
        tvOnboardingLogin.setOnClickListener(v -> goToLogin());
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
