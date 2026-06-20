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

import java.util.ArrayList;
import java.util.List;

/**
 * 充电功率分段柱状图（10 段：10%..100%）。
 *
 * <p>颜色从绿到黄渐变，反映充电功率变化；点击段位会高亮。</p>
 */
public class ChargeBarChartView extends View {

    private final List<Float> values = new ArrayList<>(); // W
    private final List<String> labels = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selectedIndex = -1;
    private float maxValue = 35f;
    private String unit = "W";

    public ChargeBarChartView(Context c) { super(c); init(); }
    public ChargeBarChartView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public ChargeBarChartView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        bgBarPaint.setStyle(Paint.Style.FILL);
        bgBarPaint.setColor(0x33CCCCCC);
        textPaint.setColor(0xFF4A5C50);
        textPaint.setTextSize(sp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(0xFF7C8C82);
        unitPaint.setTextSize(sp(8));
        unitPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setData(List<Float> watts, List<String> labels) {
        values.clear();
        if (watts != null) values.addAll(watts);
        this.labels.clear();
        if (labels != null) this.labels.addAll(labels);
        float m = 0;
        for (float v : values) if (v > m) m = v;
        maxValue = Math.max(1, m);
        invalidate();
    }

    public void setSelectedIndex(int idx) {
        selectedIndex = idx;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (values.isEmpty()) return;
        int w = getWidth();
        int h = getHeight();
        int padL = dp(28);
        int padR = dp(8);
        int padT = dp(8);
        int padB = dp(22);
        int plotW = w - padL - padR;
        int plotH = h - padT - padB;
        int n = values.size();
        float barSpace = plotW / (float) n;
        float barWidth = barSpace * 0.55f;

        for (int i = 0; i < n; i++) {
            float x = padL + barSpace * i + (barSpace - barWidth) / 2f;
            float ratio = values.get(i) / maxValue;
            if (ratio < 0.05f) ratio = 0.05f;
            if (ratio > 1f) ratio = 1f;
            float barH = plotH * ratio;
            float top = padT + plotH - barH;
            float bottom = padT + plotH;

            // 背景
            RectF bg = new RectF(x, padT, x + barWidth, bottom);
            c.drawRoundRect(bg, dp(4), dp(4), bgBarPaint);

            // 柱体（绿→黄渐变）
            int colorTop;
            int colorBottom;
            if (ratio > 0.7f) {
                colorTop = 0xFF7DE891;
                colorBottom = 0xFF3FCB58;
            } else if (ratio > 0.4f) {
                colorTop = 0xFFD0F25E;
                colorBottom = 0xFF7DE891;
            } else if (ratio > 0.2f) {
                colorTop = 0xFFFFD60A;
                colorBottom = 0xFFD0F25E;
            } else {
                colorTop = 0xFFFF9F0A;
                colorBottom = 0xFFFFD60A;
            }
            barPaint.setShader(new LinearGradient(x, top, x, bottom, colorTop, colorBottom, Shader.TileMode.CLAMP));
            RectF r = new RectF(x, top, x + barWidth, bottom);
            c.drawRoundRect(r, dp(4), dp(4), barPaint);

            if (i == selectedIndex) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF1F8B3A);
                p.setStrokeWidth(dp(2));
                c.drawRoundRect(r, dp(4), dp(4), p);
            }

            // 数值
            c.drawText(String.format("%.1f", values.get(i)), x + barWidth / 2f, top - dp(2), textPaint);

            // X 轴
            c.drawText(labels.size() > i ? labels.get(i) : (i * 10) + "%", x + barWidth / 2f, bottom + dp(14), textPaint);
        }

        // Y 轴
        Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
        axis.setColor(0xFF7C8C82);
        axis.setTextSize(sp(8));
        axis.setTextAlign(Paint.Align.RIGHT);
        c.drawText(unit, padL - dp(4), padT + sp(8), axis);
        c.drawText("0W", padL - dp(4), padT + plotH, axis);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
