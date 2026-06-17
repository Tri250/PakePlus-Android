package com.batteryhealth.app;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.room.Room;

import com.batteryhealth.app.data.database.AppDatabase;

/**
 * 电池健康应用全局Application类
 * 
 * 功能：
 * 1. 初始化全局配置
 * 2. 管理数据库实例
 * 3. 提供全局Context访问
 */
public class BatteryHealthApplication extends Application {
    
    private static final String TAG = "BatteryHealthApp";
    private static BatteryHealthApplication instance;
    private AppDatabase database;
    private Handler mainHandler;
    
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            instance = this;
            mainHandler = new Handler(Looper.getMainLooper());
            
            // 初始化数据库
            initDatabase();
        } catch (Exception e) {
            Log.e(TAG, "Error in Application onCreate: " + e.getMessage(), e);
        }
    }
    
    /**
     * 初始化Room数据库
     */
    private void initDatabase() {
        try {
            database = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class,
                    "battery_health_db"
            )
            .fallbackToDestructiveMigration()
            .build();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing database: " + e.getMessage(), e);
            // 数据库初始化失败，使用内存数据库作为后备
            try {
                database = Room.inMemoryDatabaseBuilder(
                        getApplicationContext(),
                        AppDatabase.class
                ).build();
            } catch (Exception e2) {
                Log.e(TAG, "Failed to create in-memory database: " + e2.getMessage());
            }
        }
    }
    
    /**
     * 获取全局Application实例
     */
    public static BatteryHealthApplication getInstance() {
        return instance;
    }
    
    /**
     * 获取数据库实例
     */
    public AppDatabase getDatabase() {
        return database;
    }
    
    /**
     * 获取主线程Handler
     */
    public Handler getMainHandler() {
        return mainHandler;
    }
    
    /**
     * 在主线程执行Runnable
     */
    public void runOnUiThread(Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        }
    }
    
    /**
     * 延迟执行
     */
    public void postDelayed(Runnable runnable, long delayMillis) {
        if (mainHandler != null) {
            mainHandler.postDelayed(runnable, delayMillis);
        }
    }
}