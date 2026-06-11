package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.fragments.ChurnListFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class AdminChurnActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private final String[] tabs = {"Champion", "Loyal", "At Risk", "Churned", "New"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_churn);

        Toolbar toolbar = findViewById(R.id.toolbarAdminChurn);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tabLayoutChurn);
        viewPager = findViewById(R.id.viewPagerChurn);

        viewPager.setAdapter(new ChurnPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabs[position]);
        }).attach();
    }

    private class ChurnPagerAdapter extends FragmentStateAdapter {
        public ChurnPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return ChurnListFragment.newInstance(tabs[position]);
        }

        @Override
        public int getItemCount() {
            return tabs.length;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
