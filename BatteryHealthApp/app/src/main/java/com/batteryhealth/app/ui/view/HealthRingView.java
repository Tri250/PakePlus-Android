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
 * 支持"无数据"状态（灰色虚线圆弧），区分于正常 0% 显示。
 */
public class HealthRingView extends View {

    private final Paint ringPaint;
    private final Paint trackPaint;
    private final Paint noDataPaint;
    private final RectF rectF = new RectF();

    private float progress = 0f; // 0 - 100
    private float strokeWidth = 0f;
    private int startColor = 0xFF32D74B;
    private int endColor = 0xFF66D4CF;
    /** 无数据状态标志：true 时绘制灰色虚线圆弧，而非 0% 实线弧 */
    private boolean noData = false;

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

        noDataPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        noDataPaint.setStyle(Paint.Style.STROKE);
        noDataPaint.setStrokeWidth(strokeWidth);
        noDataPaint.setColor(0xFFE0E0E0);
        noDataPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8f, 8f}, 0f));
    }

    @androidx.annotation.Keep
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(100f, progress));
        this.noData = false;
        invalidate();
    }

    /**
     * 设置为"无数据"状态：显示灰色虚线圆弧，而非 0% 实线弧。
     * 由调用方（BatteryHealthFragment）在健康度获取失败时调用，
     * 配合上方中央 TextView 显示 "--" 以明确表示"数据不可用"。
     */
    public void setNoData() {
        this.noData = true;
        this.progress = 0f;
        invalidate();
    }

    public void setColors(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        // 颜色变更后必须重建 LinearGradient，否则 onDraw 仍使用旧 shader
        updateShader();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidth / 2f + getPaddingTop() / 2f;
        rectF.set(inset, inset, w - inset, h - inset);
        updateShader();
    }

    /**
     * 根据当前 rectF 与颜色重建渐变 shader。
     * 在 onSizeChanged 与 setColors 中调用，保证颜色/尺寸变更后 shader 同步。
     */
    private void updateShader() {
        if (rectF.width() <= 0 || rectF.height() <= 0) return;
        LinearGradient gradient = new LinearGradient(
                rectF.left, rectF.top, rectF.right, rectF.bottom,
                startColor, endColor, Shader.TileMode.CLAMP);
        ringPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(rectF, 0f, 360f, false, trackPaint);
        if (noData) {
            // 无数据状态：灰色虚线圆弧（不绘制任何进度弧）
            canvas.drawArc(rectF, 0f, 360f, false, noDataPaint);
        } else {
            float sweep = 360f * progress / 100f;
            canvas.drawArc(rectF, -90f, sweep, false, ringPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (160 * getResources().getDisplayMetrics().density);
        int w = resolveSize(size, widthMeasureSpec);
        int h = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }
}
