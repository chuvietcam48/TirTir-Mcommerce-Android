package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;

public class IngredientScanActivity extends AppCompatActivity {
    private static final String TAG = "IngredientScan";
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
        setContentView(R.layout.activity_ingredient_scan);
        previewView = findViewById(R.id.previewIngredient);
        captureButton = findViewById(R.id.btnCaptureIngredient);
        findViewById(R.id.btnCloseIngredientScan).setOnClickListener(v -> finish());
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> captureIngredientList());

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
                    .setMessage("TirTir uses the camera to capture the ingredient label for analysis.")
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
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.unbindAll();
                CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        ? CameraSelector.DEFAULT_BACK_CAMERA
                        : CameraSelector.DEFAULT_FRONT_CAMERA;
                provider.bindToLifecycle(this, selector, preview, imageCapture);
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

    private void captureIngredientList() {
        if (imageCapture == null) {
            showCameraUnavailableDialog();
            return;
        }
        captureButton.setEnabled(false);
        captureButton.setText("Capturing...");
        File output = new File(getCacheDir(), "ingredient-" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(output).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        captureButton.setText("Capture");
                        captureButton.setEnabled(true);
                        openIngredientResult();
                        output.delete();
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        Log.e(TAG, "Ingredient photo capture failed", exception);
                        captureButton.setText("Capture");
                        captureButton.setEnabled(true);
                        showCaptureRetryDialog();
                    }
                });
    }

    private void openIngredientResult() {
        String ingredients = getIntent().getStringExtra("PRODUCT_INGREDIENTS");
        boolean demo = ingredients == null || ingredients.trim().isEmpty();
        ArrayList<String> ingredientList = new ArrayList<>();
        if (demo) {
            ingredientList.add("Water");
            ingredientList.add("Glycerin");
            ingredientList.add("Niacinamide");
            ingredientList.add("Retinol");
            ingredientList.add("Glycolic Acid");
            ingredientList.add("Fragrance");
        } else {
            for (String ingredient : ingredients.split("[,;\\n]")) {
                String trimmed = ingredient.trim();
                if (!trimmed.isEmpty()) ingredientList.add(trimmed);
            }
        }

        Intent intent = new Intent(this, ConflictResultActivity.class);
        intent.putExtra("PRODUCT_ID", getIntent().getStringExtra("PRODUCT_ID"));
        intent.putExtra("PRODUCT_NAME", getIntent().getStringExtra("PRODUCT_NAME"));
        intent.putExtra("SECOND_PRODUCT_ID", getIntent().getStringExtra("SECOND_PRODUCT_ID"));
        intent.putExtra("SECOND_PRODUCT_NAME", getIntent().getStringExtra("SECOND_PRODUCT_NAME"));
        intent.putExtra("IS_DEMO_OCR", demo);
        intent.putStringArrayListExtra("INGREDIENTS", ingredientList);
        startActivity(intent);
    }

    private void showCameraAccessDialog() {
        showFallbackDialog(
                "Camera access needed",
                "Allow camera access in Settings, or continue with a labelled demo scan.",
                true);
    }

    private void showCameraUnavailableDialog() {
        showFallbackDialog(
                "Camera unavailable",
                "This emulator or device cannot open a camera right now. Retry or preview a demo scan.",
                false);
    }

    private void showCaptureRetryDialog() {
        showFallbackDialog(
                "Photo not captured",
                "Hold the ingredient label steady and try again. You can also preview a demo scan.",
                false);
    }

    private void showFallbackDialog(String title, String message, boolean openSettings) {
        if (fallbackDialogShowing || isFinishing() || isDestroyed()) return;
        fallbackDialogShowing = true;
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton("Use demo", (ignored, which) -> openIngredientResult())
                .setNegativeButton("Cancel", null)
                .setPositiveButton(openSettings ? "Open Settings" : "Retry",
                        (ignored, which) -> {
                            if (openSettings) openAppSettings();
                            else startCamera();
                        })
                .create();
        dialog.setOnDismissListener(ignored -> fallbackDialogShowing = false);
        dialog.show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
}
