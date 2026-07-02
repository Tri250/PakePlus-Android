package com.batteryhealth.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.util.List;

public class BatteryRepositoryImpl implements BatteryRepository {

    private static final String TAG = "BatteryRepositoryImpl";

    private final BatteryHealthApplication application;
    private final BatteryDataManager batteryDataManager;
    private AppDatabase database;
    private final MutableLiveData<BatteryInfo> batteryInfoLiveData = new MutableLiveData<>();

    public BatteryRepositoryImpl(BatteryHealthApplication application) {
        this.application = application;
        this.batteryDataManager = new BatteryDataManager(application.getApplicationContext());
    }

    private AppDatabase getDatabase() {
        if (database == null) {
            database = application.getDatabase();
        }
        return database;
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
        AppDatabase db = getDatabase();
        if (db != null) {
            BatteryInfo snapshot = info.copy();
            ThreadExecutor.execute(() -> {
                try {
                    snapshot.setId(0);
                    snapshot.setTimestamp(System.currentTimeMillis());
                    db.batteryInfoDao().insert(snapshot);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error saving battery info: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public List<BatteryInfo> getHistorySince(long timestamp) {
        AppDatabase db = getDatabase();
        if (db != null) {
            try {
                return db.batteryInfoDao().getSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting history: " + e.getMessage());
            }
        }
        return List.of();
    }

    @Override
    public int getHistoryCountSince(long timestamp) {
        AppDatabase db = getDatabase();
        if (db != null) {
            try {
                return db.batteryInfoDao().getCountSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting history count: " + e.getMessage());
            }
        }
        return 0;
    }

    @Override
    public float getAverageHealthSince(long timestamp) {
        AppDatabase db = getDatabase();
        if (db != null) {
            try {
                return db.batteryInfoDao().getAverageHealthSince(timestamp);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error getting average health: " + e.getMessage());
            }
        }
        return 0f;
    }

    @Override
    public void deleteOlderThan(long timestamp) {
        AppDatabase db = getDatabase();
        if (db != null) {
            ThreadExecutor.execute(() -> {
                try {
                    db.batteryInfoDao().deleteOlderThan(timestamp);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error deleting old data: " + e.getMessage());
                }
            });
        }
    }

    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager;
    }

    @Override
    public void savePerformanceData(PerformanceData data) {
        AppDatabase db = getDatabase();
        if (db != null) {
            ThreadExecutor.execute(() -> {
                try {
                    db.performanceDataDao().insert(data);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error saving performance data: " + e.getMessage());
                }
            });
        }
    }
}