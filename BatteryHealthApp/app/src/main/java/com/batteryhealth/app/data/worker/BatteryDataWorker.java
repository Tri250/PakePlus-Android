package com.batteryhealth.app.data.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryDataManager;

public class BatteryDataWorker extends Worker {

    private static final String TAG = "BatteryDataWorker";

    public BatteryDataWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
            if (info != null) {
                repository.saveBatteryInfo(info);
            }

            // 数据保留期与 BatteryMonitorService 对齐：45 天。
            // 早期版本为 180 天，会导致 battery_info 表无限增长且与前台服务策略冲突。
            long retentionCutoff = System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000;
            repository.deleteOlderThan(retentionCutoff);

            return Result.success();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error saving battery data: " + e.getMessage());
            return Result.retry();
        }
    }
}