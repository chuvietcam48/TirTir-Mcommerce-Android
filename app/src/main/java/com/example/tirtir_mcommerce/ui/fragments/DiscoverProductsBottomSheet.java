package com.example.tirtir_mcommerce.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DiscoverProductsBottomSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_discover_products, container, false);
        
        ImageView btnCloseModal = view.findViewById(R.id.btnCloseModal);
        btnCloseModal.setOnClickListener(v -> dismiss());

        RecyclerView rvDiscoverProducts = view.findViewById(R.id.rvDiscoverProducts);
        // Adapter logic goes here when connected to Product API

        return view;
    }
}
