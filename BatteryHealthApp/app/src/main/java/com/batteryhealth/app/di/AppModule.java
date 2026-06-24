package com.batteryhealth.app.di;

import android.content.Context;

import androidx.room.Room;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.repository.BatteryRepositoryImpl;
import com.batteryhealth.app.data.repository.DeviceRepositoryImpl;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;
import com.batteryhealth.app.domain.usecase.CalculateHealthUseCase;
import com.batteryhealth.app.domain.usecase.DetermineBatterySourceUseCase;
import com.batteryhealth.app.domain.usecase.GetTrendDataUseCase;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceInfoManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public BatteryDataManager provideBatteryDataManager(@ApplicationContext Context context) {
        return new BatteryDataManager(context);
    }

    @Provides
    @Singleton
    public DeviceInfoManager provideDeviceInfoManager(@ApplicationContext Context context) {
        return new DeviceInfoManager(context);
    }

    @Provides
    @Singleton
    public BatteryRepository provideBatteryRepository(BatteryHealthApplication app) {
        return new BatteryRepositoryImpl(app);
    }

    @Provides
    @Singleton
    public DeviceRepository provideDeviceRepository(DeviceInfoManager deviceInfoManager) {
        return new DeviceRepositoryImpl(deviceInfoManager);
    }

    @Provides
    @Singleton
    public CalculateHealthUseCase provideCalculateHealthUseCase(
            BatteryRepository batteryRepository,
            DeviceRepository deviceRepository) {
        return new CalculateHealthUseCase(batteryRepository, deviceRepository);
    }

    @Provides
    @Singleton
    public DetermineBatterySourceUseCase provideDetermineBatterySourceUseCase(
            DeviceRepository deviceRepository,
            BatteryDataManager batteryDataManager) {
        return new DetermineBatterySourceUseCase(deviceRepository, batteryDataManager);
    }

    @Provides
    @Singleton
    public GetTrendDataUseCase provideGetTrendDataUseCase(BatteryRepository batteryRepository) {
        return new GetTrendDataUseCase(batteryRepository);
    }
}