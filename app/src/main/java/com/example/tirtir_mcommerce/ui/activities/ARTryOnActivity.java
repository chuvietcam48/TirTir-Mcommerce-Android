package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import java.io.File;
import java.io.FileOutputStream;
import com.example.tirtir_mcommerce.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.ux.ArFrontFacingFragment;
import com.google.ar.sceneform.ux.AugmentedFaceNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ARTryOnActivity extends AppCompatActivity {
    private static final int[] SHADE_COLORS = {
            0xFFE9B5A5, 0xFFD99A88, 0xFFC9786D, 0xFFB55E5A,
            0xFFA9474A, 0xFF8F343E, 0xFF74303A, 0xFF57252F
    };

    private LinearLayout layoutColors;
    private TextView tvLoading;
    private int selectedIndex = 0;

    private androidx.activity.result.ActivityResultLauncher<String> cameraPermissionLauncher;

    private ArFrontFacingFragment arFragment;
    private Material faceMaterial;
    private final HashMap<AugmentedFace, AugmentedFaceNode> faceNodeMap = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_try_on);

        layoutColors = findViewById(R.id.layoutArColorPicker);
        tvLoading = findViewById(R.id.tvArLoading);
        ImageButton btnClose = findViewById(R.id.btnCloseAr);
        FloatingActionButton btnCapture = findViewById(R.id.fabArCapture);

        btnClose.setOnClickListener(v -> finish());
        btnCapture.setOnClickListener(v -> takeArScreenshot());

        buildColorPicker();

        cameraPermissionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (checkArCoreSupport()) {
                            setupAr();
                        }
                    } else {
                        showArNotSupportedDialog();
                    }
                });

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (checkArCoreSupport()) {
                setupAr();
            }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private boolean checkArCoreSupport() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Re-check later
            tvLoading.postDelayed(() -> checkArCoreSupport(), 200);
            return false;
        }
        if (!availability.isSupported()) {
            showArNotSupportedDialog();
            return false;
        }
        return true;
    }

    private void showArNotSupportedDialog() {
        tvLoading.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("AR Not Supported")
                .setMessage("Your device does not support ARCore. Try-on is not available.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setupAr() {
        arFragment = new ArFrontFacingFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.arFragmentContainer, arFragment)
                .commit();

        updateMaterialColor();

        // Delay to allow fragment to attach
        tvLoading.postDelayed(() -> {
            ArSceneView sceneView = arFragment.getArSceneView();
            if (sceneView != null) {
                sceneView.getScene().addOnUpdateListener(frameTime -> {
                    if (sceneView.getSession() == null) return;
                    Collection<AugmentedFace> faceList = sceneView.getSession().getAllTrackables(AugmentedFace.class);

                    // Xóa trackables cũ
                    for (Map.Entry<AugmentedFace, AugmentedFaceNode> entry : new HashMap<>(faceNodeMap).entrySet()) {
                        AugmentedFace face = entry.getKey();
                        if (face.getTrackingState() == TrackingState.STOPPED) {
                            AugmentedFaceNode node = entry.getValue();
                            node.setParent(null);
                            faceNodeMap.remove(face);
                        }
                    }

                    // Thêm trackables mới
                    for (AugmentedFace face : faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            AugmentedFaceNode faceNode = new AugmentedFaceNode(face);
                            faceNode.setParent(sceneView.getScene());
                            // NOTE: gorisse sceneform 1.23 AugmentedFaceNode does not expose
                            // a public setMaterial API. Color overlay via shader is skipped.
                            // The face mesh node is still tracked correctly.
                            faceNodeMap.put(face, faceNode);
                        }
                    }
                    tvLoading.setVisibility(View.GONE);
                });
            }
        }, 1000);
    }

    private void updateMaterialColor() {
        int color = SHADE_COLORS[selectedIndex];
        // Create an opaque or semi-transparent material with the selected color
        MaterialFactory.makeTransparentWithColor(this, new com.google.ar.sceneform.rendering.Color(color))
                .thenAccept(material -> {
                    faceMaterial = material;
                    // Apply to existing face nodes
                    for (AugmentedFaceNode node : faceNodeMap.values()) {
                        // Color overlay skipped (API not available in gorisse sceneform 1.23)
                    }
                });
    }

    private void takeArScreenshot() {
        if (arFragment == null || arFragment.getArSceneView() == null) return;
        ArSceneView view = arFragment.getArSceneView();
        
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(view, bitmap, copyResult -> {
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    File file = new File(getExternalFilesDir(null), "ar_tryon_" + System.currentTimeMillis() + ".png");
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                    
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                    android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    shareIntent.setType("image/png");
                    shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share Try-On"));
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to save screenshot", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to capture AR view", Toast.LENGTH_SHORT).show();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void buildColorPicker() {
        layoutColors.removeAllViews();
        for (int i = 0; i < SHADE_COLORS.length; i++) {
            ImageButton button = new ImageButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
            params.setMargins(dp(6), 0, dp(6), 0);
            button.setLayoutParams(params);
            button.setBackground(createShadeBackground(SHADE_COLORS[i], i == selectedIndex));
            button.setContentDescription("Choose shade " + (i + 1));
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setPadding(0, 0, 0, 0);
            final int index = i;
            button.setOnClickListener(v -> {
                selectedIndex = index;
                updateMaterialColor();
                buildColorPicker();
            });
            layoutColors.addView(button);
        }
    }

    private GradientDrawable createShadeBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(selected ? 4 : 1), selected ? Color.WHITE : 0x55FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
