package com.batteryhealth.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 电池监测服务
 * 
 * 功能：
 * 1. 实时监测电池容量、温度、电压、电流
 * 2. 读取充电循环次数
 * 3. 判断电池来源（原装/第三方）
 * 4. 发送前台通知显示电池状态
 */
public class BatteryMonitorService extends Service {
    
    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "battery_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long UPDATE_INTERVAL = 5000; // 5秒更新一次
    private static final long SAVE_INTERVAL = 60000; // 60秒保存一次到数据库
    private static final long DATA_CLEANUP_INTERVAL = 86400000; // 24小时清理一次旧数据

    // 健康度衰减预警配置
    private static final String HEALTH_ALERT_CHANNEL_ID = "battery_health_alert_channel";
    private static final int HEALTH_ALERT_NOTIFICATION_ID = 1002;
    private static final long HEALTH_CHECK_INTERVAL = 24L * 60 * 60 * 1000; // 24小时
    private static final long MIN_TIME_BETWEEN_ALERTS = 7L * 24 * 60 * 60 * 1000; // 7天
    private static final int MIN_RECORDS_FOR_DEGRADATION = 10;
    private static final float DEFAULT_DEGRADATION_THRESHOLD = 2.0f;
    public static final String PREFS_NAME = "battery_health_prefs";
    public static final String PREF_ALERT_ENABLED = "health_alert_enabled";
    public static final String PREF_LAST_ALERT_TIME = "last_health_alert_time";
    public static final String PREF_DEGRADATION_THRESHOLD = "degradation_threshold";

    private Handler handler;
    private BatteryInfo currentBatteryInfo;
    private OnBatteryDataListener dataListener;
    private boolean isRunning = false;
    private long lastSaveTime = 0;
    private SharedPreferences prefs;
    private boolean healthCheckScheduled = false;
    private BatteryDataManager batteryDataManager;
    
    // 电池广播接收器
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryData(intent);
        }
    };
    
    // 定时更新任务
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            
            try {
                // 统一使用 BatteryDataManager 读取（含机型数据库校准、多路径 sysfs、BatteryManager）
                if (batteryDataManager != null) {
                    batteryDataManager.refreshFromStickyIntent();
                    currentBatteryInfo = batteryDataManager.getCurrentBatteryInfo();
                }
                if (dataListener != null && currentBatteryInfo != null) {
                    dataListener.onBatteryDataUpdated(currentBatteryInfo);
                }
                updateNotification();

                // 定时保存数据到数据库
                long now = System.currentTimeMillis();
                if (now - lastSaveTime >= SAVE_INTERVAL) {
                    saveBatteryData();
                    lastSaveTime = now;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in update task: " + e.getMessage());
            }
            
            if (handler != null) {
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };

    // 健康度衰减预警检查任务（每24小时执行一次）
    private Runnable healthCheckTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            
            try {
                checkHealthDegradation();
            } catch (Exception e) {
                Log.e(TAG, "Error in health check task: " + e.getMessage());
            }
            
            if (handler != null) {
                handler.postDelayed(this, HEALTH_CHECK_INTERVAL);
            }
        }
    };
    
    public interface OnBatteryDataListener {
        void onBatteryDataUpdated(BatteryInfo info);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            handler = new Handler(Looper.getMainLooper());
            batteryDataManager = new BatteryDataManager(this);
            batteryDataManager.refreshFromStickyIntent();
            currentBatteryInfo = batteryDataManager.getCurrentBatteryInfo();
            if (currentBatteryInfo == null) {
                currentBatteryInfo = new BatteryInfo();
            }
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            createNotificationChannel();
            createHealthAlertChannel();
            registerBatteryReceiver();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage());
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (!isRunning) {
                isRunning = true;
                startForeground(NOTIFICATION_ID, buildNotification());
                
                // 启动定时更新
                if (handler != null) {
                    handler.post(updateTask);
                }

                // 启动健康度衰减预警检查（延迟1分钟，待数据稳定后执行）
                if (handler != null && !healthCheckScheduled) {
                    healthCheckScheduled = true;
                    handler.postDelayed(healthCheckTask, 60_000);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage());
        }
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            // 接收器可能未注册
        }
        if (handler != null) {
            handler.removeCallbacks(updateTask);
            handler.removeCallbacks(healthCheckTask);
        }
    }

    /**
     * 注册电池广播接收器
     */
    private void registerBatteryReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            
            // Android 14+ 需要指定导出标志
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering receiver: " + e.getMessage());
        }
    }
    
    /**
     * 更新电池数据
     */
    private void updateBatteryData(Intent intent) {
        if (intent == null) return;

        try {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                // 统一由 BatteryDataManager 解析完整电池信息，避免本服务与核心逻辑重复/不一致
                if (batteryDataManager != null) {
                    batteryDataManager.refreshFromStickyIntent();
                    BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                    if (info != null) {
                        currentBatteryInfo = info;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery data: " + e.getMessage());
        }
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.battery_monitor_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.battery_monitor_channel_description));
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage());
            }
        }
    }

    /**
     * 创建健康度衰减预警通知渠道
     */
    private void createHealthAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        HEALTH_ALERT_CHANNEL_ID,
                        getString(R.string.health_alert_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(getString(R.string.health_alert_channel_description));
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating health alert channel: " + e.getMessage());
            }
        }
    }

    /**
     * 检查电池健康度是否出现显著衰减（基于近30天历史均值）
     */
    private void checkHealthDegradation() {
        if (prefs == null) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        boolean enabled = prefs.getBoolean(PREF_ALERT_ENABLED, true);
        if (!enabled) {
            return;
        }

        final float threshold = prefs.getFloat(PREF_DEGRADATION_THRESHOLD, DEFAULT_DEGRADATION_THRESHOLD);
        final float currentHealth = currentBatteryInfo.getHealthPercentage();
        if (currentHealth <= 0.0f || currentHealth > 100.0f) {
            return;
        }

        final long now = System.currentTimeMillis();
        long lastAlertTime = prefs.getLong(PREF_LAST_ALERT_TIME, 0);
        if (now - lastAlertTime < MIN_TIME_BETWEEN_ALERTS) {
            return;
        }

        // 数据库查询在后台线程执行，避免阻塞服务主线程
        new Thread(() -> {
            try {
                BatteryHealthApplication app = (BatteryHealthApplication) getApplicationContext();
                AppDatabase db = app.getDatabase();
                if (db == null) {
                    return;
                }

                long monthAgo = now - 30L * 24 * 60 * 60 * 1000;
                int recordCount = db.batteryInfoDao().getCountSince(monthAgo);
                if (recordCount < MIN_RECORDS_FOR_DEGRADATION) {
                    return;
                }

                float averageHealth = db.batteryInfoDao().getAverageHealthSince(monthAgo);
                if (averageHealth <= 0.0f) {
                    return;
                }

                float drop = averageHealth - currentHealth;
                if (drop >= threshold) {
                    sendHealthAlertNotification(drop, averageHealth, currentHealth);
                    prefs.edit().putLong(PREF_LAST_ALERT_TIME, now).apply();
                    Log.d(TAG, "Health degradation alert sent: drop=" + drop + "%");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking health degradation: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 发送健康度衰减预警通知
     */
    private void sendHealthAlertNotification(float drop, float historicalHealth, float currentHealth) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 1, intent, PendingIntent.FLAG_IMMUTABLE
            );

            String content = getString(
                    R.string.health_alert_content,
                    drop,
                    historicalHealth,
                    currentHealth
            );

            Notification notification = new NotificationCompat.Builder(this, HEALTH_ALERT_CHANNEL_ID)
                    .setContentTitle(getString(R.string.health_alert_title))
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_battery_health)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .build();

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(HEALTH_ALERT_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending health alert notification: " + e.getMessage());
        }
    }

    /**
     * 构建通知
     */
    private Notification buildNotification() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            );
            
            String content = String.format(
                    getString(R.string.battery_monitor_notification_content),
                    currentBatteryInfo.getLevel(),
                    currentBatteryInfo.getTemperature(),
                    currentBatteryInfo.getHealthPercentage());

            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.battery_monitor_notification_title))
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_battery)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error building notification: " + e.getMessage());
            // 返回一个基本通知
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.battery_monitor_channel_name))
                    .setContentText(getString(R.string.battery_monitor_notification_fallback))
                    .setSmallIcon(R.drawable.ic_battery)
                    .build();
        }
    }
    
    /**
     * 更新通知
     */
    private void updateNotification() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification: " + e.getMessage());
        }
    }
    
    /**
     * 设置数据监听器
     */
    public void setDataListener(OnBatteryDataListener listener) {
        this.dataListener = listener;
    }
    
    /**
     * 获取当前电池信息
     */
    public BatteryInfo getCurrentBatteryInfo() {
        return currentBatteryInfo;
    }
    
    /**
     * 保存电池数据到数据库
     */
    private void saveBatteryData() {
        new Thread(() -> {
            try {
                com.batteryhealth.app.BatteryHealthApplication app =
                    (com.batteryhealth.app.BatteryHealthApplication) getApplicationContext();
                com.batteryhealth.app.data.database.AppDatabase db = app.getDatabase();
                if (db != null && currentBatteryInfo != null) {
                    // 每次保存生成新记录，避免复用对象导致主键冲突
                    BatteryInfo record = currentBatteryInfo;
                    record.setId(0);
                    record.setTimestamp(System.currentTimeMillis());
                    record.setDeviceModel(android.os.Build.MODEL);
                    record.setDeviceBrand(android.os.Build.BRAND);

                    db.batteryInfoDao().insert(record);
                    Log.d(TAG, "Battery data saved: level=" + record.getLevel() + "% health=" + record.getHealthPercentage() + "%");

                    // 清理30天前的旧数据
                    long thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                    db.batteryInfoDao().deleteOlderThan(thirtyDaysAgo);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving battery data: " + e.getMessage());
            }
        }).start();
    }
}