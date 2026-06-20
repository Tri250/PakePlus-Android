package com.batteryhealth.app.bugreport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * bugreport 解析正则模式库（等价于 digiguide C++ RegexPatterns）。
 *
 * <p>30+ 模式覆盖国内主流品牌（小米/华为/OPPO/vivo/荣耀/三星/红魔/一加）的 bugreport 输出差异，
 * 按优先级排序，解析时按顺序匹配首个命中。</p>
 */
public final class RegexPatterns {

    private RegexPatterns() {}

    // ========== 容量提取（按优先级） ==========
    public static List<String> getCapacityPatterns() {
        return Collections.unmodifiableList(Arrays.asList(
            "Min learned battery capacity:\\s*(\\d+)\\s*mAh",
            "full charge capacity:\\s*(\\d+)\\s*mAh",
            "learned capacity:\\s*(\\d+)\\s*mAh",
            "FullCapacity:\\s*(\\d+)",
            "battery capacity:\\s*(\\d+)\\s*mAh",
            "Capacity:\\s*(\\d+)\\s*mAh"
        ));
    }

    // ========== 循环次数（覆盖 9+ 厂商写法） ==========
    public static List<String> getCycleCountPatterns() {
        return Collections.unmodifiableList(Arrays.asList(
            "battery cycle count:\\s*(\\d+)",
            "cycle count:\\s*(\\d+)",
            "charge cycles:\\s*(\\d+)",
            "battery cycles:\\s*(\\d+)",
            "CycleCount:\\s*(\\d+)",
            "BatteryCycleCount:\\s*(\\d+)",
            "ChargingCycleCount:\\s*(\\d+)",
            "battery_age_cycles:\\s*(\\d+)",
            "cycle_count:\\s*(\\d+)"
        ));
    }

    // ========== 制造日期（16 种格式） ==========
    public static List<String> getDatePatterns() {
        return Collections.unmodifiableList(Arrays.asList(
            "manufacturing_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "mfg_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "battery.*?date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "first_use_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "battery_make_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "mfg_date:\\s*(\\d{4})(\\d{2})(\\d{2})",
            "manufacturing_date:\\s*(\\d{4})(\\d{2})(\\d{2})",
            "mfgdate:\\s*(\\d{4})(\\d{2})(\\d{2})",
            "battery_produce_date:\\s*(\\d{4})(\\d{2})(\\d{2})",
            "生产日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})",
            "出厂日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})",
            "manufacturing_date:\\s*(\\d{4})[./](\\d{2})[./](\\d{2})",
            "mfg_date:\\s*(\\d{2})[./](\\d{2})[./](\\d{4})",
            "Battery\\s+MFG\\s+Date:\\s*(\\d{4})[.-](\\d{2})[.-](\\d{2})",
            "battery_production_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})",
            "battery_manufacture_time:\\s*(\\d{4})(\\d{2})(\\d{2})"
        ));
    }

    // ========== 设备标识 ==========
    public static String getBrandPattern() {
        return "ro\\.product\\.brand=\\s*([A-Za-z0-9_\\- ]+)";
    }

    public static String getManufacturerPattern() {
        return "ro\\.product\\.manufacturer=\\s*([A-Za-z0-9_\\- ]+)";
    }

    public static String getModelPattern() {
        return "ro\\.product\\.model=\\s*([A-Za-z0-9_\\- ]+)";
    }

    public static String getDevicePattern() {
        return "ro\\.product\\.device=\\s*([A-Za-z0-9_\\- ]+)";
    }

    // ========== 温度 ==========
    public static String getTemperaturePattern() {
        return "battery temperature:\\s*(\\d+\\.?\\d*)\\s*°?C";
    }

    public static String getTemperaturePatternAlt() {
        return "BatteryTemp:\\s*(\\d+\\.?\\d*)";
    }

    public static String getDesignCapacityPattern() {
        return "DesignCapacity:\\s*(\\d+)";
    }

    public static String getChargeCountPattern() {
        return "charge_count:\\s*(\\d+)";
    }

    public static String getScreenOnTimePattern() {
        return "Screen on time:\\s*(\\d+\\.?\\d*)\\s*h";
    }

    public static String getVoltagePattern() {
        return "battery voltage:\\s*(\\d+\\.?\\d*)\\s*mV";
    }

    public static String getCurrentPattern() {
        return "battery current:\\s*(-?\\d+\\.?\\d*)\\s*mA";
    }

    public static String getAppPowerPattern() {
        return "App power usage:.*?Package:\\s*([^\\n]+).*?Power:\\s*(\\d+\\.?\\d*)\\s*mAh";
    }

    public static String getBatteryHistoryPattern() {
        return "(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}).*?battery level:\\s*(\\d+).*?status:\\s*(\\w+)";
    }

    public static String getSNPattern() {
        return "ro\\.serialno=\\s*([A-Za-z0-9]+)";
    }

    public static String getIMEIPattern() {
        return "IMEI:\\s*(\\d{15})";
    }

    public static boolean isValidDate(int year, int month, int day) {
        if (year < 2000 || year > 2030) return false;
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;
        if (month == 2 && day > 29) return false;
        if ((month == 4 || month == 6 || month == 9 || month == 11) && day > 30) return false;
        return true;
    }
}
