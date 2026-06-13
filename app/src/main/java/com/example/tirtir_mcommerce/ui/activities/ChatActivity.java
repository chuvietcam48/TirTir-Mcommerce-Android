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
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentHostContainer, fragment)
                    .commit();
        }
    }
}
