package com.batteryhealth.app.data.worker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.Locale;

/**
 * 健康预警周期任务（WorkManager 兜底）。
 *
 * 策略说明：
 *  - 与 BatteryMonitorService 的衰减量阈值策略保持一致（不再使用绝对值 < 80 触发）。
 *  - 当近 30 天均值与当前健康度差值 ≥ 阈值（默认 2.0%）时触发预警。
 *  - 7 天最小间隔，避免反复打扰用户。
 *  - 复用同一渠道 "battery_health_alert_channel"，与 BatteryMonitorService 归并。
 *  - 通知 ID 使用 1005，避免与 BatteryMonitorService 的 1001/1002、ChargingMonitorService 的 1003/1004 撞号。
 *
 * 与 BatteryMonitorService 的关系：
 *  - 前台服务运行时，BatteryMonitorService 已每 24h 自检一次。
 *  - 当前台服务被系统杀死（Doze / 后台限制 / 用户停止）时，本 Worker 作为兜底触发。
 *  - 两者通过相同的 SharedPreferences（battery_health_prefs）做去重，互不重复打扰。
 */
public class HealthAlertWorker extends Worker {

    private static final String TAG = "HealthAlertWorker";

    /** 与 BatteryMonitorService 对齐：使用同一渠道 */
    private static final String CHANNEL_ID = "battery_health_alert_channel";
    /** 独立通知 ID，避免与前台服务通知冲突 */
    private static final int NOTIFICATION_ID = 1005;

    /** 与 BatteryMonitorService 对齐：使用同一 SharedPreferences */
    private static final String PREFS_NAME = "battery_health_prefs";
    /** 注意：必须与 BatteryMonitorService.PREF_ALERT_ENABLED 同名同值，确保开关一致 */
    private static final String PREF_ALERT_ENABLED = "health_alert_enabled";
    /** 与 BatteryMonitorService.PREF_LAST_ALERT_TIME 同名，互斥触发避免重复打扰 */
    private static final String PREF_LAST_ALERT_TIME = "last_health_alert_time";
    /** 本 Worker 独有：记录上次预警时的健康度，避免同水平下重复触发 */
    private static final String PREF_LAST_ALERT_HEALTH = "last_alert_health";

    /** 衰减触发阈值（与 BatteryMonitorService 默认值对齐） */
    private static final float DEFAULT_DEGRADATION_THRESHOLD = 2.0f;
    /** 7 天最小间隔 */
    private static final long MIN_TIME_BETWEEN_ALERTS = 7L * 24 * 60 * 60 * 1000;
    /** 触发预警所需的最小历史记录数 */
    private static final int MIN_RECORDS_FOR_DEGRADATION = 10;
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    public HealthAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return Result.success();

            SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean alertEnabled = prefs.getBoolean(PREF_ALERT_ENABLED, true);
            if (!alertEnabled) {
                return Result.success();
            }

            // 7 天最小间隔去重
            long now = System.currentTimeMillis();
            long lastAlertTime = prefs.getLong(PREF_LAST_ALERT_TIME, 0);
            if (lastAlertTime > 0 && (now - lastAlertTime) < MIN_TIME_BETWEEN_ALERTS) {
                return Result.success();
            }

            BatteryRepository repository = new BatteryRepositoryImpl(app);
            BatteryDataManager dataManager = ((BatteryRepositoryImpl) repository).getBatteryDataManager();
            dataManager.refreshFromStickyIntent();

            BatteryInfo info = dataManager.getCurrentBatteryInfo();
            if (info == null || !info.hasValidHealthData()) {
                return Result.success();
            }

            float currentHealth = info.getHealthPercentage();

            // 查询近 30 天历史均值
            long sinceTime = now - THIRTY_DAYS_MS;
            int recordCount = repository.getHistoryCountSince(sinceTime);
            if (recordCount < MIN_RECORDS_FOR_DEGRADATION) {
                return Result.success();
            }
            float averageHealth = repository.getAverageHealthSince(sinceTime);

            float drop = averageHealth - currentHealth;
            if (drop >= DEFAULT_DEGRADATION_THRESHOLD) {
                // 二次校验：与上次预警时的健康度比对，避免同水平下重复触发
                float lastAlertHealth = prefs.getFloat(PREF_LAST_ALERT_HEALTH, -1f);
                if (lastAlertHealth > 0 && Math.abs(lastAlertHealth - currentHealth) < 0.5f) {
                    return Result.success();
                }
                sendHealthAlertNotification(drop, averageHealth, currentHealth);
                prefs.edit()
                        .putLong(PREF_LAST_ALERT_TIME, now)
                        .putFloat(PREF_LAST_ALERT_HEALTH, currentHealth)
                        .apply();
            }

            return Result.success();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error checking health: " + e.getMessage());
            return Result.retry();
        }
    }

    private void sendHealthAlertNotification(float drop, float averageHealth, float currentHealth) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) return;

            // 渠道复用 BatteryMonitorService 创建的 "battery_health_alert_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
                if (channel == null) {
                    channel = new android.app.NotificationChannel(
                            CHANNEL_ID,
                            getApplicationContext().getString(R.string.health_alert_channel_name),
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    channel.setDescription(getApplicationContext().getString(R.string.health_alert_channel_description));
                    notificationManager.createNotificationChannel(channel);
                }
            }

            String content = String.format(Locale.getDefault(),
                    getApplicationContext().getString(R.string.health_alert_content),
                    drop, averageHealth, currentHealth);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_battery_alert)
                    .setContentTitle(getApplicationContext().getString(R.string.health_alert_title))
                    .setContentText(content)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(createPendingIntent());

            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to send health alert notification", e);
        }
    }

    private PendingIntent createPendingIntent() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
