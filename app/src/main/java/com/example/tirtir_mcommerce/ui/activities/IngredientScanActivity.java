package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.tirtir_mcommerce.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;

public class IngredientScanActivity extends AppCompatActivity {
    private PreviewView previewView;
    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "Camera permission is required to scan ingredients", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingredient_scan);
        previewView = findViewById(R.id.previewIngredient);
        findViewById(R.id.btnCloseIngredientScan).setOnClickListener(v -> finish());
        findViewById(R.id.btnCaptureIngredient).setOnClickListener(v -> {
            String ingredients = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
            if (ingredients == null || ingredients.trim().isEmpty()) {
                Toast.makeText(this,
                        "Ingredient recognition is temporarily unavailable.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(this, ConflictResultActivity.class);
            intent.putExtra("PRODUCT_ID", getIntent().getStringExtra("PRODUCT_ID"));
            intent.putExtra("PRODUCT_NAME", getIntent().getStringExtra("PRODUCT_NAME"));
            intent.putExtra("SECOND_PRODUCT_ID", getIntent().getStringExtra("SECOND_PRODUCT_ID"));
            intent.putExtra("SECOND_PRODUCT_NAME", getIntent().getStringExtra("SECOND_PRODUCT_NAME"));
            ArrayList<String> ingredientList = new ArrayList<>();
            for (String ingredient : ingredients.split("[,;\\n]")) {
                String trimmed = ingredient.trim();
                if (!trimmed.isEmpty()) ingredientList.add(trimmed);
            }
            intent.putStringArrayListExtra("INGREDIENTS", ingredientList);
            startActivity(intent);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (Exception error) {
                Toast.makeText(this, "Camera is unavailable on this device", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
