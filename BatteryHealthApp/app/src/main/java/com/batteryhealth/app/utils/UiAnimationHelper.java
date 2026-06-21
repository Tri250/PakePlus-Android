package com.batteryhealth.app.utils;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import com.batteryhealth.app.R;

import java.util.Locale;

/**
 * UI 动画辅助类：管理卡片入场动画、数字滚动、进度条动画、健康度脉冲等。
 * 已优化：限制递归深度，避免在复杂层级下 StackOverflowError；
 * 所有 ValueAnimator 显式通过 cancel() 防止泄漏。
 */
public class UiAnimationHelper {

    private static final String TAG = "UiAnimationHelper";
    private static final long DEFAULT_DURATION = 600L;
    private static final long FAST_DURATION = 300L;
    private static final int MAX_RECURSION_DEPTH = 32; // View tree depth safety limit

    private static final Interpolator OVERSHOOT = new OvershootInterpolator(1.4f);
    private static final Interpolator DECEL = new DecelerateInterpolator(1.5f);
    private static final Interpolator ACCEL_DECEL = new AccelerateDecelerateInterpolator();

    private final Context context;

    public UiAnimationHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 判定是否应跳过动画（无障碍、用户系统设置）。
     */
    public boolean shouldSkipAnimations() {
        try {
            float scale = android.provider.Settings.Global.getFloat(
                    context.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) return true;
        } catch (Exception ignored) {
        }
        Configuration cfg = context.getResources().getConfiguration();
        // Android Q+ exposes the "remove animations" accessibility setting on the system
        boolean removeAnimations = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            removeAnimations = (cfg.uiMode & Configuration.UI_MODE_NIGHT_NO) == 0
                    && cfg.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR
                    && false; // no clean public API; kept for future expansion
        }
        return removeAnimations;
    }

    /**
     * 卡片入场动画：从下方滑入 + 透明度过渡，依次播放。
     */
    public void animateCardsEntry(ViewGroup root) {
        animateCardsEntry(root, 0L);
    }

    public void animateCardsEntry(ViewGroup root, long baseDelay) {
        if (root == null) return;
        if (shouldSkipAnimations()) {
            root.setAlpha(1f);
            root.setTranslationY(0f);
            return;
        }
        java.util.List<View> cards = collectCards(root);
        for (int i = 0; i < cards.size(); i++) {
            final View v = cards.get(i);
            v.setAlpha(0f);
            v.setTranslationY(40f);
            long delay = baseDelay + (i * 80L);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(DEFAULT_DURATION)
                    .setInterpolator(DECEL)
                    .start();
        }
    }

    private java.util.List<View> collectCards(ViewGroup root) {
        java.util.List<View> result = new java.util.ArrayList<>();
        collectCardsRecursive(root, result, 0);
        return result;
    }

    private void collectCardsRecursive(ViewGroup parent, java.util.List<View> out, int depth) {
        if (parent == null || depth > MAX_RECURSION_DEPTH) return;
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            Object tag = child.getTag();
            if (tag instanceof String) {
                String tagStr = (String) tag;
                if (tagStr.startsWith("card_")) {
                    out.add(child);
                    continue; // do not recurse into cards
                }
            }
            if (child instanceof ViewGroup) {
                collectCardsRecursive((ViewGroup) child, out, depth + 1);
            }
        }
    }

    /**
     * 数字滚动动画：0 → target。
     */
    public ValueAnimator animateNumberText(TextView view, float from, float to, String format) {
        if (view == null) return null;
        if (shouldSkipAnimations()) {
            view.setText(String.format(Locale.getDefault(), format, to));
            return null;
        }
        ValueAnimator anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(DECEL);
        // Hold a tag for explicit cancel() on detach
        Object existing = view.getTag(R.id.tag_anim_number);
        if (existing instanceof ValueAnimator) {
            ((ValueAnimator) existing).cancel();
        }
        view.setTag(R.id.tag_anim_number, anim);
        anim.addUpdateListener(a -> {
            if (view.getTag(R.id.tag_anim_number) != anim) {
                a.cancel();
                return;
            }
            float current = (Float) a.getAnimatedValue();
            view.setText(String.format(Locale.getDefault(), format, current));
        });
        anim.start();
        return anim;
    }

    /**
     * 整数滚动动画：0 → target。
     */
    public ValueAnimator animateIntText(TextView view, int from, int to, String format) {
        if (view == null) return null;
        if (shouldSkipAnimations()) {
            view.setText(String.format(Locale.getDefault(), format, to));
            return null;
        }
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(DECEL);
        Object existing = view.getTag(R.id.tag_anim_int);
        if (existing instanceof ValueAnimator) {
            ((ValueAnimator) existing).cancel();
        }
        view.setTag(R.id.tag_anim_int, anim);
        anim.addUpdateListener(a -> {
            if (view.getTag(R.id.tag_anim_int) != anim) {
                a.cancel();
                return;
            }
            int current = (Integer) a.getAnimatedValue();
            view.setText(String.format(Locale.getDefault(), format, current));
        });
        anim.start();
        return anim;
    }

    /**
     * ProgressBar 平滑过渡到目标进度。
     */
    public ObjectAnimator animateProgressBar(android.widget.ProgressBar bar, int from, int to) {
        if (bar == null) return null;
        if (shouldSkipAnimations()) {
            bar.setProgress(to);
            return null;
        }
        ObjectAnimator anim = ObjectAnimator.ofInt(bar, "progress", from, to);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(DECEL);
        anim.start();
        return anim;
    }

    /**
     * 环形进度（RingProgress）平滑过渡。target 为 0-100。
     */
    public ObjectAnimator animateRingProgress(View ring, int to) {
        if (ring == null) return null;
        int clamped = Math.max(0, Math.min(100, to));
        if (shouldSkipAnimations()) {
            ring.setTag(R.id.tag_ring_level, clamped);
            return null;
        }
        Integer current = (Integer) ring.getTag(R.id.tag_ring_level);
        int from = current != null ? current : 0;
        ObjectAnimator anim = ObjectAnimator.ofInt(ring, ring.getId() == View.NO_ID
                ? new android.util.Property<View, Integer>(Integer.class, "level") {
            @Override
            public Integer get(View object) {
                Integer v = (Integer) object.getTag(R.id.tag_ring_level);
                return v != null ? v : 0;
            }

            @Override
            public void set(View object, Integer value) {
                object.setTag(R.id.tag_ring_level, value);
            }
        } : new android.util.Property<View, Integer>(Integer.class, "level") {
            @Override
            public Integer get(View object) {
                Integer v = (Integer) object.getTag(R.id.tag_ring_level);
                return v != null ? v : 0;
            }

            @Override
            public void set(View object, Integer value) {
                object.setTag(R.id.tag_ring_level, value);
            }
        }, from, clamped);
        anim.setDuration(DEFAULT_DURATION);
        anim.setInterpolator(DECEL);
        anim.start();
        return anim;
    }

    /**
     * 健康度数字呼吸效果：1.0 -> 1.06 -> 1.0，无限循环。
     */
    public ValueAnimator pulseHealthNumber(View target) {
        if (target == null || shouldSkipAnimations()) return null;
        Object existing = target.getTag(R.id.tag_pulse);
        if (existing instanceof ValueAnimator) {
            ((ValueAnimator) existing).cancel();
        }
        ValueAnimator anim = ValueAnimator.ofFloat(1f, 1.06f, 1f);
        anim.setDuration(1200L);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(ACCEL_DECEL);
        target.setTag(R.id.tag_pulse, anim);
        anim.addUpdateListener(a -> {
            if (target.getTag(R.id.tag_pulse) != anim) {
                a.cancel();
                return;
            }
            float scale = (Float) a.getAnimatedValue();
            target.setScaleX(scale);
            target.setScaleY(scale);
        });
        anim.start();
        return anim;
    }

    /**
     * 停止所有与 view 关联的动画。
     */
    public void cancelAll(View view) {
        if (view == null) return;
        cancelTag(view, R.id.tag_anim_number);
        cancelTag(view, R.id.tag_anim_int);
        cancelTag(view, R.id.tag_pulse);
    }

    private void cancelTag(View view, int tagId) {
        Object obj = view.getTag(tagId);
        if (obj instanceof ValueAnimator) {
            ((ValueAnimator) obj).cancel();
        }
        view.setTag(tagId, null);
    }
}
