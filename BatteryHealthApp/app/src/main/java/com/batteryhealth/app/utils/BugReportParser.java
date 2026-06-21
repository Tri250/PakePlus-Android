package com.batteryhealth.app.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Android bugreport 解析器，提取电池相关数据。
 * <p>
 * Java port of C++ BugreportParser from digiguide/core/src/bugreport_parser.cpp.
 * 支持从原始文本或 ZIP 文件解析，提取品牌/型号/容量/循环次数/温度等字段。
 */
public class BugReportParser {

    private static final String TAG = "BugReportParser";

    // ==================== Regex patterns (ported from regex_patterns.h) ====================

    private static final Pattern BRAND_PATTERN =
            Pattern.compile("ro\\.product\\.brand=\\s*([A-Za-z0-9_\\- ]+)");
    private static final Pattern MANUFACTURER_PATTERN =
            Pattern.compile("ro\\.product\\.manufacturer=\\s*([A-Za-z0-9_\\- ]+)");
    private static final Pattern MODEL_PATTERN =
            Pattern.compile("ro\\.product\\.model=\\s*([A-Za-z0-9_\\- ]+)");

    private static final Pattern DESIGN_CAPACITY_PATTERN =
            Pattern.compile("DesignCapacity:\\s*(\\d+)");

    private static final Pattern[] CAPACITY_PATTERNS = {
            Pattern.compile("Min learned battery capacity:\\s*(\\d+)\\s*mAh"),
            Pattern.compile("full charge capacity:\\s*(\\d+)\\s*mAh"),
            Pattern.compile("learned capacity:\\s*(\\d+)\\s*mAh"),
            Pattern.compile("FullCapacity:\\s*(\\d+)"),
            Pattern.compile("battery capacity:\\s*(\\d+)\\s*mAh"),
            Pattern.compile("Capacity:\\s*(\\d+)\\s*mAh")
    };

    private static final Pattern[] CYCLE_COUNT_PATTERNS = {
            Pattern.compile("battery cycle count:\\s*(\\d+)"),
            Pattern.compile("cycle count:\\s*(\\d+)"),
            Pattern.compile("charge cycles:\\s*(\\d+)"),
            Pattern.compile("battery cycles:\\s*(\\d+)"),
            Pattern.compile("CycleCount:\\s*(\\d+)"),
            Pattern.compile("BatteryCycleCount:\\s*(\\d+)"),
            Pattern.compile("ChargingCycleCount:\\s*(\\d+)"),
            Pattern.compile("battery_age_cycles:\\s*(\\d+)"),
            Pattern.compile("cycle_count:\\s*(\\d+)")
    };

    private static final Pattern[] DATE_PATTERNS = {
            Pattern.compile("manufacturing_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("mfg_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("battery.*?date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("first_use_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("battery_make_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("mfg_date:\\s*(\\d{4})(\\d{2})(\\d{2})"),
            Pattern.compile("manufacturing_date:\\s*(\\d{4})(\\d{2})(\\d{2})"),
            Pattern.compile("mfgdate:\\s*(\\d{4})(\\d{2})(\\d{2})"),
            Pattern.compile("battery_produce_date:\\s*(\\d{4})(\\d{2})(\\d{2})"),
            Pattern.compile("生产日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})"),
            Pattern.compile("出厂日期[:：]\\s*(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})"),
            Pattern.compile("manufacturing_date:\\s*(\\d{4})[./](\\d{2})[./](\\d{2})"),
            Pattern.compile("mfg_date:\\s*(\\d{2})[./](\\d{2})[./](\\d{4})"),
            Pattern.compile("Battery\\s+MFG\\s+Date:\\s*(\\d{4})[.-](\\d{2})[.-](\\d{2})"),
            Pattern.compile("battery_production_date:\\s*(\\d{4})-(\\d{2})-(\\d{2})"),
            Pattern.compile("battery_manufacture_time:\\s*(\\d{4})(\\d{2})(\\d{2})")
    };

    private static final Pattern TEMPERATURE_PATTERN =
            Pattern.compile("battery temperature:\\s*(\\d+\\.?\\d*)\\s*°?C");
    private static final Pattern TEMPERATURE_ALT_PATTERN =
            Pattern.compile("BatteryTemp:\\s*(\\d+\\.?\\d*)");

    private static final Pattern SCREEN_ON_TIME_PATTERN =
            Pattern.compile("Screen on time:\\s*(\\d+\\.?\\d*)\\s*h");

    private static final Pattern CHARGE_COUNT_PATTERN =
            Pattern.compile("charge_count:\\s*(\\d+)");

    private static final Pattern VOLTAGE_PATTERN =
            Pattern.compile("battery voltage:\\s*(\\d+\\.?\\d*)\\s*mV");
    private static final Pattern CURRENT_PATTERN =
            Pattern.compile("battery current:\\s*(-?\\d+\\.?\\d*)\\s*mA");

    private static final Pattern APP_POWER_PATTERN =
            Pattern.compile("App power usage:.*?Package:\\s*([^\\n]+).*?Power:\\s*(\\d+\\.?\\d*)\\s*mAh",
                    Pattern.DOTALL);

    private static final Pattern BATTERY_HISTORY_PATTERN =
            Pattern.compile("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}).*?battery level:\\s*(\\d+).*?status:\\s*(\\w+)");

    private static final Pattern SN_PATTERN =
            Pattern.compile("ro\\.serialno=\\s*([A-Za-z0-9]+)");
    private static final Pattern IMEI_PATTERN =
            Pattern.compile("IMEI:\\s*(\\d{15})");

    // ==================== Public API ====================

    /**
     * 从 ZIP 文件解析 bugreport。
     * <p>
     * 打开 ZIP，查找主 bugreport 文本文件，解析并返回 BugReportData。
     *
     * @param zipPath ZIP 文件路径
     * @return 解析结果，解析失败时返回空的 BugReportData
     */
    public BugReportData parseFromZip(String zipPath) {
        if (zipPath == null || zipPath.isEmpty()) {
            Log.w(TAG, "parseFromZip: zipPath is null or empty");
            return new BugReportData();
        }

        try (ZipFile zipFile = new ZipFile(zipPath)) {
            // 查找主 bugreport 文本文件
            String mainContent = findBugreportEntry(zipFile);
            if (mainContent == null) {
                Log.w(TAG, "parseFromZip: no bugreport entry found in " + zipPath);
                return new BugReportData();
            }
            return parseFromText(mainContent);
        } catch (Exception e) {
            Log.e(TAG, "parseFromZip: failed to open/parse zip " + zipPath, e);
            return new BugReportData();
        }
    }

    /**
     * 从原始文本解析 bugreport。
     *
     * @param text bugreport 文本内容
     * @return 解析结果
     */
    public BugReportData parseFromText(String text) {
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "parseFromText: text is null or empty");
            return new BugReportData();
        }

        BugReportData data = new BugReportData();

        extractBrandModel(text, data);
        extractSN(text, data);
        extractCapacity(text, data);
        extractCycleCount(text, data);
        extractManufacturingDate(text, data);
        extractTemperature(text, data);
        extractScreenOnTime(text, data);
        extractChargeCount(text, data);
        extractVoltageCurrent(text, data);
        extractChargingEvents(text, data);
        extractAppPowerUsage(text, data);

        Log.d(TAG, "parseFromText: extracted " + data.getAvailableDataCount() + " fields");
        return data;
    }

    // ==================== Private extractors ====================

    private void extractBrandModel(String text, BugReportData data) {
        Matcher match = BRAND_PATTERN.matcher(text);
        if (match.find()) {
            data.brand = Optional.of(match.group(1).trim());
        }

        // Fallback to manufacturer
        if (!data.brand.isPresent()) {
            match = MANUFACTURER_PATTERN.matcher(text);
            if (match.find()) {
                data.brand = Optional.of(match.group(1).trim());
            }
        }

        match = MODEL_PATTERN.matcher(text);
        if (match.find()) {
            data.model = Optional.of(match.group(1).trim());
        }
    }

    private void extractSN(String text, BugReportData data) {
        Matcher match = SN_PATTERN.matcher(text);
        if (match.find()) {
            data.sn = Optional.of(match.group(1).trim());
            return;
        }

        // Fallback to IMEI
        match = IMEI_PATTERN.matcher(text);
        if (match.find()) {
            data.sn = Optional.of(match.group(1).trim());
        }
    }

    private void extractCapacity(String text, BugReportData data) {
        // Design capacity
        Matcher match = DESIGN_CAPACITY_PATTERN.matcher(text);
        if (match.find()) {
            try {
                data.designCapacityMah = OptionalInt.of(Integer.parseInt(match.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractCapacity: failed to parse design capacity", e);
            }
        }

        // Current capacity (try patterns in priority order)
        for (Pattern pattern : CAPACITY_PATTERNS) {
            match = pattern.matcher(text);
            if (match.find()) {
                try {
                    data.currentCapacityMah = OptionalInt.of(Integer.parseInt(match.group(1)));
                    break;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "extractCapacity: failed to parse current capacity", e);
                }
            }
        }
    }

    private void extractCycleCount(String text, BugReportData data) {
        for (Pattern pattern : CYCLE_COUNT_PATTERNS) {
            Matcher match = pattern.matcher(text);
            if (match.find()) {
                try {
                    data.cycleCount = OptionalInt.of(Integer.parseInt(match.group(1)));
                    break;
                } catch (NumberFormatException e) {
                    Log.w(TAG, "extractCycleCount: failed to parse", e);
                }
            }
        }
    }

    private void extractManufacturingDate(String text, BugReportData data) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher match = pattern.matcher(text);
            if (match.find()) {
                try {
                    int year = Integer.parseInt(match.group(1));
                    int month = Integer.parseInt(match.group(2));
                    int day = Integer.parseInt(match.group(3));

                    if (isValidDate(year, month, day)) {
                        data.manufacturingDate = Optional.of(year + "-" + month + "-" + day);
                        break;
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "extractManufacturingDate: failed to parse", e);
                }
            }
        }
    }

    private void extractTemperature(String text, BugReportData data) {
        Matcher match = TEMPERATURE_PATTERN.matcher(text);
        if (match.find()) {
            try {
                data.temperatureCelsius = OptionalDouble.of(Double.parseDouble(match.group(1)));
                return;
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractTemperature: failed to parse primary", e);
            }
        }

        // Fallback pattern
        match = TEMPERATURE_ALT_PATTERN.matcher(text);
        if (match.find()) {
            try {
                data.temperatureCelsius = OptionalDouble.of(Double.parseDouble(match.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractTemperature: failed to parse alt", e);
            }
        }
    }

    private void extractScreenOnTime(String text, BugReportData data) {
        Matcher match = SCREEN_ON_TIME_PATTERN.matcher(text);
        if (match.find()) {
            try {
                data.screenOnTimeHours = OptionalInt.of((int) Double.parseDouble(match.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractScreenOnTime: failed to parse", e);
            }
        }
    }

    private void extractChargeCount(String text, BugReportData data) {
        Matcher match = CHARGE_COUNT_PATTERN.matcher(text);
        if (match.find()) {
            try {
                data.chargeCount = OptionalInt.of(Integer.parseInt(match.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractChargeCount: failed to parse", e);
            }
        }
    }

    private void extractVoltageCurrent(String text, BugReportData data) {
        List<Double> voltages = new ArrayList<>();
        List<Double> currents = new ArrayList<>();

        Matcher voltageMatch = VOLTAGE_PATTERN.matcher(text);
        while (voltageMatch.find()) {
            try {
                voltages.add(Double.parseDouble(voltageMatch.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractVoltageCurrent: failed to parse voltage", e);
            }
        }

        Matcher currentMatch = CURRENT_PATTERN.matcher(text);
        while (currentMatch.find()) {
            try {
                currents.add(Double.parseDouble(currentMatch.group(1)));
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractVoltageCurrent: failed to parse current", e);
            }
        }

        // Pair voltages and currents (assume they alternate in text)
        int minSize = Math.min(voltages.size(), currents.size());
        for (int i = 0; i < minSize; i++) {
            data.voltageCurrentPairs.add(new VoltageCurrentPair(voltages.get(i), currents.get(i)));
        }
    }

    private void extractChargingEvents(String text, BugReportData data) {
        Matcher match = BATTERY_HISTORY_PATTERN.matcher(text);
        while (match.find()) {
            try {
                String timeStr = match.group(1);
                int level = Integer.parseInt(match.group(2));
                String status = match.group(3);

                ChargingEvent event = new ChargingEvent();
                event.timestamp = 0; // Simplified: full timestamp parsing not implemented
                event.startLevel = level;
                event.endLevel = level;
                event.durationMinutes = 0;
                event.avgPowerW = 0.0f;
                data.chargingEvents.add(event);
            } catch (NumberFormatException e) {
                Log.w(TAG, "extractChargingEvents: failed to parse", e);
            }
        }
    }

    private void extractAppPowerUsage(String text, BugReportData data) {
        Matcher match = APP_POWER_PATTERN.matcher(text);
        while (match.find()) {
            AppPowerUsage usage = new AppPowerUsage();
            usage.packageName = match.group(1).trim();
            usage.displayName = usage.packageName;
            try {
                usage.powerMah = Float.parseFloat(match.group(2));
            } catch (NumberFormatException e) {
                usage.powerMah = 0f;
            }
            usage.wakeupCount = 0;
            usage.isSystem = false;

            data.appPowerUsages.add(usage);
        }

        // Sort by power consumption descending, keep top 10
        Collections.sort(data.appPowerUsages,
                (a, b) -> Float.compare(b.powerMah, a.powerMah));
        if (data.appPowerUsages.size() > 10) {
            data.appPowerUsages = new ArrayList<>(data.appPowerUsages.subList(0, 10));
        }
    }

    // ==================== Helper methods ====================

    /**
     * Find the main bugreport text entry inside a ZIP file.
     */
    private String findBugreportEntry(ZipFile zipFile) {
        // First pass: look for entry with "bugreport" in the name
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            String name = entry.getName();
            if (!entry.isDirectory() && name.contains("bugreport") && isTextEntry(name)) {
                try {
                    return readEntry(zipFile, entry);
                } catch (Exception e) {
                    Log.w(TAG, "findBugreportEntry: failed to read " + name, e);
                }
            }
        }

        // Second pass: look for entry containing bugreport content markers
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            if (entry.isDirectory() || !isTextEntry(entry.getName())) {
                continue;
            }
            try {
                String content = readEntry(zipFile, entry);
                if (content.contains("ro.product.brand") || content.contains("bugreport")) {
                    return content;
                }
            } catch (Exception e) {
                Log.w(TAG, "findBugreportEntry: failed to read " + entry.getName(), e);
            }
        }

        return null;
    }

    private boolean isTextEntry(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".log") || !lower.contains(".");
    }

    private String readEntry(ZipFile zipFile, ZipEntry entry) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(entry), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean isValidDate(int year, int month, int day) {
        if (year < 2000 || year > 2030) return false;
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;
        if (month == 2 && day > 29) return false;
        if ((month == 4 || month == 6 || month == 9 || month == 11) && day > 30) return false;
        return true;
    }

    // ==================== Inner classes ====================

    /**
     * Parsed bugreport data with all battery-related fields as Optional values.
     */
    public static class BugReportData {
        // Basic info
        public Optional<String> brand = Optional.empty();
        public Optional<String> model = Optional.empty();
        public Optional<String> sn = Optional.empty();

        // Capacity data
        public OptionalInt designCapacityMah = OptionalInt.empty();
        public OptionalInt currentCapacityMah = OptionalInt.empty();
        public OptionalInt chargeCounterMah = OptionalInt.empty();

        // Cycle & lifetime
        public OptionalInt cycleCount = OptionalInt.empty();
        public Optional<String> manufacturingDate = Optional.empty();

        // Temperature
        public OptionalDouble temperatureCelsius = OptionalDouble.empty();

        // Usage stats
        public OptionalInt screenOnTimeHours = OptionalInt.empty();
        public OptionalInt chargeCount = OptionalInt.empty();

        // Charging events
        public List<ChargingEvent> chargingEvents = new ArrayList<>();

        // App power usage
        public List<AppPowerUsage> appPowerUsages = new ArrayList<>();

        // Voltage/current data pairs
        public List<VoltageCurrentPair> voltageCurrentPairs = new ArrayList<>();

        public boolean hasCapacityData() {
            return designCapacityMah.isPresent() || currentCapacityMah.isPresent();
        }

        public boolean hasCycleData() {
            return cycleCount.isPresent();
        }

        public int getAvailableDataCount() {
            int count = 0;
            if (brand.isPresent()) count++;
            if (model.isPresent()) count++;
            if (designCapacityMah.isPresent()) count++;
            if (currentCapacityMah.isPresent()) count++;
            if (cycleCount.isPresent()) count++;
            if (manufacturingDate.isPresent()) count++;
            if (temperatureCelsius.isPresent()) count++;
            if (screenOnTimeHours.isPresent()) count++;
            if (chargeCount.isPresent()) count++;
            if (!voltageCurrentPairs.isEmpty()) count++;
            if (!chargingEvents.isEmpty()) count++;
            if (!appPowerUsages.isEmpty()) count++;
            return count;
        }
    }

    /**
     * Charging event parsed from battery history.
     */
    public static class ChargingEvent {
        public long timestamp;
        public int startLevel;
        public int endLevel;
        public int durationMinutes;
        public float avgPowerW;
    }

    /**
     * App power usage entry.
     */
    public static class AppPowerUsage {
        public String packageName;
        public String displayName;
        public float powerMah;
        public int wakeupCount;
        public boolean isSystem;
    }

    /**
     * Voltage/current data pair for internal resistance estimation.
     */
    public static class VoltageCurrentPair {
        public final double voltageMv;
        public final double currentMa;

        public VoltageCurrentPair(double voltageMv, double currentMa) {
            this.voltageMv = voltageMv;
            this.currentMa = currentMa;
        }
    }
}
