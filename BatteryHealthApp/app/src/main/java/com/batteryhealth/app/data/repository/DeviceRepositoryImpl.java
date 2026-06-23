package com.batteryhealth.app.data.repository;

import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.utils.ActivationDateHelper;
import com.batteryhealth.app.utils.DeviceDatabaseManager;
import com.batteryhealth.app.utils.DeviceInfoManager;

public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceInfoManager deviceInfoManager;
    private final DeviceDatabaseManager deviceDatabaseManager;

    public DeviceRepositoryImpl(DeviceInfoManager deviceInfoManager) {
        this.deviceInfoManager = deviceInfoManager;
        this.deviceDatabaseManager = DeviceDatabaseManager.getInstance(deviceInfoManager.getContext());
    }

    @Override
    public DeviceConfig getDeviceConfig() {
        return deviceInfoManager.getDeviceConfig();
    }

    @Override
    public int getDesignCapacity() {
        return deviceDatabaseManager.getDesignCapacity();
    }

    @Override
    public int getTypicalChargePower() {
        return deviceDatabaseManager.getTypicalChargePower();
    }

    @Override
    public int getUsageDays() {
        ActivationDateHelper.Result activation = deviceInfoManager.getActivationInfo();
        return activation != null ? activation.usageDays : -1;
    }
}