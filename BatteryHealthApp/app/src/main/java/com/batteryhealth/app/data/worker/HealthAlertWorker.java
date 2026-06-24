package com.batteryhealth.app.data.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryDataManager;

public class HealthAlertWorker extends Worker {

    private static final String TAG = "HealthAlertWorker";
    private static final String CHANNEL_ID = "battery_health_alert";
    // 独立通知 ID，避免与 BatteryMonitorService.NOTIFICATION_ID(1001) /
    // HEALTH_ALERT_NOTIFICATION_ID(1002) / ChargingMonitorService.NOTIFICATION_ID(1003) 冲突
    private static final int NOTIFICATION_ID = 1004;

    public HealthAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            BatteryRepository repository = new BatteryRepositoryImpl(app);

            BatteryDataManager dataManager = ((BatteryRepositoryImpl) repository).getBatteryDataManager();
            dataManager.refreshFromStickyIntent();

            BatteryInfo info = dataManager.getCurrentBatteryInfo();
            if (info != null && info.hasValidHealthData()) {
                float health = info.getHealthPercentage();
                if (health < 80) {
                    showHealthAlert(health);
                }
            }

            return Result.success();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error checking health: " + e.getMessage());
            return Result.retry();
        }
    }

    private void showHealthAlert(float health) {
        // Android 13+ 需运行时 POST_NOTIFICATIONS 权限，未授权时 notify() 静默失败
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "POST_NOTIFICATIONS not granted, skip health alert notification");
                return;
            }
        }

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "电池健康提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        String title = getApplicationContext().getString(R.string.app_name);
        String message = String.format("您的电池健康度为 %.0f%%，建议关注电池状态", health);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_battery_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
