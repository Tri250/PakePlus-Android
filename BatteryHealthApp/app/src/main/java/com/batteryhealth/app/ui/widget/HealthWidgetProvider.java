package com.batteryhealth.app.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.WidgetUpdateManager;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.util.Locale;

/**
 * 健康仪表盘 Widget (2x2)
 *
 * 显示：健康度环形进度 + 循环次数 + 温度
 * 点击跳转健康页
 */
public class HealthWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "HealthWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        WidgetUpdateManager.getInstance(context).startPeriodicUpdate();
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d(TAG, "HealthWidget enabled");
        WidgetUpdateManager.getInstance(context).startPeriodicUpdate();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "HealthWidget disabled");
        WidgetUpdateManager manager = WidgetUpdateManager.getInstance(context);
        if (!manager.hasAnyWidget()) {
            manager.stopPeriodicUpdate();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BATTERY_CHANGED.equals(action)
                || WidgetUpdateManager.getActionUpdateWidgets().equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, HealthWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids != null && ids.length > 0) {
                for (int id : ids) {
                    updateAppWidget(context, manager, id);
                }
            }
            if (WidgetUpdateManager.getActionUpdateWidgets().equals(action)) {
                WidgetUpdateManager.getInstance(context).reschedulePeriodicUpdate();
            }
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_health);

            BatteryDataManager batteryManager = new BatteryDataManager(context);
            BatteryInfo batteryInfo = null;
            float temperature = -1f;
            int cycleCount = -1;
            float healthPercent = -1f;

            try {
                batteryInfo = batteryManager.getCurrentBatteryInfo();
            } catch (Exception e) {
                Log.d(TAG, "BatteryDataManager not ready");
            }

            Intent intent = getBatteryStickyIntent(context);
            if (intent != null) {
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (tempRaw >= 0) {
                    temperature = tempRaw / 10.0f;
                }
            }

            if (batteryInfo != null) {
                healthPercent = batteryInfo.getHealthPercentage();
                cycleCount = batteryInfo.getCycleCount();
                if (batteryInfo.getTemperature() > 0) {
                    temperature = batteryInfo.getTemperature();
                }
            }

            int healthLevel = healthPercent >= 0 ? (int) healthPercent : 0;
            views.setProgressBar(R.id.widget_health_progress, 100, healthLevel, false);

            views.setTextViewText(R.id.widget_health_percent,
                    healthPercent >= 0 ? String.valueOf((int) healthPercent) : "--");

            if (cycleCount > 0) {
                views.setTextViewText(R.id.widget_cycle_count, String.valueOf(cycleCount));
            } else {
                views.setTextViewText(R.id.widget_cycle_count, "--");
            }

            if (temperature >= 0) {
                views.setTextViewText(R.id.widget_temp,
                        String.format(Locale.getDefault(), "%.0f°C", temperature));
            } else {
                views.setTextViewText(R.id.widget_temp, "--");
            }

            Intent clickIntent = new Intent(context, MainActivity.class);
            clickIntent.setAction(Intent.ACTION_MAIN);
            clickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            clickIntent.putExtra("nav_target", "health");
            clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 2000,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | getImmutableFlag()
            );
            views.setOnClickPendingIntent(R.id.widget_health_progress, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_health_percent, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_cycle_count, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_temp, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget: " + e.getMessage(), e);
        }
    }

    private Intent getBatteryStickyIntent(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.registerReceiver(
                        context,
                        null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                );
            } else {
                return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery sticky intent: " + e.getMessage());
            return null;
        }
    }

    private int getImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }
}
