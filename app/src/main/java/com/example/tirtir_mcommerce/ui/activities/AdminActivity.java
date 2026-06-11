package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminActivity extends AppCompatActivity {

    private AuthViewModel authViewModel;
    private MaterialButton btnAdminLogout, btnManageProducts;
    private TextView tvRevenue, tvOrders, tvUsers, tvVisits;

    private LineChart lineChartRevenue;
    private BarChart barChartProducts;
    private PieChart pieChartCategory;

    private ApiService apiService;
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;

    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private com.google.android.material.navigation.NavigationView navigationView;
    private com.google.android.material.tabs.TabLayout tabLayoutTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupNavigation();
        setupListeners();
        
        // Initial load with sample data
        loadSampleData();
        setupDummyLineChart("7 ngày");
        setupDummyBarChart();
        setupDummyPieChart();
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayoutAdmin);
        navigationView = findViewById(R.id.navigationViewAdmin);
        tabLayoutTime = findViewById(R.id.tabLayoutTime);
        
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarAdmin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
        }
        
        tvRevenue = findViewById(R.id.tvRevenue);
        tvOrders = findViewById(R.id.tvOrders);
        tvUsers = findViewById(R.id.tvUsers);
        tvVisits = findViewById(R.id.tvVisits);

        lineChartRevenue = findViewById(R.id.lineChartRevenue);
        barChartProducts = findViewById(R.id.barChartProducts);
        pieChartCategory = findViewById(R.id.pieChartCategory);
    }

    private void setupNavigation() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarAdmin);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_admin_products) {
                startActivity(new Intent(this, AdminProductListActivity.class));
            } else if (id == R.id.nav_admin_orders) {
                startActivity(new Intent(this, AdminOrdersActivity.class));
            } else if (id == R.id.nav_admin_churn) {
                startActivity(new Intent(this, AdminChurnActivity.class));
            } else if (id == R.id.nav_admin_cart_recovery) {
                startActivity(new Intent(this, AdminCartRecoveryActivity.class));
            } else if (id == R.id.nav_admin_logout) {
                confirmLogout();
            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void setupListeners() {
        tabLayoutTime.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                setupDummyLineChart(tab.getText().toString());
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
    }

    private void loadSampleData() {
        tvRevenue.setText("45.200.000 đ");
        tvOrders.setText("128");
        tvUsers.setText("52");
        tvVisits.setText("1.204");
    }

    private void setupDummyLineChart(String range) {
        List<Entry> entries = new ArrayList<>();
        float multiplier = 1.0f;
        if (range.equals("30 ngày")) multiplier = 4.0f;
        else if (range.equals("90 ngày")) multiplier = 12.0f;

        entries.add(new Entry(0, 1000000 * multiplier));
        entries.add(new Entry(1, 1500000 * multiplier));
        entries.add(new Entry(2, 1200000 * multiplier));
        entries.add(new Entry(3, 2000000 * multiplier));
        entries.add(new Entry(4, 1800000 * multiplier));
        entries.add(new Entry(5, 2500000 * multiplier));
        entries.add(new Entry(6, 3000000 * multiplier));

        LineDataSet dataSet = new LineDataSet(entries, "Doanh thu (" + range + ")");
        dataSet.setColor(Color.parseColor("#C62828")); // Tirtir Red
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(Color.parseColor("#C62828"));
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#C62828"));
        dataSet.setFillAlpha(30);

        LineData lineData = new LineData(dataSet);
        lineChartRevenue.setData(lineData);
        lineChartRevenue.getDescription().setEnabled(false);
        lineChartRevenue.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        lineChartRevenue.animateX(800);
        lineChartRevenue.invalidate();
    }

    private void setupDummyBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, 150f));
        entries.add(new BarEntry(1f, 120f));
        entries.add(new BarEntry(2f, 110f));
        entries.add(new BarEntry(3f, 95f));
        entries.add(new BarEntry(4f, 80f));

        BarDataSet dataSet = new BarDataSet(entries, "Số lượng bán");
        dataSet.setColor(Color.parseColor("#2196F3"));

        BarData barData = new BarData(dataSet);
        barChartProducts.setData(barData);
        
        String[] labels = {"Toner", "Serum", "Cream", "Cushion", "Mask"};
        barChartProducts.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels));
        barChartProducts.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        barChartProducts.getXAxis().setGranularity(1f);
        
        barChartProducts.getDescription().setEnabled(false);
        barChartProducts.animateY(1000);
        barChartProducts.invalidate();
    }

    private void setupDummyPieChart() {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(45f, "Skincare"));
        entries.add(new PieEntry(35f, "Makeup"));
        entries.add(new PieEntry(20f, "Other"));

        PieDataSet dataSet = new PieDataSet(entries, "Categories");
        dataSet.setColors(new int[]{
                Color.parseColor("#C62828"), 
                Color.parseColor("#111111"), 
                Color.parseColor("#777777")
        });
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);

        PieData pieData = new PieData(dataSet);
        pieChartCategory.setData(pieData);
        pieChartCategory.setCenterText("Category Mix");
        pieChartCategory.setHoleRadius(40f);
        pieChartCategory.getDescription().setEnabled(false);
        pieChartCategory.animateXY(1000, 1000);
        pieChartCategory.invalidate();
    }
}
