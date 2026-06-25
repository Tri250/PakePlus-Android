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

/**
 * 电池概览 Widget (4x1)
 *
 * 显示：电量百分比 + 电池图标 + 健康度 + 充电状态
 * 点击跳转 MainActivity 健康页
 */
public class BatteryWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "BatteryWidgetProvider";
    private static final String ACTION_BATTERY_UPDATED = "com.batteryhealth.app.widget.action.BATTERY_UPDATED";

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
        Log.d(TAG, "BatteryWidget enabled");
        WidgetUpdateManager.getInstance(context).startPeriodicUpdate();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "BatteryWidget disabled");
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
                || ACTION_BATTERY_UPDATED.equals(action)
                || WidgetUpdateManager.getActionUpdateWidgets().equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, BatteryWidgetProvider.class);
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
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_battery_info);

            Intent intent = getBatteryStickyIntent(context);
            BatteryDataManager batteryManager = new BatteryDataManager(context);
            BatteryInfo batteryInfo = null;
            try {
                batteryInfo = batteryManager.getCurrentBatteryInfo();
            } catch (Exception e) {
                Log.d(TAG, "BatteryDataManager not ready, using sticky intent only");
            }

            int level = -1;
            int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
            float healthPercent = -1f;

            if (intent != null) {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) {
                    level = level * 100 / scale;
                }
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            }

            if (batteryInfo != null) {
                if (batteryInfo.getLevel() >= 0) level = batteryInfo.getLevel();
                healthPercent = batteryInfo.getHealthPercentage();
                if (batteryInfo.getStatus() > 0) status = batteryInfo.getStatus();
            }

            views.setTextViewText(R.id.widget_battery_level, level >= 0 ? String.valueOf(level) : "--");
            views.setTextViewText(R.id.widget_battery_status, getStatusString(context, status));

            if (healthPercent >= 0) {
                views.setTextViewText(R.id.widget_health_percent,
                        String.format("%d%%", (int) healthPercent));
            } else {
                views.setTextViewText(R.id.widget_health_percent, "--%");
            }

            Intent clickIntent = new Intent(context, MainActivity.class);
            clickIntent.setAction(Intent.ACTION_MAIN);
            clickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | getImmutableFlag()
            );
            views.setOnClickPendingIntent(R.id.widget_battery_icon, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_battery_level, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_battery_status, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_health_percent, pendingIntent);

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

    private String getStatusString(Context context, int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return context.getString(R.string.status_charging);
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return context.getString(R.string.status_discharging);
            case BatteryManager.BATTERY_STATUS_FULL:
                return context.getString(R.string.status_fully_charged);
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return context.getString(R.string.status_not_charging);
            default:
                return context.getString(R.string.status_unknown);
        }
    }

    private int getImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }
}
