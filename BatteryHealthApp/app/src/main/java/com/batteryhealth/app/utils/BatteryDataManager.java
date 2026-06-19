package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 电池数据管理器
 * 负责从系统 sysfs / BatteryManager 读取电池实时数据，并通过本地机型数据库校准。
 */
public class BatteryDataManager {

    private static final String TAG = "BatteryDataManager";

    public static final String PREFS_NAME = "battery_health_prefs";
    public static final String PREF_CALIBRATED_CAPACITY = "calibrated_capacity_mah";

    private final Context context;
    private final DeviceDatabaseManager deviceDb;
    private ActivationDateHelper.Result activation;

    private BatteryInfo currentBatteryInfo;
    private int usageDays = -1;

    // 充电状态文本缓存
    private String chargingStatusText;
    private String healthSourceText;
    private String batterySourceText;

    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceDb = DeviceDatabaseManager.getInstance(this.context);
        this.chargingStatusText = this.context.getString(R.string.status_unknown);
        this.healthSourceText = this.context.getString(R.string.status_unknown);
        this.batterySourceText = this.context.getString(R.string.status_unknown);
    }

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
            "/sys/class/power_supply/battery/batt_cycle_count",
            // 小米/红米
            "/sys/class/power_supply/battery/cycle_count_complete",
            "/sys/class/power_supply/bms/cycle_count_complete",
            // OPPO/realme/一加
            "/sys/class/power_supply/battery/ohm_cycle_count",
            "/sys/class/power_supply/battery/battery_cycle_count",
            // vivo/iQOO
            "/sys/class/power_supply/battery/batt_cycle",
            "/sys/class/power_supply/bms/batt_cycle",
            // 华为/荣耀
            "/sys/class/power_supply/battery/cycle_count_flags",
            "/sys/class/power_supply/battery/charge_cycle",
            // 通用
            "/sys/class/power_supply/battery/health_cycle_count",
            "/sys/class/power_supply/battery/battery_health_cycle"
    };

    // 容量 sysfs 候选路径
    private static final String[] CHARGE_FULL_PATHS = {
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/maxfg/charge_full",
            "/sys/class/power_supply/battery/charge_full_raw",
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/fcc",
            // 厂商私有
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/bms/constant_charge_current_max",
            "/sys/class/power_supply/battery/batt_full_capacity",
            "/sys/class/power_supply/battery/learned_full_capacity",
            "/sys/class/power_supply/bms/learned_full_capacity",
            "/sys/class/power_supply/maxfg/learned_full_capacity",
            "/sys/class/power_supply/battery/fg_full_capacity"
    };

    // 设计容量 sysfs 候选路径（独立于 CHARGE_FULL_PATHS，用于 getDesignCapacity）
    private static final String[] DESIGN_CAPACITY_PATHS = {
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
            "/sys/class/power_supply/battery/design_capacity",
            "/sys/class/power_supply/bms/design_capacity",
            "/sys/class/power_supply/battery/design_capacity_full",
            "/sys/class/power_supply/battery/batt_design_capacity",
            "/sys/class/power_supply/bms/batt_design_capacity",
            "/sys/class/power_supply/battery/nominal_capacity",
            "/sys/class/power_supply/battery/battery_design_capacity",
            "/sys/class/power_supply/battery/fg_design_capacity"
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

    /**
     * 设置当前设备激活信息（用于计算基于使用时长的健康度估算）。由 Activity/Fragment 在创建后注入。
     */
    public void setActivationInfo(ActivationDateHelper.Result activation) {
        this.activation = activation;
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
        // 部分国产设备 EXTRA_VOLTAGE 返回 µV 而非 mV，需做合理性校验
        // 正常手机电池电压范围：2500-5000 mV（2.5-5.0 V）
        if (voltageMv > 10000) {
            // 值过大，大概率是 µV，转换为 mV
            voltageMv = voltageMv / 1000;
        }
        if (voltageMv <= 0 || voltageMv < 2500) {
            // intent 电压无效或异常，尝试 sysfs
            long voltageSysfs = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
            if (voltageSysfs > 1000000) {
                // sysfs 返回 µV
                voltageMv = (int) (voltageSysfs / 1000);
            } else if (voltageSysfs > 2500) {
                // sysfs 返回 mV
                voltageMv = (int) voltageSysfs;
            }
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
        info.setTechnology(technology.isEmpty() ? context.getString(R.string.battery_technology_default) : technology);

        // 7. 容量相关（关键：用于健康度）
        String[] designSource = new String[1];
        int designCapacity = getDesignCapacity(intent, designSource);
        int fullCapacity = getFullCapacity(batteryManager);
        int chargeCounterMah = getChargeCounter(batteryManager);

        info.setDesignCapacity(designCapacity);
        info.setDesignCapacitySource(designCapacity > 0 ? designSource[0] : "unknown");
        info.setCurrentCapacity(fullCapacity);
        info.setCurrentCapacitySource(fullCapacity > 0 ? "battery_manager_or_sysfs" : "unknown");
        info.setChargeCounter(chargeCounterMah * 1000); // uAh

        // 8. 健康度计算
        BatteryHealthResult health = calculateHealth(designCapacity, fullCapacity, chargeCounterMah, percentage);
        info.setHealthPercentage(health.healthPercentage);
        info.setHealthStatus(mapHealthStatusToCode(health.healthLevel));
        info.setHealthConfidence(health.confidence);
        info.setHealthDataSource(mapHealthDataSource(health.confidence));

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
     * 带单位判断：部分设备 BatteryManager 返回 mA 而非 µA。
     */
    public int readCurrentNow(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentRaw != Integer.MIN_VALUE && currentRaw != 0) {
                int absCurrent = Math.abs(currentRaw);
                // 正常充电电流：500mA-10A = 500000-10000000 µA
                if (absCurrent > 100000) {
                    // µA → mA
                    return currentRaw / 1000;
                } else {
                    // 已经是 mA
                    return currentRaw;
                }
            }
        }
        long sysfsRaw = readSysfsLong(CURRENT_NOW_PATHS, 0);
        if (sysfsRaw != 0) {
            long absRaw = Math.abs(sysfsRaw);
            if (absRaw > 100000) {
                return (int) (sysfsRaw / 1000); // µA → mA
            } else {
                return (int) sysfsRaw; // 已经是 mA
            }
        }
        return 0;
    }

    /**
     * 读取当前电压（mV）。
     * 带合理性校验：正常手机电池电压 2500-5000 mV。
     */
    public int readVoltageNow() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int voltageMv = intent != null ? intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
        // 部分国产设备返回 µV，需转换
        if (voltageMv > 10000) voltageMv = voltageMv / 1000;
        if (voltageMv > 0 && voltageMv >= 2500) return voltageMv;
        // sysfs 回退
        long voltageSysfs = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
        if (voltageSysfs > 1000000) return (int) (voltageSysfs / 1000);
        if (voltageSysfs > 2500) return (int) voltageSysfs;
        return (int) voltageSysfs;
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
     * 设计容量：优先用户校准值，其次本地数据库，再次 sysfs，再次 BatteryManager CHARGE_FULL 兜底。
     *
     * @param sourceHolder 长度为 1 的数组，用于回传容量来源（user_calibrated / device_database / sysfs / battery_manager / unknown）
     */
    private int getDesignCapacity(Intent intent, String[] sourceHolder) {
        // 1. 用户校准值（最优先）
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int calibrated = prefs.getInt(PREF_CALIBRATED_CAPACITY, -1);
        if (calibrated > 0) {
            if (sourceHolder != null && sourceHolder.length > 0) {
                sourceHolder[0] = "user_calibrated";
            }
            return calibrated;
        }

        // 2. 本地机型数据库（最准确）
        int dbCapacity = deviceDb.getDesignCapacity();
        if (dbCapacity > 0) {
            if (sourceHolder != null && sourceHolder.length > 0) {
                sourceHolder[0] = "device_database";
            }
            return dbCapacity;
        }

        // 3. sysfs 多路径
        int design = readSysfsInt(DESIGN_CAPACITY_PATHS, -1);
        if (design > 100000) {
            // sysfs 返回值单位为 uAh（如 4500000），需转换为 mAh
            if (sourceHolder != null && sourceHolder.length > 0) {
                sourceHolder[0] = "sysfs";
            }
            return design / 1000;
        } else if (design > 100) {
            // sysfs 直接返回 mAh（如 4500）
            if (sourceHolder != null && sourceHolder.length > 0) {
                sourceHolder[0] = "sysfs";
            }
            return design;
        }

        // 4. BatteryManager BATTERY_PROPERTY_CHARGE_FULL（部分设备该值接近设计容量）
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            int microAh = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_FULL);
            if (microAh != Integer.MIN_VALUE && microAh > 1000) {
                int mah = microAh / 1000;
                // 仅当值在合理范围内（1000-10000 mAh）才采纳
                if (mah >= 1000 && mah <= 10000) {
                    if (sourceHolder != null && sourceHolder.length > 0) {
                        sourceHolder[0] = "battery_manager";
                    }
                    return mah;
                }
            }
        }

        if (sourceHolder != null && sourceHolder.length > 0) {
            sourceHolder[0] = "unknown";
        }
        return -1;
    }

    /**
     * 满充容量（FCC）：优先 BatteryManager，其次 sysfs。
     * 带合理性校验：正常手机电池满充容量 1000-10000 mAh。
     */
    private int getFullCapacity(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int microAh = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_FULL);
            if (microAh != Integer.MIN_VALUE && microAh > 1000) {
                int mah = microAh / 1000;
                if (mah >= 1000 && mah <= 10000) return mah;
                // 如果 mah 不在合理范围，可能 microAh 本身就是 mAh 单位
                if (microAh >= 1000 && microAh <= 10000) return microAh;
            }
        }
        int full = readSysfsInt(CHARGE_FULL_PATHS, -1);
        if (full > 100000) return full / 1000; // µAh → mAh
        if (full > 100) return full; // 已经是 mAh
        return -1;
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
     * 读取循环次数：系统 API + sysfs + 充电历史估算 + 使用天数估算兜底。
     */
    private int readCycleCount(BatteryManager batteryManager) {
        // 1. BatteryManager 循环次数 API（Android 12+ 部分设备已支持，不仅限 14+）
        if (batteryManager != null) {
            try {
                int count = batteryManager.getIntProperty(BATTERY_PROP_CYCLE_COUNT);
                if (count > 0 && count < 10000) return count;
            } catch (Exception ignored) {
            }
        }

        // 2. sysfs 多路径
        int count = readSysfsInt(CYCLE_COUNT_PATHS, -1);
        if (count > 0 && count < 10000) return count;

        // 3. 基于充电历史估算（从数据库统计完整 0→100% 充电次数）
        int estimatedCycles = estimateCycleCountFromHistory();
        if (estimatedCycles > 0) return estimatedCycles;

        // 4. 基于使用天数估算兜底（假设每天约 0.8 次完整充电循环）
        int effectiveUsageDays = usageDays;
        if (effectiveUsageDays < 0 && activation != null) {
            effectiveUsageDays = activation.usageDays;
        }
        if (effectiveUsageDays > 0) {
            return Math.max(1, (int) (effectiveUsageDays * 0.8f));
        }

        return -1;
    }

    /**
     * 从数据库历史记录估算循环次数。
     * 简化方案：按天去重，同一天内无论充几次只算 1 次。
     * 判断依据：存在从低电量（<20%）开始充电，到拔掉充电器时电量 >80% 的完整会话。
     */
    private int estimateCycleCountFromHistory() {
        try {
            com.batteryhealth.app.BatteryHealthApplication app =
                    (com.batteryhealth.app.BatteryHealthApplication) context.getApplicationContext();
            if (app == null) return -1;
            AppDatabase db = app.getDatabase();
            if (db == null) return -1;

            // 取最近 180 天数据
            long startTime = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
            List<BatteryInfo> records = db.batteryInfoDao().getSince(startTime);
            if (records == null || records.size() < 5) return -1;

            java.util.Set<String> cycleDays = new java.util.HashSet<>();
            boolean wasLow = false;
            boolean wasCharging = false;
            int lastLevel = -1;

            for (BatteryInfo info : records) {
                int level = info.getLevel();
                int status = info.getStatus();
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                long ts = info.getTimestamp();
                String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(new java.util.Date(ts));

                if (level < 20) {
                    wasLow = true;
                }

                if (wasLow && isCharging) {
                    wasCharging = true;
                }

                // 从充电变为不充电，且电量 >80%，算作一次完整充电会话
                if (wasLow && wasCharging && !isCharging && level > 80) {
                    cycleDays.add(day);
                    wasLow = false;
                    wasCharging = false;
                }

                lastLevel = level;
            }

            return cycleDays.isEmpty() ? -1 : cycleDays.size();
        } catch (Exception e) {
            Log.d(TAG, "Failed to estimate cycle count from history: " + e.getMessage());
            return -1;
        }
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
                result.source = context.getString(R.string.battery_source_original);
                result.confidence = 0.75f;
            } else {
                result.source = context.getString(R.string.battery_source_third_party);
                result.confidence = 0.45f;
            }
        }

        // 2. 容量偏差校验
        if (designCapacity > 0 && fullCapacity > 0) {
            float ratio = fullCapacity / (float) designCapacity;
            if (ratio < 0.55f || ratio > 1.25f) {
                // 容量严重偏离官方规格，强烈怀疑非原装
                result.source = context.getString(R.string.battery_source_third_party);
                result.confidence = Math.min(result.confidence + 0.25f, 0.95f);
            } else if (ratio >= 0.85f && ratio <= 1.05f && result.confidence < 0.85f) {
                result.source = context.getString(R.string.battery_source_original);
                result.confidence = 0.85f;
            }
        }

        // 3. 无法获取任何有效信息
        if (result.source == null || result.source.isEmpty()) {
            result.source = context.getString(R.string.battery_source_unverifiable);
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
        return readSysfsString(SERIAL_PATHS, context.getString(R.string.status_unknown));
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

        // 优先从注入的激活信息中读取使用天数
        int effectiveUsageDays = usageDays;
        if (effectiveUsageDays < 0 && activation != null) {
            effectiveUsageDays = activation.usageDays;
        }

        if (fullCapacity > 0 && designCapacity > 0) {
            // 最可信：FCC / 设计容量
            float health = (fullCapacity / (float) designCapacity) * 100f;
            result.healthPercentage = clampHealth(health);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel);
            // 用户校准值置信度略低于实测 sysfs，但高于数据库兜底
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isCalibrated = prefs.getInt(PREF_CALIBRATED_CAPACITY, -1) > 0;
            result.confidence = isCalibrated ? 0.90f : 0.95f;
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

        // 3. 兜底 1：基于使用天数的经验估算（仅用于完全无容量数据的场景，置信度低）
        if (effectiveUsageDays > 0 && designCapacity > 0) {
            // 锂电池典型衰减：约 7%/年（365 天），使用 4 年后约 75%
            float estimatedHealth = 100f - (effectiveUsageDays * 0.018f);
            result.healthPercentage = clampHealth(estimatedHealth);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel) + context.getString(R.string.confidence_format, 35);
            result.confidence = 0.35f;
            return result;
        }

        // 4. 兜底 2：仅有使用天数无设计容量，给出参考值
        if (effectiveUsageDays > 0) {
            float estimatedHealth = 100f - (effectiveUsageDays * 0.018f);
            result.healthPercentage = clampHealth(estimatedHealth);
            result.healthLevel = getHealthLevel(result.healthPercentage);
            result.healthStatus = getHealthStatusString(result.healthLevel) + context.getString(R.string.confidence_format, 20);
            result.confidence = 0.20f;
            return result;
        }

        // 没有任何容量信息时，无法计算，返回未知
        result.healthPercentage = -1;
        result.healthLevel = context.getString(R.string.health_unknown);
        result.healthStatus = context.getString(R.string.health_status_no_data);
        result.confidence = 0.0f;
        return result;
    }

    private float clampHealth(float value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private String getHealthLevel(float percentage) {
        if (percentage < 0) return context.getString(R.string.health_unknown);
        if (percentage >= 95) return context.getString(R.string.health_excellent);
        if (percentage >= 85) return context.getString(R.string.health_good);
        if (percentage >= 75) return context.getString(R.string.health_average);
        if (percentage >= 60) return context.getString(R.string.health_poor);
        return context.getString(R.string.health_very_poor);
    }

    private String getHealthStatusString(String level) {
        if (level.equals(context.getString(R.string.health_excellent))) {
            return context.getString(R.string.health_status_excellent);
        } else if (level.equals(context.getString(R.string.health_good))) {
            return context.getString(R.string.health_status_good);
        } else if (level.equals(context.getString(R.string.health_average))) {
            return context.getString(R.string.health_status_average);
        } else if (level.equals(context.getString(R.string.health_poor))) {
            return context.getString(R.string.health_status_poor);
        } else if (level.equals(context.getString(R.string.health_very_poor))) {
            return context.getString(R.string.health_status_very_poor);
        } else {
            return context.getString(R.string.health_status_unknown);
        }
    }

    /**
     * 将健康等级映射为数据库存储的代码。
     */
    private String mapHealthStatusToCode(String level) {
        String excellent = context.getString(R.string.health_excellent);
        String good = context.getString(R.string.health_good);
        String average = context.getString(R.string.health_average);
        String poor = context.getString(R.string.health_poor);
        String veryPoor = context.getString(R.string.health_very_poor);
        if (level.equals(excellent) || level.equals(good)) {
            return "good";
        } else if (level.equals(average)) {
            return "normal";
        } else if (level.equals(poor)) {
            return "warning";
        } else if (level.equals(veryPoor)) {
            return "poor";
        } else {
            return "unknown";
        }
    }

    /**
     * 将电池来源描述映射为数据库存储代码。
     */
    private String mapSourceToCode(String source) {
        if (source == null) return "unknown";
        if (source.equals(context.getString(R.string.battery_source_original))) {
            return "original";
        } else if (source.equals(context.getString(R.string.battery_source_third_party))) {
            return "third_party";
        } else {
                return "unknown";
        }
    }

    /**
     * 根据置信度给出数据来源标签。
     */
    private String mapHealthDataSource(float confidence) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isCalibrated = prefs.getInt(PREF_CALIBRATED_CAPACITY, -1) > 0;
        if (isCalibrated && confidence >= 0.85f) return "user_calibrated";
        if (confidence >= 0.95f) return "fcc_ratio";
        if (confidence >= 0.70f) return "charge_counter_ratio";
        if (confidence >= 0.30f) return "usage_days_estimate";
        if (confidence > 0f) return "usage_only_estimate";
        return "unknown";
    }

    /**
     * 格式化循环次数显示文本。
     */
    public String formatCycleCount(BatteryInfo info) {
        if (info == null || !info.hasValidCycleCount()) return context.getString(R.string.cycle_count_unreadable);
        if (info.isCycleCountEstimated()) {
            return String.format(Locale.getDefault(), context.getString(R.string.cycle_count_estimate_format), info.getCycleCount());
        }
        return String.format(Locale.getDefault(), context.getString(R.string.cycle_count_format), info.getCycleCount());
    }

    private String getStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return context.getString(R.string.status_charging);
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return context.getString(R.string.status_discharging);
            case BatteryManager.BATTERY_STATUS_FULL:
                return context.getString(R.string.status_fully_charged);
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return context.getString(R.string.status_not_charging_short);
            default:
                return context.getString(R.string.status_unknown);
        }
    }

    private String getChargeTypeString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return context.getString(R.string.charging_status_ac);
            case BatteryManager.BATTERY_PLUGGED_USB:
                return context.getString(R.string.charging_status_usb);
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return context.getString(R.string.charging_status_wireless);
            case BatteryManager.BATTERY_PLUGGED_DOCK:
                return "Dock";
            default:
                return context.getString(R.string.status_not_charging_short);
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
        if (powerW >= 100) return context.getString(R.string.charge_type_ultra_fast);
        if (powerW >= 60) return context.getString(R.string.charge_type_extreme_fast);
        if (powerW >= 30) return context.getString(R.string.charge_type_fast);
        if (powerW >= 10) return context.getString(R.string.charge_type_standard);
        if (powerW > 0) return context.getString(R.string.charge_type_slow);
        return context.getString(R.string.status_not_charging_short);
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
        if (info == null) return context.getString(R.string.status_unknown);
        float conf = info.getHealthConfidence();
        String source = info.getHealthDataSource();
        if ("user_calibrated".equals(source)) return context.getString(R.string.health_source_user_calibrated, (int) (conf * 100));
        if ("fcc_ratio".equals(source)) return context.getString(R.string.health_source_fcc_ratio, (int) (conf * 100));
        if ("charge_counter_ratio".equals(source)) return context.getString(R.string.health_source_charge_counter, (int) (conf * 100));
        if ("usage_days_estimate".equals(source)) return context.getString(R.string.health_source_usage_days, (int) (conf * 100));
        if ("usage_only_estimate".equals(source)) return context.getString(R.string.health_source_usage_only, (int) (conf * 100));
        if (conf > 0) return context.getString(R.string.health_source_estimate, (int) (conf * 100));
        return context.getString(R.string.health_source_unavailable);
    }

    private String formatBatterySource(BatteryInfo info) {
        if (info == null) return context.getString(R.string.status_unknown);
        String source = info.getBatterySource();
        float conf = info.getBatterySourceConfidence();
        if ("original".equals(source)) return context.getString(R.string.battery_source_original_confidence, (int) (conf * 100));
        if ("third_party".equals(source)) return context.getString(R.string.battery_source_third_party_confidence, (int) (conf * 100));
        return context.getString(R.string.battery_source_unverifiable);
    }

    // endregion
}
