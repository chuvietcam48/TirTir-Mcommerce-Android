package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

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
                user -> {
                    // Gửi Voucher
                    Map<String, Object> body = new HashMap<>();
                    body.put("userId", user.id);
                    body.put("discountPct", 15);
                    body.put("expiryDays", 7);
                    api.sendVoucher(body).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(getContext(), "Gửi Voucher thành công cho " + user.name, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Không gửi được Voucher", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            Toast.makeText(getContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                }, user -> {
                    // Gửi FCM
                    Map<String, Object> body = new HashMap<>();
                    body.put("userId", user.id);
                    body.put("voucherCode", "TIRTIR-REGAIN");
                    body.put("discountPct", 15);
                    body.put("expiryDays", 7);
                    api.sendVoucherFcm(body).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(getContext(), "Gửi FCM thành công cho " + user.name, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Không gửi được FCM", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            Toast.makeText(getContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
        rvChurnList.setAdapter(adapter);

        tvChurnEmpty.setText("No " + (segment == null ? "customer" : segment)
                + " retention segment is available right now.");
        
        loadData(api);

        return v;
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
                    Toast.makeText(getContext(), "Lỗi tải danh sách Churn", Toast.LENGTH_SHORT).show();
                }
                tvChurnEmpty.setVisibility(View.VISIBLE);
                rvChurnList.setVisibility(View.GONE);
            }
        });
    }
}
