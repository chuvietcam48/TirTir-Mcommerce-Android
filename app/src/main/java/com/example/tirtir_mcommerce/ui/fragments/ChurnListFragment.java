package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.ChurnResponseItem;
import com.example.tirtir_mcommerce.ui.adapters.ChurnUserAdapter;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChurnListFragment extends Fragment {

    private String segment;
    private RecyclerView rvChurnList;
    private TextView tvChurnEmpty;
    private ChurnUserAdapter adapter;
    private List<ChurnUserAdapter.ChurnUser> userList = new ArrayList<>();

    public static ChurnListFragment newInstance(String segment) {
        ChurnListFragment fragment = new ChurnListFragment();
        Bundle args = new Bundle();
        args.putString("segment", segment);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            segment = getArguments().getString("segment");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_churn_list, container, false);
        rvChurnList = v.findViewById(R.id.rvChurnList);
        tvChurnEmpty = v.findViewById(R.id.tvChurnEmpty);
        rvChurnList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        ApiService api = RetrofitClient.getAuthClient(getContext()).create(ApiService.class);

        adapter = new ChurnUserAdapter(getContext(), userList,
                user -> showVoucherDialog(api, user),
                user -> showFcmDialog(api, user));
        rvChurnList.setAdapter(adapter);

        tvChurnEmpty.setText("No " + (segment == null ? "customer" : segment)
                + " retention segment is available right now.");
        
        loadData(api);

        return v;
    }

    private void showVoucherDialog(ApiService api, ChurnUserAdapter.ChurnUser user) {
        if (getContext() == null) return;
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, 0);

        EditText etDiscount = new EditText(getContext());
        etDiscount.setHint("Discount % (e.g. 15)");
        etDiscount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDiscount.setText("15");

        EditText etExpiry = new EditText(getContext());
        etExpiry.setHint("Expiry days (e.g. 7)");
        etExpiry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etExpiry.setText("7");

        layout.addView(etDiscount);
        layout.addView(etExpiry);

        new AlertDialog.Builder(getContext())
                .setTitle("Send Voucher to " + user.name)
                .setView(layout)
                .setPositiveButton("Send", (d, w) -> {
                    int discount = parseIntOrDefault(etDiscount.getText().toString(), 15);
                    int expiry   = parseIntOrDefault(etExpiry.getText().toString(), 7);
                    Map<String, Object> body = new HashMap<>();
                    body.put("userId", user.id);
                    body.put("discountPct", discount);
                    body.put("expiryDays", expiry);
                    api.sendVoucher(body).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (getContext() == null) return;
                            Toast.makeText(getContext(),
                                    response.isSuccessful() ? "Voucher sent to " + user.name : "Failed to send voucher",
                                    Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            if (getContext() != null)
                                Toast.makeText(getContext(), "Connection error", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFcmDialog(ApiService api, ChurnUserAdapter.ChurnUser user) {
        if (getContext() == null) return;
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, 0);

        EditText etCode = new EditText(getContext());
        etCode.setHint("Voucher code (e.g. TIRTIR-REGAIN)");
        etCode.setText("TIRTIR-REGAIN");

        EditText etDiscount = new EditText(getContext());
        etDiscount.setHint("Discount % (e.g. 15)");
        etDiscount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDiscount.setText("15");

        EditText etExpiry = new EditText(getContext());
        etExpiry.setHint("Expiry days (e.g. 7)");
        etExpiry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etExpiry.setText("7");

        layout.addView(etCode);
        layout.addView(etDiscount);
        layout.addView(etExpiry);

        new AlertDialog.Builder(getContext())
                .setTitle("Send Push Notification to " + user.name)
                .setView(layout)
                .setPositiveButton("Send", (d, w) -> {
                    String code  = etCode.getText().toString().trim();
                    int discount = parseIntOrDefault(etDiscount.getText().toString(), 15);
                    int expiry   = parseIntOrDefault(etExpiry.getText().toString(), 7);
                    Map<String, Object> body = new HashMap<>();
                    body.put("userId", user.id);
                    body.put("voucherCode", code.isEmpty() ? "TIRTIR-REGAIN" : code);
                    body.put("discountPct", discount);
                    body.put("expiryDays", expiry);
                    api.sendVoucherFcm(body).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (getContext() == null) return;
                            Toast.makeText(getContext(),
                                    response.isSuccessful() ? "Notification sent to " + user.name : "Failed to send notification",
                                    Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            if (getContext() != null)
                                Toast.makeText(getContext(), "Connection error", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int parseIntOrDefault(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private void loadData(ApiService api) {
        api.getChurnList().enqueue(new Callback<ApiResponse<List<ChurnResponseItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ChurnResponseItem>>> call, Response<ApiResponse<List<ChurnResponseItem>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ChurnResponseItem> items = response.body().getData();
                    userList.clear();
                    if (items != null) {
                        for (ChurnResponseItem item : items) {
                            if (item.getClassification() != null && item.getClassification().equalsIgnoreCase(segment)) {
                                String id = item.getUser() != null ? item.getUser().getId() : "";
                                String name = item.getUser() != null ? item.getUser().getName() : "Unknown";
                                String email = item.getUser() != null ? item.getUser().getEmail() : "";
                                int r = (item.getRfm() != null && item.getRfm().getRecency() != null) ? item.getRfm().getRecency() : 0;
                                int f = (item.getRfm() != null && item.getRfm().getFrequency() != null) ? item.getRfm().getFrequency() : 0;
                                double mDouble = (item.getRfm() != null && item.getRfm().getMonetary() != null) ? item.getRfm().getMonetary() : 0.0;
                                int m = (int) mDouble;
                                userList.add(new ChurnUserAdapter.ChurnUser(id, name, email, segment, r, f, m));
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                    
                    if (userList.isEmpty()) {
                        tvChurnEmpty.setVisibility(View.VISIBLE);
                        rvChurnList.setVisibility(View.GONE);
                    } else {
                        tvChurnEmpty.setVisibility(View.GONE);
                        rvChurnList.setVisibility(View.VISIBLE);
                    }
                } else {
                    tvChurnEmpty.setVisibility(View.VISIBLE);
                    rvChurnList.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ChurnResponseItem>>> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load retention data", Toast.LENGTH_SHORT).show();
                }
                tvChurnEmpty.setVisibility(View.VISIBLE);
                rvChurnList.setVisibility(View.GONE);
            }
        });
    }
}
