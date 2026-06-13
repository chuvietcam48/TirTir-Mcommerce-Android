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
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.example.tirtir_mcommerce.ui.activities.ConflictResultActivity;
import com.example.tirtir_mcommerce.ui.adapters.ScanHistoryAdapter;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
        adapter = new ScanHistoryAdapter(item -> {
            Intent intent = new Intent(requireContext(), ConflictResultActivity.class);
            intent.putStringArrayListExtra("INGREDIENTS", item.ingredients);
            startActivity(intent);
        });
        rvHistory.setAdapter(adapter);
        loadHistory();
    }

    private void loadHistory() {
        User user = new SharedPrefsManager(requireContext()).getCachedUser();
        if (user == null || user.getId() == null) {
            showEmpty("Sign in to view your ingredient scan history.");
            return;
        }
        ApiService api = RetrofitClient.getAuthClient(requireContext()).create(ApiService.class);
        api.getIngredientHistory(user.getId()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    showEmpty("Unable to load scan history.");
                    return;
                }
                bindHistory(response.body().get("history"));
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) showEmpty("Connection error. Please try again.");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void bindHistory(Object historyObject) {
        List<ScanHistoryAdapter.ScanHistoryItem> items = new ArrayList<>();
        if (historyObject instanceof List) {
            for (Object entry : (List<?>) historyObject) {
                if (!(entry instanceof Map)) continue;
                Map<String, Object> row = (Map<String, Object>) entry;
                ArrayList<String> ingredients = new ArrayList<>();
                Object ingredientObject = row.get("ingredients");
                if (ingredientObject instanceof List) {
                    for (Object ingredient : (List<?>) ingredientObject) {
                        if (ingredient != null) ingredients.add(String.valueOf(ingredient));
                    }
                }
                String preview = ingredients.isEmpty()
                        ? "No ingredient preview"
                        : android.text.TextUtils.join(", ", ingredients.subList(0, Math.min(3, ingredients.size())));
                items.add(new ScanHistoryAdapter.ScanHistoryItem(
                        value(row.get("productName"), "Scanned product"),
                        value(row.get("scannedAt"), "Date unavailable"),
                        preview,
                        ingredients));
            }
        }
        adapter.submitList(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String value(Object value, String fallback) {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private void showEmpty(String message) {
        adapter.submitList(new ArrayList<>());
        tvEmpty.setText(message);
        tvEmpty.setVisibility(View.VISIBLE);
    }
}
