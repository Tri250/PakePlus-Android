package com.batteryhealth.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;

import java.util.List;

public interface BatteryRepository {

    LiveData<BatteryInfo> observeBatteryInfo();

    BatteryInfo getCurrentBatteryInfo();

    void saveBatteryInfo(BatteryInfo info);

    List<BatteryInfo> getHistorySince(long timestamp);

    int getHistoryCountSince(long timestamp);

    float getAverageHealthSince(long timestamp);

    void deleteOlderThan(long timestamp);

    void savePerformanceData(PerformanceData data);
}