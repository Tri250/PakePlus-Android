package com.batteryhealth.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 电池数据管理器（重写版 v4.5.0 (Android 16 / ColorOS 16)）。
 *
 * 数据采集与计算逻辑：
 *  1. 设计容量优先级：用户校准 > 机型数据库 > sysfs 设计容量节点。
 *  2. 当前满充容量（FCC）：BatteryManager BATTERY_PROPERTY_CHARGE_FULL + sysfs charge_full。
 *  3. 健康度三段损耗：出厂损耗（设计 vs 首充 FCC）+ 循环损耗（FCC vs 当前 FCC）+ 兜底估算。
 *  4. 中值滤波：5 次采样取中值，避免瞬时波动影响显示。
 *  5. 充电计数法：仅在电量 >= 60% 时启用，置信度根据电量梯度提升。
 *  6. 循环次数：sysfs 多节点 + BatteryManager + 历史充电会话推算。
 *  7. 电池来源：基于 psy-info / factory_serial / oem-info / 设计容量偏差多维验证。
 */
public class BatteryDataManager {

    private static final String TAG = "BatteryDataManager";

    public static final String PREFS_NAME = "battery_health_prefs";
    public static final String PREF_CALIBRATED_CAPACITY = "calibrated_capacity_mah";

    private final Context context;
    private final DeviceDatabaseManager deviceDb;
    private ActivationDateHelper.Result activation;

    private volatile BatteryInfo currentBatteryInfo;
    private int usageDays = -1;

    private String chargingStatusText;
    private String healthSourceText;
    private String batterySourceText;

    // 中值滤波缓冲
    private final List<Float> healthBuffer = new ArrayList<>();
    private static final int MEDIAN_WINDOW = 5;

    public BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceDb = DeviceDatabaseManager.getInstance(this.context);
        this.chargingStatusText = this.context.getString(R.string.status_unknown);
        this.healthSourceText = this.context.getString(R.string.status_unknown);
        this.batterySourceText = this.context.getString(R.string.status_unknown);
    }

    private static final int BATTERY_PROP_CYCLE_COUNT = 7;
    private static final int BATTERY_PROP_CHARGE_FULL = getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL", 24);
    private static final int BATTERY_PROP_CHARGE_COUNTER = getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_COUNTER", 6);

    // Android 16 (API 36) 新增常量
    private static final int BATTERY_PROPERTY_BATTERY_HEALTH = getBatteryIntConstant("BATTERY_PROPERTY_BATTERY_HEALTH", 8);
    private static final int BATTERY_PROPERTY_CHARGE_FULL_DESIGN = getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL_DESIGN", 9);

    // 设计容量候选
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
            "/sys/class/power_supply/battery/fg_design_capacity",
            "/sys/class/power_supply/bms/fg_design_capacity",
            "/sys/class/power_supply/battery/fg_nominal_capacity"
    };

    // 当前满充容量（FCC）候选
    private static final String[] CHARGE_FULL_PATHS = {
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/maxfg/charge_full",
            "/sys/class/power_supply/battery/charge_full_raw",
            "/sys/class/power_supply/battery/fcc",
            "/sys/class/power_supply/battery/learned_full_capacity",
            "/sys/class/power_supply/bms/learned_full_capacity",
            "/sys/class/power_supply/maxfg/learned_full_capacity",
            "/sys/class/power_supply/battery/fg_full_capacity",
            "/sys/class/power_supply/battery/learned_capacity",
            "/sys/class/power_supply/bms/learned_capacity",
            "/sys/class/power_supply/battery/fg_learned_capacity"
    };

    // 循环次数候选
    private static final String[] CYCLE_COUNT_PATHS = {
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/battery/cyclecount",
            "/sys/class/power_supply/bms/battery_cycle",
            "/sys/class/power_supply/battery/store_full_cc"
    };

    // 厂商私有循环次数节点
    private static final String[] CYCLE_COUNT_VENDOR_PATHS = {
            "/sys/class/power_supply/battery/battery_cycle_count",          // OPPO/realme/一加
            "/sys/class/power_supply/battery/charge_cycle",                  // 部分华为
            "/sys/class/power_supply/battery/cycle_count_complete",          // 小米
            "/sys/class/power_supply/bms/cycle_count_complete",              // 小米 BMS
            "/sys/class/power_supply/battery/batt_cycle",                    // vivo
            "/sys/class/power_supply/bms/batt_cycle",                        // vivo BMS
            "/sys/class/power_supply/battery/charge_full_cycles",
            "/sys/class/power_supply/battery/mmi_cycle_count",
            "/sys/class/power_supply/battery/batt_cycle_count",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/battery/cycle_count_details",
            "/sys/class/power_supply/battery/fg_cycle_count"
    };

    private static final String[] CURRENT_NOW_PATHS = {
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/maxfg/current_now",
            "/sys/class/power_supply/usb/current_now",
            "/sys/class/power_supply/battery/input_current_now",
            "/sys/class/power_supply/battery/constant_charge_current"
    };

    private static final String[] VOLTAGE_NOW_PATHS = {
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/bms/voltage_now",
            "/sys/class/power_supply/maxfg/voltage_now",
            "/sys/class/power_supply/usb/voltage_now",
            "/sys/class/power_supply/battery/voltage_ocv"
    };

    private static final String[] TEMP_PATHS = {
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/battery/batt_temp",
            "/sys/class/power_supply/bms/temp"
    };

    private static final String[] SERIAL_PATHS = {
            "/sys/class/power_supply/battery/serial_number",
            "/sys/class/power_supply/bms/serial_number",
            "/sys/class/power_supply/battery/batt_serial_number"
    };

    private static final String[] TECH_PATHS = {
            "/sys/class/power_supply/battery/technology",
            "/sys/class/power_supply/bms/technology"
    };

    // psy 节点（用于电池来源验证）
    private static final String[] PSY_INFO_PATHS = {
            "/sys/class/power_supply/bms/psy_info",
            "/sys/class/power_supply/battery/psy_info",
            "/sys/class/power_supply/maxfg/psy_info"
    };
    private static final String[] FACTORY_SERIAL_PATHS = {
            "/sys/class/power_supply/battery/factory_serial",
            "/sys/class/power_supply/bms/factory_serial",
            "/sys/class/power_supply/battery/oem_info",
            "/sys/class/power_supply/bms/oem_info",
            "/sys/class/power_supply/battery/oem-serial",
            "/sys/class/power_supply/bms/oem-serial"
    };
    private static final String[] MANUFACTURER_INFO_PATHS = {
            "/sys/class/power_supply/battery/manufacturer",
            "/sys/class/power_supply/bms/manufacturer",
            "/sys/class/power_supply/battery/company",
            "/sys/class/power_supply/bms/company"
    };

    // 旁路充电检测节点（ColorOS 16 特性：充电器直接供电给设备，不经过电池）
    private static final String[] BYPASS_CHARGING_PATHS = {
            "/sys/class/power_supply/battery/bypass_charging",
            "/sys/class/power_supply/usb/bypass_charging"
    };

    public void setActivationInfo(ActivationDateHelper.Result activation) {
        this.activation = activation;
    }

    /**
     * 主入口：构造完整电池信息。
     * 包含大量 sysfs 文件读取，应始终在后台线程调用！
     */
    @androidx.annotation.WorkerThread
    public BatteryInfo getBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        info.setTimestamp(System.currentTimeMillis());
        info.setDeviceModel(Build.MODEL);
        info.setDeviceBrand(Build.BRAND);

        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return info;

        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        // 1. 基础电量与状态
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percentage = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;
        info.setLevel(percentage);

        info.setStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN));
        info.setPlugged(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));

        // 2. 温度
        int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (tempRaw < 0) tempRaw = readSysfsInt(TEMP_PATHS, -1);
        // EXTRA_TEMPERATURE 单位 0.1°C
        info.setTemperature(tempRaw / 10.0f);

        // 3. 电压
        int voltageMv = readVoltage(intent);
        info.setVoltage(voltageMv);

        // 4. 电流
        int currentMa = readCurrentNow(batteryManager);
        info.setCurrentNow(currentMa * 1000); // uA

        // 5. 功率
        float powerW = calculatePower(voltageMv, currentMa);
        info.setChargingPower(powerW);
        info.setChargingVoltage(voltageMv / 1000.0f);
        info.setChargingCurrent(Math.abs(currentMa) / 1000.0f);

        // 6. 技术
        String technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null || technology.isEmpty()) {
            technology = readSysfsString(TECH_PATHS, "");
        }
        info.setTechnology(technology.isEmpty() ? context.getString(R.string.battery_technology_default) : technology);

        // 7. 设计容量
        String[] designSourceHolder = new String[1];
        int designCapacity = getDesignCapacity(designSourceHolder);
        info.setDesignCapacity(designCapacity);
        info.setDesignCapacitySource(designCapacity > 0 ? designSourceHolder[0] : "unknown");

        // 8. 当前满充容量（FCC）
        int fullCapacity = getFullCapacity(batteryManager);
        info.setCurrentCapacity(fullCapacity);
        info.setCurrentCapacitySource(fullCapacity > 0 ? "battery_manager_or_sysfs" : "unknown");

        // 9. 充电计数
        int chargeCounterMah = getChargeCounterMah(batteryManager);
        info.setChargeCounter(chargeCounterMah * 1000);

        // 10. 健康度（三段损耗 + 中值滤波）
        BatteryHealthResult health = calculateHealth(designCapacity, fullCapacity, chargeCounterMah, percentage);
        // 中值滤波
        float filteredHealth = applyMedianFilter(health.healthPercentage);
        info.setHealthPercentage(filteredHealth);
        info.setHealthStatus(mapHealthStatusToCode(health.healthLevel));
        info.setHealthConfidence(health.confidence);
        info.setHealthDataSource(mapHealthDataSource(health.confidence, health.sourceTag));
        info.setFactoryLossPercent(health.factoryLossPercent);
        info.setCycleLossPercent(health.cycleLossPercent);
        info.setUsageLossPercent(health.usageLossPercent);

        // 11. 循环次数
        int cycleCount = readCycleCount(batteryManager);
        info.setCycleCount(cycleCount);
        info.setCycleCountEstimated(cycleCount < 0);
        info.setCycleCountSource(cycleCount >= 0 ? "sysfs_or_battery_manager" : "unavailable");

        // 12. 电池来源（多维验证）
        BatterySourceResult source = determineBatterySource(intent, fullCapacity, designCapacity);
        info.setBatterySource(mapSourceToCode(source.source));
        info.setBatterySourceConfidence(source.confidence);
        info.setBatterySourceReason(source.reason);

        // 13. 序列号
        info.setBatterySerial(readBatterySerial(intent));

        // 14. 系统健康
        info.setSystemHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN));

        return info;
    }

    // region 电压

    private int readVoltage(Intent intent) {
        int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        // 部分国产设备返回 µV（数值远超 5000）
        if (voltageMv > 10000) voltageMv = voltageMv / 1000;
        if (voltageMv > 0 && voltageMv >= 2500 && voltageMv <= 6000) return voltageMv;

        long voltageSysfs = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
        if (voltageSysfs > 1000000) return (int) (voltageSysfs / 1000); // µV
        if (voltageSysfs > 2500) return (int) voltageSysfs;             // mV
        return -1;
    }

    // endregion

    // region 电流

    public int readCurrentNow(BatteryManager batteryManager) {
        long sysfsRaw = readSysfsLong(CURRENT_NOW_PATHS, Long.MIN_VALUE);
        if (sysfsRaw != Long.MIN_VALUE && sysfsRaw != 0) {
            long absRaw = Math.abs(sysfsRaw);
            if (absRaw > 100000) return (int) (sysfsRaw / 1000); // µA → mA
            return (int) sysfsRaw;                                // mA
        }
        if (batteryManager != null) {
            try {
                int currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentRaw != Integer.MIN_VALUE && currentRaw != 0) {
                    int absCurrent = Math.abs(currentRaw);
                    if (absCurrent > 100000) return currentRaw / 1000;
                    return currentRaw;
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private float calculatePower(int voltageMv, int currentMa) {
        if (voltageMv <= 0) return 0f;
        return (voltageMv * Math.abs(currentMa)) / 1_000_000.0f;
    }

    // endregion

    // region 设计容量

    private int getDesignCapacity(String[] sourceHolder) {
        // 1. 用户校准（最高优先级）
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int calibrated = prefs.getInt(PREF_CALIBRATED_CAPACITY, -1);
        if (calibrated > 0) {
            if (sourceHolder != null) sourceHolder[0] = "user_calibrated";
            return calibrated;
        }

        // 2. 机型数据库（最准确）
        int dbCapacity = deviceDb.getDesignCapacity();
        if (dbCapacity > 0) {
            if (sourceHolder != null) sourceHolder[0] = "device_database";
            return dbCapacity;
        }

        // 2.5 Android 16+ 原生设计容量（API 36 BATTERY_PROPERTY_CHARGE_FULL_DESIGN）
        if (Build.VERSION.SDK_INT >= 36) {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                try {
                    int designMicroAh = batteryManager.getIntProperty(BATTERY_PROPERTY_CHARGE_FULL_DESIGN);
                    if (designMicroAh != Integer.MIN_VALUE && designMicroAh > 1000) {
                        if (sourceHolder != null) sourceHolder[0] = "android16_native_design";
                        // µAh → mAh
                        if (designMicroAh >= 1_000_000) return designMicroAh / 1000;
                        return designMicroAh;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 3. sysfs 设计容量节点
        int design = readSysfsInt(DESIGN_CAPACITY_PATHS, -1);
        if (design > 100000) {
            if (sourceHolder != null) sourceHolder[0] = "sysfs";
            return design / 1000; // µAh → mAh
        }
        if (design > 100) {
            if (sourceHolder != null) sourceHolder[0] = "sysfs";
            return design;
        }
        if (sourceHolder != null) sourceHolder[0] = "unknown";
        return -1;
    }

    // endregion

    // region 满充容量

    private int getFullCapacity(BatteryManager batteryManager) {
        if (batteryManager != null) {
            try {
                int microAh = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_FULL);
                if (microAh != Integer.MIN_VALUE && microAh > 1000) {
                    if (microAh >= 1_000_000 && microAh <= 10_000_000) return microAh / 1000;
                    if (microAh >= 1000 && microAh <= 10000) return microAh;
                }
            } catch (Exception ignored) {
            }
        }
        int full = readSysfsInt(CHARGE_FULL_PATHS, -1);
        if (full > 100000) return full / 1000;
        if (full > 100) return full;
        // learned_capacity 节点（ColorOS 16 / OPPO / 现代 BMS）
        int learned = readSysfsInt(new String[]{
                "/sys/class/power_supply/battery/learned_capacity",
                "/sys/class/power_supply/bms/learned_capacity"
        }, -1);
        if (learned > 100000) return learned / 1000;
        if (learned > 100) return learned;
        return -1;
    }

    // endregion

    // region 充电计数

    private int getChargeCounterMah(BatteryManager batteryManager) {
        if (batteryManager != null) {
            try {
                int raw = batteryManager.getIntProperty(BATTERY_PROP_CHARGE_COUNTER);
                if (raw != Integer.MIN_VALUE && raw != 0) {
                    int abs = Math.abs(raw);
                    if (abs > 100_000) return abs / 1000;
                    return abs;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    // endregion

    // region 循环次数

    private int readCycleCount(BatteryManager batteryManager) {
        // 1. BatteryManager 官方 API
        if (batteryManager != null) {
            try {
                int count = batteryManager.getIntProperty(BATTERY_PROP_CYCLE_COUNT);
                if (count > 0 && count < 20000) return count;
            } catch (Exception ignored) {
            }
        }
        // 2. sysfs 标准节点
        int count = readSysfsInt(CYCLE_COUNT_PATHS, -1);
        if (count > 0 && count < 20000) return count;
        // 3. 厂商私有节点
        count = readSysfsInt(CYCLE_COUNT_VENDOR_PATHS, -1);
        if (count > 0 && count < 20000) return count;
        // 4. 历史充电会话推算
        int estimatedCycles = estimateCycleCountFromHistory();
        if (estimatedCycles > 0) return estimatedCycles;
        // 5. 使用天数兜底
        int effectiveUsageDays = usageDays;
        if (effectiveUsageDays < 0 && activation != null) effectiveUsageDays = activation.usageDays;
        if (effectiveUsageDays > 0) {
            int estimate = Math.max(1, (int) (effectiveUsageDays * 0.65f));
            return Math.min(estimate, effectiveUsageDays);
        }
        return -1;
    }

    private int estimateCycleCountFromHistory() {
        try {
            com.batteryhealth.app.BatteryHealthApplication app =
                    (com.batteryhealth.app.BatteryHealthApplication) context.getApplicationContext();
            if (app == null) return -1;
            com.batteryhealth.app.data.database.AppDatabase db = app.getDatabase();
            if (db == null) return -1;

            long startTime = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
            List<BatteryInfo> records = db.batteryInfoDao().getSince(startTime);
            if (records == null || records.size() < 5) return -1;

            java.util.Set<String> cycleDays = new java.util.HashSet<>();
            boolean wasLow = false;
            boolean wasCharging = false;

            for (BatteryInfo info : records) {
                int level = info.getLevel();
                int status = info.getStatus();
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                long ts = info.getTimestamp();
                String day = new java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                        .format(new java.util.Date(ts));

                if (level < 20) wasLow = true;
                if (wasLow && isCharging) wasCharging = true;
                if (wasLow && wasCharging && !isCharging && level > 80) {
                    cycleDays.add(day);
                    wasLow = false;
                    wasCharging = false;
                }
            }
            return cycleDays.isEmpty() ? -1 : cycleDays.size();
        } catch (Exception e) {
            Log.d(TAG, "estimateCycleCountFromHistory failed: " + e.getMessage());
            return -1;
        }
    }

    // endregion

    // region 电池来源（多维验证）

    private BatterySourceResult determineBatterySource(Intent intent, int fullCapacity, int designCapacity) {
        BatterySourceResult result = new BatterySourceResult();
        result.confidence = 0f;
        Map<String, Float> signals = new HashMap<>();

        // 信号 1: psy_info / oem_info / factory_serial（厂商留下的原厂标识）
        String vendorInfo = readSysfsString(PSY_INFO_PATHS, "");
        if (vendorInfo.isEmpty()) vendorInfo = readSysfsString(FACTORY_SERIAL_PATHS, "");
        if (!vendorInfo.isEmpty()) {
            // 厂商标识符通常包含品牌或厂内编码；非空即"疑似原厂"
            if (looksLikeOemSerial(vendorInfo)) {
                signals.put("vendor_serial", 0.4f);
            } else {
                signals.put("vendor_serial", -0.3f);
            }
        }

        // 信号 2: manufacturer / company 字段
        String mfg = readSysfsString(MANUFACTURER_INFO_PATHS, "");
        if (!mfg.isEmpty()) {
            if (mfg.toLowerCase(Locale.ROOT).matches(".*(coslight|sunwoda(desay|scud|desay)|byd|at|lg|chem|sanyo|tdk).*")) {
                signals.put("manufacturer", 0.3f);
            } else if (mfg.equalsIgnoreCase("unknown") || mfg.equalsIgnoreCase("0")) {
                signals.put("manufacturer", -0.1f);
            }
        }

        // 信号 3: BatteryManager 序列号
        String serial = readBatterySerial(intent);
        if (serial != null && !serial.isEmpty() && !serial.equalsIgnoreCase("unknown")) {
            // 仅当序列号格式与原厂规则一致时计正分
            if (isValidOemSerialFormat(serial)) {
                signals.put("serial_format", 0.25f);
            } else {
                signals.put("serial_format", -0.35f);
            }
        }

        // 信号 4: 容量偏差
        if (designCapacity > 0 && fullCapacity > 0) {
            float ratio = fullCapacity / (float) designCapacity;
            if (ratio >= 0.85f && ratio <= 1.05f) {
                signals.put("capacity_ratio", 0.3f);
            } else if (ratio >= 0.55f && ratio <= 1.25f) {
                signals.put("capacity_ratio", 0f);
            } else {
                signals.put("capacity_ratio", -0.5f); // 严重偏离
            }
        }

        // 信号 5: 机型数据库匹配（数据库里有这台机型的容量基准）
        if (deviceDb.findDevice() != null) {
            signals.put("device_database_match", 0.2f);
        } else {
            signals.put("device_database_match", -0.1f);
        }

        // 汇总
        float total = 0f;
        for (float v : signals.values()) total += v;
        if (total >= 0.5f) {
            result.source = context.getString(R.string.battery_source_original);
            result.confidence = Math.min(0.95f, 0.6f + total * 0.1f);
            result.reason = "综合多项原厂标识通过";
        } else if (total <= -0.3f) {
            result.source = context.getString(R.string.battery_source_third_party);
            result.confidence = Math.min(0.9f, 0.55f - total * 0.1f);
            result.reason = "存在明显非原厂特征";
        } else {
            result.source = context.getString(R.string.battery_source_unverifiable);
            result.confidence = 0f;
            result.reason = "原厂标识不足";
        }
        return result;
    }

    /**
     * OEM 序列号格式校验：原厂序列号通常由字母+数字组成，长度 10-20，区分大小写。
     * 纯数字（≤8 位）或纯字母（≤3 位）视为可疑。
     */
    private boolean isValidOemSerialFormat(String serial) {
        if (serial == null || serial.length() < 10 || serial.length() > 24) return false;
        int letters = 0, digits = 0;
        for (int i = 0; i < serial.length(); i++) {
            char c = serial.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else return false; // 含非字母数字
        }
        return letters >= 3 && digits >= 3;
    }

    private boolean looksLikeOemSerial(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 8 || t.length() > 64) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') continue;
            return false;
        }
        return true;
    }

    // endregion

    // region 健康度计算（核心）

    private BatteryHealthResult calculateHealth(int designCapacity, int fullCapacity, int chargeCounter, int percentage) {
        BatteryHealthResult r = new BatteryHealthResult();

        int effectiveUsageDays = usageDays;
        if (effectiveUsageDays < 0 && activation != null) effectiveUsageDays = activation.usageDays;

        // === 路径 0: Android 16+ 原生健康度百分比（最高优先级） ===
        if (Build.VERSION.SDK_INT >= 36) {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                try {
                    int nativeHealth = batteryManager.getIntProperty(BATTERY_PROPERTY_BATTERY_HEALTH);
                    if (nativeHealth != Integer.MIN_VALUE && nativeHealth >= 0 && nativeHealth <= 100) {
                        r.healthPercentage = nativeHealth;
                        r.sourceTag = "android16_native_health";
                        r.cycleLossPercent = Math.max(0f, 100f - nativeHealth);
                        r.confidence = 0.98f;
                        r.healthLevel = getHealthLevel(r.healthPercentage);
                        r.healthStatus = getHealthStatusString(r.healthLevel);
                        return r;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // === 路径 1: FCC / 设计容量 ===
        if (fullCapacity > 0 && designCapacity > 0) {
            float ratio = (fullCapacity / (float) designCapacity) * 100f;
            r.healthPercentage = clampHealth(ratio);
            r.sourceTag = "fcc_ratio";

            // 出厂损耗 = 1 - 首次开机 FCC / 设计容量（无法获取首次 FCC 时记 0）
            r.factoryLossPercent = 0f;
            // 循环损耗 = 1 - 当前 FCC / 设计容量
            r.cycleLossPercent = Math.max(0f, 100f - ratio);
            // 使用时长损耗（仅在缺乏循环数据时显著）
            int cycleInfo = -1; // 留作未来扩展
            if (cycleInfo < 0 && effectiveUsageDays > 0) {
                // 仅当 ratio 极低时，提示使用时长损耗
                r.usageLossPercent = Math.max(0f, 100f - ratio) * 0.1f;
            }

            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isCalibrated = prefs.getInt(PREF_CALIBRATED_CAPACITY, -1) > 0;
            r.confidence = isCalibrated ? 0.9f : 0.95f;
            r.healthLevel = getHealthLevel(r.healthPercentage);
            r.healthStatus = getHealthStatusString(r.healthLevel);
            return r;
        }

        // === 路径 2: 充电计数法（仅在电量 >= 60% 时启用） ===
        if (chargeCounter > 0 && percentage >= 60 && designCapacity > 0) {
            float currentMax = chargeCounter / (percentage / 100f);
            float ratio = (currentMax / designCapacity) * 100f;
            r.healthPercentage = clampHealth(ratio);
            r.sourceTag = "charge_counter_ratio";
            r.cycleLossPercent = Math.max(0f, 100f - ratio);
            // 置信度：60%-80% 线性递增
            r.confidence = 0.65f + (percentage - 60) / 40f * 0.2f;
            r.healthLevel = getHealthLevel(r.healthPercentage);
            r.healthStatus = getHealthStatusString(r.healthLevel);
            return r;
        }

        // === 路径 3: 使用天数兜底（必须有设计容量） ===
        if (effectiveUsageDays > 0 && designCapacity > 0) {
            // 行业经验：0.026%/天 ≈ 9.5%/年（普通用户），激进用户取 0.035%/天
            float daysLoss = effectiveUsageDays * 0.026f;
            float estimatedHealth = 100f - daysLoss;
            r.healthPercentage = clampHealth(estimatedHealth);
            r.sourceTag = "usage_days_estimate";
            r.usageLossPercent = daysLoss;
            r.confidence = 0.35f;
            r.healthLevel = getHealthLevel(r.healthPercentage);
            r.healthStatus = getHealthStatusString(r.healthLevel) + context.getString(R.string.confidence_format, 35);
            return r;
        }

        // === 路径 4: 完全无数据 ===
        r.healthPercentage = -1;
        r.sourceTag = "no_data";
        r.healthLevel = "unknown";
        r.healthStatus = context.getString(R.string.health_status_no_data);
        r.confidence = 0f;
        return r;
    }

    private float applyMedianFilter(float currentValue) {
        if (currentValue < 0) return currentValue;
        synchronized (healthBuffer) {
            healthBuffer.add(currentValue);
            if (healthBuffer.size() > MEDIAN_WINDOW) {
                healthBuffer.remove(0);
            }
            if (healthBuffer.size() < 3) return currentValue;
            List<Float> sorted = new ArrayList<>(healthBuffer);
            Collections.sort(sorted);
            int mid = sorted.size() / 2;
            if (sorted.size() % 2 == 0) {
                return (sorted.get(mid - 1) + sorted.get(mid)) / 2f;
            }
            return sorted.get(mid);
        }
    }

    // endregion

    // region 等级映射

    private float clampHealth(float v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private String getHealthLevel(float p) {
        if (p < 0) return "unknown";
        if (p >= 95) return "excellent";
        if (p >= 85) return "good";
        if (p >= 75) return "average";
        if (p >= 60) return "poor";
        return "very_poor";
    }

    private String getHealthStatusString(String level) {
        switch (level) {
            case "excellent": return context.getString(R.string.health_excellent);
            case "good": return context.getString(R.string.health_good);
            case "average": return context.getString(R.string.health_average);
            case "poor": return context.getString(R.string.health_poor);
            case "very_poor": return context.getString(R.string.health_very_poor);
            default: return context.getString(R.string.health_unknown);
        }
    }

    private String mapHealthStatusToCode(String level) {
        switch (level) {
            case "excellent":
            case "good":
                return "good";
            case "average":
                return "normal";
            case "poor":
                return "warning";
            case "very_poor":
                return "poor";
            default:
                return "unknown";
        }
    }

    private String mapSourceToCode(String source) {
        if (source == null) return "unknown";
        if (source.equals(context.getString(R.string.battery_source_original))) return "original";
        if (source.equals(context.getString(R.string.battery_source_third_party))) return "third_party";
        return "unknown";
    }

    private String mapHealthDataSource(float confidence, String sourceTag) {
        if ("android16_native_health".equals(sourceTag)) return "android16_native_health";
        if ("fcc_ratio".equals(sourceTag)) {
            return confidence >= 0.95f ? "fcc_ratio" : "user_calibrated";
        }
        if ("charge_counter_ratio".equals(sourceTag)) return "charge_counter_ratio";
        if ("usage_days_estimate".equals(sourceTag)) return "usage_days_estimate";
        return "unknown";
    }

    public String formatCycleCount(BatteryInfo info) {
        if (info == null || !info.hasValidCycleCount()) return context.getString(R.string.cycle_count_unreadable);
        if (info.isCycleCountEstimated()) {
            return String.format(Locale.getDefault(),
                    context.getString(R.string.cycle_count_estimate_format), info.getCycleCount());
        }
        return String.format(Locale.getDefault(),
                context.getString(R.string.cycle_count_format), info.getCycleCount());
    }

    // endregion

    // region 状态文本

    private String getStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return context.getString(R.string.status_charging);
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return context.getString(R.string.status_discharging);
            case BatteryManager.BATTERY_STATUS_FULL: return context.getString(R.string.status_fully_charged);
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return context.getString(R.string.status_not_charging_short);
            default: return context.getString(R.string.status_unknown);
        }
    }

    private String getChargeTypeString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return context.getString(R.string.charging_status_ac);
            case BatteryManager.BATTERY_PLUGGED_USB: return context.getString(R.string.charging_status_usb);
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return context.getString(R.string.charging_status_wireless);
            case BatteryManager.BATTERY_PLUGGED_DOCK: return "Dock";
            default: return context.getString(R.string.status_not_charging_short);
        }
    }

    // endregion

    // region 序列号

    private String readBatterySerial(Intent intent) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String serial = tryGetBatterySerial(bm);
            if (serial != null && !serial.isEmpty()
                    && !serial.equals("0")
                    && !serial.equalsIgnoreCase("unknown")) {
                return serial;
            }
        }
        return readSysfsString(SERIAL_PATHS, context.getString(R.string.status_unknown));
    }

    private String tryGetBatterySerial(BatteryManager bm) {
        try {
            Method m = bm.getClass().getMethod("getBatterySerialNumber");
            Object r = m.invoke(bm);
            if (r instanceof String) return (String) r;
        } catch (Exception ignored) {
        }
        try {
            Method m = bm.getClass().getMethod("getBatterySerialNumber", Context.class);
            Object r = m.invoke(bm, context);
            if (r instanceof String) return (String) r;
        } catch (Exception ignored) {
        }
        try {
            Method m = BatteryManager.class.getMethod("getBatterySerialNumber", Context.class);
            Object r = m.invoke(null, context);
            if (r instanceof String) return (String) r;
        } catch (Exception ignored) {
        }
        return null;
    }

    // endregion

    // region sysfs 工具

    private int readSysfsInt(String[] paths, int def) {
        for (String p : paths) {
            try {
                String v = readFile(p);
                if (v != null && !v.isEmpty()) return Integer.parseInt(v.trim());
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private long readSysfsLong(String[] paths, long def) {
        for (String p : paths) {
            try {
                String v = readFile(p);
                if (v != null && !v.isEmpty()) return Long.parseLong(v.trim());
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private String readSysfsString(String[] paths, String def) {
        for (String p : paths) {
            try {
                String v = readFile(p);
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private String readFile(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        } catch (IOException e) {
            return null;
        }
        return sb.toString().trim();
    }

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
        String sourceTag;
        float confidence;
        float factoryLossPercent;
        float cycleLossPercent;
        float usageLossPercent;
    }

    private static class BatterySourceResult {
        String source;
        String reason;
        float confidence;
    }

    // endregion

    // region 公开辅助方法

    public boolean isCharging() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * 读取当前电池电压（mV），供外部 UI 复用。
     */
    public int readVoltageNow() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltageMv > 10000) voltageMv = voltageMv / 1000;
            if (voltageMv > 0 && voltageMv >= 2500 && voltageMv <= 6000) return voltageMv;
        }
        long raw = readSysfsLong(VOLTAGE_NOW_PATHS, -1);
        if (raw > 1000000) return (int) (raw / 1000);
        if (raw > 2500) return (int) raw;
        return -1;
    }

    /**
     * 读取当前电池电流（mA），供外部 UI 复用。
     */
    public int readCurrentMa() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return readCurrentNow(bm);
    }

    public String getPowerLevelLabel(float powerW) {
        if (powerW >= 100) return context.getString(R.string.charge_type_ultra_fast);
        if (powerW >= 60) return context.getString(R.string.charge_type_extreme_fast);
        if (powerW >= 30) return context.getString(R.string.charge_type_fast);
        if (powerW >= 10) return context.getString(R.string.charge_type_standard);
        if (powerW > 0) return context.getString(R.string.charge_type_slow);
        return context.getString(R.string.status_not_charging_short);
    }

    public boolean isNearOfficialFastCharge(float currentPowerW) {
        int official = deviceDb.getTypicalChargePower();
        if (official <= 0) return currentPowerW >= 18;
        return currentPowerW >= official * 0.6f;
    }

    public void refreshFromStickyIntent() {
        currentBatteryInfo = getBatteryInfo();
        if (currentBatteryInfo != null) {
            chargingStatusText = getStatusString(currentBatteryInfo.getStatus());
            healthSourceText = formatHealthSource(currentBatteryInfo);
            batterySourceText = formatBatterySource(currentBatteryInfo);
        }
    }

    public BatteryInfo getCurrentBatteryInfo() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return currentBatteryInfo;
    }

    public void refreshAllDataAsync() {
        new Thread(this::refreshFromStickyIntent).start();
    }

    public void setUsageDays(int days) {
        this.usageDays = days;
    }

    /**
     * 检测旁路充电是否激活（ColorOS 16 特性）。
     * 旁路充电模式下，充电器直接为设备供电而不经过电池，有助于减少电池发热和损耗。
     *
     * @return true 表示旁路充电模式已激活
     */
    public boolean isBypassCharging() {
        String value = readSysfsString(BYPASS_CHARGING_PATHS, "");
        return "1".equals(value.trim());
    }

    /**
     * 读取用户配置的充电限制百分比（ColorOS 16 / Android 16 特性）。
     * 常见值：80、85、90、95、100。
     *
     * @return 充电限制百分比，未设置时返回 100（即无限制）
     */
    public int getChargingLimitPercent() {
        // 依次尝试 Settings.Global、Settings.Secure、Settings.System
        String[] keys = {"charge_limit_percent", "battery_charge_limit", "smart_charging_limit"};
        for (String key : keys) {
            try {
                int value = Settings.Global.getInt(context.getContentResolver(), key, -1);
                if (value > 0 && value <= 100) return value;
            } catch (Exception ignored) {
            }
            try {
                int value = Settings.Secure.getInt(context.getContentResolver(), key, -1);
                if (value > 0 && value <= 100) return value;
            } catch (Exception ignored) {
            }
            try {
                int value = Settings.System.getInt(context.getContentResolver(), key, -1);
                if (value > 0 && value <= 100) return value;
            } catch (Exception ignored) {
            }
        }
        return 100;
    }

    public String getChargingStatusText() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return chargingStatusText;
    }

    public String getHealthSourceText() {
        if (currentBatteryInfo == null) refreshFromStickyIntent();
        return healthSourceText;
    }

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
