package com.example.tirtir_mcommerce.ui.activities;

import android.graphics.Bitmap;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFrontFacingFragment;
import com.google.ar.sceneform.ux.AugmentedFaceNode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ARTryOnActivity extends AppCompatActivity {

    private LinearLayout layoutColors;
    private TextView tvLoading;
    private int selectedIndex = 0;

        java.util.ArrayList<String> namesExtra = getIntent().getStringArrayListExtra("SHADE_NAMES");
        java.util.ArrayList<String> hexesExtra = getIntent().getStringArrayListExtra("SHADE_HEXES");

        if (namesExtra != null && hexesExtra != null && !hexesExtra.isEmpty()) {
            activeShadeColors = new int[hexesExtra.size()];
            activeShadeNames = new String[hexesExtra.size()];
            for (int i = 0; i < hexesExtra.size(); i++) {
                String hex = hexesExtra.get(i);
                activeShadeNames[i] = i < namesExtra.size() ? namesExtra.get(i) : ("Shade " + (i + 1));
                try {
                    if (!hex.startsWith("#")) {
                        hex = "#" + hex;
                    }
                    activeShadeColors[i] = Color.parseColor(hex);
                } catch (Exception e) {
                    activeShadeColors[i] = SHADE_COLORS[i % SHADE_COLORS.length];
                }
            }
        } else {
            activeShadeColors = SHADE_COLORS;
            activeShadeNames = new String[SHADE_COLORS.length];
            for (int i = 0; i < SHADE_COLORS.length; i++) {
                activeShadeNames[i] = "Shade " + (i + 1);
            }
        }

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

    private void fetchShadesFromApi() {
        if (productId == null || productId.trim().isEmpty()) {
            Toast.makeText(this, "Product ID is missing", Toast.LENGTH_SHORT).show();
            return;
        }
        tvLoading.setVisibility(View.VISIBLE);
        tvLoading.setText("Loading shades...");

        ApiService api = RetrofitClient.getClient().create(ApiService.class);
        api.getShades(productId, null, 100).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    availableShades.clear();
                    parsedColors.clear();
                    availableShades.addAll(response.body());

                    for (Map<String, Object> shade : availableShades) {
                        String hex = "";
                        if (shade.get("Hex_Code") != null) {
                            hex = shade.get("Hex_Code").toString().trim();
                        } else if (shade.get("shade_color_hex") != null) {
                            hex = shade.get("shade_color_hex").toString().trim();
                        }
                        
                        if (!hex.startsWith("#") && !hex.isEmpty()) hex = "#" + hex;
                        int color = Color.parseColor("#E9B5A5"); // Fallback
                        try {
                            if (!hex.isEmpty()) color = Color.parseColor(hex);
                        } catch (Exception ignored) {}
                        parsedColors.add(color);
                    }
                    buildColorPicker();
                    updateMaterialColor();
                } else {
                    Toast.makeText(ARTryOnActivity.this, "No shades available for AR", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(ARTryOnActivity.this, "Failed to load shades", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkArCoreSupport() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        android.util.Log.d("ARTryOnActivity", "ARCore availability: " + availability.name());

        switch (availability) {
            case SUPPORTED_INSTALLED:
                // ARCore is ready — proceed
                return true;

            case SUPPORTED_NOT_INSTALLED:
            case SUPPORTED_APK_TOO_OLD:
                // Can install/update — prompt user
                showInstallArCoreDialog();
                return false;

            case UNKNOWN_CHECKING:
            case UNKNOWN_TIMED_OUT:
                // Still checking — retry shortly
                tvLoading.postDelayed(this::checkArCoreSupport, 500);
                return false;

            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
            default:
                showArNotSupportedDialog();
                return false;
        }
    }

    private void showInstallArCoreDialog() {
        tvLoading.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("AR Services Required")
                .setMessage("This feature requires Google Play Services for AR. Would you like to install it?")
                .setPositiveButton("Install", (dialog, which) -> {
                    try {
                        // Request install via ARCore SDK
                        ArCoreApk.getInstance().requestInstall(this, true);
                    } catch (Exception e) {
                        // Fallback: open Play Store directly
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=com.google.ar.core")));
                        } catch (Exception ex) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core")));
                        }
                    }
                })
                .setNegativeButton("Not Now", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showArNotSupportedDialog() {
        tvLoading.setVisibility(View.GONE);
        new AlertDialog.Builder(this)
                .setTitle("AR Not Supported")
                .setMessage("Your device does not support AR Try-On. If you're on an emulator, please test on a real device.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setupAr() {
        arFragment = new ArFrontFacingFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.arFragmentContainer, arFragment)
                .commit();

        tvLoading.setText("Detecting face...");
        tvLoading.postDelayed(() -> {
            ArSceneView sceneView = arFragment.getArSceneView();
            if (sceneView != null) {
                sceneView.getScene().addOnUpdateListener(frameTime -> {
                    if (sceneView.getSession() == null) return;
                    Collection<AugmentedFace> faceList = sceneView.getSession().getAllTrackables(AugmentedFace.class);

                    for (Map.Entry<AugmentedFace, AugmentedFaceNode> entry : new HashMap<>(faceNodeMap).entrySet()) {
                        AugmentedFace face = entry.getKey();
                        if (face.getTrackingState() == TrackingState.STOPPED) {
                            AugmentedFaceNode node = entry.getValue();
                            node.setParent(null);
                            faceNodeMap.remove(face);
                        }
                    }

                    for (AugmentedFace face : faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            AugmentedFaceNode faceNode = new AugmentedFaceNode(face);
                            faceNode.setParent(sceneView.getScene());
                            if (lipsTexture != null) {
                                faceNode.setFaceMeshTexture(lipsTexture);
                                setFaceMeshMaterial(faceNode, null);
                            }
                            faceNodeMap.put(face, faceNode);
                        }
                    }
                    if (!faceList.isEmpty()) {
                        tvLoading.setVisibility(View.GONE);
                    }
                });
            }
        }, 1000);
    }

    private void updateMaterialColor() {

        int color = activeShadeColors[selectedIndex];
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int alphaColor = Color.argb(100, r, g, b); // ~0.4 alpha
        com.google.ar.sceneform.rendering.MaterialFactory.makeTransparentWithColor(this, new com.google.ar.sceneform.rendering.Color(alphaColor))
                .thenAccept(material -> {
                    for (AugmentedFaceNode node : faceNodeMap.values()) {
                        node.setFaceMeshTexture(null);
                        setFaceMeshMaterial(node, material);
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Failed to create material", Toast.LENGTH_SHORT).show();
                    return null;
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

        for (int i = 0; i < activeShadeColors.length; i++) {

            ImageButton button = new ImageButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
            params.setMargins(dp(6), 0, dp(6), 0);
            button.setLayoutParams(params);

            button.setBackground(createShadeBackground(activeShadeColors[i], i == selectedIndex));
            button.setContentDescription("Choose " + activeShadeNames[i]);

            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setPadding(0, 0, 0, 0);
            final int index = i;
            button.setOnClickListener(v -> {
                selectedIndex = index;
                updateMaterialColor();
                buildColorPicker();
                Toast.makeText(this, "Selected: " + activeShadeNames[index], Toast.LENGTH_SHORT).show();
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

    private void setFaceMeshMaterial(AugmentedFaceNode node, Material material) {
        try {
            java.lang.reflect.Field field = AugmentedFaceNode.class.getDeclaredField("overrideFaceMeshMaterial");
            field.setAccessible(true);
            field.set(node, material);
        } catch (Exception e) {
            android.util.Log.e("ARTryOnActivity", "Failed to set face mesh material via reflection", e);
        }
    }
}

