package com.batteryhealth.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 充电服务启动器
 *
 * 策略：
 * - 仅在设备接入电源时启动 ChargingMonitorService，避免与 BatteryMonitorService
 *   同时常驻前台，降低 Android 12+ 前台服务限制与 ANR 风险。
 * - 断开电源时停止服务。
 */
public class ChargingServiceStarter extends BroadcastReceiver {

    private static final String TAG = "ChargingServiceStarter";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        try {
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                startChargingService(context);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                stopChargingService(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling charging broadcast: " + e.getMessage());
        }
    }

    private void startChargingService(Context context) {
        Intent intent = new Intent(context, ChargingMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.d(TAG, "ChargingMonitorService started on power connected");
    }

    private void stopChargingService(Context context) {
        Intent intent = new Intent(context, ChargingMonitorService.class);
        context.stopService(intent);
        Log.d(TAG, "ChargingMonitorService stopped on power disconnected");
    }
}
