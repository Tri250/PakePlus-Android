package com.batteryhealth.app.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

public class ChargingCountdownView extends View {

    private final Paint ringPaint;
    private final Paint trackPaint;
    private final Paint textPaint;
    private final Paint subTextPaint;
    private final RectF rectF = new RectF();

    private float currentProgress = 0f;
    private float targetProgress = 0f;
    private float strokeWidth = 0f;

    private int currentLevel = 0;
    private int targetLevel = 100;
    private long estimatedSeconds = 0;

    private ValueAnimator progressAnimator;
    private static final long ANIM_DURATION = 800;

    public ChargingCountdownView(Context context) {
        this(context, null);
    }

    public ChargingCountdownView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChargingCountdownView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = context.getResources().getDisplayMetrics().density;
        strokeWidth = 14 * density;

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(0x1A000000);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(20 * density);
        textPaint.setColor(ContextCompat.getColor(context, R.color.label));
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(13 * density);
        subTextPaint.setColor(ContextCompat.getColor(context, R.color.label_2));
    }

    public void setChargingInfo(int level, int targetLevel, long estimatedSeconds) {
        this.currentLevel = level;
        this.targetLevel = Math.max(level, targetLevel);
        this.estimatedSeconds = Math.max(0, estimatedSeconds);

        float newProgress;
        if (this.targetLevel <= level) {
            newProgress = 100f;
        } else {
            newProgress = (level / (float) this.targetLevel) * 100f;
        }
        newProgress = Math.max(0f, Math.min(100f, newProgress));

        animateProgressTo(newProgress);
    }

    private void animateProgressTo(float newProgress) {
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }

        targetProgress = newProgress;
        progressAnimator = ValueAnimator.ofFloat(currentProgress, newProgress);
        progressAnimator.setDuration(ANIM_DURATION);
        progressAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        progressAnimator.addUpdateListener(animation -> {
            currentProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidth / 2f;
        rectF.set(inset, inset, w - inset, h - inset);
        updateShader();
    }

    private void updateShader() {
        if (rectF.width() <= 0 || rectF.height() <= 0) return;
        LinearGradient gradient = new LinearGradient(
                rectF.left, rectF.top, rectF.right, rectF.bottom,
                0xFF007AFF, 0xFF5AC8FA, Shader.TileMode.CLAMP);
        ringPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float sweep = 360f * currentProgress / 100f;
        canvas.drawArc(rectF, 0f, 360f, false, trackPaint);
        canvas.drawArc(rectF, -90f, sweep, false, ringPaint);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        String mainText = buildCountdownText();
        float textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f - 6f;
        canvas.drawText(mainText, centerX, textY, textPaint);

        String subText = buildLevelText();
        float subTextY = centerY - (subTextPaint.descent() + subTextPaint.ascent()) / 2f + 28f;
        canvas.drawText(subText, centerX, subTextY, subTextPaint);
    }

    private String buildCountdownText() {
        if (currentLevel >= targetLevel || estimatedSeconds <= 0) {
            return getContext().getString(R.string.charging_full);
        }
        long totalMinutes = estimatedSeconds / 60;
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) {
            return getContext().getString(R.string.charging_countdown_format, hours, minutes);
        } else {
            return getContext().getString(R.string.charging_countdown_format, 0, minutes);
        }
    }

    private String buildLevelText() {
        return currentLevel + "% → " + targetLevel + "%";
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (180 * getResources().getDisplayMetrics().density);
        int w = resolveSize(size, widthMeasureSpec);
        int h = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        super.onDetachedFromWindow();
    }
}
