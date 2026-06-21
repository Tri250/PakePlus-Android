package com.batteryhealth.app.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.batteryhealth.app.R;

/**
 * 统一的空状态/加载状态/错误恢复系统。
 *
 * <p>ColorOS 16 风格：半透明背景、大圆角、柔和配色。
 * 通过在内容容器上叠加 overlay 视图来实现状态切换，
 * 不侵入原始布局结构。</p>
 *
 * <ul>
 *   <li>LOADING — 居中进度指示器 + 可选提示文字</li>
 *   <li>EMPTY — 图标/插图 + 消息 + 可选操作按钮</li>
 *   <li>ERROR — 错误图标 + 消息 + 重试按钮</li>
 *   <li>CONTENT — 隐藏所有 overlay，显示原始内容</li>
 * </ul>
 */
public class StateLayoutHelper {

    public enum State { LOADING, EMPTY, ERROR, CONTENT }

    private static final long CROSS_FADE_DURATION = 200L;

    private final ViewGroup contentContainer;
    private final FrameLayout overlayContainer;

    private LinearLayout loadingView;
    private TextView loadingMessageView;

    private LinearLayout emptyView;
    private ImageView emptyIconView;
    private TextView emptyMessageView;
    private TextView emptyActionButton;

    private LinearLayout errorView;
    private TextView errorMessageView;
    private TextView retryButtonView;

    private State currentState = State.CONTENT;

    public StateLayoutHelper(@NonNull ViewGroup contentContainer) {
        this.contentContainer = contentContainer;

        overlayContainer = new FrameLayout(contentContainer.getContext());
        overlayContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlayContainer.setVisibility(View.GONE);

        if (contentContainer.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) contentContainer.getParent();
            int index = parent.indexOfChild(contentContainer);
            parent.removeView(contentContainer);

            FrameLayout wrapper = new FrameLayout(contentContainer.getContext());
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            contentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            wrapper.addView(contentContainer);
            wrapper.addView(overlayContainer);

            parent.addView(wrapper, index);
        } else {
            contentContainer.addView(overlayContainer);
        }

        initOverlayViews();
    }

    private void initOverlayViews() {
        Context ctx = contentContainer.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;

        // ---- Loading View ----
        loadingView = createCenteredContainer(ctx, d);

        ProgressBar progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleLarge);
        int greenColor = ContextCompat.getColor(ctx, R.color.coloros_green);
        Drawable progressDrawable = progressBar.getIndeterminateDrawable();
        if (progressDrawable != null) {
            Drawable wrapped = DrawableCompat.wrap(progressDrawable);
            DrawableCompat.setTint(wrapped, greenColor);
            progressBar.setIndeterminateDrawable(wrapped);
        }
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                (int) (48 * d), (int) (48 * d));
        progressBar.setLayoutParams(pbParams);
        loadingView.addView(progressBar);

        loadingMessageView = new TextView(ctx);
        loadingMessageView.setTextSize(16);
        loadingMessageView.setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
        loadingMessageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = (int) (16 * d);
        loadingMessageView.setLayoutParams(msgParams);
        loadingView.addView(loadingMessageView);

        // ---- Empty View ----
        emptyView = createCenteredContainer(ctx, d);

        emptyIconView = new ImageView(ctx);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                (int) (64 * d), (int) (64 * d));
        emptyIconView.setLayoutParams(iconParams);
        emptyIconView.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_battery));
        emptyIconView.setColorFilter(ContextCompat.getColor(ctx, R.color.label_3),
                PorterDuff.Mode.SRC_IN);
        emptyView.addView(emptyIconView);

        emptyMessageView = new TextView(ctx);
        emptyMessageView.setTextSize(16);
        emptyMessageView.setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
        emptyMessageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emptyMsgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        emptyMsgParams.topMargin = (int) (16 * d);
        emptyMessageView.setLayoutParams(emptyMsgParams);
        emptyView.addView(emptyMessageView);

        emptyActionButton = new TextView(ctx);
        emptyActionButton.setTextSize(14);
        emptyActionButton.setTextColor(ContextCompat.getColor(ctx, R.color.coloros_green));
        emptyActionButton.setTypeface(null, android.graphics.Typeface.BOLD);
        emptyActionButton.setGravity(Gravity.CENTER);
        emptyActionButton.setPadding(
                (int) (24 * d), (int) (10 * d), (int) (24 * d), (int) (10 * d));
        emptyActionButton.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_btn_secondary));
        emptyActionButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = (int) (20 * d);
        emptyActionButton.setLayoutParams(actionParams);
        emptyView.addView(emptyActionButton);

        // ---- Error View ----
        errorView = createCenteredContainer(ctx, d);

        ImageView errorIcon = new ImageView(ctx);
        LinearLayout.LayoutParams errorIconParams = new LinearLayout.LayoutParams(
                (int) (64 * d), (int) (64 * d));
        errorIcon.setLayoutParams(errorIconParams);
        errorIcon.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_error));
        errorView.addView(errorIcon);

        errorMessageView = new TextView(ctx);
        errorMessageView.setTextSize(16);
        errorMessageView.setTextColor(ContextCompat.getColor(ctx, R.color.label_2));
        errorMessageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams errorMsgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        errorMsgParams.topMargin = (int) (16 * d);
        errorMessageView.setLayoutParams(errorMsgParams);
        errorView.addView(errorMessageView);

        retryButtonView = new TextView(ctx);
        retryButtonView.setTextSize(14);
        retryButtonView.setTextColor(ContextCompat.getColor(ctx, R.color.coloros_green));
        retryButtonView.setTypeface(null, android.graphics.Typeface.BOLD);
        retryButtonView.setGravity(Gravity.CENTER);
        retryButtonView.setText(R.string.action_retry);
        retryButtonView.setPadding(
                (int) (24 * d), (int) (10 * d), (int) (24 * d), (int) (10 * d));
        retryButtonView.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_btn_secondary));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = (int) (20 * d);
        retryButtonView.setLayoutParams(retryParams);
        errorView.addView(retryButtonView);

        // 初始状态：所有 overlay 不可见
        loadingView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        overlayContainer.addView(loadingView);
        overlayContainer.addView(emptyView);
        overlayContainer.addView(errorView);
    }

    private LinearLayout createCenteredContainer(Context ctx, float d) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_card));
        container.setPadding(
                (int) (32 * d), (int) (48 * d), (int) (32 * d), (int) (48 * d));
        return container;
    }

    /**
     * 显示加载状态。
     *
     * @param message 加载提示文字，可为 null
     */
    public void showLoading(String message) {
        if (currentState == State.LOADING) return;
        currentState = State.LOADING;

        emptyView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        if (message != null) {
            loadingMessageView.setText(message);
            loadingMessageView.setVisibility(View.VISIBLE);
        } else {
            loadingMessageView.setVisibility(View.GONE);
        }

        showOverlay(loadingView);
    }

    /**
     * 显示空状态。
     *
     * @param message        空状态提示文字
     * @param icon           图标资源 ID，0 表示使用默认图标
     * @param actionListener 操作按钮点击监听，null 则不显示按钮
     * @param actionText     操作按钮文字
     */
    public void showEmpty(String message, @DrawableRes int icon,
                          View.OnClickListener actionListener, String actionText) {
        if (currentState == State.EMPTY) return;
        currentState = State.EMPTY;

        loadingView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        if (icon != 0) {
            Context ctx = contentContainer.getContext();
            emptyIconView.setImageDrawable(ContextCompat.getDrawable(ctx, icon));
            emptyIconView.setColorFilter(ContextCompat.getColor(ctx, R.color.label_3),
                    PorterDuff.Mode.SRC_IN);
        }

        emptyMessageView.setText(message);

        if (actionListener != null && actionText != null) {
            emptyActionButton.setText(actionText);
            emptyActionButton.setOnClickListener(actionListener);
            emptyActionButton.setVisibility(View.VISIBLE);
        } else {
            emptyActionButton.setVisibility(View.GONE);
        }

        showOverlay(emptyView);
    }

    /**
     * 显示错误状态。
     *
     * @param message       错误提示文字
     * @param retryListener 重试按钮点击监听
     */
    public void showError(String message, View.OnClickListener retryListener) {
        if (currentState == State.ERROR) return;
        currentState = State.ERROR;

        loadingView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        errorMessageView.setText(message);

        if (retryListener != null) {
            retryButtonView.setOnClickListener(retryListener);
        }

        showOverlay(errorView);
    }

    /**
     * 显示原始内容，隐藏所有 overlay。
     */
    public void showContent() {
        if (currentState == State.CONTENT) return;
        currentState = State.CONTENT;

        hideOverlay();
    }

    /**
     * 获取当前状态。
     */
    public State getCurrentState() {
        return currentState;
    }

    private void showOverlay(View activeView) {
        overlayContainer.setVisibility(View.VISIBLE);
        activeView.setAlpha(0f);
        activeView.setVisibility(View.VISIBLE);
        activeView.animate()
                .alpha(1f)
                .setDuration(CROSS_FADE_DURATION)
                .start();
    }

    private void hideOverlay() {
        if (overlayContainer.getVisibility() == View.GONE) return;

        View activeView = null;
        if (loadingView.getVisibility() == View.VISIBLE) activeView = loadingView;
        else if (emptyView.getVisibility() == View.VISIBLE) activeView = emptyView;
        else if (errorView.getVisibility() == View.VISIBLE) activeView = errorView;

        if (activeView != null) {
            activeView.animate()
                    .alpha(0f)
                    .setDuration(CROSS_FADE_DURATION)
                    .withEndAction(() -> {
                        loadingView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.GONE);
                        errorView.setVisibility(View.GONE);
                        overlayContainer.setVisibility(View.GONE);
                    })
                    .start();
        } else {
            overlayContainer.setVisibility(View.GONE);
        }
    }
}
