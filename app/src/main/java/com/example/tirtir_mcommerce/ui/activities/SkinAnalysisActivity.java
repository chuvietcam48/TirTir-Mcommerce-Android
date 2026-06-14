package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private static final String TAG = "SkinAnalysisActivity";
    private PreviewView previewView;
    private MaterialButton captureButton;
    private ImageCapture imageCapture;
    private boolean fallbackDialogShowing;
    private boolean cameraStarting;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showCameraAccessDialog();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_analysis);
        previewView = findViewById(R.id.previewSkin);
        captureButton = findViewById(R.id.btnCaptureSkin);
        findViewById(R.id.btnCloseSkinAnalysis).setOnClickListener(v -> finish());
        captureButton.setOnClickListener(v -> captureAndAnalyze());
        captureButton.setEnabled(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraAccess();
        }
    }

    private void requestCameraAccess() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Camera access")
                    .setMessage("TirTir uses the camera only to capture a photo for your skin analysis.")
                    .setNegativeButton("Not now", (dialog, which) -> showCameraAccessDialog())
                    .setPositiveButton("Continue",
                            (dialog, which) -> cameraPermission.launch(Manifest.permission.CAMERA))
                    .show();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (cameraStarting) return;
        cameraStarting = true;
        captureButton.setEnabled(false);
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
                CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;
                provider.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        imageCapture);
                cameraStarting = false;
                captureButton.setEnabled(true);
            } catch (Exception error) {
                cameraStarting = false;
                Log.e(TAG, "Unable to bind CameraX", error);
                imageCapture = null;
                captureButton.setEnabled(false);
                showCameraUnavailableDialog();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (previewView != null && imageCapture == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
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
                        Log.e(TAG, "Skin photo capture failed", exception);
                        setAnalyzing(false);
                        showCaptureRetryDialog();
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
                        Log.e(TAG, "Skin analysis API failed with HTTP " + response.code());
                        showAnalysisUnavailableDialog();
                        return;
                    }
                    openResult(data);
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    Log.e(TAG, "Skin analysis request failed", t);
                    setAnalyzing(false);
                    showAnalysisUnavailableDialog();
                }
            });
        } catch (Exception error) {
            Log.e(TAG, "Unable to prepare skin photo", error);
            setAnalyzing(false);
            showAnalysisUnavailableDialog();
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

    private void openDemoResult() {
        Intent intent = new Intent(this, SkinResultActivity.class);
        intent.putExtra("IS_DEMO", true);
        intent.putExtra("SKIN_HEX", "#D8A087");
        intent.putExtra("SKIN_TONE", "Medium");
        intent.putExtra("UNDERTONE", "Neutral-warm");
        intent.putExtra("SKIN_TYPE", "Combination");
        intent.putExtra("ITA_ANGLE", 28.0);
        intent.putExtra("TEXTURE_SCORE", 76);
        intent.putExtra("PORES_SCORE", 68);
        intent.putExtra("HYDRATION_SCORE", 72);
        startActivity(intent);
    }

    private void showCameraAccessDialog() {
        showFallbackDialog(
                "Camera access needed",
                "Allow camera access in Settings, or continue with a clearly labelled demo result.",
                true,
                this::requestCameraAccess);
    }

    private void showCameraUnavailableDialog() {
        showFallbackDialog(
                "Camera unavailable",
                "This emulator or device cannot open a camera right now. You can retry or preview a demo analysis.",
                false,
                this::startCamera);
    }

    private void showCaptureRetryDialog() {
        showFallbackDialog(
                "Photo not captured",
                "Keep your face inside the oval and try again, or preview a demo analysis.",
                false,
                this::captureAndAnalyze);
    }

    private void showAnalysisUnavailableDialog() {
        showFallbackDialog(
                "Analysis temporarily unavailable",
                "Your photo could not be analyzed right now. You can retry or preview a demo result.",
                false,
                () -> setAnalyzing(false));
    }

    private void showFallbackDialog(String title, String message, boolean openSettings,
                                    Runnable retryAction) {
        if (fallbackDialogShowing || isFinishing() || isDestroyed()) return;
        fallbackDialogShowing = true;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton("Use demo", (dialog, which) -> openDemoResult())
                .setNegativeButton("Cancel", null)
                .setPositiveButton(openSettings ? "Open Settings" : "Retry",
                        (dialog, which) -> {
                            if (openSettings) openAppSettings();
                            else if (retryAction != null) retryAction.run();
                        });
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(ignored -> fallbackDialogShowing = false);
        dialog.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double numberValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private void setAnalyzing(boolean analyzing) {
        if (captureButton != null) {
            captureButton.setEnabled(!analyzing && imageCapture != null);
            captureButton.setText(analyzing ? "Analyzing..." : "Capture & Analyze");
        }
    }
}
