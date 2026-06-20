package com.batteryhealth.app.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 健康度环形进度视图
 *
 * 使用渐变弧线展示电池健康度百分比，与 Web 端圆环保持一致。
 */
public class HealthRingView extends View {

    private final Paint ringPaint;
    private final Paint trackPaint;
    private final RectF rectF = new RectF();

    private float progress = 0f; // 0 - 100
    private float strokeWidth = 0f;
    private int startColor = 0xFF32D74B;
    private int endColor = 0xFF66D4CF;

    public HealthRingView(Context context) {
        this(context, null);
    }

    public HealthRingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HealthRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = context.getResources().getDisplayMetrics().density;
        strokeWidth = 12 * density;

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(0x1A000000);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(100f, progress));
        invalidate();
    }

    public void setColors(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidth / 2f + getPaddingTop() / 2f;
        rectF.set(inset, inset, w - inset, h - inset);

        LinearGradient gradient = new LinearGradient(
                rectF.left, rectF.top, rectF.right, rectF.bottom,
                startColor, endColor, Shader.TileMode.CLAMP);
        ringPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sweep = 360f * progress / 100f;
        canvas.drawArc(rectF, 0f, 360f, false, trackPaint);
        canvas.drawArc(rectF, -90f, sweep, false, ringPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (160 * getResources().getDisplayMetrics().density);
        int w = resolveSize(size, widthMeasureSpec);
        int h = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }
}
