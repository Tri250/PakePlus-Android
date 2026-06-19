package com.batteryhealth.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统一的 UI 动效辅助类：2026 精致液态玻璃风格。
 * 提供卡片入场、数字增长、进度条平滑过渡等动画，并内置低内存/用户关闭动画的降级处理。
 */
public class UiAnimationHelper {

    private static final String PREFS_GLOBAL = "app_global_prefs";
    private static final String PREF_DISABLE_ANIMATIONS = "disable_animations";

    private static final long CARD_STAGGER_DELAY = 55L;
    private static final long CARD_DURATION = 420L;
    private static final long NUMBER_DURATION = 900L;
    private static final long PROGRESS_DURATION = 700L;

    // 标准缓动曲线：iOS 风格的 ease-out-cubic / spring
    private static final android.view.animation.Interpolator EASE_OUT_CUBIC =
            new PathInterpolator(0.33f, 1f, 0.68f, 1f);
    private static final android.view.animation.Interpolator SPRING =
            new android.view.animation.OvershootInterpolator(0.65f);

    private UiAnimationHelper() {}

    /**
     * 判断当前设备是否应该跳过复杂动画（低内存或用户手动关闭）。
     */
    public static boolean shouldSkipAnimations(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_DISABLE_ANIMATIONS, false)) {
                return true;
            }
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMemGb = mi.totalMem / (1024L * 1024L * 1024L);
                if (totalMemGb < 4) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d("UiAnimationHelper", "Animation check skipped: " + e.getMessage());
        }
        return false;
    }

    /**
     * 对指定根视图下的所有 MaterialCardView 执行错开入场动画。
     */
    public static void animateCardsEntry(View root) {
        animateCardsEntry(root, null);
    }

    public static void animateCardsEntry(View root, Runnable onComplete) {
        if (root == null) return;
        Context context = root.getContext();
        if (context != null && shouldSkipAnimations(context)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<View> cards = new ArrayList<>();
        collectCards(root, cards);

        int count = cards.size();
        if (count == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final int[] finished = {0};
        for (int i = 0; i < count; i++) {
            View child = cards.get(i);
            child.setAlpha(0f);
            child.setTranslationY(48f);
            child.setScaleX(0.96f);
            child.setScaleY(0.96f);

            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(CARD_DURATION)
                    .setStartDelay(i * CARD_STAGGER_DELAY)
                    .setInterpolator(EASE_OUT_CUBIC)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finished[0]++;
                            if (finished[0] >= count && onComplete != null) {
                                onComplete.run();
                            }
                        }
                    })
                    .start();
        }
    }

    private static void collectCards(View view, List<View> cards) {
        if (view instanceof MaterialCardView) {
            cards.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectCards(group.getChildAt(i), cards);
            }
        }
    }

    /**
     * 数字从当前值动画增长到目标值，支持自定义格式后缀。
     */
    public static void animateNumberText(TextView textView, float target, String format) {
        if (textView == null) return;
        Context context = textView.getContext();
        if (context != null && shouldSkipAnimations(context)) {
            textView.setText(String.format(Locale.getDefault(), format, target));
            return;
        }

        // 尝试从当前文本解析起始值
        float start = 0f;
        try {
            CharSequence current = textView.getText();
            if (current != null) {
                String s = current.toString().replaceAll("[^0-9\\.]", "");
                if (!s.isEmpty()) {
                    start = Float.parseFloat(s);
                }
            }
        } catch (Exception ignored) {
        }

        if (Math.abs(start - target) < 0.01f) {
            textView.setText(String.format(Locale.getDefault(), format, target));
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        animator.setDuration(NUMBER_DURATION);
        animator.setInterpolator(EASE_OUT_CUBIC);
        animator.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            textView.setText(String.format(Locale.getDefault(), format, value));
        });
        animator.start();
    }

    public static void animateIntText(TextView textView, int target, String format) {
        if (textView == null) return;
        Context context = textView.getContext();
        if (context != null && shouldSkipAnimations(context)) {
            textView.setText(String.format(Locale.getDefault(), format, target));
            return;
        }

        int start = 0;
        try {
            CharSequence current = textView.getText();
            if (current != null) {
                String s = current.toString().replaceAll("[^0-9]", "");
                if (!s.isEmpty()) {
                    start = Integer.parseInt(s);
                }
            }
        } catch (Exception ignored) {
        }

        if (start == target) {
            textView.setText(String.format(Locale.getDefault(), format, target));
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(start, target);
        animator.setDuration(NUMBER_DURATION);
        animator.setInterpolator(EASE_OUT_CUBIC);
        animator.addUpdateListener(a -> {
            int value = (int) a.getAnimatedValue();
            textView.setText(String.format(Locale.getDefault(), format, value));
        });
        animator.start();
    }

    /**
     * 平滑过渡 ProgressBar 进度。
     */
    public static void animateProgressBar(ProgressBar progressBar, int targetProgress) {
        if (progressBar == null) return;
        Context context = progressBar.getContext();
        if (context != null && shouldSkipAnimations(context)) {
            progressBar.setProgress(targetProgress);
            return;
        }

        int start = progressBar.getProgress();
        if (start == targetProgress) {
            progressBar.setProgress(targetProgress);
            return;
        }

        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", start, targetProgress);
        animator.setDuration(PROGRESS_DURATION);
        animator.setInterpolator(EASE_OUT_CUBIC);
        animator.start();
    }

    /**
     * 给主健康度数字添加轻微的“呼吸光晕”强调效果，增强科技感。
     */
    public static void pulseHealthNumber(View view) {
        if (view == null) return;
        Context context = view.getContext();
        if (context != null && shouldSkipAnimations(context)) return;

        view.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(220L)
                .setInterpolator(SPRING)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(280L)
                        .setInterpolator(EASE_OUT_CUBIC)
                        .start())
                .start();
    }
}
