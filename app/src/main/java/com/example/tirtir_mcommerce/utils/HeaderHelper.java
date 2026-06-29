package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;
import com.example.tirtir_mcommerce.ui.activities.NotificationCenterActivity;
import com.example.tirtir_mcommerce.ui.fragments.CartFragment;

public class HeaderHelper {

    public static void bind(View root, Context context, FragmentManager fragmentManager) {
        TextView tvGreeting = root.findViewById(R.id.tvGreeting);
        if (tvGreeting != null) {
            SharedPrefsManager prefs = new SharedPrefsManager(context);
            User user = prefs.getCachedUser();
            String name = (user != null && user.getName() != null && !user.getName().isEmpty())
                    ? user.getName().split(" ")[0]
                    : "Guest";
            tvGreeting.setText("Hello, " + name);
        }

        View btnNotifications = root.findViewById(R.id.btnNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v ->
                    context.startActivity(new Intent(context, NotificationCenterActivity.class)));
        }

        View btnCart = root.findViewById(R.id.btnCart);
        if (btnCart != null && fragmentManager != null) {
            btnCart.setOnClickListener(v ->
                    fragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, new CartFragment())
                            .addToBackStack(null)
                            .commit());
        }
    }
}
