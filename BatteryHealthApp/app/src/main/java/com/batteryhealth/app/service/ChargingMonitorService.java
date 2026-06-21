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
import androidx.core.app.ServiceCompat;

import com.batteryhealth.app.BuildConfigHelper;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.service.BatteryMonitorService;

import java.io.BufferedReader;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final int NOTIFICATION_ID = 1003;
    private static final long UPDATE_INTERVAL = 3000; // 3秒更新一次

    private Handler handler;
    private ExecutorService executor;
    private String currentSessionId;
    private volatile boolean isCharging = false;
    private boolean foregroundStarted = false;
    private long chargingStartTime;
    private SharedPreferences prefs;

    // 充电统计数据
    private float maxPower = 0;
    private float avgPower = 0;
    private int powerSampleCount = 0;
    private float totalPower = 0;

    // 缓存最近一次成功读取的功率信息（供前台通知等主线程调用使用，避免在主线程读 sysfs）
    private volatile float cachedVoltage = 0f;
    private volatile float cachedCurrent = 0f;
    private volatile int cachedLevel = 0;
    private volatile float cachedTemp = 0f;
    private volatile String cachedChargeType = "none";

    // 用于智能充电阶段判断的滑动窗口（最近 30 个采样点，约 90 秒）
    private static final int MAX_SAMPLES = 30;
    private final LinkedList<PowerSample> powerSamples = new LinkedList<>();

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

    // 定时更新任务：UI 调度在 Handler，IO 与数据库操作下沉到 executor
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (isCharging && executor != null) {
                executor.submit(() -> {
                    PowerHistory history = readChargingPower();
                    if (history != null) {
                        handler.post(() -> {
                            if (!isCharging) return;
                            if (isNotificationEnabled()) {
                                updateNotification(history);
                            }
                            if (dataListener != null) {
                                dataListener.onChargingDataUpdated(history);
                            }
                        });
                    }
                });
            }
            if (handler != null) {
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
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

    /**
     * 充电采样点，用于 dI/dt、dV/dt 分析。
     */
    private static class PowerSample {
        long timestamp;
        float voltage;
        float current;
        float power;
        int level;

        PowerSample(long timestamp, float voltage, float current, float power, int level) {
            this.timestamp = timestamp;
            this.voltage = voltage;
            this.current = current;
            this.power = power;
            this.level = level;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("charging-io"));
        prefs = getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE);

        createNotificationChannel();
        registerChargingReceiver();

        // 启动定时更新
        handler.post(updateTask);

        // 检查当前是否在充电
        checkChargingStatus();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && "STOP_FOREGROUND".equals(intent.getAction())) {
                if (foregroundStarted) {
                    stopForeground(true);
                    foregroundStarted = false;
                }
                return START_STICKY;
            }

            // 仅在充电时提升为前台服务，避免未充电时显示常驻通知
            updateForegroundState();
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e instanceof android.app.ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowedException: cannot start foreground from background", e);
            } else {
                Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
            }
        }
        return START_STICKY;
    }

    /**
     * 检查通知设置是否开启
     */
    private boolean isNotificationEnabled() {
        if (prefs == null) {
            prefs = getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE);
        }
        return prefs.getBoolean(BatteryMonitorService.PREF_ALERT_ENABLED, true);
    }

    /**
     * 根据充电状态更新前台服务状态
     */
    private void updateForegroundState() {
        boolean showNotification = isNotificationEnabled();
        if (isCharging && !foregroundStarted && showNotification) {
            int foregroundType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                foregroundType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType);
            foregroundStarted = true;
        } else if (!isCharging && foregroundStarted) {
            stopForeground(true);
            foregroundStarted = false;
        } else if (isCharging && foregroundStarted && !showNotification) {
            stopForeground(true);
            foregroundStarted = false;
        }
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
        if (executor != null) {
            executor.shutdown();
            executor = null;
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
    private synchronized void startChargingSession() {
        if (isCharging) return;

        isCharging = true;
        currentSessionId = UUID.randomUUID().toString();
        chargingStartTime = System.currentTimeMillis();

        // 重置统计数据
        maxPower = 0;
        avgPower = 0;
        powerSampleCount = 0;
        totalPower = 0;
        powerSamples.clear();

        Log.d(TAG, "Charging session started: " + currentSessionId);

        if (dataListener != null) {
            dataListener.onChargingSessionStarted(currentSessionId);
        }

        // 充电开始时提升为前台服务
        updateForegroundState();
        if (isNotificationEnabled()) {
            updateNotification();
        }
    }

    /**
     * 结束充电会话
     */
    private synchronized void endChargingSession() {
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

        // 发送充电完成本地通知
        sendChargingCompleteNotification(summary);

        // 发送广播供 UI 层接收
        Intent broadcast = new Intent("com.batteryhealth.app.CHARGING_COMPLETED");
        broadcast.putExtra("session_id", summary.sessionId);
        broadcast.putExtra("duration", summary.duration);
        broadcast.putExtra("max_power", summary.maxPower);
        broadcast.putExtra("avg_power", summary.avgPower);
        sendBroadcast(broadcast);

        currentSessionId = null;
        // 充电结束时退出前台服务，避免未充电时显示常驻通知
        updateForegroundState();
        if (isNotificationEnabled()) {
            updateNotification();
        }
    }

    private void sendChargingCompleteNotification(ChargingSummary summary) {
        try {
            if (!isNotificationEnabled()) return;
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            String channelId = "charging_complete_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "充电完成提醒",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("充电完成时发送通知");
                manager.createNotificationChannel(channel);
            }

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            );

            String content = String.format(Locale.getDefault(),
                    "本次充电耗时 %d 分钟，平均功率 %.1f W，峰值功率 %.1f W",
                    summary.duration / (1000 * 60),
                    summary.avgPower,
                    summary.maxPower);

            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("充电完成")
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_charging)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build();

            manager.notify(NOTIFICATION_ID + 1, notification);
        } catch (Exception e) {
            Log.e(TAG, "Error sending charging complete notification: " + e.getMessage());
        }
    }

    /**
     * 读取充电功率并生成历史记录（应在后台线程调用）。
     *
     * @return 生成的 {@link PowerHistory}，失败时返回 null
     */
    private PowerHistory readChargingPower() {
        try {
            PowerHistory history = new PowerHistory();
            history.setSessionId(currentSessionId);
            history.setTimestamp(System.currentTimeMillis());

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

            // 记录采样点用于智能阶段判断
            addPowerSample(voltage, current, history.getPower(), history.getBatteryLevel());

            // 判断充电阶段
            history.setChargingPhase(detectChargingPhase(history));

            // 判断充电类型
            history.setChargeType(detectChargeType(history.getPower()));

            // 更新缓存（供主线程通知使用）
            cachedVoltage = voltage;
            cachedCurrent = current;
            cachedLevel = history.getBatteryLevel();
            cachedTemp = history.getBatteryTemp();
            cachedChargeType = history.getChargeType();

            // 更新统计数据
            float power = history.getPower();
            if (power > maxPower) {
                maxPower = power;
            }
            totalPower += power;
            powerSampleCount++;

            // 保存到数据库
            savePowerHistory(history);

            return history;
        } catch (Exception e) {
            Log.e(TAG, "Error reading charging power: " + e.getMessage());
            return null;
        }
    }

    /**
     * 读取电压（单位：V）
     * 带合理性校验：正常手机电池电压 2.5-5.0 V
     */
    private float readVoltage() {
        // 1. 尝试从 BatteryManager 读取（最可靠）
        try {
            Intent batteryStatus = getBatteryIntent();
            if (batteryStatus != null) {
                int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                // 部分国产设备返回 µV，需转换
                if (voltageMv > 10000) voltageMv = voltageMv / 1000;
                if (voltageMv >= 2500 && voltageMv <= 5000) {
                    return voltageMv / 1000.0f;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading voltage from BatteryManager: " + e.getMessage());
        }

        // 2. sysfs 回退
        File voltageFile = new File("/sys/class/power_supply/battery/voltage_now");
        if (voltageFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(voltageFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    long voltageRaw = Long.parseLong(line.trim());
                    // sysfs voltage_now 通常返回 µV
                    if (voltageRaw > 1000000) {
                        return voltageRaw / 1000000.0f;
                    } else if (voltageRaw > 2500) {
                        // 部分设备返回 mV
                        return voltageRaw / 1000.0f;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading voltage from sysfs: " + e.getMessage());
            }
        }

        return 0;
    }

    /**
     * 读取电流（单位：A，取绝对值）
     * 带单位判断：部分设备返回 mA 而非 µA。
     */
    private float readCurrent() {
        File currentFile = new File("/sys/class/power_supply/battery/current_now");
        if (currentFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    long currentRaw = Long.parseLong(line.trim());
                    long absCurrent = Math.abs(currentRaw);
                    // sysfs current_now 通常返回 µA
                    if (absCurrent > 100000) {
                        return absCurrent / 1000000.0f; // µA → A
                    } else if (absCurrent > 0) {
                        return absCurrent / 1000.0f; // mA → A
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading current from sysfs: " + e.getMessage());
            }
        }

        // 尝试从 BatteryManager 读取
        try {
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentUa != Integer.MIN_VALUE && currentUa != 0) {
                    int absCurrent = Math.abs(currentUa);
                    if (absCurrent > 100000) {
                        return absCurrent / 1000000.0f; // µA → A
                    } else if (absCurrent > 0) {
                        return absCurrent / 1000.0f; // mA → A
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading current from BatteryManager: " + e.getMessage());
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
     * 记录充电采样点，维护固定长度滑动窗口。
     */
    private void addPowerSample(float voltage, float current, float power, int level) {
        long now = System.currentTimeMillis();
        powerSamples.addLast(new PowerSample(now, voltage, current, power, level));
        while (powerSamples.size() > MAX_SAMPLES) {
            powerSamples.removeFirst();
        }
    }

    /**
     * 智能检测充电阶段。
     *
     * 策略：
     * 1. 电量 >= 99% -> 充满/涓流（trickle/full）
     * 2. 电量 >= 80% 且电流明显下降（dI/dt 负向）-> 恒压阶段（constant_voltage）
     * 3. 大功率稳定输出 -> 恒流阶段（constant_current）
     * 4. 低功率且电量低 -> 涓流（trickle）
     */
    private String detectChargingPhase(PowerHistory history) {
        int level = history.getBatteryLevel();
        float power = history.getPower();

        if (level >= 99) {
            return "full";
        }

        // 当样本足够时，计算电流变化趋势和电压变化趋势
        if (powerSamples.size() >= 10) {
            PowerSample first = powerSamples.getFirst();
            PowerSample last = powerSamples.getLast();
            long timeDiff = last.timestamp - first.timestamp; // ms
            if (timeDiff > 10_000) { // 至少 10 秒数据
                float currentDiff = last.current - first.current; // A
                float voltageDiff = last.voltage - first.voltage; // V
                float hours = timeDiff / (1000.0f * 60 * 60);
                float didt = currentDiff / hours; // A/h
                float dvdt = voltageDiff / hours; // V/h

                // 恒压阶段特征：电流快速下降，电压基本稳定
                if (level >= 75 && didt < -0.3f && Math.abs(dvdt) < 0.05f) {
                    return "constant_voltage";
                }

                // 恒流阶段特征：电流稳定或缓慢下降，电压上升
                if (power > 5 && Math.abs(didt) < 0.5f && dvdt > 0.01f) {
                    return "constant_current";
                }
            }
        }

        // 兜底逻辑
        if (level >= 80) {
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
        } else if (power > 0) {
            return "slow";
        } else {
            return "none";
        }
    }

    /**
     * 保存功率历史记录（调用方已在后台线程时可直接执行，否则提交到 executor）
     */
    private void savePowerHistory(PowerHistory history) {
        Runnable saveTask = () -> {
            try {
                com.batteryhealth.app.data.database.AppDatabase db =
                        com.batteryhealth.app.BatteryHealthApplication.getDatabase();
                if (db != null) {
                    db.powerHistoryDao().insert(history);
                    if (BuildConfigHelper.isDebugMode()) {
                        Log.d(TAG, "Power history saved: " + history.getPower() + "W");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving power history: " + e.getMessage());
            }
        };
        if (executor != null) {
            executor.submit(saveTask);
        } else {
            saveTask.run();
        }
    }

    /**
     * 获取当前功率历史记录。
     * 优先返回缓存值，避免在主线程上读取 sysfs 触发 StrictMode / ANR。
     */
    private PowerHistory getCurrentPowerHistory() {
        PowerHistory history = new PowerHistory();
        history.setSessionId(currentSessionId);
        history.setVoltage(cachedVoltage);
        history.setCurrent(cachedCurrent);
        history.setBatteryLevel(cachedLevel);
        history.setBatteryTemp(cachedTemp);
        history.setChargeType(cachedChargeType);
        history.calculatePower();
        return history;
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.charging_monitor_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.charging_monitor_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建通知
     */
    private Notification buildNotification(PowerHistory history) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        String content;
        if (isCharging && history != null) {
            content = String.format(Locale.getDefault(),
                    getString(R.string.charging_monitor_notification_content_charging),
                    history.getBatteryLevel(),
                    history.getPower(),
                    history.getChargeTypeDescription());
        } else {
            content = getString(R.string.charging_monitor_notification_content_idle);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.charging_monitor_notification_title))
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
    private void updateNotification(PowerHistory history) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(history));
        }
    }

    /**
     * 构建通知（无参版本，使用最新历史记录）
     */
    private Notification buildNotification() {
        return buildNotification(getCurrentPowerHistory());
    }

    /**
     * 更新通知（使用最新生成的历史记录）
     */
    private void updateNotification() {
        updateNotification(getCurrentPowerHistory());
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

    /**
     * 命名线程工厂，用于为线程池中的线程设置可读名称与未捕获异常处理器。
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, ex) -> {
                Log.e("NamedThreadFactory", "Uncaught exception in thread " + thread.getName(), ex);
            });
            return t;
        }
    }
}
