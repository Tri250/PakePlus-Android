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
 * 充电状态 Widget (2x2)
 *
 * 显示：当前功率 + 预计充满时间 + 电量环形进度
 * 仅充电时显示详细数据
 * 点击跳转充电页
 */
public class ChargingWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ChargingWidgetProvider";

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
        Log.d(TAG, "ChargingWidget enabled");
        WidgetUpdateManager.getInstance(context).startPeriodicUpdate();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "ChargingWidget disabled");
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
            ComponentName component = new ComponentName(context, ChargingWidgetProvider.class);
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
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_charging);

            Intent intent = getBatteryStickyIntent(context);
            BatteryDataManager batteryManager = new BatteryDataManager(context);
            BatteryInfo batteryInfo = null;
            try {
                batteryInfo = batteryManager.getCurrentBatteryInfo();
            } catch (Exception e) {
                Log.d(TAG, "BatteryDataManager not ready");
            }

            int level = -1;
            int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
            boolean isCharging = false;
            float powerW = 0f;

            if (intent != null) {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) {
                    level = level * 100 / scale;
                }
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }

            if (batteryInfo != null) {
                if (batteryInfo.getLevel() >= 0) level = batteryInfo.getLevel();
                powerW = batteryInfo.getChargingPower();
                isCharging = batteryInfo.isCharging();
                if (batteryInfo.getStatus() > 0) status = batteryInfo.getStatus();
            }

            int progressLevel = Math.max(0, Math.min(100, level));
            views.setProgressBar(R.id.widget_charging_progress, 100, progressLevel, false);

            if (isCharging && powerW > 0) {
                views.setTextViewText(R.id.widget_charging_power,
                        String.format(Locale.getDefault(), "%.1f", powerW));
                views.setTextViewText(R.id.widget_charging_power_unit, "W");

                String timeLeft = calculateTimeLeft(context, level, powerW);
                views.setTextViewText(R.id.widget_charging_time, timeLeft);
            } else {
                views.setTextViewText(R.id.widget_charging_power, "--");
                views.setTextViewText(R.id.widget_charging_power_unit, "");
                views.setTextViewText(R.id.widget_charging_time,
                        getStatusString(context, status));
            }

            views.setTextViewText(R.id.widget_charging_level,
                    level >= 0 ? level + "%" : "--%");

            Intent clickIntent = new Intent(context, MainActivity.class);
            clickIntent.setAction(Intent.ACTION_MAIN);
            clickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            clickIntent.putExtra("nav_target", "power");
            clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 1000,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | getImmutableFlag()
            );
            views.setOnClickPendingIntent(R.id.widget_charging_progress, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_charging_power, pendingIntent);
            views.setOnClickPendingIntent(R.id.widget_charging_time, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget: " + e.getMessage(), e);
        }
    }

    private String calculateTimeLeft(Context context, int currentLevel, float powerW) {
        if (currentLevel >= 100 || powerW <= 0) {
            return context.getString(R.string.status_fully_charged);
        }

        int remainingPercent = 100 - currentLevel;
        BatteryDataManager tempManager = null;
        try {
            tempManager = new BatteryDataManager(context);
            BatteryInfo info = tempManager.getCurrentBatteryInfo();
            if (info != null && info.getCurrentCapacity() > 0) {
                float remainingMah = info.getCurrentCapacity() * remainingPercent / 100f;
                float currentA = powerW / 4.2f;
                float hours = remainingMah / (currentA * 1000f);

                if (hours < 0.05f) {
                    return context.getString(R.string.status_fully_charged);
                }

                int totalMinutes = (int) Math.ceil(hours * 60);
                if (totalMinutes < 60) {
                    return String.format(Locale.getDefault(),
                            context.getString(R.string.widget_minutes_format), totalMinutes);
                } else {
                    int hoursInt = totalMinutes / 60;
                    int minsInt = totalMinutes % 60;
                    return String.format(Locale.getDefault(),
                            context.getString(R.string.widget_hours_minutes_format), hoursInt, minsInt);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not calculate time left: " + e.getMessage());
        }

        float estimatedHours = remainingPercent / 100f * 1.5f;
        int totalMinutes = (int) Math.ceil(estimatedHours * 60);
        if (totalMinutes < 60) {
            return String.format(Locale.getDefault(),
                    context.getString(R.string.widget_minutes_format), totalMinutes);
        } else {
            int hoursInt = totalMinutes / 60;
            int minsInt = totalMinutes % 60;
            return String.format(Locale.getDefault(),
                    context.getString(R.string.widget_hours_minutes_format), hoursInt, minsInt);
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
