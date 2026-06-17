package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用全局管理器（单例）
 * 
 * 解决Fragment获取Manager为null的问题
 * 使用单例模式确保Manager在所有Fragment中都可访问
 */
public class AppManager {
    
    private static volatile AppManager instance;
    
    private BatteryDataManager batteryDataManager;
    private DeviceInfoManager deviceInfoManager;
    
    private final List<Runnable> dataChangeListeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private boolean batteryDataReady = false;
    private boolean deviceDataReady = false;
    
    private AppManager() {}
    
    public static AppManager getInstance() {
        if (instance == null) {
            synchronized (AppManager.class) {
                if (instance == null) {
                    instance = new AppManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化（必须在Application或Activity onCreate中调用）
     */
    public void init(Context context) {
        if (context == null) return;
        
        if (batteryDataManager == null) {
            try {
                batteryDataManager = new BatteryDataManager(context);
                batteryDataManager.setOnDataChangedCallback(() -> onBatteryDataChanged());
                // 同步加载的基础数据已可用
                batteryDataReady = true;
                // 启动异步读取高级数据
                batteryDataManager.readCycleCountAsync();
                batteryDataManager.readBatteryCapacityAsync();
                batteryDataManager.detectBatterySourceAsync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (deviceInfoManager == null) {
            try {
                deviceInfoManager = new DeviceInfoManager(context);
                deviceDataReady = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 电池数据变化回调
     */
    private void onBatteryDataChanged() {
        batteryDataReady = true;
        notifyDataChanged();
    }
    
    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager;
    }
    
    public DeviceInfoManager getDeviceInfoManager() {
        return deviceInfoManager;
    }
    
    /**
     * 添加数据变更监听
     * 如果数据已经准备好，会立即触发一次回调
     */
    public void addDataChangeListener(Runnable listener) {
        if (listener == null) return;
        synchronized (dataChangeListeners) {
            if (!dataChangeListeners.contains(listener)) {
                dataChangeListeners.add(listener);
            }
        }
        // 如果数据已就绪，立即触发一次，避免监听器错过初始化通知
        if (batteryDataReady || deviceDataReady) {
            mainHandler.post(() -> {
                try {
                    listener.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
    
    /**
     * 移除数据变更监听
     */
    public void removeDataChangeListener(Runnable listener) {
        synchronized (dataChangeListeners) {
            dataChangeListeners.remove(listener);
        }
    }
    
    /**
     * 通知所有监听器
     */
    private void notifyDataChanged() {
        mainHandler.post(() -> {
            List<Runnable> listeners;
            synchronized (dataChangeListeners) {
                listeners = new ArrayList<>(dataChangeListeners);
            }
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * 主动触发刷新
     */
    public void refreshAll() {
        notifyDataChanged();
    }
    
    /**
     * 数据是否已准备好
     */
    public boolean isDataReady() {
        return batteryDataReady || deviceDataReady;
    }
}
