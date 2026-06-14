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
import com.example.tirtir_mcommerce.ui.adapters.ChurnUserAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChurnListFragment extends Fragment {

    private String segment;
    private RecyclerView rvChurnList;

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
        rvChurnList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        com.example.tirtir_mcommerce.network.ApiService api = com.example.tirtir_mcommerce.network.RetrofitClient.getAuthClient(getContext()).create(com.example.tirtir_mcommerce.network.ApiService.class);

        rvChurnList.setAdapter(new ChurnUserAdapter(getContext(), new ArrayList<>(),
                user -> {
                    java.util.Map<String, String> body = new java.util.HashMap<>();
                    body.put("email", user.email);
                    api.sendVoucher(body).enqueue(new retrofit2.Callback<com.example.tirtir_mcommerce.model.ApiResponse<Object>>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Object>> call, retrofit2.Response<com.example.tirtir_mcommerce.model.ApiResponse<Object>> response) {
                            Toast.makeText(getContext(), "Voucher sent to " + user.name, Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onFailure(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Object>> call, Throwable t) {
                            Toast.makeText(getContext(), "Failed to send voucher", Toast.LENGTH_SHORT).show();
                        }
                    });
                }, user -> {}));
        TextView empty = v.findViewById(R.id.tvChurnEmpty);
        empty.setText("No " + (segment == null ? "customer" : segment)
                + " retention segment is available right now.");
        return v;
    }
}
