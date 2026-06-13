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
        
        rvChurnList.setAdapter(new ChurnUserAdapter(getContext(), new ArrayList<>(),
                user -> {}, user -> {}));
        TextView empty = v.findViewById(R.id.tvChurnEmpty);
        empty.setText("No " + (segment == null ? "customer" : segment)
                + " retention segment is available right now.");
        return v;
    }
}
