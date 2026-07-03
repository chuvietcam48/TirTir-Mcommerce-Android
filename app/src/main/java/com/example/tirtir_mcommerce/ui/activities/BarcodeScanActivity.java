package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
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

@androidx.camera.core.ExperimentalGetImage
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
                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                if (!cameraProvider.hasCamera(selector) && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                }
                
                try {
                    cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis);
                } catch (Exception e) {
                    Log.e("BarcodeScanActivity", "Use case binding failed, trying front", e);
                    cameraProvider.unbindAll();
                    try {
                        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
                    } catch (Exception e2) {
                        Log.e("BarcodeScanActivity", "Fallback failed", e2);
                        runOnUiThread(() -> Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception mainException) {
                Log.e("BarcodeScanActivity", "Camera initialization failed", mainException);
            }
        }, ContextCompat.getMainExecutor(this));
    }

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
        body.put("barcodeValue", code); // Bug fixed: change from "code" to "barcodeValue"
        
        api.scanBarcode(body).enqueue(new retrofit2.Callback<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> call, retrofit2.Response<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> response) {
                if (!isFinishing()) {
                    if (response.isSuccessful() && response.body() != null) {
                        showResultBottomSheet(true, code, response.body().getMessage());
                    } else {
                        String errorMsg = "Mã vạch không hợp lệ hoặc đã được sử dụng.";
                        if (response.errorBody() != null) {
                            try {
                                String errStr = response.errorBody().string();
                                com.example.tirtir_mcommerce.model.ApiResponse<?> errResp = 
                                    new com.google.gson.Gson().fromJson(errStr, com.example.tirtir_mcommerce.model.ApiResponse.class);
                                if (errResp != null && errResp.getMessage() != null) {
                                    errorMsg = errResp.getMessage();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        showResultBottomSheet(false, code, errorMsg);
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.tirtir_mcommerce.model.ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isFinishing()) {
                    showResultBottomSheet(false, code, "Không thể kết nối máy chủ");
                }
            }
        });
    }

    private void showResultBottomSheet(boolean success, String code, String message) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_scan_result, null);
        
        ImageView ivIcon = view.findViewById(R.id.ivResultStatusIcon);
        TextView tvTitle = view.findViewById(R.id.tvResultTitle);
        TextView tvDesc = view.findViewById(R.id.tvResultDesc);
        com.google.android.material.button.MaterialButton btnClose = view.findViewById(R.id.btnResultClose);
        
        if (success) {
            ivIcon.setImageResource(android.R.drawable.ic_dialog_info);
            ivIcon.setColorFilter(Color.parseColor("#2E7D32")); // Success green
            tvTitle.setText("+50 điểm");
            tvTitle.setTextColor(Color.parseColor("#2E7D32"));
            tvDesc.setText(message != null ? message : "Quét mã vạch thành công! Bạn nhận được 50 điểm.");
        } else {
            ivIcon.setImageResource(android.R.drawable.ic_dialog_alert);
            ivIcon.setColorFilter(Color.parseColor("#D32F2F")); // Error red
            tvTitle.setText("Thất bại");
            tvTitle.setTextColor(Color.parseColor("#D32F2F"));
            tvDesc.setText(message != null ? message : "Mã vạch không hợp lệ hoặc đã được sử dụng.");
        }
        
        btnClose.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        
        dialog.setContentView(view);
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
