package com.batteryhealth.app.data.worker;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager 周期任务调度器。
 *
 * 注意：WorkManager 最小周期为 15 分钟，传入小于 15 分钟的值会被系统静默提升到 15 分钟。
 * 早期版本传 5 分钟实际会变成 15 分钟，但语义不一致，已统一为 15 分钟。
 */
public class WorkManagerScheduler {

    private static final String DATA_WORK_NAME = "battery_data_work";
    private static final String ALERT_WORK_NAME = "health_alert_work";

    /**
     * 调度电池数据采集周期任务。
     * - 周期：15 分钟（WorkManager 最小周期），flex 5 分钟。
     * - 约束：无需网络，电池电量非低。
     * - KEEP 策略：重复调用不会创建新任务。
     *
     * 与 BatteryMonitorService 的关系：
     *  - 前台服务运行时，本 Worker 同样会执行（写库时会触发 BatteryRepository.saveBatteryInfo 内部去重）。
     *  - 当前台服务被系统杀死时，本 Worker 作为兜底采集。
     */
    public static void scheduleBatteryDataWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest dataRequest = new PeriodicWorkRequest.Builder(
                BatteryDataWorker.class,
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DATA_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                dataRequest
        );
    }

    /**
     * 调度健康预警周期任务。
     * - 周期：1 小时，flex 15 分钟。
     * - 约束：无需网络，电池电量非低。
     * - KEEP 策略：重复调用不会创建新任务。
     *
     * 与 BatteryMonitorService 的关系：
     *  - 前台服务运行时，BatteryMonitorService 已每 24h 自检一次。
     *  - 当前台服务被系统杀死时，本 Worker 作为兜底触发。
     *  - 两者共享 SharedPreferences（battery_health_prefs）的 last_health_alert_time，互斥触发避免重复打扰。
     */
    public static void scheduleHealthAlertWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest alertRequest = new PeriodicWorkRequest.Builder(
                HealthAlertWorker.class,
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ALERT_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                alertRequest
        );
    }

    /**
     * 精确取消本应用的两个周期任务。
     * 注意：早期版本使用 cancelAllWork() 会取消应用内所有 WorkManager 任务，粒度过粗；
     *       已改为按 workName 精确取消。
     */
    public static void cancelAllWork(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(DATA_WORK_NAME);
        workManager.cancelUniqueWork(ALERT_WORK_NAME);
    }
}
