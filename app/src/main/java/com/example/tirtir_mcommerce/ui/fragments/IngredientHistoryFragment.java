package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.activities.ConflictResultActivity;
import com.example.tirtir_mcommerce.ui.adapters.ScanHistoryAdapter;

import java.util.ArrayList;

public class IngredientHistoryFragment extends Fragment {
    private ScanHistoryAdapter adapter;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ingredient_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tvIngredientHistoryEmpty);
        RecyclerView rvHistory = view.findViewById(R.id.rvIngredientHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ScanHistoryAdapter(item -> startActivity(new Intent(requireContext(), ConflictResultActivity.class)));
        rvHistory.setAdapter(adapter);
        adapter.submitList(new ArrayList<>());
        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
