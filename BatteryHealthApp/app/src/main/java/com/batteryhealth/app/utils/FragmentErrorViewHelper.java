package com.batteryhealth.app.utils;

import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

public final class FragmentErrorViewHelper {
    private FragmentErrorViewHelper() {}

    public static TextView createErrorView(Context context, Exception e) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        TextView tv = new TextView(context);
        String message = context.getString(
                R.string.error_view_load_failed,
                e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "");
        tv.setText(message);
        tv.setTextColor(ContextCompat.getColor(context, R.color.ios_label));
        tv.setTextSize(16);
        tv.setPadding(40, 100, 40, 40);
        tv.setBackgroundColor(ContextCompat.getColor(context, R.color.ios_background));
        return tv;
    }
}
