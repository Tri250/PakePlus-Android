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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
 * 3. 自定义实现彻底绕过上述限制与兼容性问题，并对 6 Tab 平均分布、系统手势条 inset 做专门优化。
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
    private boolean forceAverageWidth = true;
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
        // 问题修复：HorizontalScrollView 的直接子 View 应该使用 WRAP_CONTENT 宽度，
        // 这样当 item 总宽度超过屏幕时才能正确滚动；使用 MATCH_PARENT 会导致
        // 子 View 被强制拉伸到屏幕宽度，6 个 item 会被过度压缩，图标和文字显示半截。
        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(container);

        activeColor = getResources().getColor(R.color.coloros_blue, getContext().getTheme());
        inactiveColor = getResources().getColor(R.color.label_3, getContext().getTheme());

        // 自动监听系统手势条/导航栏高度，给每个 item 底部增加 padding
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int inset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            applySystemBottomInset(inset);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * 设置导航项
     * 问题修复：
     * 1. 当 forceAverageWidth=true 时，计算每个 item 的等分宽度 = 屏幕宽度 / item 数量，
     *    而不是使用 LinearLayout weight=1（weight 在 WRAP_CONTENT 父容器中行为不稳定）。
     * 2. 为每个 item 设置最小宽度和固定高度，确保图标和文字有足够的显示空间。
     * 3. 设置合理的左右 padding，避免文字被截断。
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

        // 计算每个 item 的等分宽度（基于屏幕宽度）
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int itemWidth = forceAverageWidth && items.size() > 0
                ? screenWidth / items.size()
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        // 确保每个 item 至少有足够显示图标+文字的最小宽度
        int minItemWidth = (int) (64 * getResources().getDisplayMetrics().density + 0.5f);
        if (itemWidth != LinearLayout.LayoutParams.WRAP_CONTENT && itemWidth < minItemWidth) {
            itemWidth = minItemWidth;
        }

        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, container, false);

            // 问题修复：使用固定宽度替代 weight，避免 measure 计算错误导致图标/文字被压缩
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    itemWidth == LinearLayout.LayoutParams.WRAP_CONTENT ? itemWidth : Math.max(itemWidth, minItemWidth),
                    LinearLayout.LayoutParams.MATCH_PARENT);
            view.setLayoutParams(lp);

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);
            if (icon != null) {
                icon.setImageResource(item.iconRes);
                // 问题修复：确保 ImageView 的 drawable 被正确设置且可见
                icon.setVisibility(View.VISIBLE);
            }
            if (label != null) {
                label.setText(item.label);
                label.setVisibility(View.VISIBLE);
            }

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

            if (icon != null) {
                icon.setSelected(selected);
                icon.setColorFilter(selected ? activeColor : inactiveColor);
            }
            if (label != null) label.setTextColor(selected ? activeColor : inactiveColor);

            // 选中态缩放动画：图标从 1.0 -> 1.12 -> 1.0
            if (selected && icon != null) {
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
                if (label != null) label.setAlpha(1.0f);
            } else {
                if (icon != null) {
                    icon.setScaleX(1.0f);
                    icon.setScaleY(1.0f);
                }
                if (label != null) label.setAlpha(0.85f);
            }
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * 应用系统底部导航栏/手势条高度：把 inset 均匀加到每个 item 的底部 padding，
     * 保证图标+文字始终在手势条上方居中，而不会整体被遮挡或压缩变形。
     * 问题修复：同时调整顶部 padding，使图标+文字在总高度中保持垂直居中。
     */
    public void applySystemBottomInset(int inset) {
        if (this.bottomInset == inset) return;
        this.bottomInset = inset;
        float density = getResources().getDisplayMetrics().density;
        int basePaddingTop = (int) (6 * density + 0.5f);
        int basePaddingBottom = (int) (6 * density + 0.5f);
        for (View item : itemViews) {
            item.setPadding(item.getPaddingLeft(), basePaddingTop,
                    item.getPaddingRight(), basePaddingBottom + inset);
        }
        // 问题修复：inset 改变后请求重新 layout，确保底部导航栏高度更新正确
        requestLayout();
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