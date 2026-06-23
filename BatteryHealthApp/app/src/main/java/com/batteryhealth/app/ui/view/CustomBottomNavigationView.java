package com.batteryhealth.app.ui.view;

import android.content.Context;
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
import java.util.List;

/**
 * 自定义底部导航栏
 *
 * 重构原因：
 * 1. Material 的 BottomNavigationView 最多仅支持 5 个菜单项，本应用需要 6 个 Tab。
 * 2. BottomNavigationView 在低版本 ROM / 特定 Material 组件版本下解析自定义
 *    TextAppearance 时会出现 Binary XML 崩溃。
 * 3. 自定义 LinearLayout 实现彻底绕过上述限制与兼容性问题。
 */
public class CustomBottomNavigationView extends HorizontalScrollView {

    public interface OnItemSelectedListener {
        void onItemSelected(int position);
    }

    private final List<NavItem> items = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();
    private OnItemSelectedListener listener;
    private int selectedPosition = 0;

    private int activeColor;
    private int inactiveColor;
    private LinearLayout container;
    private boolean forceAverageWidth = true; // 6 个 Tab 时强制平均分布，避免滚动

    private int baseHeightPx = -1;
    private int bottomInset = 0;

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

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        // 默认撑满宽度，配合子项 layout_weight 实现平均分布
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(container);

        activeColor = getResources().getColor(R.color.ios_blue, getContext().getTheme());
        inactiveColor = getResources().getColor(R.color.ios_tertiary_label, getContext().getTheme());
    }

    /**
     * 设置导航项
     */
    public void setItems(List<NavItem> navItems) {
        items.clear();
        itemViews.clear();
        container.removeAllViews();

        if (navItems == null || navItems.isEmpty()) {
            return;
        }

        items.addAll(navItems);
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // 根据总宽度与子项数量决定是否平均分布
        // 屏幕宽度 >= 360dp 且项数 <= 6 时强制平均；否则允许横向滚动
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        boolean average = forceAverageWidth && items.size() > 0
                && (items.size() <= 6 || screenWidthDp >= 360);

        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, container, false);

            // 强制平均分布：每个 item 宽度 = 容器宽度 / 数量
            if (average) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                view.setLayoutParams(lp);
            } else {
                // 窄屏 + 多 Tab：固定最小宽度，允许横向滚动
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                view.setLayoutParams(lp);
            }

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);

            icon.setImageResource(item.iconRes);
            label.setText(item.label);
            // 长按显示 Tooltip，提升可访问性
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

    /**
     * 设置选中项
     */
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

            // 选中态缩放动画：图标从 1.0 -> 1.12 -> 1.0，文字透明度变化
            if (selected) {
                icon.animate()
                        .scaleX(1.12f)
                        .scaleY(1.12f)
                        .setDuration(120)
                        .withEndAction(() -> icon.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(120)
                                .start())
                        .start();
                label.setAlpha(1.0f);
            } else {
                icon.setScaleX(1.0f);
                icon.setScaleY(1.0f);
                label.setAlpha(0.85f);
            }
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 应用系统底部导航栏/手势条高度：总高度 = 内容高度（XML 64dp）+ 系统 inset
     */
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

    /**
     * 单个导航项数据
     */
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
