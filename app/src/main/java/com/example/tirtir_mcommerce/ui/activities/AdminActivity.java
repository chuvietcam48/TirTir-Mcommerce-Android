package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.material.button.MaterialButton;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        db = FirebaseFirestore.getInstance();

        bindViews();
        setupListeners();
        
        loadChartsData();
    }

    private void bindViews() {
        btnAdminLogout = findViewById(R.id.btnAdminLogout);
        btnManageProducts = findViewById(R.id.btnManageProducts);
        tvRevenue = findViewById(R.id.tvRevenue);
        tvOrders = findViewById(R.id.tvOrders);
        tvUsers = findViewById(R.id.tvUsers);
        tvVisits = findViewById(R.id.tvVisits);

        lineChartRevenue = findViewById(R.id.lineChartRevenue);
        barChartProducts = findViewById(R.id.barChartProducts);
        pieChartCategory = findViewById(R.id.pieChartCategory);
    }

    private void setupListeners() {
        btnAdminLogout.setOnClickListener(v -> confirmLogout());
        btnManageProducts.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminProductListActivity.class));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Lắng nghe realtime từ Firestore cho 4 tiles
        firestoreListener = db.collection("analytics").document("today")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("AdminActivity", "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        updateTiles(snapshot);
                    } else {
                        Log.d("AdminActivity", "Current data: null");
                    }
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }

    private void updateTiles(DocumentSnapshot doc) {
        Long revenue = doc.getLong("revenue");
        Long orders = doc.getLong("orders");
        Long users = doc.getLong("newUsers");
        Long visits = doc.getLong("visits");

        if (revenue != null) tvRevenue.setText(currencyFormat.format(revenue) + " đ");
        if (orders != null) tvOrders.setText(String.valueOf(orders));
        if (users != null) tvUsers.setText(String.valueOf(users));
        if (visits != null) tvVisits.setText(String.valueOf(visits));
    }

    private void loadChartsData() {
        // Line Chart: Doanh thu 7 ngày qua
        apiService.getAdminMetrics("7d").enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    setupLineChart(response.body().getData());
                } else {
                    setupDummyLineChart(); // Fallback if API missing/fails
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                setupDummyLineChart();
            }
        });

        // Bar Chart: Top sản phẩm
        apiService.getTopProducts().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call, Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    setupBarChart(response.body().getData());
                } else {
                    setupDummyBarChart();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                setupDummyBarChart();
            }
        });

        // Pie Chart: Dummy categories for now or fetch actual
        setupDummyPieChart();
    }

    private void setupLineChart(Map<String, Object> data) {
        // data contains "labels" (List<String>) and "values" (List<Double>)
        try {
            List<String> labels = (List<String>) data.get("labels");
            List<Double> values = (List<Double>) data.get("values");

            if (labels == null || values == null) {
                setupDummyLineChart();
                return;
            }

            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                entries.add(new Entry(i, values.get(i).floatValue()));
            }

            LineDataSet dataSet = new LineDataSet(entries, "Doanh thu (đ)");
            dataSet.setColor(Color.parseColor("#E91E8C"));
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setLineWidth(2f);
            dataSet.setCircleColor(Color.parseColor("#E91E8C"));

            LineData lineData = new LineData(dataSet);
            lineChartRevenue.setData(lineData);

            XAxis xAxis = lineChartRevenue.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);

            lineChartRevenue.getDescription().setEnabled(false);
            lineChartRevenue.invalidate();
        } catch (Exception e) {
            setupDummyLineChart();
        }
    }

    private void setupDummyLineChart() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(0, 1000000));
        entries.add(new Entry(1, 1500000));
        entries.add(new Entry(2, 1200000));
        entries.add(new Entry(3, 2000000));
        entries.add(new Entry(4, 1800000));
        entries.add(new Entry(5, 2500000));
        entries.add(new Entry(6, 3000000));

        LineDataSet dataSet = new LineDataSet(entries, "Doanh thu (Mock)");
        dataSet.setColor(Color.parseColor("#E91E8C"));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setLineWidth(2f);

        LineData lineData = new LineData(dataSet);
        lineChartRevenue.setData(lineData);
        lineChartRevenue.getDescription().setEnabled(false);
        lineChartRevenue.invalidate();
    }

    private void setupBarChart(List<Map<String, Object>> dataList) {
        try {
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> item = dataList.get(i);
                String name = (String) item.get("name");
                Double sales = (Double) item.get("sales");
                if (sales == null) sales = 0.0;
                
                // Giới hạn độ dài tên
                if (name != null && name.length() > 10) name = name.substring(0, 10) + "...";
                
                labels.add(name != null ? name : "Unknown");
                entries.add(new BarEntry(i, sales.floatValue()));
            }

            BarDataSet dataSet = new BarDataSet(entries, "Số lượng bán");
            dataSet.setColor(Color.parseColor("#2196F3"));

            BarData barData = new BarData(dataSet);
            barChartProducts.setData(barData);

            XAxis xAxis = barChartProducts.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);

            barChartProducts.getDescription().setEnabled(false);
            barChartProducts.invalidate();
        } catch (Exception e) {
            setupDummyBarChart();
        }
    }

    private void setupDummyBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, 50f));
        entries.add(new BarEntry(1f, 80f));
        entries.add(new BarEntry(2f, 60f));
        entries.add(new BarEntry(3f, 100f));

        BarDataSet dataSet = new BarDataSet(entries, "Sản phẩm bán chạy (Mock)");
        dataSet.setColor(Color.parseColor("#2196F3"));

        BarData barData = new BarData(dataSet);
        barChartProducts.setData(barData);
        barChartProducts.getDescription().setEnabled(false);
        barChartProducts.invalidate();
    }

    private void setupDummyPieChart() {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(40f, "Skincare"));
        entries.add(new PieEntry(30f, "Makeup"));
        entries.add(new PieEntry(20f, "Bodycare"));
        entries.add(new PieEntry(10f, "Other"));

        PieDataSet dataSet = new PieDataSet(entries, "Danh mục");
        dataSet.setColors(new int[]{Color.parseColor("#E91E8C"), Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"), Color.parseColor("#FF9800")});
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData pieData = new PieData(dataSet);
        pieChartCategory.setData(pieData);
        pieChartCategory.getDescription().setEnabled(false);
        pieChartCategory.invalidate();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất khỏi tài khoản Admin không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> performLogout())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performLogout() {
        authViewModel.logout(() -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}
