package com.batteryhealth.app.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 趋势追踪折线图（电池健康度变化曲线）。
 *
 * <p>支持传入 0..100 的健康度序列，绘制平滑曲线 + 渐变填充 + 关键点圆圈。</p>
 */
public class TrendLineChartView extends View {

    private final List<Float> data = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int lineColor = 0xFF3FCB58;
    private int fillTop = 0x663FCB58;
    private int fillBottom = 0x113FCB58;
    private int pointColor = 0xFFFFFFFF;
    private int labelColor = 0xCC0F1B14;
    private int bgColor = 0x00000000;
    private int gridColor = 0x1A0F1B14;

    public TrendLineChartView(Context c) { super(c); init(); }
    public TrendLineChartView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public TrendLineChartView(Context c, @Nullable AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.5f));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint.setColor(pointColor);
        pointStroke.setColor(lineColor);
        pointStroke.setStyle(Paint.Style.STROKE);
        pointStroke.setStrokeWidth(dp(1.5f));

        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(sp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        bgPaint.setColor(bgColor);
    }

    public void setData(List<Float> series) {
        data.clear();
        if (series != null) data.addAll(series);
        invalidate();
    }

    public void setColors(int lineColor, int fillTop, int fillBottom) {
        this.lineColor = lineColor;
        this.fillTop = fillTop;
        this.fillBottom = fillBottom;
        linePaint.setColor(lineColor);
        pointStroke.setColor(lineColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (data.isEmpty()) return;
        int w = getWidth();
        int h = getHeight();
        int padL = dp(28);
        int padR = dp(12);
        int padT = dp(18);
        int padB = dp(24);
        int plotW = w - padL - padR;
        int plotH = h - padT - padB;

        // 背景网格线
        for (int i = 0; i < 5; i++) {
            float y = padT + plotH * i / 4f;
            Paint p = new Paint();
            p.setColor(gridColor);
            p.setStrokeWidth(1);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(padL, y, w - padR, y, p);

            // Y 轴刻度
            float yVal = 100 - (100f * i / 4f);
            Paint t = new Paint(labelPaint);
            t.setTextAlign(Paint.Align.RIGHT);
            t.setColor(0xFF7C8C82);
            t.setTextSize(sp(9));
            c.drawText(String.format("%.0f", yVal), padL - dp(4), y + sp(3), t);
        }

        // 计算点位置
        int n = data.size();
        if (n < 2) return;
        float[] xs = new float[n];
        float[] ys = new float[n];
        float min = 80f, max = 110f;
        for (float v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max - min < 1) max = min + 1;
        for (int i = 0; i < n; i++) {
            xs[i] = padL + plotW * i / (float) (n - 1);
            float v = data.get(i);
            ys[i] = padT + plotH - (v - min) / (max - min) * plotH;
        }

        // 渐变填充
        Path fillPath = new Path();
        fillPath.moveTo(xs[0], padT + plotH);
        for (int i = 0; i < n; i++) {
            if (i == 0) fillPath.lineTo(xs[i], ys[i]);
            else {
                // 平滑曲线
                float prevX = xs[i - 1], prevY = ys[i - 1];
                float midX = (prevX + xs[i]) / 2f;
                fillPath.cubicTo(midX, prevY, midX, ys[i], xs[i], ys[i]);
            }
        }
        fillPath.lineTo(xs[n - 1], padT + plotH);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, padT, 0, padT + plotH,
                fillTop, fillBottom, Shader.TileMode.CLAMP));
        c.drawPath(fillPath, fillPaint);

        // 曲线
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            if (i == 0) path.moveTo(xs[i], ys[i]);
            else {
                float prevX = xs[i - 1], prevY = ys[i - 1];
                float midX = (prevX + xs[i]) / 2f;
                path.cubicTo(midX, prevY, midX, ys[i], xs[i], ys[i]);
            }
        }
        c.drawPath(path, linePaint);

        // 关键点
        for (int i = 0; i < n; i++) {
            c.drawCircle(xs[i], ys[i], dp(4), pointPaint);
            c.drawCircle(xs[i], ys[i], dp(4), pointStroke);
        }

        // X 轴首尾日期标签（这里显示序号）
        if (n >= 1) {
            Paint t = new Paint(labelPaint);
            t.setColor(0xFF7C8C82);
            t.setTextSize(sp(9));
            c.drawText("1", xs[0], padT + plotH + dp(14), t);
            c.drawText(String.valueOf(n), xs[n - 1], padT + plotH + dp(14), t);
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
}
