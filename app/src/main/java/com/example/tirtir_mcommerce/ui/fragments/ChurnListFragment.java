package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
        
        setupMockData();
        return v;
    }

    private void setupMockData() {
        List<ChurnUserAdapter.ChurnUser> users = new ArrayList<>();
        users.add(new ChurnUserAdapter.ChurnUser("User 1", "user1@gmail.com", segment, 5, 5, 5));
        users.add(new ChurnUserAdapter.ChurnUser("User 2", "user2@gmail.com", segment, 4, 3, 5));
        users.add(new ChurnUserAdapter.ChurnUser("User 3", "user3@gmail.com", segment, 2, 1, 3));

        ChurnUserAdapter adapter = new ChurnUserAdapter(getContext(), users, user -> {
            Toast.makeText(getContext(), "Đã gửi voucher cho " + user.name, Toast.LENGTH_SHORT).show();
        }, user -> {
            Toast.makeText(getContext(), "Đã gửi FCM cho " + user.name, Toast.LENGTH_SHORT).show();
        });
        rvChurnList.setAdapter(adapter);
    }
}
