package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.fragments.ChatFragment;

public class ChatActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_host);
        if (savedInstanceState == null) {
            ChatFragment fragment = new ChatFragment();
            Bundle args = new Bundle();
            args.putString("PRODUCT_ID", getIntent().getStringExtra("PRODUCT_ID"));
            args.putString("PRODUCT_NAME", getIntent().getStringExtra("PRODUCT_NAME"));
            args.putString("PRODUCT_INGREDIENTS", getIntent().getStringExtra("PRODUCT_INGREDIENTS"));
            args.putString("PRODUCT_SKIN_TYPES", getIntent().getStringExtra("PRODUCT_SKIN_TYPES"));
            args.putString("PRODUCT_HOW_TO_USE", getIntent().getStringExtra("PRODUCT_HOW_TO_USE"));
            args.putString("PRODUCT_DESCRIPTION", getIntent().getStringExtra("PRODUCT_DESCRIPTION"));
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentHostContainer, fragment)
                    .commit();
        }
    }
}
