package com.batteryhealth.app.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

public final class EmptyStateHelper {

    private EmptyStateHelper() {}

    public static View createEmptyView(Context context, int iconRes, String title, String subtitle,
                                        String actionText, View.OnClickListener actionListener) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        int paddingHorizontal = (int) (32 * density);
        container.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);

        if (iconRes != 0) {
            ImageView iconView = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    (int) (96 * density),
                    (int) (96 * density)
            );
            iconParams.bottomMargin = (int) (24 * density);
            iconView.setLayoutParams(iconParams);
            iconView.setImageResource(iconRes);
            iconView.setAlpha(0.6f);
            container.addView(iconView);
        }

        if (!TextUtils.isEmpty(title)) {
            TextView titleView = new TextView(context);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.bottomMargin = (int) (8 * density);
            titleView.setLayoutParams(titleParams);
            titleView.setText(title);
            titleView.setTextSize(18);
            titleView.setTextColor(ContextCompat.getColor(context, R.color.label));
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setGravity(Gravity.CENTER);
            container.addView(titleView);
        }

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = new TextView(context);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.bottomMargin = (int) (24 * density);
            subtitleView.setLayoutParams(subtitleParams);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(14);
            subtitleView.setTextColor(ContextCompat.getColor(context, R.color.label_2));
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setLineSpacing(0, 1.3f);
            container.addView(subtitleView);
        }

        if (!TextUtils.isEmpty(actionText) && actionListener != null) {
            TextView actionView = new TextView(context);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            actionView.setLayoutParams(actionParams);
            actionView.setText(actionText);
            actionView.setTextSize(15);
            actionView.setTextColor(ContextCompat.getColor(context, R.color.blue));
            actionView.setGravity(Gravity.CENTER);
            actionView.setPadding(
                    (int) (24 * density),
                    (int) (10 * density),
                    (int) (24 * density),
                    (int) (10 * density)
            );
            actionView.setBackgroundResource(android.R.drawable.list_selector_background);
            actionView.setOnClickListener(actionListener);
            container.addView(actionView);
        }

        return container;
    }

    public static View createEmptyView(Context context, int iconRes, int titleRes, int subtitleRes,
                                        int actionTextRes, View.OnClickListener actionListener) {
        String title = titleRes != 0 ? context.getString(titleRes) : null;
        String subtitle = subtitleRes != 0 ? context.getString(subtitleRes) : null;
        String actionText = actionTextRes != 0 ? context.getString(actionTextRes) : null;
        return createEmptyView(context, iconRes, title, subtitle, actionText, actionListener);
    }
}
