package com.batteryhealth.app.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.batteryhealth.app.ui.widget.BatteryWidgetProvider;
import com.batteryhealth.app.ui.widget.ChargingWidgetProvider;
import com.batteryhealth.app.ui.widget.HealthWidgetProvider;

/**
 * Widget 更新管理器
 *
 * 功能：
 * 1. 统一管理所有 Widget 的更新
 * 2. 支持 AlarmManager 定时更新（每分钟）
 * 3. 支持电池广播即时更新
 * 4. 线程安全
 */
public class WidgetUpdateManager {

    private static final String TAG = "WidgetUpdateManager";
    private static final String ACTION_UPDATE_WIDGETS = "com.batteryhealth.app.widget.action.UPDATE_WIDGETS";
    private static final int ALARM_REQUEST_CODE = 0xB4A7_0100;
    private static final long UPDATE_INTERVAL_MS = 60 * 1000L;

    private static volatile WidgetUpdateManager instance;
    private final Context context;
    private final Handler mainHandler;
    private boolean alarmScheduled = false;

    private WidgetUpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static WidgetUpdateManager getInstance(Context context) {
        if (instance == null) {
            synchronized (WidgetUpdateManager.class) {
                if (instance == null) {
                    instance = new WidgetUpdateManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 更新所有 Widget
     */
    public void updateAllWidgets() {
        ThreadExecutor.execute(() -> {
            try {
                updateBatteryWidget();
                updateChargingWidget();
                updateHealthWidget();
            } catch (Exception e) {
                Log.e(TAG, "Error updating all widgets: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 更新电池概览 Widget
     */
    public void updateBatteryWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, BatteryWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids != null && ids.length > 0) {
                Intent intent = new Intent(context, BatteryWidgetProvider.class);
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery widget: " + e.getMessage());
        }
    }

    /**
     * 更新充电状态 Widget
     */
    public void updateChargingWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, ChargingWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids != null && ids.length > 0) {
                Intent intent = new Intent(context, ChargingWidgetProvider.class);
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating charging widget: " + e.getMessage());
        }
    }

    /**
     * 更新健康仪表盘 Widget
     */
    public void updateHealthWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, HealthWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids != null && ids.length > 0) {
                Intent intent = new Intent(context, HealthWidgetProvider.class);
                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating health widget: " + e.getMessage());
        }
    }

    /**
     * 启动定时更新（每分钟）
     */
    public void startPeriodicUpdate() {
        if (alarmScheduled) return;

        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            PendingIntent pendingIntent = createAlarmPendingIntent();
            long triggerAt = System.currentTimeMillis() + UPDATE_INTERVAL_MS;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            } else {
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            }
            alarmScheduled = true;
            Log.d(TAG, "Periodic widget update scheduled");
        } catch (Exception e) {
            Log.e(TAG, "Error starting periodic update: " + e.getMessage());
        }
    }

    /**
     * 停止定时更新
     */
    public void stopPeriodicUpdate() {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                PendingIntent pendingIntent = createAlarmPendingIntent();
                alarmManager.cancel(pendingIntent);
            }
            alarmScheduled = false;
            Log.d(TAG, "Periodic widget update stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping periodic update: " + e.getMessage());
        }
    }

    /**
     * 重新调度定时更新（用于收到广播后重新计时）
     */
    public void reschedulePeriodicUpdate() {
        stopPeriodicUpdate();
        startPeriodicUpdate();
    }

    private PendingIntent createAlarmPendingIntent() {
        Intent intent = new Intent(ACTION_UPDATE_WIDGETS);
        intent.setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
    }

    public static String getActionUpdateWidgets() {
        return ACTION_UPDATE_WIDGETS;
    }

    /**
     * 检查是否有任何 Widget 被启用
     */
    public boolean hasAnyWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName battery = new ComponentName(context, BatteryWidgetProvider.class);
            ComponentName charging = new ComponentName(context, ChargingWidgetProvider.class);
            ComponentName health = new ComponentName(context, HealthWidgetProvider.class);

            int[] batteryIds = manager.getAppWidgetIds(battery);
            int[] chargingIds = manager.getAppWidgetIds(charging);
            int[] healthIds = manager.getAppWidgetIds(health);

            return (batteryIds != null && batteryIds.length > 0)
                    || (chargingIds != null && chargingIds.length > 0)
                    || (healthIds != null && healthIds.length > 0);
        } catch (Exception e) {
            Log.e(TAG, "Error checking widgets: " + e.getMessage());
            return false;
        }
    }
}
