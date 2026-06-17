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
        if (batteryDataManager == null) {
            try {
                batteryDataManager = new BatteryDataManager(context);
                batteryDataManager.setOnDataChangedCallback(() -> notifyDataChanged());
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public BatteryDataManager getBatteryDataManager() {
        if (batteryDataManager == null) {
            // 尝试从系统上下文恢复
            return null;
        }
        return batteryDataManager;
    }
    
    public DeviceInfoManager getDeviceInfoManager() {
        if (deviceInfoManager == null) {
            return null;
        }
        return deviceInfoManager;
    }
    
    /**
     * 添加数据变更监听
     */
    public void addDataChangeListener(Runnable listener) {
        if (listener != null && !dataChangeListeners.contains(listener)) {
            dataChangeListeners.add(listener);
        }
    }
    
    /**
     * 移除数据变更监听
     */
    public void removeDataChangeListener(Runnable listener) {
        dataChangeListeners.remove(listener);
    }
    
    /**
     * 通知所有监听器
     */
    private void notifyDataChanged() {
        mainHandler.post(() -> {
            for (Runnable listener : new ArrayList<>(dataChangeListeners)) {
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
}
