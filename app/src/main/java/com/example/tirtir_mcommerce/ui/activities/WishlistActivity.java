package com.example.tirtir_mcommerce.ui.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.ui.fragments.WishlistFragment;

public class WishlistActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wishlist);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.wishlistFragmentContainer, new WishlistFragment())
                    .commit();
        }
    }
}
