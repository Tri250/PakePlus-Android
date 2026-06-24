package com.batteryhealth.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.util.List;

public class BatteryRepositoryImpl implements BatteryRepository {

    private static final String TAG = "BatteryRepositoryImpl";

    private final BatteryDataManager batteryDataManager;
    private final AppDatabase database;
    private final MutableLiveData<BatteryInfo> batteryInfoLiveData = new MutableLiveData<>();

    public BatteryRepositoryImpl(BatteryHealthApplication application) {
        this.batteryDataManager = new BatteryDataManager(application.getApplicationContext());
        this.database = application.getDatabase();
    }

    @Override
    public LiveData<BatteryInfo> observeBatteryInfo() {
        return batteryInfoLiveData;
    }

    @Override
    public BatteryInfo getCurrentBatteryInfo() {
        batteryDataManager.refreshFromStickyIntent();
        BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
        batteryInfoLiveData.postValue(info);
        return info;
    }

    @Override
    public void saveBatteryInfo(BatteryInfo info) {
        if (database != null) {
            new Thread(() -> {
                try {
                    info.setId(0);
                    info.setTimestamp(System.currentTimeMillis());
                    database.batteryInfoDao().insert(info);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error saving battery info: " + e.getMessage());
                }
            }).start();
        }
    }

    @Override
    public List<BatteryInfo> getHistorySince(long timestamp) {
        if (database != null) {
            try {
                return database.batteryInfoDao().getSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting history: " + e.getMessage());
            }
        }
        return List.of();
    }

    @Override
    public int getHistoryCountSince(long timestamp) {
        if (database != null) {
            try {
                return database.batteryInfoDao().getCountSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting history count: " + e.getMessage());
            }
        }
        return 0;
    }

    @Override
    public float getAverageHealthSince(long timestamp) {
        if (database != null) {
            try {
                return database.batteryInfoDao().getAverageHealthSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting average health: " + e.getMessage());
            }
        }
        return 0f;
    }

    @Override
    public void deleteOlderThan(long timestamp) {
        if (database != null) {
            new Thread(() -> {
                try {
                    database.batteryInfoDao().deleteOlderThan(timestamp);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error deleting old data: " + e.getMessage());
                }
            }).start();
        }
    }

    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager;
    }

    @Override
    public void savePerformanceData(PerformanceData data) {
        if (database != null) {
            new Thread(() -> {
                try {
                    database.performanceDataDao().insert(data);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error saving performance data: " + e.getMessage());
                }
            }).start();
        }
    }
}