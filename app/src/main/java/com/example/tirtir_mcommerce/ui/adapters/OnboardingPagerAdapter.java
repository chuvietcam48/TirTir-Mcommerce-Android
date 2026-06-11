package com.example.tirtir_mcommerce.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;

import java.util.List;

/**
 * OnboardingPagerAdapter — ViewPager2 adapter for 3 onboarding pages.
 *
 * Each page is an OnboardingPage data object with:
 * - illustrationRes: drawable resource ID (TirTir brand illustrations, NOT Android robot)
 * - title: page headline
 * - subtitle: supporting description
 *
 * SCR-01 OnboardingActivity
 */
public class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder> {

    private final List<OnboardingPage> pages;

    public OnboardingPagerAdapter(List<OnboardingPage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        OnboardingPage page = pages.get(position);
        holder.ivIllustration.setImageResource(page.illustrationRes);
        holder.tvTitle.setText(page.title);
        holder.tvSubtitle.setText(page.subtitle);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    // ===========================
    // VIEW HOLDER
    // ===========================
    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIllustration;
        TextView tvTitle, tvSubtitle;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIllustration = itemView.findViewById(R.id.ivOnboardingIllustration);
            tvTitle        = itemView.findViewById(R.id.tvOnboardingTitle);
            tvSubtitle     = itemView.findViewById(R.id.tvOnboardingSubtitle);
        }
    }

    // ===========================
    // DATA MODEL
    // ===========================
    public static class OnboardingPage {
        public final int illustrationRes;
        public final String title;
        public final String subtitle;

        public OnboardingPage(int illustrationRes, String title, String subtitle) {
            this.illustrationRes = illustrationRes;
            this.title           = title;
            this.subtitle        = subtitle;
        }
    }
}
