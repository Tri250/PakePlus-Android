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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.PowerHistory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 充电监测服务
 * 
 * 功能：
 * 1. 实时监测充电功率
 * 2. 记录充电曲线
 * 3. 识别充电阶段
 * 4. 生成充电报告
 */
public class ChargingMonitorService extends Service {
    
    private static final String TAG = "ChargingMonitorService";
    private static final String CHANNEL_ID = "charging_monitor_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final long UPDATE_INTERVAL = 3000; // 3秒更新一次
    
    private Handler handler;
    private String currentSessionId;
    private boolean isCharging = false;
    private long chargingStartTime;
    
    // 充电统计数据
    private float maxPower = 0;
    private float avgPower = 0;
    private int powerSampleCount = 0;
    private float totalPower = 0;
    
    private OnChargingDataListener dataListener;
    
    // 电池广播接收器
    private BroadcastReceiver chargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                startChargingSession();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                endChargingSession();
            }
        }
    };
    
    // 定时更新任务
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (isCharging) {
                readChargingPower();
                updateNotification();
                if (dataListener != null) {
                    dataListener.onChargingDataUpdated(getCurrentPowerHistory());
                }
            }
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };
    
    public interface OnChargingDataListener {
        void onChargingDataUpdated(PowerHistory history);
        void onChargingSessionStarted(String sessionId);
        void onChargingSessionEnded(String sessionId, ChargingSummary summary);
    }
    
    /**
     * 充电摘要数据
     */
    public static class ChargingSummary {
        public String sessionId;
        public long startTime;
        public long endTime;
        public long duration; // 充电时长(毫秒)
        public int startLevel;
        public int endLevel;
        public float maxPower;
        public float avgPower;
        public float totalEnergy; // 充电能量 (Wh)
        
        public ChargingSummary() {
            this.startTime = System.currentTimeMillis();
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        registerChargingReceiver();
        
        // 启动定时更新
        handler.post(updateTask);
        
        // 检查当前是否在充电
        checkChargingStatus();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
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
        try {
            unregisterReceiver(chargingReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
        if (handler != null) {
            handler.removeCallbacks(updateTask);
        }
    }
    
    /**
     * 注册充电广播接收器
     */
    private void registerChargingReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            
            // Android 14+ 需要指定导出标志
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(chargingReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(chargingReceiver, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering charging receiver: " + e.getMessage());
        }
    }
    
    /**
     * 检查充电状态
     */
    private void checkChargingStatus() {
        Intent batteryStatus = getBatteryIntent();
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
            
            if (isCharging) {
                startChargingSession();
            }
        }
    }
    
    /**
     * 安全获取电池sticky intent（兼容Android 14+）
     */
    private Intent getBatteryIntent() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                return registerReceiver(null, filter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery intent: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 开始充电会话
     */
    private void startChargingSession() {
        if (isCharging) return;
        
        isCharging = true;
        currentSessionId = UUID.randomUUID().toString();
        chargingStartTime = System.currentTimeMillis();
        
        // 重置统计数据
        maxPower = 0;
        avgPower = 0;
        powerSampleCount = 0;
        totalPower = 0;
        
        Log.d(TAG, "Charging session started: " + currentSessionId);
        
        if (dataListener != null) {
            dataListener.onChargingSessionStarted(currentSessionId);
        }
        
        updateNotification();
    }
    
    /**
     * 结束充电会话
     */
    private void endChargingSession() {
        if (!isCharging) return;
        
        isCharging = false;
        
        // 生成充电摘要
        ChargingSummary summary = new ChargingSummary();
        summary.sessionId = currentSessionId;
        summary.startTime = chargingStartTime;
        summary.endTime = System.currentTimeMillis();
        summary.duration = summary.endTime - summary.startTime;
        summary.maxPower = maxPower;
        summary.avgPower = powerSampleCount > 0 ? totalPower / powerSampleCount : 0;
        
        Log.d(TAG, "Charging session ended: " + currentSessionId);
        
        if (dataListener != null) {
            dataListener.onChargingSessionEnded(currentSessionId, summary);
        }
        
        currentSessionId = null;
        updateNotification();
    }
    
    /**
     * 读取充电功率
     */
    private void readChargingPower() {
        try {
            PowerHistory history = new PowerHistory();
            history.setSessionId(currentSessionId);
            
            // 读取电压
            float voltage = readVoltage();
            history.setVoltage(voltage);
            
            // 读取电流
            float current = readCurrent();
            history.setCurrent(current);
            
            // 计算功率
            history.calculatePower();
            
            // 读取电池电量
            history.setBatteryLevel(readBatteryLevel());
            
            // 读取电池温度
            history.setBatteryTemp(readBatteryTemperature());
            
            // 判断充电阶段
            history.setChargingPhase(detectChargingPhase(history));
            
            // 判断充电类型
            history.setChargeType(detectChargeType(history.getPower()));
            
            // 更新统计数据
            float power = history.getPower();
            if (power > maxPower) {
                maxPower = power;
            }
            totalPower += power;
            powerSampleCount++;
            
            // 保存到数据库
            savePowerHistory(history);
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading charging power: " + e.getMessage());
        }
    }
    
    /**
     * 读取电压
     */
    private float readVoltage() {
        try {
            File voltageFile = new File("/sys/class/power_supply/battery/voltage_now");
            if (voltageFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(voltageFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    long voltageUv = Long.parseLong(line.trim());
                    return voltageUv / 1000000.0f; // 转换为V
                }
            }
            
            // 尝试从BatteryManager读取
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                Intent batteryStatus = getBatteryIntent();
                if (batteryStatus != null) {
                    int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    if (voltageMv != -1) {
                        return voltageMv / 1000.0f; // 转换为V
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading voltage: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 读取电流
     */
    private float readCurrent() {
        try {
            File currentFile = new File("/sys/class/power_supply/battery/current_now");
            if (currentFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(currentFile));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    long currentUa = Long.parseLong(line.trim());
                    return Math.abs(currentUa) / 1000000.0f; // 转换为A，取绝对值
                }
            }
            
            // 尝试从BatteryManager读取
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                return Math.abs(currentUa) / 1000000.0f; // 转换为A
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading current: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 读取电池电量
     */
    private int readBatteryLevel() {
        try {
            Intent batteryStatus = getBatteryIntent();
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level != -1 && scale != -1) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery level: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 读取电池温度
     */
    private float readBatteryTemperature() {
        try {
            Intent batteryStatus = getBatteryIntent();
            if (batteryStatus != null) {
                int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temperature != -1) {
                    return temperature / 10.0f; // 转换为°C
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery temperature: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 检测充电阶段
     */
    private String detectChargingPhase(PowerHistory history) {
        int level = history.getBatteryLevel();
        float power = history.getPower();
        
        if (level >= 99) {
            return "full";
        } else if (level >= 80) {
            return "constant_voltage";
        } else if (power > 5) {
            return "constant_current";
        } else {
            return "trickle";
        }
    }
    
    /**
     * 检测充电类型
     */
    private String detectChargeType(float power) {
        if (power >= 60) {
            return "super";
        } else if (power >= 18) {
            return "fast";
        } else if (power >= 5) {
            return "normal";
        } else {
            return "wireless";
        }
    }
    
    /**
     * 保存功率历史记录
     */
    private void savePowerHistory(PowerHistory history) {
        new Thread(() -> {
            try {
                com.batteryhealth.app.BatteryHealthApplication app = 
                    (com.batteryhealth.app.BatteryHealthApplication) getApplicationContext();
                com.batteryhealth.app.data.database.AppDatabase db = app.getDatabase();
                if (db != null) {
                    db.powerHistoryDao().insert(history);
                    Log.d(TAG, "Power history saved: " + history.getPower() + "W");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving power history: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 获取当前功率历史记录
     */
    private PowerHistory getCurrentPowerHistory() {
        PowerHistory history = new PowerHistory();
        history.setSessionId(currentSessionId);
        history.setVoltage(readVoltage());
        history.setCurrent(readCurrent());
        history.calculatePower();
        history.setBatteryLevel(readBatteryLevel());
        history.setBatteryTemp(readBatteryTemperature());
        return history;
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "充电监测",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("实时监测充电功率");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * 构建通知
     */
    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        
        String content;
        if (isCharging) {
            PowerHistory history = getCurrentPowerHistory();
            content = String.format("充电中: %.1fW | 电量: %d%% | %s",
                    history.getPower(),
                    history.getBatteryLevel(),
                    history.getChargeTypeDescription());
        } else {
            content = "未在充电";
        }
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("充电监测")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_charging)
                .setContentIntent(pendingIntent)
                .setOngoing(isCharging)
                .setOnlyAlertOnce(true)
                .build();
    }
    
    /**
     * 更新通知
     */
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }
    
    /**
     * 设置数据监听器
     */
    public void setDataListener(OnChargingDataListener listener) {
        this.dataListener = listener;
    }
    
    /**
     * 获取当前会话ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }
    
    /**
     * 检查是否在充电
     */
    public boolean isCharging() {
        return isCharging;
    }
}