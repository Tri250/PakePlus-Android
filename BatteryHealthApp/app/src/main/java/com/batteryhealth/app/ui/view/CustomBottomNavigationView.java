package com.batteryhealth.app.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;

import com.batteryhealth.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义底部导航栏
 *
 * 特性：
 * 1. 突破 Material BottomNavigationView 5 项限制
 * 2. 支持 Badge 红点/数字提示
 * 3. 选中态弹性缩放动画 + 颜色渐变
 * 4. 长按 Tooltip 无障碍提示
 * 5. Edge-to-Edge 手势条适配
 * 6. 窄屏自动切换横向滚动模式
 */
public class CustomBottomNavigationView extends HorizontalScrollView {

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
    }

    private final List<NavItem> items = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();
    private final Map<Integer, Badge> badgeMap = new HashMap<>();
    private OnItemSelectedListener listener;
    private int selectedPosition = 0;

    private int activeColor;
    private int inactiveColor;
    private int badgeColor;
    private LinearLayout container;

    private int baseHeightPx = -1;
    private int bottomInset = 0;

    // onDraw 高频使用的 Paint 与尺寸缓存，避免每帧分配对象造成 GC 抖动
    private final Paint badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect measureBounds = new Rect();
    private float density;

    public CustomBottomNavigationView(@NonNull Context context) {
        this(context, null);
    }

    public CustomBottomNavigationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomBottomNavigationView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(container);

        activeColor = getResources().getColor(R.color.ios_blue, getContext().getTheme());
        inactiveColor = getResources().getColor(R.color.ios_tertiary_label, getContext().getTheme());
        badgeColor = getResources().getColor(R.color.red, getContext().getTheme());
        density = getResources().getDisplayMetrics().density;

        // 预配置复用 Paint，onDraw 中仅动态计算坐标
        badgeBgPaint.setColor(badgeColor);
        badgeBgPaint.setStyle(Paint.Style.FILL);
        badgeDotPaint.setColor(badgeColor);
        badgeDotPaint.setStyle(Paint.Style.FILL);
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        measurePaint.setTextSize(density * 5f);
    }

    public void setItems(List<NavItem> navItems) {
        items.clear();
        itemViews.clear();
        badgeMap.clear();
        container.removeAllViews();

        if (navItems == null || navItems.isEmpty()) {
            return;
        }

        items.addAll(navItems);
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, container, false);

            // 7 Tab 在主流屏幕上平均分布
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            view.setLayoutParams(lp);

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);

            icon.setImageResource(item.iconRes);
            label.setText(item.label);
            TooltipCompat.setTooltipText(view, item.label);

            view.setOnClickListener(v -> {
                if (listener != null && position != selectedPosition) {
                    listener.onItemSelected(position);
                }
            });

            container.addView(view);
            itemViews.add(view);
        }

        updateSelection(0);
    }

    public void setSelectedPosition(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        this.selectedPosition = position;
        updateSelection(position);
    }

    private void updateSelection(int position) {
        for (int i = 0; i < itemViews.size(); i++) {
            View view = itemViews.get(i);
            boolean selected = i == position;

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);

            icon.setSelected(selected);
            icon.setColorFilter(selected ? activeColor : inactiveColor);
            label.setTextColor(selected ? activeColor : inactiveColor);

            if (selected) {
                icon.animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(150)
                        .withEndAction(() -> icon.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start())
                        .start();
                label.setAlpha(1.0f);
            } else {
                icon.setScaleX(1.0f);
                icon.setScaleY(1.0f);
                label.setAlpha(0.7f);
            }
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    // ==================== Badge 支持 ====================

    /**
     * 显示红点 Badge（无数字）
     */
    public void showBadge(int position) {
        showBadge(position, 0);
    }

    /**
     * 显示数字 Badge
     *
     * @param position 导航项位置
     * @param count    数字（<=0 时显示红点）
     */
    public void showBadge(int position, int count) {
        badgeMap.put(position, new Badge(true, count));
        invalidate();
    }

    /**
     * 隐藏 Badge
     */
    public void hideBadge(int position) {
        badgeMap.remove(position);
        invalidate();
    }

    /**
     * 清除所有 Badge
     */
    public void clearBadges() {
        badgeMap.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制 Badge
        for (Map.Entry<Integer, Badge> entry : badgeMap.entrySet()) {
            int position = entry.getKey();
            Badge badge = entry.getValue();
            if (position < 0 || position >= itemViews.size()) continue;

            View itemView = itemViews.get(position);
            ImageView icon = itemView.findViewById(R.id.nav_icon);
            if (icon == null) continue;

            // Badge 位置：图标右上角
            float iconCenterX = itemView.getLeft() + icon.getLeft() + icon.getWidth() / 2f;
            float iconTop = itemView.getTop() + icon.getTop();
            float badgeRadius = density * 4f; // 4dp

            if (badge.count > 0) {
                // 数字 Badge：椭圆背景
                float textWidth = measureTextWidth(String.valueOf(badge.count));
                float badgeWidth = Math.max(badgeRadius * 2, textWidth + badgeRadius);
                float badgeHeight = badgeRadius * 2;
                float cx = iconCenterX + icon.getWidth() / 2f - badgeWidth / 2f;
                float cy = iconTop - badgeHeight / 4f;

                canvas.drawRoundRect(cx, cy, cx + badgeWidth, cy + badgeHeight,
                        badgeHeight, badgeHeight, badgeBgPaint);

                badgeTextPaint.setTextSize(badgeRadius * 1.4f);
                Paint.FontMetrics fm = badgeTextPaint.getFontMetrics();
                float textY = cy + badgeHeight / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(String.valueOf(badge.count), cx + badgeWidth / 2f, textY, badgeTextPaint);
            } else {
                // 红点 Badge
                float cx = iconCenterX + icon.getWidth() / 2f - badgeRadius;
                float cy = iconTop - badgeRadius / 2f;
                canvas.drawCircle(cx, cy, badgeRadius, badgeDotPaint);
            }
        }
    }

    private float measureTextWidth(String text) {
        measurePaint.getTextBounds(text, 0, text.length(), measureBounds);
        return measureBounds.width();
    }

    // ==================== 系统适配 ====================

    public void applySystemBottomInset(int inset) {
        ensureBaseHeight();
        if (this.bottomInset == inset) {
            return;
        }
        this.bottomInset = inset;
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = baseHeightPx + inset;
            setLayoutParams(lp);
        }
    }

    private void ensureBaseHeight() {
        if (baseHeightPx > 0) {
            return;
        }
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.height > 0) {
            baseHeightPx = lp.height;
        } else {
            baseHeightPx = (int) (64 * getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    // ==================== 数据模型 ====================

    private static class Badge {
        final boolean visible;
        final int count;

        Badge(boolean visible, int count) {
            this.visible = visible;
            this.count = count;
        }
    }

    public static class NavItem {
        public final String label;
        @DrawableRes
        public final int iconRes;

        public NavItem(String label, int iconRes) {
            this.label = label;
            this.iconRes = iconRes;
        }
    }
}
