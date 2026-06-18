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
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

/**
 * 电池数据管理器
 * 负责从系统 sysfs / BatteryManager 读取电池实时数据，并通过本地机型数据库校准。
 */
public class BatteryDataManager {

    private static final String TAG = "BatteryDataManager";

    private final Context context;
    private final DeviceDatabaseManager deviceDb;

    private BatteryInfo currentBatteryInfo;
    private int usageDays = -1;

    // 充电状态文本缓存
    private String chargingStatusText = "未知";
    private String healthSourceText = "未知";
    private String batterySourceText = "未知";

    // BatteryManager 常量兜底（兼容不同 SDK 版本及预览版平台缺失的符号）
    private static final int BATTERY_PROP_CYCLE_COUNT = 7;
    private static final int BATTERY_PROP_CHARGE_FULL = getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL", 24);
    private static final int BATTERY_PROP_CHARGE_COUNTER = getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_COUNTER", 6);

    // 循环次数 sysfs 候选路径（按优先级排序）
    private static final String[] CYCLE_COUNT_PATHS = {
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/battery/cyclecount",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/bms/battery_cycle",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/max77843-fuelgauge/cycle_count",
            "/sys/class/power_supply/bq27441/cycle_count",
            "/sys/class/power_supply/bq27520/cycle_count",
            "/sys/class/power_supply/bq27741/cycle_count",
            "/sys/class/power_supply/battery/store_full_cc",
            // 厂商私有节点
            "/sys/class/power_supply/battery/battery_cycle_2",
            "/sys/class/power_supply/battery/charge_full_cycles",
            "/sys/class/power_supply/battery/mmi_cycle_count",
            "/sys/class/power_supply/battery/batt_cycle_count"
    };

    // 容量 sysfs 候选路径
    private static final String[] CHARGE_FULL_PATHS = {
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/maxfg/charge_full",
            "/sys/class/power_supply/battery/charge_full_raw",
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/fcc"
    };

    // 电流 sysfs 候选路径
    private static final String[] CURRENT_NOW_PATHS = {
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/maxfg/current_now"
    };

    // 电压 sysfs 候选路径
    private static final String[] VOLTAGE_NOW_PATHS = {
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/bms/voltage_now",
            "/sys/class/power_supply/maxfg/voltage_now"
    };

    // 温度 sysfs 候选路径
    private static final String[] TEMP_PATHS = {
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/battery/batt_temp",
            "/sys/class/power_supply/bms/temp"
    };

    // 电池序列号 sysfs 候选路径
    private static final String[] SERIAL_PATHS = {
            "/sys/class/power_supply/battery/serial_number",
            "/sys/class/power_supply/bms/serial_number",
            "/sys/class/power_supply/battery/batt_serial_number"
    };

    // 电池技术 sysfs 候选路径
    private static final String[] TECH_PATHS = {
            "/sys/class/power_supply/battery/technology",
            "/sys/class/power_supply/bms/technology"
    };

    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceDb = DeviceDatabaseManager.getInstance(this.context);
    }

    /**
     * 获取完整电池信息
     */
    public BatteryInfo getBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setDeviceModel(Build.MODEL);
        info.setDeviceBrand(Build.BRAND);

        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return info;
        }

        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        // 1. 基础电量与状态
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percentage = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
        info.setLevel(percentage);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        info.setStatus(status);

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        info.setPlugged(plugged);

        // 2. 温度：优先 BatteryManager，其次 sysfs
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temp < 0) temp = readSysfsInt(TEMP_PATHS, -1);
        info.setTemperature(temp / 10.0f);

        // 3. 电压
        int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        if (voltageMv <= 0) {
            long voltageUv = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
            if (voltageUv > 1000000) voltageMv = (int) (voltageUv / 1000);
        }
        info.setVoltage(voltageMv);

        // 4. 电流
        int currentMa = readCurrentNow(batteryManager);
        info.setCurrentNow(currentMa * 1000); // BatteryInfo 使用 uA

        // 5. 功率
        float powerW = calculatePower(voltageMv, currentMa);
        info.setChargingPower(powerW);
        info.setChargingVoltage(voltageMv / 1000.0f);
        info.setChargingCurrent(Math.abs(currentMa) / 1000.0f);

        // 6. 技术类型
        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null || technology.isEmpty()) {
            technology = readSysfsString(TECH_PATHS, "");
        }
        info.setTechnology(technology.isEmpty() ? "锂离子" : technology);

        // 7. 容量相关（关键：用于健康度）
        int designCapacity = getDesignCapacity(intent);
        int fullCapacity = getFullCapacity(batteryManager);
        int chargeCounterMah = getChargeCounter(batteryManager);

        info.setDesignCapacity(designCapacity);
        info.setDesignCapacitySource(designCapacity > 0 ? "device_database" : "unknown");
        info.setCurrentCapacity(fullCapacity);
        info.setCurrentCapacitySource(fullCapacity > 0 ? "battery_manager_or_sysfs" : "unknown");
        info.setChargeCounter(chargeCounterMah * 1000); // uAh

        // 8. 健康度计算
        BatteryHealthResult health = calculateHealth(designCapacity, fullCapacity, chargeCounterMah, percentage);
        info.setHealthPercentage(health.healthPercentage);
        info.setHealthStatus(mapHealthStatusToCode(health.healthLevel));
        info.setHealthConfidence(health.confidence);
        info.setHealthDataSource(health.confidence >= 0.95f ? "fcc_ratio" :
                (health.confidence >= 0.70f ? "charge_counter_ratio" : "unknown"));

        // 9. 循环次数
        int cycleCount = readCycleCount(batteryManager);
        info.setCycleCount(cycleCount);
        info.setCycleCountEstimated(cycleCount < 0);
        info.setCycleCountSource(cycleCount >= 0 ? "sysfs_or_battery_manager" : "unavailable");

        // 10. 电池来源
        BatterySourceResult source = determineBatterySource(intent, fullCapacity, designCapacity);
        info.setBatterySource(mapSourceToCode(source.source));
        info.setBatterySourceConfidence(source.confidence);

        // 11. 电池序列号
        info.setBatterySerial(readBatterySerial(intent));

        // 12. 系统健康状态
        info.setSystemHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN));

        return info;
    }

    /**
     * 读取当前电流（mA），正值充电，负值放电。
     */
    public int readCurrentNow(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int microAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (microAmps != Integer.MIN_VALUE && microAmps != 0) {
                return Math.abs(microAmps) > 100000 ? microAmps / 1000 : microAmps;
            }
        }
        long sysfsUa = readSysfsLong(CURRENT_NOW_PATHS, 0);
        if (sysfsUa != 0) {
            return sysfsUa > 100000 ? (int) (sysfsUa / 1000) : (int) sysfsUa;
        }
        return 0;
    }

    /**
     * 读取当前电压（mV）。
     */
    public int readVoltageNow() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int voltageMv = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
        if (voltageMv > 0) return voltageMv;
        long voltageUv = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
        return voltageUv > 1000 ? (int) (voltageUv / 1000) : (int) voltageUv;
    }

    /**
     * 读取当前功率（W）。
     */
    public float readPowerNow() {
        return calculatePower(readVoltageNow(), readCurrentNow(
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE)));
    }

    /**
     * 计算功率：P = U * I / 1000（W）。
     */
    private float calculatePower(int voltageMv, int currentMa) {
        if (voltageMv <= 0) return 0f;
        return (voltageMv * Math.abs(currentMa)) / 1000000.0f;
    }

    /**
     * 设计容量：优先本地数据库，其次 sysfs charge_full_design，最后 BatteryManager。
     */
    private int getDesignCapacity(Intent intent) {
        int dbCapacity = deviceDb.getDesignCapacity();
        if (dbCapacity > 0) return dbCapacity;

        int design = readSysfsInt(new String[]{
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design"
        }, -1);
        if (design > 1000) return design / 1000;

        // Intent 中没有设计容量字段，直接返回未知
        return -1;
    }

    /**
     * 满充容量（FCC）：优先 BatteryManager，其次 sysfs。
     */
    private int getFullCapacity(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int microAh = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_FULL);
            if (microAh != Integer.MIN_VALUE && microAh > 1000) {
                return microAh / 1000;
            }
        }
        int full = readSysfsInt(CHARGE_FULL_PATHS, -1);
        return full > 1000 ? full / 1000 : full;
    }

    /**
     * 当前剩余电量（mAh）。
     */
    private int getChargeCounter(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int microAh = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_COUNTER);
            if (microAh != Integer.MIN_VALUE && microAh != 0) {
                return Math.abs(microAh) / 1000;
            }
        }
        return -1;
    }

    /**
     * 读取循环次数：系统 API + sysfs + 估算兜底。
     */
    private int readCycleCount(BatteryManager batteryManager) {
        // 1. Android 14+ BatteryManager 隐藏 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && batteryManager != null) {
            try {
                int count = batteryManager.getIntProperty(BATTERY_PROP_CYCLE_COUNT);
                if (count > 0 && count < 10000) return count;
            } catch (Exception ignored) {
            }
        }

        // 2. sysfs 多路径
        int count = readSysfsInt(CYCLE_COUNT_PATHS, -1);
        if (count > 0 && count < 10000) return count;

        // 3. 部分厂商把循环次数放在 charge_full 相关文件中，格式不同
        // 兜底：不可读取时返回 -1，不允许臆造
        return -1;
    }

    /**
     * 电池来源判定：综合数据库 + 序列号 + 容量偏差。
     */
    private BatterySourceResult determineBatterySource(Intent intent, int fullCapacity, int designCapacity) {
        BatterySourceResult result = new BatterySourceResult();

        String serial = readBatterySerial(intent);

        // 1. 序列号分析
        if (serial != null && !serial.isEmpty() && !serial.equalsIgnoreCase("unknown")) {
            String upper = serial.toUpperCase(Locale.ROOT);
            String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.ROOT) : "";

            boolean matchesBrandPattern = false;
            if (brand.contains("xiaomi") || brand.contains("redmi")) {
                // 小米/红米电池序列号常见 15 位纯数字或特定前缀
                matchesBrandPattern = upper.matches("^[0-9A-Z]{12,20}$");
            } else if (brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme")) {
                matchesBrandPattern = upper.matches("^[0-9A-Z]{10,18}$");
            } else if (brand.contains("vivo") || brand.contains("iqoo")) {
                matchesBrandPattern = upper.matches("^[0-9A-Z]{10,20}$");
            } else if (brand.contains("honor")) {
                matchesBrandPattern = upper.matches("^[0-9A-Z]{12,20}$");
            } else if (brand.contains("nubia") || brand.contains("redmagic")) {
                matchesBrandPattern = upper.matches("^[0-9A-Z]{10,20}$");
            }

            if (matchesBrandPattern) {
                result.source = "原装";
                result.confidence = 0.75f;
            } else {
                result.source = "第三方";
                result.confidence = 0.45f;
            }
        }

        // 2. 容量偏差校验
        if (designCapacity > 0 && fullCapacity > 0) {
            float ratio = fullCapacity / (float) designCapacity;
            if (ratio < 0.55f || ratio > 1.25f) {
                // 容量严重偏离官方规格，强烈怀疑非原装
                result.source = "第三方";
                result.confidence = Math.min(result.confidence + 0.25f, 0.95f);
            } else if (ratio >= 0.85f && ratio <= 1.05f && result.confidence < 0.85f) {
                result.source = "原装";
                result.confidence = 0.85f;
            }
        }

        // 3. 无法获取任何有效信息
        if (result.source == null || result.source.isEmpty()) {
            result.source = "无法验证";
            result.confidence = 0.0f;
        }

        return result;
    }

    private String readBatterySerial(Intent intent) {
        // Android 15+ 部分系统提供该 API，使用反射避免编译期符号缺失。
        // 不同厂商/版本的方法签名可能为实例无参、实例带 Context 或静态带 Context，逐一尝试。
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String serial = tryGetBatterySerial(batteryManager);
            if (serial != null && !serial.isEmpty() && !serial.equals("0") && !serial.equalsIgnoreCase("unknown")) {
                return serial;
            }
        }
        return readSysfsString(SERIAL_PATHS, "未知");
    }

    private String tryGetBatterySerial(BatteryManager batteryManager) {
        // 1. 实例无参：Android 15 公开 API 形式
        try {
            Method method = batteryManager.getClass().getMethod("getBatterySerialNumber");
            Object result = method.invoke(batteryManager);
            if (result instanceof String) return (String) result;
        } catch (Exception ignored) {
        }
        // 2. 实例带 Context
        try {
            Method method = batteryManager.getClass().getMethod("getBatterySerialNumber", Context.class);
            Object result = method.invoke(batteryManager, context);
            if (result instanceof String) return (String) result;
        } catch (Exception ignored) {
        }
        // 3. 静态带 Context
        try {
            Method method = BatteryManager.class.getMethod("getBatterySerialNumber", Context.class);
            Object result = method.invoke(null, context);
            if (result instanceof String) return (String) result;
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 健康度计算。
     */
    private BatteryHealthResult calculateHealth(int designCapacity, int fullCapacity, int chargeCounter, int percentage) {
        BatteryHealthResult result = new BatteryHealthResult();

        if (fullCapacity > 0 && designCapacity > 0) {
            // 最可信：FCC / 设计容量
            float health = (fullCapacity / (float) designCapacity) * 100f;
            result.healthPercentage = clampHealth(health);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel);
            result.confidence = 0.95f;
            return result;
        }

        if (chargeCounter > 0 && percentage > 0 && designCapacity > 0) {
            // 次可信：charge_counter / 电量 * 设计容量
            float currentMax = chargeCounter / (percentage / 100f);
            float health = (currentMax / designCapacity) * 100f;
            result.healthPercentage = clampHealth(health);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel);
            result.confidence = 0.70f;
            return result;
        }

        // 3. 兜底：基于使用天数的经验估算（仅用于完全无容量数据的场景，置信度低）
        if (usageDays > 0 && designCapacity > 0) {
            // 锂电池典型衰减：约 7%/年（365 天），使用 4 年后约 75%
            float estimatedHealth = 100f - (usageDays * 0.018f);
            result.healthPercentage = clampHealth(estimatedHealth);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel) + "（基于使用时长估算）";
            result.confidence = 0.35f;
            return result;
        }

        // 没有任何容量信息时，无法计算，返回未知
        result.healthPercentage = -1;
        result.healthLevel = "未知";
        result.healthStatus = "缺少容量数据，无法评估";
        result.confidence = 0.0f;
        return result;
    }

    private float clampHealth(float value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private String getHealthLevel(float percentage) {
        if (percentage < 0) return "未知";
        if (percentage >= 95) return "极佳";
        if (percentage >= 85) return "良好";
        if (percentage >= 75) return "一般";
        if (percentage >= 60) return "较差";
        return "极差";
    }

    private String getHealthStatusString(String level) {
        switch (level) {
            case "极佳":
                return "电池状态极佳，可继续使用";
            case "良好":
                return "电池状态良好，性能正常";
            case "一般":
                return "电池健康度一般，建议关注";
            case "较差":
                return "电池损耗明显，建议考虑更换";
            case "极差":
                return "电池健康度极差，建议尽快更换";
            default:
                return "无法评估";
        }
    }

    /**
     * 将健康等级映射为数据库存储的代码。
     */
    private String mapHealthStatusToCode(String level) {
        switch (level) {
            case "极佳":
            case "良好":
                return "good";
            case "一般":
                return "normal";
            case "较差":
                return "warning";
            case "极差":
                return "poor";
            default:
                return "unknown";
        }
    }

    /**
     * 将电池来源描述映射为数据库存储代码。
     */
    private String mapSourceToCode(String source) {
        if (source == null) return "unknown";
        switch (source) {
            case "原装":
                return "original";
            case "第三方":
                return "third_party";
            default:
                return "unknown";
        }
    }

    private String getStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            default:
                return "未知";
        }
    }

    private String getChargeTypeString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "交流电源";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "无线充电";
            case BatteryManager.BATTERY_PLUGGED_DOCK:
                return "Dock";
            default:
                return "未连接";
        }
    }

    // region sysfs 读取工具

    private int readSysfsInt(String[] paths, int defaultValue) {
        for (String path : paths) {
            try {
                String value = readFile(path);
                if (value != null && !value.isEmpty()) {
                    return Integer.parseInt(value.trim());
                }
            } catch (Exception e) {
                Log.v(TAG, "readSysfsInt failed: " + path);
            }
        }
        return defaultValue;
    }

    private long readSysfsLong(String[] paths, long defaultValue) {
        for (String path : paths) {
            try {
                String value = readFile(path);
                if (value != null && !value.isEmpty()) {
                    return Long.parseLong(value.trim());
                }
            } catch (Exception e) {
                Log.v(TAG, "readSysfsLong failed: " + path);
            }
        }
        return defaultValue;
    }

    private String readSysfsString(String[] paths, String defaultValue) {
        for (String path : paths) {
            try {
                String value = readFile(path);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            } catch (Exception e) {
                Log.v(TAG, "readSysfsString failed: " + path);
            }
        }
        return defaultValue;
    }

    private String readFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString().trim();
    }

    /**
     * 通过反射获取 BatteryManager 整型常量，兼容 SDK 预览版缺失符号的情况。
     */
    private static int getBatteryIntConstant(String name, int fallback) {
        try {
            return BatteryManager.class.getField(name).getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // endregion

    // region 内部结果类

    private static class BatteryHealthResult {
        float healthPercentage;
        String healthLevel;
        String healthStatus;
        float confidence;
    }

    private static class BatterySourceResult {
        String source;
        float confidence;
    }

    // endregion

    /**
     * 获取当前是否处于充电状态。
     */
    public boolean isCharging() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * 获取当前充电功率等级，用于UI展示。
     */
    public String getPowerLevelLabel(float powerW) {
        if (powerW >= 100) return "超快闪充";
        if (powerW >= 60) return "极速快充";
        if (powerW >= 30) return "快速充电";
        if (powerW >= 10) return "标准充电";
        if (powerW > 0) return "慢速充电";
        return "未充电";
    }

    /**
     * 根据机型数据库，判断当前功率是否接近官方快充功率。
     */
    public boolean isNearOfficialFastCharge(float currentPowerW) {
        int official = deviceDb.getTypicalChargePower();
        if (official <= 0) return currentPowerW >= 18;
        return currentPowerW >= official * 0.6f;
    }

    // region 兼容原 Fragment 调用接口

    /**
     * 立即从系统 sticky intent 刷新一次当前电池信息。
     */
    public void refreshFromStickyIntent() {
        currentBatteryInfo = getBatteryInfo();
        chargingStatusText = getStatusString(currentBatteryInfo != null ? currentBatteryInfo.getStatus() : BatteryManager.BATTERY_STATUS_UNKNOWN);
        healthSourceText = formatHealthSource(currentBatteryInfo);
        batterySourceText = formatBatterySource(currentBatteryInfo);
    }

    /**
     * 获取当前缓存的电池信息。
     */
    public BatteryInfo getCurrentBatteryInfo() {
        if (currentBatteryInfo == null) {
            refreshFromStickyIntent();
        }
        return currentBatteryInfo;
    }

    /**
     * 异步刷新所有电池数据。
     */
    public void refreshAllDataAsync() {
        new Thread(this::refreshFromStickyIntent).start();
    }

    /**
     * 设置使用天数（由 DeviceInfoManager 提供）。
     */
    public void setUsageDays(int days) {
        this.usageDays = days;
    }

    /**
     * 获取充电状态文本。
     */
    public String getChargingStatusText() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return chargingStatusText;
    }

    /**
     * 获取健康度来源文本。
     */
    public String getHealthSourceText() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return healthSourceText;
    }

    /**
     * 获取电池来源文本。
     */
    public String getBatterySourceText() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return batterySourceText;
    }

    private String formatHealthSource(BatteryInfo info) {
        if (info == null) return "未知";
        float conf = info.getHealthConfidence();
        String source = info.getHealthDataSource();
        if ("fcc_ratio".equals(source)) return "实测容量比（置信度 " + (int) (conf * 100) + "%）";
        if ("charge_counter_ratio".equals(source)) return "剩余电量推算（置信度 " + (int) (conf * 100) + "%）";
        if ("usage_days_estimate".equals(source)) return "使用时长估算（置信度 " + (int) (conf * 100) + "%）";
        return "无法获取";
    }

    private String formatBatterySource(BatteryInfo info) {
        if (info == null) return "未知";
        String source = info.getBatterySource();
        float conf = info.getBatterySourceConfidence();
        if ("original".equals(source)) return "原装（置信度 " + (int) (conf * 100) + "%）";
        if ("third_party".equals(source)) return "第三方（置信度 " + (int) (conf * 100) + "%）";
        return "无法验证";
    }

    // endregion
}
