package com.example.tirtir_mcommerce.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BarcodeScannerOverlayView extends View {
    private Paint paint;
    private Paint eraserPaint;
    private Paint borderPaint;
    private Paint cornerPaint;
    private RectF rect;
    private int cornerLength = 40;
    private int cornerWidth = 8;

    public BarcodeScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // Required for PorterDuff.Mode.CLEAR on views

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.parseColor("#99000000")); // dark translucent background

        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(cornerWidth);

        rect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int boxWidth = w * 80 / 100;
        int boxHeight = h * 25 / 100; // Rectangular box fits barcode aspect ratio better
        float left = (w - boxWidth) / 2f;
        float top = (h - boxHeight) / 2f;
        rect.set(left, top, left + boxWidth, top + boxHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw translucent background
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Clear the rectangular scan box
        canvas.drawRect(rect, eraserPaint);

        // Draw white stroke around scan box
        canvas.drawRect(rect, borderPaint);

        // Draw 4 corner marks
        // Top-Left
        canvas.drawLine(rect.left - cornerWidth/2f, rect.top, rect.left + cornerLength, rect.top, cornerPaint);
        canvas.drawLine(rect.left, rect.top - cornerWidth/2f, rect.left, rect.top + cornerLength, cornerPaint);

        // Top-Right
        canvas.drawLine(rect.right + cornerWidth/2f, rect.top, rect.right - cornerLength, rect.top, cornerPaint);
        canvas.drawLine(rect.right, rect.top - cornerWidth/2f, rect.right, rect.top + cornerLength, cornerPaint);

        // Bottom-Left
        canvas.drawLine(rect.left - cornerWidth/2f, rect.bottom, rect.left + cornerLength, rect.bottom, cornerPaint);
        canvas.drawLine(rect.left, rect.bottom + cornerWidth/2f, rect.left, rect.bottom - cornerLength, cornerPaint);

        // Bottom-Right
        canvas.drawLine(rect.right + cornerWidth/2f, rect.bottom, rect.right - cornerLength, rect.bottom, cornerPaint);
        canvas.drawLine(rect.right, rect.bottom + cornerWidth/2f, rect.right, rect.bottom - cornerLength, cornerPaint);
    }
}
