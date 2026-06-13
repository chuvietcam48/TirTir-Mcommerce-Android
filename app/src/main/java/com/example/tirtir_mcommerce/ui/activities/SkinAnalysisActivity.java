package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SkinAnalysisActivity extends AppCompatActivity {
    private PreviewView previewView;
    private MaterialButton captureButton;
    private ImageCapture imageCapture;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "Camera permission is required for skin analysis", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_analysis);
        previewView = findViewById(R.id.previewSkin);
        captureButton = findViewById(R.id.btnCaptureSkin);
        findViewById(R.id.btnCloseSkinAnalysis).setOnClickListener(v -> finish());
        captureButton.setOnClickListener(v -> captureAndAnalyze());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageCapture);
                captureButton.setEnabled(true);
            } catch (Exception error) {
                captureButton.setEnabled(false);
                Toast.makeText(this, "Camera is unavailable on this device", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndAnalyze() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera is still starting", Toast.LENGTH_SHORT).show();
            return;
        }
        setAnalyzing(true);
        File imageFile = new File(getCacheDir(), "skin-analysis-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(imageFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        analyzeImage(imageFile);
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        setAnalyzing(false);
                        Toast.makeText(SkinAnalysisActivity.this,
                                "Unable to capture image. Please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void analyzeImage(File imageFile) {
        try {
            byte[] imageBytes;
            try (FileInputStream input = new FileInputStream(imageFile);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                imageBytes = output.toByteArray();
            }
            String encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            Map<String, String> body = new HashMap<>();
            body.put("imageData", "data:image/jpeg;base64," + encoded);
            ApiService api = RetrofitClient.getAuthClient(this).create(ApiService.class);
            api.analyzeSkin(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                       Response<ApiResponse<Map<String, Object>>> response) {
                    setAnalyzing(false);
                    Map<String, Object> data = response.body() == null ? null : response.body().getData();
                    if (!response.isSuccessful() || data == null) {
                        Toast.makeText(SkinAnalysisActivity.this,
                                "Skin analysis is unavailable. Please try again.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    openResult(data);
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    setAnalyzing(false);
                    Toast.makeText(SkinAnalysisActivity.this,
                            "Connection error. Please try again.",
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception error) {
            setAnalyzing(false);
            Toast.makeText(this, "Unable to prepare the photo for analysis", Toast.LENGTH_LONG).show();
        } finally {
            imageFile.delete();
        }
    }

    @SuppressWarnings("unchecked")
    private void openResult(Map<String, Object> data) {
        Intent intent = new Intent(this, SkinResultActivity.class);
        intent.putExtra("SKIN_TONE", stringValue(data.get("skinTone")));
        intent.putExtra("UNDERTONE", stringValue(data.get("undertone")));
        intent.putExtra("SKIN_TYPE", stringValue(data.get("skinType")));
        Object confidence = data.get("confidence");
        if (confidence instanceof Number) {
            intent.putExtra("CONFIDENCE", ((Number) confidence).doubleValue());
        }
        Object debugObject = data.get("debug_values");
        if (debugObject instanceof Map) {
            Map<String, Object> debug = (Map<String, Object>) debugObject;
            double l = numberValue(debug.get("L"));
            double b = numberValue(debug.get("b"));
            if (b != 0) intent.putExtra("ITA_ANGLE", Math.toDegrees(Math.atan((l - 50.0) / b)));
        }
        startActivity(intent);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double numberValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private void setAnalyzing(boolean analyzing) {
        captureButton.setEnabled(!analyzing);
        captureButton.setText(analyzing ? "Analyzing..." : "Capture & Analyze");
    }
}
