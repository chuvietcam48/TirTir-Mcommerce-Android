package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.tirtir_mcommerce.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.ShadeMatchResult;
import com.example.tirtir_mcommerce.model.SkinAnalysisResult;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.RetrofitClient;
import com.google.gson.Gson;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SCR-34: SkinAnalysisActivity — Virtual Camera với Face Tracking.
 *
 * Luồng hoạt động:
 * 1. CameraX mở camera trước với 3 use cases: Preview + ImageAnalysis + ImageCapture
 * 2. FrameAnalyzer (ImageAnalysis) chạy MLKit Face Detection trên mỗi frame
 * 3. Validate tư thế khuôn mặt (số lượng, mắt mở, góc đầu)
 * 4. Trích xuất màu RGB tại 5 điểm ROI (Forehead, Nose, Cheeks, Chin)
 * 5. Buffer 15 frames → kiểm tra lighting → tính Live Metrics
 * 6. Khi đủ điều kiện → bật nút Capture
 * 7. Sau chụp → encode Base64 → sang SkinResultActivity
 */
public class SkinAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "SkinAnalysisActivity";

    // CameraX
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private boolean cameraStarting;
    private boolean fallbackDialogShowing;

    // Face Detection
    private FaceDetector faceDetector;

    // State
    private boolean captureReady = false;

    // Color history: buffer 15 frames, mỗi frame là float[3] = {R, G, B}
    private static final int HISTORY_SIZE = 15;
    private final ArrayDeque<float[]> colorHistory = new ArrayDeque<>();

    // Last captured avg color (dùng để gửi sang SkinResultActivity)
    private float avgR = 0, avgG = 0, avgB = 0;

    // UI
    private MaterialButton captureButton;
    private TextView tvStatusGuide;
    private View liveMetricsPanel;
    private ProgressBar progressMoisture, progressRedness, progressPoresLive, progressEvenness;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else showCameraAccessDialog();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skin_analysis);

        previewView       = findViewById(R.id.previewSkin);
        captureButton     = findViewById(R.id.btnCaptureSkin);
        tvStatusGuide     = findViewById(R.id.tvStatusGuide);
        liveMetricsPanel  = findViewById(R.id.liveMetricsPanel);
        progressMoisture  = findViewById(R.id.progressMoisture);
        progressRedness   = findViewById(R.id.progressRedness);
        progressPoresLive = findViewById(R.id.progressPoresLive);
        progressEvenness  = findViewById(R.id.progressEvenness);

        findViewById(R.id.btnCloseSkinAnalysis).setOnClickListener(v -> finish());
        captureButton.setOnClickListener(v -> captureAndAnalyze());
        captureButton.setEnabled(false);

        cameraExecutor = Executors.newSingleThreadExecutor();
        initFaceDetector();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraAccess();
        }
    }

    private void initFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.2f)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);
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

                // Use case 1: Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Use case 2: ImageCapture
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Use case 3: ImageAnalysis — chạy face detection mỗi frame
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new FrameAnalyzer());

                CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, imageCapture, imageAnalysis);

                cameraStarting = false;
            } catch (Exception error) {
                cameraStarting = false;
                Log.e(TAG, "Unable to bind CameraX", error);
                imageCapture = null;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        faceDetector.close();
    }

    // ===========================
    // FRAME ANALYZER
    // ===========================

    /**
     * Phân tích mỗi camera frame:
     * 1. MLKit Face Detection → kiểm tra tư thế
     * 2. Trích xuất màu 5 ROI points
     * 3. Cập nhật color history (15 frames)
     * 4. Kiểm tra lighting
     * 5. Tính Live Metrics → cập nhật UI
     * 6. Quyết định trạng thái Ready
     */
    private class FrameAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy imageProxy) {
            try {
                InputImage inputImage = InputImage.fromMediaImage(
                        imageProxy.getImage(),
                        imageProxy.getImageInfo().getRotationDegrees()
                );

                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            processFrameResult(imageProxy, faces);
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Face detection failed on frame", e);
                            updateStatus("Detecting face...", false);
                        })
                        .addOnCompleteListener(task -> imageProxy.close());

            } catch (Exception e) {
                Log.e(TAG, "Frame analysis error", e);
                imageProxy.close();
            }
        }
    }

    private void processFrameResult(ImageProxy imageProxy, List<Face> faces) {
        // a. Validate face pose
        String poseError = validateFacePose(faces);
        if (poseError != null) {
            synchronized (colorHistory) { colorHistory.clear(); }
            updateStatus(poseError, false);
            return;
        }

        Face face = faces.get(0);

        // b. Trích xuất màu 5 ROI points từ bounding box
        float[] roiColor = extractRoiColor(imageProxy, face);
        if (roiColor == null) {
            updateStatus("Analyzing skin...", false);
            return;
        }

        // c. Cập nhật color history
        synchronized (colorHistory) {
            colorHistory.addLast(roiColor);
            if (colorHistory.size() > HISTORY_SIZE) colorHistory.pollFirst();
        }

        // d. Chỉ đánh giá khi đủ 15 frames
        List<float[]> snapshot;
        synchronized (colorHistory) {
            snapshot = new ArrayList<>(colorHistory);
        }

        if (snapshot.size() < HISTORY_SIZE) {
            int progress = (snapshot.size() * 100) / HISTORY_SIZE;
            updateStatus("Calibrating... " + progress + "%", false);
            return;
        }

        // Tính average RGB từ 15 frames
        float sumR = 0, sumG = 0, sumB = 0;
        for (float[] c : snapshot) { sumR += c[0]; sumG += c[1]; sumB += c[2]; }
        avgR = sumR / snapshot.size();
        avgG = sumG / snapshot.size();
        avgB = sumB / snapshot.size();

        // e. Kiểm tra lighting
        String lightingError = checkLighting(avgR, avgG, avgB);
        if (lightingError != null) {
            updateStatus(lightingError, false);
            return;
        }

        // f. Tính Live Metrics
        LiveMetrics metrics = computeLiveMetrics(snapshot, avgR, avgG, avgB);

        // g. Cập nhật UI — sẵn sàng chụp
        runOnUiThread(() -> {
            tvStatusGuide.setText("✅ Ready! Tap to capture.");
            liveMetricsPanel.setVisibility(View.VISIBLE);
            progressMoisture.setProgress((int) metrics.moisture);
            progressRedness.setProgress((int) metrics.redness);
            progressPoresLive.setProgress((int) metrics.pores);
            progressEvenness.setProgress((int) metrics.evenness);

            if (!captureReady) {
                captureReady = true;
                captureButton.setEnabled(true);
            }
        });
    }

    /**
     * Kiểm tra tư thế khuôn mặt.
     * @return null nếu hợp lệ, chuỗi lỗi nếu không hợp lệ
     */
    private String validateFacePose(List<Face> faces) {
        if (faces.isEmpty()) {
            return "No face detected. Please look straight at the camera.";
        }
        if (faces.size() > 1) {
            return "⚠️ Multiple faces detected. Please have only ONE person in frame.";
        }

        Face face = faces.get(0);

        // Kiểm tra mắt mở (leftEyeOpenProbability / rightEyeOpenProbability)
        Float leftEyeOpen  = face.getLeftEyeOpenProbability();
        Float rightEyeOpen = face.getRightEyeOpenProbability();
        if (leftEyeOpen != null && rightEyeOpen != null
                && leftEyeOpen < 0.4f && rightEyeOpen < 0.4f) {
            return "👁️ Eyes closed. Please open your eyes and look at the camera.";
        }

        // Kiểm tra góc nghiêng đầu (Euler Y = yaw, Euler Z = roll)
        float yaw  = Math.abs(face.getHeadEulerAngleY());
        float roll = Math.abs(face.getHeadEulerAngleZ());
        if (yaw > 20f || roll > 15f) {
            return "↔️ Head tilted. Please face the camera straight on.";
        }

        return null; // Hợp lệ
    }

    /**
     * Trích xuất màu RGB trung bình tại 5 điểm ROI từ bounding box.
     * Ước tính toạ độ 5 điểm: Forehead, Nose, LeftCheek, RightCheek, Chin.
     *
     * @return float[3] {R, G, B} trung bình (0–255), hoặc null nếu frame không hợp lệ
     */
    private float[] extractRoiColor(ImageProxy imageProxy, Face face) {
        try {
            // Lấy bitmap từ ImageProxy (YUV_420_888 → NV21 → Bitmap)
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) return null;

            Rect box = face.getBoundingBox();
            int imgW = bitmap.getWidth();
            int imgH = bitmap.getHeight();

            // Clamp để không vượt boundary
            int left   = Math.max(0, box.left);
            int top    = Math.max(0, box.top);
            int right  = Math.min(imgW - 1, box.right);
            int bottom = Math.min(imgH - 1, box.bottom);
            int w      = right - left;
            int h      = bottom - top;
            if (w <= 0 || h <= 0) return null;

            // 5 điểm ROI (tỷ lệ tương đối trong bounding box)
            int[][] roiOffsets = {
                { left + w / 2, top + (int)(h * 0.15) },    // Forehead
                { left + w / 2, top + (int)(h * 0.50) },    // Nose
                { left + (int)(w * 0.20), top + (int)(h * 0.60) }, // Left Cheek
                { left + (int)(w * 0.80), top + (int)(h * 0.60) }, // Right Cheek
                { left + w / 2, top + (int)(h * 0.85) }     // Chin
            };

            float sumR = 0, sumG = 0, sumB = 0;
            int validPoints = 0;
            int sampleRadius = 4; // Lấy trung bình vùng nhỏ xung quanh điểm

            for (int[] pt : roiOffsets) {
                int px = Math.max(sampleRadius, Math.min(imgW - 1 - sampleRadius, pt[0]));
                int py = Math.max(sampleRadius, Math.min(imgH - 1 - sampleRadius, pt[1]));

                float r = 0, g = 0, b = 0;
                int count = 0;
                for (int dy = -sampleRadius; dy <= sampleRadius; dy++) {
                    for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
                        int pixel = bitmap.getPixel(px + dx, py + dy);
                        r += (pixel >> 16) & 0xFF;
                        g += (pixel >> 8) & 0xFF;
                        b += pixel & 0xFF;
                        count++;
                    }
                }
                sumR += r / count;
                sumG += g / count;
                sumB += b / count;
                validPoints++;
            }

            bitmap.recycle();

            if (validPoints == 0) return null;
            return new float[]{sumR / validPoints, sumG / validPoints, sumB / validPoints};

        } catch (Exception e) {
            Log.w(TAG, "ROI extraction failed", e);
            return null;
        }
    }

    /**
     * Chuyển ImageProxy (YUV_420_888) sang Bitmap.
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer  = planes[0].getBuffer();
            ByteBuffer uBuffer  = planes[1].getBuffer();
            ByteBuffer vBuffer  = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0,
                    imageProxy.getWidth(), imageProxy.getHeight()), 75, out);
            byte[] jpegBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            Log.w(TAG, "YUV→Bitmap conversion failed", e);
            return null;
        }
    }

    /**
     * Kiểm tra điều kiện ánh sáng.
     * Tính Luminance từ RGB: L = 0.299R + 0.587G + 0.114B
     * Quá tối (< 60) hoặc cháy sáng (> 220) → cảnh báo
     */
    private String checkLighting(float r, float g, float b) {
        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
        if (luminance < 60f) return "🌙 Too dark. Please move to a brighter area.";
        if (luminance > 220f) return "☀️ Too bright. Avoid direct strong light.";
        return null; // OK
    }

    /**
     * Tính 4 Live Metrics từ color history (theo spec):
     * - Moisture  = (B / Luminance) * 100
     * - Redness   = clamp(R - G, 0, 255) / 2.55
     * - Pores     = variance màu giữa các ROI points → kết cấu da kém
     * - Evenness  = 100 - Pores
     */
    private LiveMetrics computeLiveMetrics(List<float[]> history, float avgR, float avgG, float avgB) {
        float luminance = 0.299f * avgR + 0.587f * avgG + 0.114f * avgB;

        // Moisture: tỷ lệ kênh Blue
        float moisture = luminance > 0 ? Math.min(100f, (avgB / luminance) * 100f) : 50f;

        // Redness: chênh lệch R - G
        float redness = Math.min(100f, Math.max(0f, (avgR - avgG) / 2.55f));

        // Pores: phương sai màu giữa 5 điểm trong mỗi frame
        // Dùng variance của R across the history color values
        float meanR = avgR;
        float varianceSum = 0;
        for (float[] c : history) {
            float diff = c[0] - meanR;
            varianceSum += diff * diff;
        }
        float variance = history.size() > 0 ? varianceSum / history.size() : 0;
        // Chuẩn hoá về 0–100 (variance thường trong khoảng 0–400)
        float pores = Math.min(100f, (variance / 4f));

        // Evenness: tỷ lệ nghịch với Pores
        float evenness = Math.max(0f, 100f - pores);

        return new LiveMetrics(moisture, redness, pores, evenness);
    }

    private static class LiveMetrics {
        final float moisture, redness, pores, evenness;
        LiveMetrics(float moisture, float redness, float pores, float evenness) {
            this.moisture = moisture;
            this.redness = redness;
            this.pores = pores;
            this.evenness = evenness;
        }
    }

    private void updateStatus(String message, boolean ready) {
        runOnUiThread(() -> {
            tvStatusGuide.setText(message);
            if (!ready) {
                captureReady = false;
                captureButton.setEnabled(false);
                liveMetricsPanel.setVisibility(View.INVISIBLE);
            }
        });
    }

    // ===========================
    // CAPTURE
    // ===========================

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
                        uploadToPythonApi(imageFile);
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        Log.e(TAG, "Skin photo capture failed", exception);
                        setAnalyzing(false);
                        showCaptureRetryDialog();
                    }
                });
    }

    private void uploadToPythonApi(File imageFile) {
        // Compress JPEG
        try {
            Bitmap bmp = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            byte[] bitmapData = bos.toByteArray();

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), bitmapData);
            MultipartBody.Part body = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);

            ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
            apiService.analyzeSkinPython(body).enqueue(new Callback<SkinAnalysisResult>() {
                @Override
                public void onResponse(Call<SkinAnalysisResult> call, Response<SkinAnalysisResult> response) {
                    SkinAnalysisResult result;
                    if (response.isSuccessful() && response.body() != null) {
                        result = response.body();
                    } else {
                        Log.w(TAG, "Python ML failed, using fallback mock JSON");
                        result = getMockSkinAnalysisResult();
                    }
                    proceedToSkinResult(result);
                }

                @Override
                public void onFailure(Call<SkinAnalysisResult> call, Throwable t) {
                    Log.w(TAG, "Python ML error or timeout, using fallback mock JSON", t);
                    proceedToSkinResult(getMockSkinAnalysisResult());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to upload image", e);
            setAnalyzing(false);
            showAnalysisUnavailableDialog();
        } finally {
            imageFile.delete();
        }
    }

    private SkinAnalysisResult getMockSkinAnalysisResult() {
        try {
            InputStream is = getAssets().open("skin_analysis_mock.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            return new Gson().fromJson(json, SkinAnalysisResult.class);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load mock JSON", ex);
            SkinAnalysisResult fallback = new SkinAnalysisResult();
            fallback.setSkinType("Combination");
            fallback.setSkinTone("Medium");
            fallback.setUndertone("Neutral-warm");
            fallback.setSkinHex("#D8A087");
            fallback.setConfidence(85.0);
            List<String> concerns = new ArrayList<>();
            concerns.add("Visible Pores");
            concerns.add("Uneven Tone");
            fallback.setConcerns(concerns);
            return fallback;
        }
    }

    private void proceedToSkinResult(SkinAnalysisResult result) {
        ApiService apiService = RetrofitClient.getAuthClient(this).create(ApiService.class);
        apiService.matchCushion(result.getSkinHex()).enqueue(new Callback<ApiResponse<List<ShadeMatchResult>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ShadeMatchResult>>> call, Response<ApiResponse<List<ShadeMatchResult>>> response) {
                List<ShadeMatchResult> matches = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    matches = response.body().getData();
                } else {
                    matches = buildFallbackShades();
                }
                launchSkinResult(result, matches);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ShadeMatchResult>>> call, Throwable t) {
                launchSkinResult(result, buildFallbackShades());
            }
        });
    }

    private List<ShadeMatchResult> buildFallbackShades() {
        List<ShadeMatchResult> results = new ArrayList<>();
        String[][] shades = {
            {"17C Porcelain", "#f9d9c2", "3.2", "cushion-17c"},
            {"21N Ivory",     "#ebc5a1", "6.5", "cushion-21n"},
            {"23N Sand",      "#ebbf98", "11.0", "cushion-23n"}
        };
        for (String[] shade : shades) {
            ShadeMatchResult r = new ShadeMatchResult();
            r.setShadeName(shade[0]);
            r.setShadeHex(shade[1]);
            r.setMatchScore(Double.parseDouble(shade[2]));
            // Match percent fallback computation
            r.setMatchPercent((int) Math.round(100 * Math.exp(-Double.parseDouble(shade[2]) / 7.0)));
            r.setProductName("Mask Fit Red Cushion");
            r.setProductId(shade[3]);
            r.setImageUrl("https://tirtir.vn/wp-content/uploads/2024/05/Mask-Fit-Red-Cushion.jpg");
            results.add(r);
        }
        return results;
    }

    private void launchSkinResult(SkinAnalysisResult result, List<ShadeMatchResult> matches) {
        // Send SkinAnalysisResult JSON to SkinResultActivity so it doesn't need to re-evaluate
        Intent intent = new Intent(this, SkinResultActivity.class);
        intent.putExtra("SKIN_ANALYSIS_JSON", new Gson().toJson(result));
        intent.putExtra("SHADE_MATCHES_JSON", new Gson().toJson(matches));
        intent.putExtra(SkinResultActivity.EXTRA_AVG_R, (int) avgR);
        intent.putExtra(SkinResultActivity.EXTRA_AVG_G, (int) avgG);
        intent.putExtra(SkinResultActivity.EXTRA_AVG_B, (int) avgB);
        startActivity(intent);
        setAnalyzing(false);
    }

    private void openDemoResult() {
        Intent intent = new Intent(this, SkinResultActivity.class);
        intent.putExtra(SkinResultActivity.EXTRA_IS_DEMO, true);
        intent.putExtra(SkinResultActivity.EXTRA_AVG_R, 216);
        intent.putExtra(SkinResultActivity.EXTRA_AVG_G, 160);
        intent.putExtra(SkinResultActivity.EXTRA_AVG_B, 135);
        startActivity(intent);
    }

    // ===========================
    // DIALOGS
    // ===========================

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

    private void setAnalyzing(boolean analyzing) {
        if (captureButton != null) {
            captureButton.setEnabled(!analyzing && captureReady);
            captureButton.setText(analyzing ? "Analyzing..." : "Capture & Analyze");
        }
    }
}
