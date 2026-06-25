package com.batteryhealth.app.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

/**
 * Fragment 错误视图辅助工具。
 * 在 Fragment.onCreateView 抛出异常时，返回一个安全的兜底 TextView，
 * 而不是让整个 Fragment 崩溃。
 *
 * <p>调用方（各 Fragment）在 catch 块中使用：
 * <pre>{@code
 *   return FragmentErrorViewHelper.createErrorView(ctx, e);
 * }</pre>
 */
public final class FragmentErrorViewHelper {
    private FragmentErrorViewHelper() {}

    /**
     * 创建错误展示视图。
     *
     * @param context 上下文，可为 null（null 时返回空白占位 View，避免 NPE 崩溃）
     * @param e       异常对象，可为 null
     * @return 错误展示 TextView，绝不会返回 null
     */
    public static TextView createErrorView(Context context, Exception e) {
        TextView tv;
        if (context == null) {
            // context 为 null 时无法使用任何资源，返回最小化空白 TextView
            tv = new TextView(null);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(40, 100, 40, 40);
            tv.setGravity(Gravity.CENTER);
            tv.setText("加载失败");
            tv.setTextSize(16);
            return tv;
        }

        tv = new TextView(context);
        String className = e != null ? e.getClass().getSimpleName() : "UnknownError";
        String detail = (e != null && e.getMessage() != null) ? e.getMessage() : "";
        String message = context.getString(R.string.error_view_load_failed, className, detail);
        tv.setText(message);
        tv.setTextColor(ContextCompat.getColor(context, R.color.ios_label));
        tv.setTextSize(16);
        tv.setPadding(40, 100, 40, 40);
        tv.setBackgroundColor(ContextCompat.getColor(context, R.color.ios_background));
        return tv;
    }
}
