package com.batteryhealth.app.domain.repository;

import com.batteryhealth.app.data.model.DeviceConfig;

public interface DeviceRepository {

    DeviceConfig getDeviceConfig();

    int getDesignCapacity();

    int getTypicalChargePower();

    int getUsageDays();
}