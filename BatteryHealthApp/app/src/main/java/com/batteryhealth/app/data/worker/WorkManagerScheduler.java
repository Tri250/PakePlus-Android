package com.batteryhealth.app.data.worker;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WorkManagerScheduler {

    private static final String DATA_WORK_NAME = "battery_data_work";
    private static final String ALERT_WORK_NAME = "health_alert_work";

    public static void scheduleBatteryDataWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest dataRequest = new PeriodicWorkRequest.Builder(
                BatteryDataWorker.class,
                5, TimeUnit.MINUTES,
                1, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DATA_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                dataRequest
        );
    }

    public static void scheduleHealthAlertWork(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
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
                ExistingPeriodicWorkPolicy.REPLACE,
                alertRequest
        );
    }

    public static void cancelAllWork(Context context) {
        WorkManager.getInstance(context).cancelAllWork();
    }
}