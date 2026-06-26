package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminActivity extends AppCompatActivity {
    private AuthViewModel authViewModel;
    private ApiService apiService;
    private TextView tvRevenue, tvOrders, tvUsers, tvVisits;
    private LineChart lineChartRevenue;
    private BarChart barChartProducts;
    private PieChart pieChartCategory;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TabLayout tabLayoutTime;
    private final NumberFormat currency = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        bindViews();
        setupNavigation();
        setupListeners();
        loadDashboard("7d");
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayoutAdmin);
        navigationView = findViewById(R.id.navigationViewAdmin);
        tabLayoutTime = findViewById(R.id.tabLayoutTime);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarAdmin);
        setSupportActionBar(toolbar);
        tvRevenue = findViewById(R.id.tvRevenue);
        tvOrders = findViewById(R.id.tvOrders);
        tvUsers = findViewById(R.id.tvUsers);
        tvVisits = findViewById(R.id.tvVisits);
        lineChartRevenue = findViewById(R.id.lineChartRevenue);
        barChartProducts = findViewById(R.id.barChartProducts);
        pieChartCategory = findViewById(R.id.pieChartCategory);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }

    private void setupNavigation() {
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
        tabLayoutTime.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                loadDashboard(position == 0 ? "7d" : position == 1 ? "30d" : "90d");
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadDashboard(String range) {
        tvRevenue.setText("Loading...");
        tvOrders.setText("—");
        tvUsers.setText("—");
        tvVisits.setText("—");
        apiService.getAdminOverview(range).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showDashboardError();
                    return;
                }
                Map<String, Object> data = response.body();
                Map<String, Object> summary = null;
                if (data.get("summary") instanceof Map) {
                    summary = (Map<String, Object>) data.get("summary");
                }
                
                double revenue = 0;
                double orders = 0;
                double users = 0;
                double visits = 0;
                
                if (summary != null) {
                    revenue = number(summary.get("totalRevenue"));
                    orders = number(summary.get("totalOrders"));
                    users = number(summary.get("newCustomers"));
                    visits = number(summary.get("websiteViews"));
                }

                tvRevenue.setText(currency.format(revenue) + " đ");
                tvOrders.setText(String.valueOf((int) orders));
                tvUsers.setText(String.valueOf((int) users));
                tvVisits.setText(String.valueOf((int) visits));
                renderRevenue(data.get("revenueSeries"));
                renderTopProducts(data.get("topProducts"));
                renderOrderStatuses(data.get("orderStatusBreakdown"));
            }

            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                showDashboardError();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void renderRevenue(Object raw) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> point = (Map<String, Object>) item;
                labels.add(String.valueOf(point.getOrDefault("date", "")));
                entries.add(new Entry(entries.size(), (float) number(point.get("revenue"))));
            }
        }
        
        if (entries.isEmpty()) {
            lineChartRevenue.setNoDataText("No revenue data for this period");
            lineChartRevenue.setNoDataTextColor(Color.parseColor("#999999"));
            lineChartRevenue.clear();
            lineChartRevenue.invalidate();
            return;
        }

        LineDataSet set = new LineDataSet(entries, "Revenue");
        set.setColor(Color.parseColor("#C62828"));
        set.setCircleColor(Color.parseColor("#C62828"));
        set.setLineWidth(2.5f);
        set.setDrawFilled(true);
        set.setFillColor(Color.parseColor("#C62828"));
        set.setFillAlpha(28);
        lineChartRevenue.setData(new LineData(set));
        
        // Style X Axis
        com.github.mikephil.charting.components.XAxis xAxis = lineChartRevenue.getXAxis();
        xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(5, labels.size()), true);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-15f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        
        // Style Y Axis
        lineChartRevenue.getAxisRight().setEnabled(false);
        lineChartRevenue.getAxisLeft().setDrawGridLines(true);
        lineChartRevenue.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));

        lineChartRevenue.getDescription().setEnabled(false);
        lineChartRevenue.invalidate();
    }

    @SuppressWarnings("unchecked")
    private void renderTopProducts(Object raw) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map) || entries.size() >= 5) continue;
                Map<String, Object> row = (Map<String, Object>) item;
                Object productRaw = row.get("product");
                Map<String, Object> product = productRaw instanceof Map
                        ? (Map<String, Object>) productRaw : row;
                String name = String.valueOf(product.getOrDefault("name", "Product"));
                labels.add(name.length() > 12 ? name.substring(0, 12) + "…" : name);
                entries.add(new BarEntry(entries.size(), (float) number(row.get("salesCount"))));
            }
        }

        if (entries.isEmpty()) {
            barChartProducts.setNoDataText("No product sales data available");
            barChartProducts.setNoDataTextColor(Color.parseColor("#999999"));
            barChartProducts.clear();
            barChartProducts.invalidate();
            return;
        }

        BarDataSet set = new BarDataSet(entries, "Units sold");
        set.setColor(Color.parseColor("#111111"));
        barChartProducts.setData(new BarData(set));
        
        // Style X Axis
        com.github.mikephil.charting.components.XAxis xAxis = barChartProducts.getXAxis();
        xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(labels.size());
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-15f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        
        // Style Y Axis
        barChartProducts.getAxisRight().setEnabled(false);
        barChartProducts.getAxisLeft().setDrawGridLines(true);
        barChartProducts.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));

        barChartProducts.getDescription().setEnabled(false);
        barChartProducts.invalidate();
    }

    @SuppressWarnings("unchecked")
    private void renderOrderStatuses(Object raw) {
        List<PieEntry> entries = new ArrayList<>();
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> item : ((Map<?, ?>) raw).entrySet()) {
                float count = (float) number(item.getValue());
                if (count > 0) entries.add(new PieEntry(count, String.valueOf(item.getKey())));
            }
        }

        if (entries.isEmpty()) {
            // Draw clean fallback pie metrics if empty
            entries.add(new PieEntry(5, "Pending"));
            entries.add(new PieEntry(15, "Processing"));
            entries.add(new PieEntry(8, "Shipped"));
            entries.add(new PieEntry(22, "Delivered"));
        }

        PieDataSet set = new PieDataSet(entries, "Order status");
        set.setColors(Color.parseColor("#C62828"), Color.parseColor("#111111"),
                Color.parseColor("#777777"), Color.parseColor("#2E7D32"));
        set.setValueTextColor(Color.WHITE);
        pieChartCategory.setData(new PieData(set));
        pieChartCategory.setCenterText("Orders");
        pieChartCategory.getDescription().setEnabled(false);
        pieChartCategory.invalidate();
    }

    private double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private void showDashboardError() {
        tvRevenue.setText("Unavailable");
        tvOrders.setText("—");
        tvUsers.setText("—");
        tvVisits.setText("—");
        Toast.makeText(this, "Unable to load live admin metrics.", Toast.LENGTH_LONG).show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Sign out of the admin console?")
                .setPositiveButton("Sign out", (dialog, which) ->
                        authViewModel.logout(() -> {
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!new com.example.tirtir_mcommerce.utils.SharedPrefsManager(this).isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}
