package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScanActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 10;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;
    private boolean isScanning = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scan);

        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        scanner = BarcodeScanning.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e("BarcodeScanActivity", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void processImageProxy(ImageProxy imageProxy) {
        if (!isScanning || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue != null && rawValue.startsWith("TIRTIR-")) {
                            isScanning = false;
                            processLoyaltyBarcode(rawValue);
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("BarcodeScanActivity", "Barcode scanning failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void processLoyaltyBarcode(String code) {
        ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        
        api.scanBarcode(body).enqueue(new retrofit2.Callback<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> call, retrofit2.Response<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> response) {
                showResultBottomSheet(response.isSuccessful(), code);
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> call, Throwable t) {
                showResultBottomSheet(false, code);
            }
        });
    }

    private void showResultBottomSheet(boolean success, String code) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // Simple programmatically created layout for BottomSheet
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(64, 64, 64, 64);
        
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(success ? "Scan Successful!" : "Scan Failed");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        
        android.widget.TextView tvDesc = new android.widget.TextView(this);
        tvDesc.setText("Code: " + code);
        tvDesc.setPadding(0, 32, 0, 32);
        
        com.google.android.material.button.MaterialButton btnClose = new com.google.android.material.button.MaterialButton(this);
        btnClose.setText("Close");
        btnClose.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        
        layout.addView(tvTitle);
        layout.addView(tvDesc);
        layout.addView(btnClose);
        
        dialog.setContentView(layout);
        dialog.setOnDismissListener(d -> finish());
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
