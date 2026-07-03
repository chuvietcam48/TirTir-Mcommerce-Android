package com.example.tirtir_mcommerce.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * OvalGuideView — Vẽ overlay hướng dẫn khuôn mặt trên camera preview.
 *
 * Chức năng:
 * 1. Scrim (semi-transparent) phủ toàn màn hình, khoét lỗ hình oval
 * 2. Viền oval trắng (bình thường) / đỏ pulse (cảnh báo)
 * 3. Vẽ 5 ROI points (Forehead, Nose, LeftCheek, RightCheek, Chin) lên overlay
 * 4. Expose ovalRect cho SkinAnalysisActivity kiểm tra face boundary
 */
public class OvalGuideView extends View {

    // --- Drawing paints ---
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roiBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // --- Oval geometry (fraction of view size) ---
    private static final float OVAL_WIDTH_FRACTION = 0.68f;
    private static final float OVAL_HEIGHT_FRACTION = 0.46f;
    private static final float OVAL_TOP_FRACTION = 0.18f;

    // --- State ---
    private boolean warning = false;
    private float warningAlpha = 1.0f;
    private ValueAnimator pulseAnimator;

    // --- ROI Points: each is {x, y, color} in VIEW coordinates ---
    private float[][] roiPoints;   // [5][2] = {{x,y}, ...}
    private int[] roiColors;       // [5] = {color, ...}
    private boolean showRoiPoints = false;

    // --- Cached oval rect ---
    private final RectF cachedOval = new RectF();

    public OvalGuideView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        scrimPaint.setColor(Color.parseColor("#99000000"));
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        strokePaint.setColor(Color.WHITE);
        roiPaint.setStyle(Paint.Style.FILL);
        roiBorderPaint.setStyle(Paint.Style.STROKE);
        roiBorderPaint.setStrokeWidth(2f);
        roiBorderPaint.setColor(Color.WHITE);
    }

    // ===========================
    // PUBLIC API
    // ===========================

    /**
     * Trả về RectF của oval guide trong tọa độ view.
     * SkinAnalysisActivity dùng để so sánh face bounding box.
     */
    public RectF getOvalRect() {
        computeOval();
        return new RectF(cachedOval);
    }

    /**
     * Bật/tắt chế độ cảnh báo (viền oval đỏ + pulse animation).
     * Gọi khi face vượt ra ngoài oval hoặc lighting xấu.
     */
    public void setWarning(boolean warning) {
        if (this.warning == warning) return;
        this.warning = warning;
        if (warning) {
            startPulseAnimation();
        } else {
            stopPulseAnimation();
            strokePaint.setColor(Color.WHITE);
            strokePaint.setStrokeWidth(4f);
        }
        invalidate();
    }

    /**
     * Cập nhật vị trí và màu của 5 ROI points.
     *
     * @param points [5][2] mỗi phần tử = {x, y} trong tọa độ VIEW
     * @param colors [5] mỗi phần tử = 0xAARRGGBB
     */
    public void setRoiPoints(float[][] points, int[] colors) {
        if (points != null && points.length == 5 && colors != null && colors.length == 5) {
            this.roiPoints = points;
            this.roiColors = colors;
            this.showRoiPoints = true;
        } else {
            this.showRoiPoints = false;
        }
        invalidate();
    }

    /** Ẩn ROI points. */
    public void clearRoiPoints() {
        this.showRoiPoints = false;
        invalidate();
    }

    // ===========================
    // DRAWING
    // ===========================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        computeOval();

        // 1. Scrim overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        // 2. Clear oval hole
        canvas.drawOval(cachedOval, clearPaint);

        // 3. Oval stroke (white or red pulse)
        if (warning) {
            int alpha = (int) (warningAlpha * 255);
            strokePaint.setColor(Color.argb(alpha, 255, 60, 60));
            strokePaint.setStrokeWidth(6f);
        }
        canvas.drawOval(cachedOval, strokePaint);

        // 4. ROI points
        if (showRoiPoints && roiPoints != null && roiColors != null) {
            float dotRadius = 8f * getResources().getDisplayMetrics().density;
            for (int i = 0; i < roiPoints.length; i++) {
                float px = roiPoints[i][0];
                float py = roiPoints[i][1];
                // Vẽ chấm tròn với màu extracted
                roiPaint.setColor(roiColors[i]);
                canvas.drawCircle(px, py, dotRadius, roiPaint);
                // Viền trắng
                canvas.drawCircle(px, py, dotRadius, roiBorderPaint);
            }
        }
    }

    // ===========================
    // INTERNAL
    // ===========================

    private void computeOval() {
        float w = getWidth() * OVAL_WIDTH_FRACTION;
        float h = getHeight() * OVAL_HEIGHT_FRACTION;
        float left = (getWidth() - w) / 2f;
        float top = getHeight() * OVAL_TOP_FRACTION;
        cachedOval.set(left, top, left + w, top + h);
    }

    private void startPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ValueAnimator.ofFloat(0.4f, 1.0f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            warningAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        warningAlpha = 1.0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulseAnimation();
    }
}
