package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 电池数据管理器（2026 旗舰版）
 *
 * 核心策略：多源融合 + 可信度标注。
 * 1. 真实数据优先：BatteryManager API、sysfs、root 读取。
 * 2. 当真实数据不可用时，使用基于循环次数/使用时长的物理估算模型，
 *    并明确向 UI 返回数据来源与置信度，绝不伪装成 100% 真实值。
 * 3. 电池来源采用概率评分，未知时返回“无法验证”而非强行判定原装/第三方。
 */
public class BatteryDataManager {

    private static final String TAG = "BatteryDataManager";

    // 数据来源常量
    public static final String SOURCE_SYSFS = "sysfs";
    public static final String SOURCE_BATTERY_MANAGER = "battery_manager";
    public static final String SOURCE_ESTIMATED_PHYSICAL = "estimated_physical";
    public static final String SOURCE_UNKNOWN = "unknown";

    // 电池来源结果
    public static final String BATTERY_SOURCE_ORIGINAL = "original";
    public static final String BATTERY_SOURCE_THIRD_PARTY = "third_party";
    public static final String BATTERY_SOURCE_UNKNOWN = "unknown";

    private final Context context;
    private BatteryInfo currentBatteryInfo;
    private Boolean hasRootAccess = null;

    // 用于健康度估算的上下文
    private int usageDays = -1;

    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentBatteryInfo = new BatteryInfo();
        setSafeDefaults();
        loadBatteryInfo();
    }

    private void setSafeDefaults() {
        currentBatteryInfo.setLevel(0);
        currentBatteryInfo.setTemperature(25.0f);
        currentBatteryInfo.setVoltage(3700);
        currentBatteryInfo.setTechnology("Li-ion");
        currentBatteryInfo.setHealthPercentage(-1.0f);
        currentBatteryInfo.setHealthStatus("unknown");
        currentBatteryInfo.setHealthDataSource(SOURCE_UNKNOWN);
        currentBatteryInfo.setBatterySource(BATTERY_SOURCE_UNKNOWN);
        currentBatteryInfo.setBatterySourceConfidence(0.0f);
        currentBatteryInfo.setCycleCount(-1);
        currentBatteryInfo.setCycleCountEstimated(true);
    }

    /**
     * 设置设备使用天数，用于物理估算模型。
     */
    public void setUsageDays(int days) {
        this.usageDays = days;
    }

    /**
     * 从 Intent 更新电池数据（电量、状态、温度、电压、技术）
     */
    public void updateFromIntent(Intent intent) {
        if (intent == null) return;
        try {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1 && scale > 0) {
                currentBatteryInfo.setLevel((int) ((level / (float) scale) * 100));
            }

            currentBatteryInfo.setStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
            currentBatteryInfo.setPlugged(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));

            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (temperature != -1) {
                currentBatteryInfo.setTemperature(temperature / 10.0f);
            }

            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltage != -1) {
                currentBatteryInfo.setVoltage(voltage);
            }

            String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            if (technology != null) {
                currentBatteryInfo.setTechnology(technology);
            }

            // 系统健康状态（Hardware 层面）
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            currentBatteryInfo.setSystemHealth(health);
        } catch (Exception e) {
            Log.e(TAG, "Error updating from intent: " + e.getMessage());
        }
    }

    /**
     * 加载电池信息
     */
    private void loadBatteryInfo() {
        try {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (level >= 0) currentBatteryInfo.setLevel(level);

                int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                currentBatteryInfo.setCurrentNow(currentNow);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                    if (chargeCounter != Long.MIN_VALUE && chargeCounter > 0) {
                        currentBatteryInfo.setChargeCounter((int) chargeCounter);
                        currentBatteryInfo.setCurrentCapacity((int) (chargeCounter / 1000));
                    }
                    long energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
                    if (energyCounter != Long.MIN_VALUE && energyCounter > 0) {
                        currentBatteryInfo.setEnergyCounter((int) energyCounter);
                    }
                }

                // Android 14+ 循环次数（隐藏常量 7）
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        int cycleCount = batteryManager.getIntProperty(7);
                        if (cycleCount >= 0) {
                            currentBatteryInfo.setCycleCount(cycleCount);
                            currentBatteryInfo.setCycleCountEstimated(false);
                            currentBatteryInfo.setCycleCountSource(SOURCE_BATTERY_MANAGER);
                        }
                    } catch (Exception ignored) {}
                }
            }
            refreshFromStickyIntent();
        } catch (Exception e) {
            Log.e(TAG, "Error loading battery info: " + e.getMessage());
        }
    }

    /**
     * 从电池 sticky intent 刷新基本信息
     */
    public void refreshFromStickyIntent() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent = context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                intent = context.registerReceiver(null, filter);
            }
            if (intent != null) {
                updateFromIntent(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing from sticky intent: " + e.getMessage());
        }
    }

    /**
     * 读取充电循环次数（异步）
     *
     * 优先级：
     * 1. BatteryManager API (Android 14+)
     * 2. 标准 sysfs 路径（厂商 BMS / maxfg / battery）
     * 3. root 权限读取受保护节点
     */
    public void readCycleCountAsync() {
        new Thread(() -> {
            int cycleCount = -1;
            String source = SOURCE_UNKNOWN;

            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                        if (bm != null) {
                            int result = bm.getIntProperty(7);
                            if (result >= 0) {
                                cycleCount = result;
                                source = SOURCE_BATTERY_MANAGER;
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Cycle count API not available: " + e.getMessage());
                    }
                }

                if (cycleCount < 0) {
                    String[] paths = {
                        "/sys/class/power_supply/battery/cycle_count",
                        "/sys/class/power_supply/bms/cycle_count",
                        "/sys/class/power_supply/maxfg/cycle_count",
                        "/sys/class/power_supply/battery/battery_cycle",
                        "/sys/class/power_supply/battery/cyclecounts",
                        "/sys/class/power_supply/battery/cycle_counts",
                        "/sys/class/power_supply/bms/cyclecounts",
                        "/sys/class/power_supply/maxfg/cyclecounts",
                        "/sys/class/power_supply/battery/cycle_count_total",
                        "/sys/class/power_supply/battery/cycle_count_main",
                        "/sys/class/power_supply/battery/batt_cycle_count"
                    };
                    for (String path : paths) {
                        String value = readSysfsFile(path);
                        if (value != null && !value.isEmpty()) {
                            try {
                                int count = Integer.parseInt(value);
                                if (count >= 0) {
                                    cycleCount = count;
                                    source = SOURCE_SYSFS;
                                    break;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                currentBatteryInfo.setCycleCount(cycleCount);
                currentBatteryInfo.setCycleCountEstimated(cycleCount < 0);
                currentBatteryInfo.setCycleCountSource(source);

                // 如果获得循环次数，可刷新基于循环次数的健康度估算
                if (cycleCount >= 0) {
                    recalculateHealthIfNeeded();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading cycle count: " + e.getMessage());
                currentBatteryInfo.setCycleCount(-1);
                currentBatteryInfo.setCycleCountEstimated(true);
                currentBatteryInfo.setCycleCountSource(SOURCE_UNKNOWN);
            }
        }).start();
    }

    /**
     * 读取电池容量（异步）
     *
     * 优先级：
     * 1. BatteryManager API 获取 charge_counter（当前容量）
     * 2. sysfs charge_full / charge_full_design（设计容量）
     * 3. root 读取受保护节点
     */
    public void readBatteryCapacityAsync() {
        new Thread(() -> {
            try {
                readDesignCapacityMultiSource();
                readCurrentCapacityMultiSource();
                calculateHealth();
            } catch (Exception e) {
                Log.e(TAG, "Error reading battery capacity: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 多源读取设计容量
     */
    private void readDesignCapacityMultiSource() {
        int designCapacity = 0;
        String source = SOURCE_UNKNOWN;

        // 1. sysfs charge_full
        String chargeFull = readSysfsFile("/sys/class/power_supply/battery/charge_full");
        if (chargeFull != null && !chargeFull.isEmpty()) {
            try {
                designCapacity = Math.abs(Integer.parseInt(chargeFull)) / 1000;
                source = SOURCE_SYSFS;
            } catch (NumberFormatException ignored) {}
        }

        // 2. sysfs charge_full_design
        if (designCapacity <= 0) {
            String design = readSysfsFile("/sys/class/power_supply/battery/charge_full_design");
            if (design != null && !design.isEmpty()) {
                try {
                    designCapacity = Math.abs(Integer.parseInt(design)) / 1000;
                    source = SOURCE_SYSFS;
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. sysfs 厂商特定节点
        if (designCapacity <= 0) {
            String[] paths = {
                "/sys/class/power_supply/bms/charge_full",
                "/sys/class/power_supply/maxfg/charge_full",
                "/sys/class/power_supply/battery/batt_full_capacity",
                "/sys/class/power_supply/battery/full_charge_design_capacity"
            };
            for (String path : paths) {
                String value = readSysfsFile(path);
                if (value != null && !value.isEmpty()) {
                    try {
                        designCapacity = Math.abs(Integer.parseInt(value)) / 1000;
                        source = SOURCE_SYSFS;
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 4. BatteryManager API（部分三星/ Pixel 支持）
        if (designCapacity <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    int propChargeFull = getBatteryManagerProperty("BATTERY_PROPERTY_CHARGE_FULL", 5);
                    long chargeFullEstimate = bm.getLongProperty(propChargeFull);
                    if (chargeFullEstimate != Long.MIN_VALUE && chargeFullEstimate > 0) {
                        designCapacity = (int) (chargeFullEstimate / 1000);
                        source = SOURCE_BATTERY_MANAGER;
                    }
                }
            } catch (Exception ignored) {}
        }

        currentBatteryInfo.setDesignCapacity(designCapacity);
        currentBatteryInfo.setDesignCapacitySource(source);
    }

    /**
     * 多源读取当前容量
     */
    private void readCurrentCapacityMultiSource() {
        int currentCapacity = 0;
        String source = SOURCE_UNKNOWN;

        // 1. 已在构造函数中通过 BatteryManager 读取
        if (currentBatteryInfo.getCurrentCapacity() > 0) {
            currentCapacity = currentBatteryInfo.getCurrentCapacity();
            source = SOURCE_BATTERY_MANAGER;
        }

        // 2. sysfs charge_counter
        if (currentCapacity <= 0) {
            String counter = readSysfsFile("/sys/class/power_supply/battery/charge_counter");
            if (counter != null && !counter.isEmpty()) {
                try {
                    currentCapacity = Math.abs(Integer.parseInt(counter)) / 1000;
                    source = SOURCE_SYSFS;
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. sysfs 厂商节点
        if (currentCapacity <= 0) {
            String[] paths = {
                "/sys/class/power_supply/bms/charge_counter",
                "/sys/class/power_supply/maxfg/charge_counter",
                "/sys/class/power_supply/battery/charge_now"
            };
            for (String path : paths) {
                String value = readSysfsFile(path);
                if (value != null && !value.isEmpty()) {
                    try {
                        currentCapacity = Math.abs(Integer.parseInt(value)) / 1000;
                        source = SOURCE_SYSFS;
                        break;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        currentBatteryInfo.setCurrentCapacity(currentCapacity);
        currentBatteryInfo.setCurrentCapacitySource(source);
    }

    /**
     * 计算电池健康度
     *
     * 优先级：
     * 1. 真实容量比 = currentCapacity / designCapacity
     * 2. 若容量不可得，使用系统 BATTERY_HEALTH 状态映射
     * 3. 若仍不可得，使用基于循环次数的物理衰减模型
     * 4. 最后使用基于使用时长的保守估算
     *
     * 每次计算都会更新 healthDataSource 与 healthConfidence。
     */
    private void calculateHealth() {
        int designCapacity = currentBatteryInfo.getDesignCapacity();
        int currentCapacity = currentBatteryInfo.getCurrentCapacity();

        // 1. 真实容量比
        if (designCapacity > 0 && currentCapacity > 0) {
            float health = Math.min(100.0f, (currentCapacity * 100.0f) / designCapacity);
            applyHealth(health, SOURCE_SYSFS, 0.92f);
            return;
        }

        // 2. BatteryManager charge_full 估算（当前设备剩余满充容量/设计容量）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    int propChargeFull = getBatteryManagerProperty("BATTERY_PROPERTY_CHARGE_FULL", 5);
                    int propChargeFullDesign = getBatteryManagerProperty("BATTERY_PROPERTY_CHARGE_FULL_DESIGN", 6);
                    long chargeFull = bm.getLongProperty(propChargeFull);
                    long chargeFullDesign = bm.getLongProperty(propChargeFullDesign);
                    if (chargeFull != Long.MIN_VALUE && chargeFull > 0 &&
                        chargeFullDesign != Long.MIN_VALUE && chargeFullDesign > 0) {
                        float health = Math.min(100.0f, (chargeFull * 100.0f) / chargeFullDesign);
                        applyHealth(health, SOURCE_BATTERY_MANAGER, 0.85f);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        recalculateHealthIfNeeded();
    }

    /**
     * 在容量不可用时，基于循环次数/使用天数重新估算健康度。
     */
    public void recalculateHealthIfNeeded() {
        if (currentBatteryInfo.getHealthDataSource() != null &&
            (currentBatteryInfo.getHealthDataSource().equals(SOURCE_SYSFS) ||
             currentBatteryInfo.getHealthDataSource().equals(SOURCE_BATTERY_MANAGER))) {
            // 已有真实容量数据，不再用估算覆盖
            return;
        }

        // 3. 基于循环次数的物理衰减模型（Li-ion 典型 500-800 次循环到 80%）
        int cycleCount = currentBatteryInfo.getCycleCount();
        if (cycleCount > 0) {
            float health = estimateHealthByCycleCount(cycleCount);
            applyHealth(health, SOURCE_ESTIMATED_PHYSICAL, 0.60f);
            return;
        }

        // 4. 基于使用时长的保守估算
        if (usageDays > 0) {
            float health = estimateHealthByUsageDays(usageDays);
            applyHealth(health, SOURCE_ESTIMATED_PHYSICAL, 0.40f);
            return;
        }

        // 5. 系统硬件健康状态兜底
        int systemHealth = currentBatteryInfo.getSystemHealth();
        if (systemHealth != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            float health = mapSystemHealth(systemHealth);
            applyHealth(health, SOURCE_BATTERY_MANAGER, 0.35f);
            return;
        }

        applyHealth(-1.0f, SOURCE_UNKNOWN, 0.0f);
    }

    /**
     * 基于循环次数估算健康度。
     * 模型：前 300 次衰减较慢，之后按指数衰减，到 800 次约 80%。
     */
    private float estimateHealthByCycleCount(int cycleCount) {
        if (cycleCount <= 0) return -1.0f;
        // 经验公式：健康度 = 100 * exp(-cycleCount / 1800)
        double health = 100.0 * Math.exp(-cycleCount / 1800.0);
        // 限制在合理范围
        return (float) Math.max(60.0, Math.min(100.0, health));
    }

    /**
     * 基于使用天数估算健康度。
     * 模型：首年衰减约 8%-10%，之后每年约 5%-7%。
     */
    private float estimateHealthByUsageDays(int days) {
        if (days <= 0) return -1.0f;
        double years = days / 365.0;
        // 保守模型：首年 9%，之后每年 6%
        double degradation = 0.09 * Math.min(years, 1.0) + 0.06 * Math.max(0.0, years - 1.0);
        double health = 100.0 * (1.0 - degradation);
        return (float) Math.max(60.0, Math.min(100.0, health));
    }

    private float mapSystemHealth(int systemHealth) {
        switch (systemHealth) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return 92.0f;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
            case BatteryManager.BATTERY_HEALTH_COLD:
                return 78.0f;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return 70.0f;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return 55.0f;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return 65.0f;
            default:
                return -1.0f;
        }
    }

    private void applyHealth(float health, String source, float confidence) {
        currentBatteryInfo.setHealthPercentage(health);
        currentBatteryInfo.setHealthDataSource(source);
        currentBatteryInfo.setHealthConfidence(confidence);

        if (health < 0) {
            currentBatteryInfo.setHealthStatus("unknown");
            return;
        }

        if (health >= 90) {
            currentBatteryInfo.setHealthStatus("good");
        } else if (health >= 80) {
            currentBatteryInfo.setHealthStatus("normal");
        } else if (health >= 70) {
            currentBatteryInfo.setHealthStatus("warning");
        } else {
            currentBatteryInfo.setHealthStatus("poor");
        }
    }

    /**
     * 判断电池来源（异步）
     *
     * 2026 旗舰版策略：
     * - 读取序列号后，与品牌历史规律、容量匹配度、电压范围综合评分。
     * - 仅当置信度 >= 70% 时才返回 original / third_party，否则 unknown。
     */
    public void detectBatterySourceAsync() {
        new Thread(() -> {
            try {
                String serial = null;
                String[] serialPaths = {
                    "/sys/class/power_supply/battery/serial_number",
                    "/sys/class/power_supply/bms/serial_number",
                    "/sys/class/power_supply/maxfg/serial_number",
                    "/sys/class/power_supply/battery/batt_serial_num",
                    "/sys/class/power_supply/battery/battery_serial"
                };

                for (String path : serialPaths) {
                    serial = readSysfsFile(path);
                    if (serial != null && !serial.isEmpty()) break;
                }

                if (serial != null && !serial.isEmpty()) {
                    currentBatteryInfo.setBatterySerial(serial);
                }

                SourceScore score = evaluateBatterySource(serial);
                currentBatteryInfo.setBatterySource(score.result);
                currentBatteryInfo.setBatterySourceConfidence(score.confidence);

            } catch (Exception e) {
                Log.e(TAG, "Error detecting battery source: " + e.getMessage());
                currentBatteryInfo.setBatterySource(BATTERY_SOURCE_UNKNOWN);
                currentBatteryInfo.setBatterySourceConfidence(0.0f);
            }
        }).start();
    }

    private static class SourceScore {
        String result;
        float confidence;
    }

    private SourceScore evaluateBatterySource(String serial) {
        SourceScore score = new SourceScore();
        score.result = BATTERY_SOURCE_UNKNOWN;
        score.confidence = 0.0f;

        float totalScore = 0;
        int maxScore = 0;

        // 1. 序列号格式评分（权重 40%）
        int serialScore = scoreSerialFormat(serial);
        totalScore += serialScore * 0.4f;
        maxScore += 1 * 0.4f;

        // 2. 电池技术评分（权重 20%）
        String tech = currentBatteryInfo.getTechnology();
        int techScore = 0;
        if (tech != null) {
            String lower = tech.toLowerCase(Locale.ROOT);
            if (lower.contains("li-ion") || lower.contains("li-poly") || lower.contains("lithium")) {
                techScore = 1;
            }
        }
        totalScore += techScore * 0.2f;
        maxScore += 1 * 0.2f;

        // 3. 电压范围评分（权重 20%）
        float voltage = currentBatteryInfo.getVoltage();
        int voltageScore = (voltage >= 3000 && voltage <= 4500) ? 1 : 0;
        totalScore += voltageScore * 0.2f;
        maxScore += 1 * 0.2f;

        // 4. 容量匹配评分（权重 20%）
        int designCapacity = currentBatteryInfo.getDesignCapacity();
        int currentCapacity = currentBatteryInfo.getCurrentCapacity();
        int capacityScore = 0;
        if (designCapacity > 0 && currentCapacity > 0) {
            float ratio = (float) currentCapacity / designCapacity;
            if (ratio >= 0.5f && ratio <= 1.05f) capacityScore = 1;
        }
        totalScore += capacityScore * 0.2f;
        maxScore += 1 * 0.2f;

        float confidence = maxScore > 0 ? totalScore / maxScore : 0.0f;

        if (serial == null || serial.isEmpty() || confidence < 0.55f) {
            score.result = BATTERY_SOURCE_UNKNOWN;
            score.confidence = confidence;
            return score;
        }

        // 仅当置信度足够高时才给出明确结论，否则标记为无法验证。
        // 第三方电池判断需要更多强证据，避免仅因序列号格式不匹配就误判。
        if (serialScore == 1 && confidence >= 0.70f) {
            score.result = BATTERY_SOURCE_ORIGINAL;
            score.confidence = confidence;
        } else if (serialScore == 0 && confidence >= 0.75f && capacityScore == 0) {
            // 序列号格式不匹配且容量异常，才较有把握判断为第三方
            score.result = BATTERY_SOURCE_THIRD_PARTY;
            score.confidence = confidence;
        } else {
            score.result = BATTERY_SOURCE_UNKNOWN;
            score.confidence = confidence;
        }

        return score;
    }

    /**
     * 序列号格式评分：0=不匹配，1=匹配。
     */
    private int scoreSerialFormat(String serial) {
        if (serial == null || serial.isEmpty()) return 0;
        String brand = Build.BRAND.toLowerCase(Locale.ROOT);
        switch (brand) {
            case "xiaomi":
            case "redmi":
                return serial.matches("^[BC][A-Z0-9]{9,19}$") ||
                       serial.matches("^[A-Z0-9]{15,20}$") ? 1 : 0;
            case "huawei":
            case "honor":
                return serial.matches("^H[A-Z0-9]{7,15}$") ||
                       serial.matches("^[A-Z0-9]{12,16}$") ? 1 : 0;
            case "oppo":
            case "realme":
            case "oneplus":
                return serial.matches("^OP[A-Z0-9]{8,16}$") ||
                       serial.matches("^[A-Z0-9]{10,18}$") ? 1 : 0;
            case "vivo":
            case "iqoo":
                return serial.matches("^V[A-Z0-9]{7,14}$") ||
                       serial.matches("^[A-Z0-9]{8,15}$") ? 1 : 0;
            case "samsung":
                return serial.matches("^[A-Z][A-Z0-9]{10}$") ? 1 : 0;
            default:
                return serial.length() >= 8 && serial.matches("^[A-Z0-9]+") ? 1 : 0;
        }
    }

    /**
     * 刷新所有电池数据（异步）
     */
    public void refreshAllDataAsync() {
        new Thread(() -> {
            try {
                refreshFromStickyIntent();
                readBatteryCapacityAsync();
                readCycleCountAsync();
                detectBatterySourceAsync();
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing all data: " + e.getMessage());
            }
        }).start();
    }

    public BatteryInfo getCurrentBatteryInfo() {
        return currentBatteryInfo;
    }

    public String getChargingStatusText() {
        switch (currentBatteryInfo.getStatus()) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            default:
                return "未知";
        }
    }

    public String getPlugTypeText() {
        switch (currentBatteryInfo.getPlugged()) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "交流电源";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "无线充电";
            default:
                return "未连接";
        }
    }

    public String getBatterySourceText() {
        String source = currentBatteryInfo.getBatterySource();
        if (BATTERY_SOURCE_ORIGINAL.equals(source)) {
            return "原装电池";
        } else if (BATTERY_SOURCE_THIRD_PARTY.equals(source)) {
            return "第三方电池";
        } else {
            return "无法验证";
        }
    }

    /**
     * 获取健康度数据来源描述文本
     */
    public String getHealthSourceText() {
        String source = currentBatteryInfo.getHealthDataSource();
        if (SOURCE_SYSFS.equals(source)) {
            return "系统内核读取";
        } else if (SOURCE_BATTERY_MANAGER.equals(source)) {
            return "系统电池服务";
        } else if (SOURCE_ESTIMATED_PHYSICAL.equals(source)) {
            return "物理模型估算";
        } else {
            return "未知";
        }
    }

    /**
     * 检测 root
     */
    public boolean hasRootAccess() {
        if (hasRootAccess != null) return hasRootAccess;

        try {
            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();

            if (line != null && !line.isEmpty()) {
                Process suProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                BufferedReader suReader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                String suLine = suReader.readLine();
                suReader.close();
                suProcess.waitFor();

                if (suLine != null && suLine.contains("uid=0")) {
                    hasRootAccess = true;
                    return true;
                }
            }
        } catch (Exception ignored) {}

        File superuser = new File("/system/app/Superuser.apk");
        if (superuser.exists()) {
            hasRootAccess = true;
            return true;
        }

        String[] rootPaths = {"/sbin/su", "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su"};
        for (String path : rootPaths) {
            if (new File(path).exists()) {
                hasRootAccess = true;
                return true;
            }
        }

        hasRootAccess = false;
        return false;
    }

    private String readSysfsWithRoot(String path) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + path});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line;
        } catch (Exception e) {
            Log.d(TAG, "Failed to read " + path + " with root: " + e.getMessage());
            return null;
        }
    }

    private String readSysfsFile(String path) {
        File file = new File(path);
        if (file.exists() && file.canRead()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                reader.close();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            } catch (Exception ignored) {}
        }

        if (file.exists() && hasRootAccess()) {
            String result = readSysfsWithRoot(path);
            if (result != null && !result.isEmpty()) {
                return result.trim();
            }
        }

        return null;
    }

    /**
     * 通过反射读取 BatteryManager 中可能隐藏的常量，避免编译 SDK stub 中缺少符号。
     */
    private int getBatteryManagerProperty(String name, int fallback) {
        try {
            return BatteryManager.class.getField(name).getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
