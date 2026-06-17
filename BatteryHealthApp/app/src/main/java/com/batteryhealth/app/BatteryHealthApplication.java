package com.batteryhealth.app;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.room.Room;

import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.database.DatabaseEncryptionHelper;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.model.PowerHistory;

import net.zetetic.database.sqlcipher.SupportFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * 初始化Room数据库（启用SQLCipher加密）
     */
    private void initDatabase() {
        boolean plainBackupCreated = false;
        DatabaseEncryptionHelper.DatabaseSnapshot snapshot = null;
        try {
            // 1. 若存在旧版明文数据库，在后台线程导出数据并重命名为备份，避免阻塞主线程
            final DatabaseEncryptionHelper.DatabaseSnapshot[] snapshotHolder =
                    new DatabaseEncryptionHelper.DatabaseSnapshot[1];
            final boolean[] backupCreatedHolder = new boolean[1];
            final CountDownLatch readLatch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    snapshotHolder[0] = DatabaseEncryptionHelper.migratePlainDatabaseIfNeeded(this);
                    if (snapshotHolder[0] != null) {
                        backupCreatedHolder[0] = DatabaseEncryptionHelper.renamePlainDatabaseToBackup(this);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error migrating plain database: " + e.getMessage(), e);
                } finally {
                    readLatch.countDown();
                }
            }).start();
            readLatch.await(30, TimeUnit.SECONDS);
            snapshot = snapshotHolder[0];
            plainBackupCreated = backupCreatedHolder[0];

            // 2. 使用 SQLCipher 创建加密数据库
            byte[] passphrase = DatabaseEncryptionHelper.getPassphrase(this);
            SupportFactory factory = new SupportFactory(passphrase);
            database = Room.databaseBuilder(
                            getApplicationContext(),
                            AppDatabase.class,
                            "battery_health_db"
                    )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build();

            // 3. 将历史数据恢复到加密数据库，成功后删除备份
            if (snapshot != null) {
                final CountDownLatch restoreLatch = new CountDownLatch(1);
                final boolean[] restoreSuccess = {true};
                new Thread(() -> {
                    try {
                        restoreSnapshot(database, snapshot);
                    } catch (Exception e) {
                        restoreSuccess[0] = false;
                        Log.e(TAG, "Error restoring database snapshot: " + e.getMessage(), e);
                    } finally {
                        restoreLatch.countDown();
                    }
                }).start();
                boolean restored = restoreLatch.await(60, TimeUnit.SECONDS) && restoreSuccess[0];
                if (restored) {
                    DatabaseEncryptionHelper.deletePlainDatabaseBackup(this);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing encrypted database: " + e.getMessage(), e);
            // 加密数据库初始化失败，尝试从备份恢复明文数据库
            if (plainBackupCreated) {
                DatabaseEncryptionHelper.restorePlainDatabaseFromBackup(this);
            }
            // 回退到明文数据库，避免应用无法启动
            try {
                database = Room.databaseBuilder(
                                getApplicationContext(),
                                AppDatabase.class,
                                "battery_health_db"
                        )
                        .fallbackToDestructiveMigration()
                        .build();
            } catch (Exception e2) {
                Log.e(TAG, "Failed to create plain database: " + e2.getMessage(), e2);
                // 数据库初始化失败，使用内存数据库作为后备
                try {
                    database = Room.inMemoryDatabaseBuilder(
                            getApplicationContext(),
                            AppDatabase.class
                    ).build();
                } catch (Exception e3) {
                    Log.e(TAG, "Failed to create in-memory database: " + e3.getMessage());
                }
            }
        }
    }

    /**
     * 将明文数据库快照恢复到加密数据库（调用方需保证不在主线程）
     */
    private void restoreSnapshot(final AppDatabase db,
                                 final DatabaseEncryptionHelper.DatabaseSnapshot snapshot) {
        if (snapshot == null) return;
        db.runInTransaction(() -> {
            if (snapshot.batteryInfoList != null) {
                for (BatteryInfo info : snapshot.batteryInfoList) {
                    info.setId(0);
                    db.batteryInfoDao().insert(info);
                }
            }
            if (snapshot.performanceDataList != null) {
                for (PerformanceData data : snapshot.performanceDataList) {
                    data.setId(0);
                    db.performanceDataDao().insert(data);
                }
            }
            if (snapshot.powerHistoryList != null) {
                for (PowerHistory history : snapshot.powerHistoryList) {
                    history.setId(0);
                    db.powerHistoryDao().insert(history);
                }
            }
        });
        Log.d(TAG, "Database snapshot restored to encrypted database successfully");
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