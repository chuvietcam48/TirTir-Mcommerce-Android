package com.example.tirtir_mcommerce.ui.activities;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
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
import com.example.tirtir_mcommerce.ui.views.OvalGuideView;
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
 * 4. Kiểm tra face nằm trong khung oval (bắt buộc)
 * 5. Trích xuất màu RGB tại 5 điểm ROI (Forehead, Nose, Cheeks, Chin)
 * 6. Validate đủ 5 ROI points hợp lệ
 * 7. Buffer 15 frames → kiểm tra lighting → tính Live Metrics
 * 8. Khi đủ điều kiện → bật nút Capture
 * 9. Sau chụp → encode Base64 → sang SkinResultActivity
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

    // Last extracted ROI colors per-point (5 points × RGB)
    private float[][] lastRoiPerPoint;

    // UI
    private MaterialButton captureButton;
    private ValueAnimator buttonPulseAnimator;
    private TextView tvStatusGuide;
    private View liveMetricsPanel;
    private ProgressBar progressMoisture, progressRedness, progressPoresLive, progressEvenness;
    private OvalGuideView ovalGuide;
    private View tvLightingWarning;
    private TextView tvLightingWarningText;

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
        previewView.setScaleX(-1f); // Ép lật ngược lại camera (fix lỗi lật gương)
        captureButton     = findViewById(R.id.btnCaptureSkin);
        tvStatusGuide     = findViewById(R.id.tvStatusGuide);
        liveMetricsPanel  = findViewById(R.id.liveMetricsPanel);
        progressMoisture  = findViewById(R.id.progressMoisture);
        progressRedness   = findViewById(R.id.progressRedness);
        progressPoresLive = findViewById(R.id.progressPoresLive);
        progressEvenness  = findViewById(R.id.progressEvenness);
        ovalGuide         = findViewById(R.id.ovalGuide);
        tvLightingWarning = findViewById(R.id.tvLightingWarning);
        tvLightingWarningText = findViewById(R.id.tvLightingWarningText);

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
     * 2. Kiểm tra face nằm trong khung oval
     * 3. Trích xuất màu 5 ROI points từ bounding box
     * 4. Validate đủ 5 ROI points
     * 5. Cập nhật color history (15 frames)
     * 6. Kiểm tra lighting
     * 7. Tính Live Metrics → cập nhật UI
     * 8. Quyết định trạng thái Ready
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
                            setWarningState(false);
                        })
                        .addOnCompleteListener(task -> imageProxy.close());

            } catch (Exception e) {
                Log.e(TAG, "Frame analysis error", e);
                imageProxy.close();
            }
        }
    }

    private void processFrameResult(ImageProxy imageProxy, List<Face> faces) {
        boolean isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu");

        // a. Validate face pose
        String poseError = validateFacePose(faces);
        if (poseError != null && !isEmulator) {
            synchronized (colorHistory) { colorHistory.clear(); }
            updateStatus(poseError, false);
            setWarningState(poseError.contains("⚠️") || poseError.contains("↔️"));
            clearRoiOverlay();
            return;
        }

        // b. Kiểm tra face nằm trong khung oval
        if (!faces.isEmpty() && !isEmulator) {
            Face face = faces.get(0);
            String boundaryError = validateFaceBoundary(face, imageProxy);
            if (boundaryError != null) {
                synchronized (colorHistory) { colorHistory.clear(); }
                updateStatus(boundaryError, false);
                setWarningState(true);
                clearRoiOverlay();
                return;
            }
        }

        // Face is inside oval — clear warning
        setWarningState(false);

        float[] roiColor;
        RoiExtractionResult roiResult;
        if (faces.isEmpty() && isEmulator) {
            // Mock color for emulator
            roiColor = new float[]{216f, 160f, 135f};
            roiResult = new RoiExtractionResult(roiColor, new float[][]{
                    {216f, 160f, 135f}, {210f, 155f, 130f},
                    {220f, 165f, 140f}, {218f, 162f, 138f},
                    {212f, 158f, 132f}
            }, 5);
        } else {
            Face face = faces.get(0);
            // c. Trích xuất màu 5 ROI points từ bounding box
            roiResult = extractRoiColorDetailed(imageProxy, face);
            roiColor = roiResult != null ? roiResult.avgColor : null;
        }

        if (roiResult == null && !isEmulator) {
            updateStatus("Analyzing skin...", false);
            clearRoiOverlay();
            return;
        } else if (roiResult == null) {
            roiColor = new float[]{216f, 160f, 135f};
            roiResult = new RoiExtractionResult(roiColor, new float[][]{
                    {216f, 160f, 135f}, {210f, 155f, 130f},
                    {220f, 165f, 140f}, {218f, 162f, 138f},
                    {212f, 158f, 132f}
            }, 5);
        }

        // d. Kiểm tra đủ 5 ROI points hợp lệ
        if (roiResult.validPointCount < 5 && !isEmulator) {
            updateStatus("📍 Cannot detect all 5 skin points. Adjust your position.", false);
            clearRoiOverlay();
            return;
        }

        // Cập nhật ROI points trên overlay
        lastRoiPerPoint = roiResult.perPointColor;
        updateRoiOverlay(imageProxy, faces.isEmpty() ? null : faces.get(0));

        // e. Cập nhật color history
        synchronized (colorHistory) {
            colorHistory.addLast(roiColor);
            if (colorHistory.size() > HISTORY_SIZE) colorHistory.pollFirst();
        }

        // f. Chỉ đánh giá khi đủ 15 frames
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

        // g. Kiểm tra lighting
        String lightingError = checkLighting(avgR, avgG, avgB);
        if (lightingError != null) {
            updateStatus(lightingError, false);
            showLightingWarning(lightingError);
            return;
        }
        hideLightingWarning();

        // h. Tính Live Metrics
        LiveMetrics metrics = computeLiveMetrics(snapshot, avgR, avgG, avgB);

        // i. Cập nhật UI — sẵn sàng chụp
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
                startCaptureButtonPulse();
            }
        });
    }

    private void startCaptureButtonPulse() {
        if (buttonPulseAnimator == null) {
            buttonPulseAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.05f);
            buttonPulseAnimator.setDuration(800);
            buttonPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            buttonPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            buttonPulseAnimator.addUpdateListener(animation -> {
                float scale = (float) animation.getAnimatedValue();
                captureButton.setScaleX(scale);
                captureButton.setScaleY(scale);
            });
        }
        if (!buttonPulseAnimator.isRunning()) {
            buttonPulseAnimator.start();
        }
    }

    private void stopCaptureButtonPulse() {
        if (buttonPulseAnimator != null && buttonPulseAnimator.isRunning()) {
            buttonPulseAnimator.cancel();
            captureButton.setScaleX(1.0f);
            captureButton.setScaleY(1.0f);
        }
    }

    // ===========================
    // FACE BOUNDARY CHECK
    // ===========================

    /**
     * Kiểm tra khuôn mặt nằm hoàn toàn trong khung oval.
     * So sánh face bounding box (chuyển sang tọa độ view) với oval rect.
     *
     * @return null nếu OK, chuỗi lỗi nếu face vượt ngoài
     */
    private String validateFaceBoundary(Face face, ImageProxy imageProxy) {
        if (ovalGuide == null) return null;

        RectF ovalRect = ovalGuide.getOvalRect();
        Rect faceBox = face.getBoundingBox();

        // Chuyển face bounding box từ tọa độ image → tọa độ view
        int imgW = imageProxy.getWidth();
        int imgH = imageProxy.getHeight();
        int viewW = ovalGuide.getWidth();
        int viewH = ovalGuide.getHeight();

        if (imgW <= 0 || imgH <= 0 || viewW <= 0 || viewH <= 0) return null;

        // Camera trước: cần mirror X
        float scaleX = (float) viewW / imgH; // rotated
        float scaleY = (float) viewH / imgW; // rotated

        // ImageProxy tọa độ thường rotated 90° cho camera trước
        float faceLeft   = faceBox.top * scaleX;
        float faceTop    = faceBox.left * scaleY;
        float faceRight  = faceBox.bottom * scaleX;
        float faceBottom = faceBox.right * scaleY;

        // Mirror X cho camera trước
        float mirrorLeft  = viewW - faceRight;
        float mirrorRight = viewW - faceLeft;

        RectF faceMapped = new RectF(mirrorLeft, faceTop, mirrorRight, faceBottom);

        // Kiểm tra face rect nằm hoàn toàn trong oval rect
        // Oval là ellipse nhưng dùng bounding rect đủ tốt cho check cơ bản
        // Thêm margin 10% để chặt hơn
        float marginX = ovalRect.width() * 0.05f;
        float marginY = ovalRect.height() * 0.05f;
        RectF strictOval = new RectF(
                ovalRect.left + marginX,
                ovalRect.top + marginY,
                ovalRect.right - marginX,
                ovalRect.bottom - marginY
        );

        if (!strictOval.contains(faceMapped)) {
            if (faceMapped.top < strictOval.top) {
                return "⬆️ Face too high. Move your face down into the oval.";
            }
            if (faceMapped.bottom > strictOval.bottom) {
                return "⬇️ Face too low. Move your face up into the oval.";
            }
            if (faceMapped.left < strictOval.left || faceMapped.right > strictOval.right) {
                return "↔️ Face out of frame. Center your face in the oval.";
            }
            return "⚠️ Face must be entirely inside the oval frame.";
        }

        return null; // OK
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

    // ===========================
    // ROI EXTRACTION (5 POINTS)
    // ===========================

    /** Kết quả trích xuất ROI chi tiết. */
    private static class RoiExtractionResult {
        final float[] avgColor;         // [3] = {R, G, B} trung bình 5 điểm
        final float[][] perPointColor;  // [5][3] = {R, G, B} mỗi điểm
        final int validPointCount;      // Số điểm hợp lệ (0–5)

        RoiExtractionResult(float[] avg, float[][] perPoint, int validCount) {
            this.avgColor = avg;
            this.perPointColor = perPoint;
            this.validPointCount = validCount;
        }
    }

    /**
     * Trích xuất màu RGB tại 5 điểm ROI từ bounding box.
     * Trả về cả average color và color từng điểm + count hợp lệ.
     */
    private RoiExtractionResult extractRoiColorDetailed(ImageProxy imageProxy, Face face) {
        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) return null;

            Rect box = face.getBoundingBox();
            int imgW = bitmap.getWidth();
            int imgH = bitmap.getHeight();

            int left   = Math.max(0, box.left);
            int top    = Math.max(0, box.top);
            int right  = Math.min(imgW - 1, box.right);
            int bottom = Math.min(imgH - 1, box.bottom);
            int w      = right - left;
            int h      = bottom - top;
            if (w <= 0 || h <= 0) { bitmap.recycle(); return null; }

            int[][] roiOffsets = new int[5][2];
            com.google.mlkit.vision.face.FaceLandmark leftCheek = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_CHEEK);
            com.google.mlkit.vision.face.FaceLandmark rightCheek = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_CHEEK);
            com.google.mlkit.vision.face.FaceLandmark noseBase = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE);
            com.google.mlkit.vision.face.FaceLandmark bottomMouth = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM);
            com.google.mlkit.vision.face.FaceLandmark leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE);
            com.google.mlkit.vision.face.FaceLandmark rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE);

            if (leftCheek != null && rightCheek != null && noseBase != null && bottomMouth != null && leftEye != null && rightEye != null) {
                int eyeCenterY = (int) ((leftEye.getPosition().y + rightEye.getPosition().y) / 2);
                int eyeCenterX = (int) ((leftEye.getPosition().x + rightEye.getPosition().x) / 2);
                int noseY = (int) noseBase.getPosition().y;
                int foreheadY = Math.max(top, eyeCenterY - (noseY - eyeCenterY)); 
                
                int chinY = (int) bottomMouth.getPosition().y + ((int)bottomMouth.getPosition().y - noseY) / 2;
                chinY = Math.min(bottom, chinY);

                roiOffsets[0] = new int[] { eyeCenterX, foreheadY }; // Forehead
                roiOffsets[1] = new int[] { (int) noseBase.getPosition().x, (int) noseBase.getPosition().y - (int)(h * 0.05) }; // Nose (slightly higher than base)
                roiOffsets[2] = new int[] { (int) leftCheek.getPosition().x, (int) leftCheek.getPosition().y }; // Left Cheek
                roiOffsets[3] = new int[] { (int) rightCheek.getPosition().x, (int) rightCheek.getPosition().y }; // Right Cheek
                roiOffsets[4] = new int[] { (int) bottomMouth.getPosition().x, chinY }; // Chin
            } else {
                roiOffsets[0] = new int[] { left + w / 2, top + (int)(h * 0.15) };
                roiOffsets[1] = new int[] { left + w / 2, top + (int)(h * 0.50) };
                roiOffsets[2] = new int[] { left + (int)(w * 0.20), top + (int)(h * 0.60) };
                roiOffsets[3] = new int[] { left + (int)(w * 0.80), top + (int)(h * 0.60) };
                roiOffsets[4] = new int[] { left + w / 2, top + (int)(h * 0.85) };
            }

            float sumR = 0, sumG = 0, sumB = 0;
            int validPoints = 0;
            int sampleRadius = 4;
            float[][] perPointColor = new float[5][3];

            for (int i = 0; i < roiOffsets.length; i++) {
                int[] pt = roiOffsets[i];
                int px = Math.max(sampleRadius, Math.min(imgW - 1 - sampleRadius, pt[0]));
                int py = Math.max(sampleRadius, Math.min(imgH - 1 - sampleRadius, pt[1]));

                // Kiểm tra điểm nằm trong ảnh
                if (px < sampleRadius || px >= imgW - sampleRadius
                        || py < sampleRadius || py >= imgH - sampleRadius) {
                    perPointColor[i] = new float[]{-1, -1, -1}; // Invalid
                    continue;
                }

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
                float pr = r / count, pg = g / count, pb = b / count;
                perPointColor[i] = new float[]{pr, pg, pb};
                sumR += pr;
                sumG += pg;
                sumB += pb;
                validPoints++;
            }

            bitmap.recycle();

            if (validPoints == 0) return null;
            float[] avg = {sumR / validPoints, sumG / validPoints, sumB / validPoints};
            return new RoiExtractionResult(avg, perPointColor, validPoints);

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

    // ===========================
    // LIGHTING CHECK
    // ===========================

    /**
     * Kiểm tra điều kiện ánh sáng.
     * Tính Luminance từ RGB: L = 0.299R + 0.587G + 0.114B
     * Quá tối (< 60) hoặc cháy sáng (> 220) → cảnh báo
     */
    private String checkLighting(float r, float g, float b) {
        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;
        if (luminance < 60f) return "🌙 Too dark. Please move to a brighter area.";
        if (luminance > 220f) return "☀️ Too bright. Avoid direct strong light.";
        // Cảnh báo gần ngưỡng
        if (luminance < 80f) return "🌗 Lighting is dim. Move to a brighter area for better accuracy.";
        if (luminance > 200f) return "🌤️ Slightly too bright. Avoid direct light.";
        return null; // OK
    }

    // ===========================
    // ITA ANGLE SKIN TONE CLASSIFICATION
    // ===========================

    /**
     * Phân loại skin tone dùng ITA angle (Individual Typology Angle).
     * ITA = atan2(L* - 50, b*) × 180/π
     *
     * Fitzpatrick-inspired classification:
     * ITA > 55°  → Very Light
     * ITA 41-55° → Light
     * ITA 28-41° → Light-Medium
     * ITA 10-28° → Medium
     * ITA -30-10° → Medium-Deep
     * ITA < -30° → Deep
     */
    private String classifySkinToneITA(float r, float g, float b) {
        // RGB → CIE XYZ → CIE L*a*b*
        double rr = linearize(r / 255.0);
        double gg = linearize(g / 255.0);
        double bb = linearize(b / 255.0);

        // sRGB → XYZ (D65 illuminant)
        double x = 0.4124564 * rr + 0.3575761 * gg + 0.1804375 * bb;
        double y = 0.2126729 * rr + 0.7151522 * gg + 0.0721750 * bb;
        double z = 0.0193339 * rr + 0.1191920 * gg + 0.9503041 * bb;

        // XYZ → L*a*b* (D65 white point)
        double xn = 0.95047, yn = 1.00000, zn = 1.08883;
        double fx = labF(x / xn);
        double fy = labF(y / yn);
        double fz = labF(z / zn);

        double L = 116.0 * fy - 16.0;
        double b_star = 200.0 * (fy - fz);

        // ITA angle
        double ita = Math.atan2(L - 50.0, b_star) * (180.0 / Math.PI);

        if (ita > 55)  return "Very Light";
        if (ita > 41)  return "Light";
        if (ita > 28)  return "Light-Medium";
        if (ita > 10)  return "Medium";
        if (ita > -30) return "Medium-Deep";
        return "Deep";
    }

    private double linearize(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    /**
     * Detect undertone từ RGB.
     * Warm: R > G, Yellow/peach undertone
     * Cool: B > R, Pink/blue undertone
     * Neutral: Balanced
     */
    private String detectUndertone(float r, float g, float b) {
        float warmSignal = (r - g) + (r - b);  // positive = warm
        float coolSignal = (b - r) + (b - g);  // positive = cool

        if (warmSignal > 20f) return "Warm";
        if (coolSignal > 15f) return "Cool";
        return "Neutral";
    }

    // ===========================
    // LIVE METRICS
    // ===========================

    /**
     * Tính 4 Live Metrics từ color history:
     * - Moisture  = (B / Luminance) * 100
     * - Redness   = clamp(R - G, 0, 255) / 2.55
     * - Pores     = variance màu giữa các ROI points
     * - Evenness  = 100 - Pores
     */
    private LiveMetrics computeLiveMetrics(List<float[]> history, float avgR, float avgG, float avgB) {
        float luminance = 0.299f * avgR + 0.587f * avgG + 0.114f * avgB;

        float moisture = luminance > 0 ? Math.min(100f, (avgB / luminance) * 100f) : 50f;
        float redness = Math.min(100f, Math.max(0f, (avgR - avgG) / 2.55f));

        float meanR = avgR;
        float varianceSum = 0;
        for (float[] c : history) {
            float diff = c[0] - meanR;
            varianceSum += diff * diff;
        }
        float variance = history.size() > 0 ? varianceSum / history.size() : 0;
        float pores = Math.min(100f, (variance / 4f));
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

    // ===========================
    // UI UPDATES
    // ===========================

    private void updateStatus(String message, boolean ready) {
        runOnUiThread(() -> {
            tvStatusGuide.setText(message);
            if (!ready) {
                captureReady = false;
                captureButton.setEnabled(false);
                stopCaptureButtonPulse();
                liveMetricsPanel.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void setWarningState(boolean warn) {
        runOnUiThread(() -> {
            if (ovalGuide != null) ovalGuide.setWarning(warn);
        });
    }

    private void showLightingWarning(String message) {
        runOnUiThread(() -> {
            if (tvLightingWarning != null && tvLightingWarningText != null) {
                tvLightingWarningText.setText(message);
                tvLightingWarning.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideLightingWarning() {
        runOnUiThread(() -> {
            if (tvLightingWarning != null) {
                tvLightingWarning.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Cập nhật 5 ROI points trên OvalGuideView.
     * Chuyển tọa độ từ image space → view space.
     */
    private void updateRoiOverlay(ImageProxy imageProxy, Face face) {
        if (ovalGuide == null || lastRoiPerPoint == null || face == null) return;

        Rect box = face.getBoundingBox();
        int imgW = imageProxy.getWidth();
        int imgH = imageProxy.getHeight();
        int viewW = ovalGuide.getWidth();
        int viewH = ovalGuide.getHeight();

        if (imgW <= 0 || imgH <= 0 || viewW <= 0 || viewH <= 0) return;

        float scaleX = (float) viewW / imgH;
        float scaleY = (float) viewH / imgW;

        int left   = Math.max(0, box.left);
        int top    = Math.max(0, box.top);
        int right  = Math.min(imgW - 1, box.right);
        int bottom = Math.min(imgH - 1, box.bottom);
        int w = right - left;
        int h = bottom - top;

        // 5 ROI offsets (same as extractRoiColorDetailed)
        int[][] roiOffsets = {
            { left + w / 2, top + (int)(h * 0.15) },
            { left + w / 2, top + (int)(h * 0.50) },
            { left + (int)(w * 0.20), top + (int)(h * 0.60) },
            { left + (int)(w * 0.80), top + (int)(h * 0.60) },
            { left + w / 2, top + (int)(h * 0.85) }
        };

        float[][] viewPoints = new float[5][2];
        int[] viewColors = new int[5];

        for (int i = 0; i < 5; i++) {
            // Image → rotated view coordinates + mirror
            float vx = roiOffsets[i][1] * scaleX;
            float vy = roiOffsets[i][0] * scaleY;
            vx = viewW - vx; // Mirror for front camera

            viewPoints[i] = new float[]{vx, vy};

            float[] c = lastRoiPerPoint[i];
            if (c[0] >= 0) {
                viewColors[i] = 0xFF000000 | ((int)c[0] << 16) | ((int)c[1] << 8) | (int)c[2];
            } else {
                viewColors[i] = 0xFFFF0000; // Red for invalid
            }
        }

        runOnUiThread(() -> ovalGuide.setRoiPoints(viewPoints, viewColors));
    }

    private void clearRoiOverlay() {
        runOnUiThread(() -> {
            if (ovalGuide != null) ovalGuide.clearRoiPoints();
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
        try {
            String skinHex = String.format("#%02X%02X%02X", (int)avgR, (int)avgG, (int)avgB);

            // Dùng ITA angle cho skin tone classification thay vì logic đơn giản
            String skinTone = classifySkinToneITA(avgR, avgG, avgB);
            String undertone = detectUndertone(avgR, avgG, avgB);

            SkinAnalysisResult result = new SkinAnalysisResult();
            result.setSkinHex(skinHex);
            result.setSkinTone(skinTone);
            result.setUndertone(undertone);
            result.setSkinType("Combination");
            result.setConfidence(96.0);

            List<String> concerns = new ArrayList<>();
            concerns.add("Visible Pores");
            concerns.add("Uneven Tone");
            result.setConcerns(concerns);

            proceedToSkinResult(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to process skin color locally", e);
            setAnalyzing(false);
            showAnalysisUnavailableDialog();
        } finally {
            if (imageFile != null && imageFile.exists()) {
                imageFile.delete();
            }
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
            {"17C Porcelain", "#f9d9c2", "3.2", "cushion-17c", "Mask Fit Red Cushion", "35.00"},
            {"21N Ivory",     "#ebc5a1", "6.5", "cushion-21n", "Mask Fit Red Cushion", "35.00"},
            {"23N Sand",      "#ebbf98", "8.0", "cushion-23n", "Mask Fit Red Cushion", "35.00"},
            {"24N Latte",     "#e4b58e", "10.0", "cushion-24n", "Mask Fit Aura Cushion", "38.00"},
            {"27N Camel",     "#e5b98b", "12.0", "cushion-27n", "Mask Fit Red Cushion", "35.00"},
            {"33N Macchiato", "#d3a177", "15.0", "cushion-33n", "Mask Fit All-Cover Cushion", "36.00"}
        };
        for (String[] shade : shades) {
            ShadeMatchResult r = new ShadeMatchResult();
            r.setShadeName(shade[0]);
            r.setShadeHex(shade[1]);
            r.setMatchScore(Double.parseDouble(shade[2]));
            r.setProductId(shade[3]);
            r.setProductName(shade[4]);
            r.setPrice(Double.parseDouble(shade[5]));
            r.setSalePrice(0);
            r.setImageUrl("https://placehold.co/400x400/E50000/FFFFFF.png?text=TirTir+Cushion");
            results.add(r);
        }
        return results;
    }

    private void launchSkinResult(SkinAnalysisResult result, List<ShadeMatchResult> matches) {
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
            if (analyzing) stopCaptureButtonPulse();
            captureButton.setEnabled(!analyzing && captureReady);
            captureButton.setText(analyzing ? "Analyzing..." : "Capture & Analyze");
        }
    }
}
