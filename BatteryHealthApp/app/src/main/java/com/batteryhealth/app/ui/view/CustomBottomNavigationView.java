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

        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
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

        boolean average = forceAverageWidth && items.size() > 0;
        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, container, false);

            if (average) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                view.setLayoutParams(lp);
            }

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);
            if (icon != null) icon.setImageResource(item.iconRes);
            if (label != null) label.setText(item.label);

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
     * 保证图标+文字始终在手势条上方居中，而不会整体被压缩或拉伸变形。
     */
    public void applySystemBottomInset(int inset) {
        if (this.bottomInset == inset) return;
        this.bottomInset = inset;
        int basePaddingBottom = (int) (6 * getResources().getDisplayMetrics().density + 0.5f);
        for (View item : itemViews) {
            item.setPadding(item.getPaddingLeft(), item.getPaddingTop(),
                    item.getPaddingRight(), basePaddingBottom + inset);
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