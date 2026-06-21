package com.batteryhealth.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.widget.RemoteViews;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.service.BatteryMonitorService;

import java.util.Locale;

/**
 * 电池健康桌面小组件：2x2 显示电量、健康度、温度
 */
public class BatteryWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = buildWidgetViews(context);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private RemoteViews buildWidgetViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_battery);

        // 获取电池信息
        Intent batteryIntent = getBatteryIntent(context);
        int level = 0;
        int scale = 1;
        int temp = 0;

        if (batteryIntent != null) {
            level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        }

        int batteryPct = (int) ((level / (float) scale) * 100);
        float tempC = temp / 10f;

        // 获取健康度
        SharedPreferences prefs = context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE);
        int healthPct = prefs.getInt("last_health_pct", batteryPct);

        views.setTextViewText(R.id.tv_widget_level, String.format(Locale.getDefault(), "%d%%", batteryPct));
        views.setTextViewText(R.id.tv_widget_health, String.format(Locale.getDefault(), "健康 %d%%", healthPct));
        views.setTextViewText(R.id.tv_widget_temp, String.format(Locale.getDefault(), "%.1f°C", tempC));

        // 设置进度条
        views.setProgressBar(R.id.progress_widget_battery, 100, batteryPct, false);

        // 点击打开应用
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        return views;
    }

    private Intent getBatteryIntent(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                return context.registerReceiver(null, filter);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 请求更新所有小组件
     */
    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, BatteryWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            Intent intent = new Intent(context, BatteryWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
            context.sendBroadcast(intent);
        }
    }
}