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
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(container);

        activeColor = getResources().getColor(R.color.ios_blue, getContext().getTheme());
        inactiveColor = getResources().getColor(R.color.ios_secondary_label, getContext().getTheme());
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

        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            NavItem item = items.get(i);
            View view = inflater.inflate(R.layout.item_bottom_nav, container, false);

            ImageView icon = view.findViewById(R.id.nav_icon);
            TextView label = view.findViewById(R.id.nav_label);

            icon.setImageResource(item.iconRes);
            label.setText(item.label);

            view.setOnClickListener(v -> {
                if (listener != null) {
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
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
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
