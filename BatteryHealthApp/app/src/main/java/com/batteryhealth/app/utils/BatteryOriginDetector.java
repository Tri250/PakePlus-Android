package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatteryOriginDetector {

    private static final String TAG = "BatteryOriginDetector";

    // Primary battery sysfs paths (most common first)
    private static final String[] BATTERY_SYSFS_PATHS = {
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms",
            "/sys/class/power_supply/battery0",
            "/sys/class/power_supply/maxfg"
    };

    // OEM battery manufacturers
    private static final String[] KNOWN_OEM_MANUFACTURERS = {
            "coslight", "sunwoda", "desay", "scud", "byd", "atlb",
            "lg", "chem", "sanyo", "tdk", "samsung", "murata",
            "lishen", "guoguang", "zhuhai", "cosmx", "farasis",
            "amperex", "atl", "bak", "eve", "tenpower"
    };

    private final Context context;
    private BatteryDataManager batteryDataManager;

    public BatteryOriginDetector(Context context) {
        this.context = context;
    }

    public void setBatteryDataManager(BatteryDataManager manager) {
        this.batteryDataManager = manager;
    }

    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager;
    }

    public OriginResult detect() {
        OriginResult result = new OriginResult();
        result.brand = Build.BRAND;
        result.model = Build.MODEL;
        result.detectionMethods = new ArrayList<>();

        List<DetectionMethod> methods = new ArrayList<>();

        // 1. Read comprehensive battery info
        String batteryInfo = readBatteryInfo();
        if (batteryInfo != null) {
            methods.add(new DetectionMethod("电池信息", batteryInfo));
            result.batteryInfo = batteryInfo;
        }

        // 2. Detect manufacturer
        String manufacturer = detectManufacturer();
        if (manufacturer != null) {
            result.manufacturer = manufacturer;
            methods.add(new DetectionMethod("电池厂商", manufacturer));
        }

        // 3. Detect manufacture date
        String manufactureDate = detectManufactureDate(batteryInfo);
        if (manufactureDate != null) {
            result.manufactureDate = manufactureDate;
            methods.add(new DetectionMethod("生产日期", manufactureDate));
        }

        // 4. Detect serial number
        String serialNumber = detectSerialNumber(batteryInfo);
        if (serialNumber != null) {
            result.serialNumber = serialNumber;
            methods.add(new DetectionMethod("序列号", serialNumber));
        }

        // 5. Detect health status
        String healthStatus = detectHealthStatus();
        if (healthStatus != null) {
            result.healthStatus = healthStatus;
            methods.add(new DetectionMethod("健康状态", healthStatus));
        }

        // 6. Detect cycle count
        String cycleCount = detectCycleCount();
        if (cycleCount != null) {
            result.cycleCount = cycleCount;
            methods.add(new DetectionMethod("循环次数", cycleCount));
        }

        // 7. Detect design capacity vs actual (also store for analysis)
        CapacityData capacityData = detectCapacityData();
        if (capacityData != null) {
            result.designCapacity = capacityData.designCapacity;
            result.currentCapacity = capacityData.currentCapacity;
            String capacityInfo = capacityData.getDisplayText();
            methods.add(new DetectionMethod("容量信息", capacityInfo));
        }

        // 8. Detect OEM info / psy_info
        String oemInfo = detectOemInfo();
        if (oemInfo != null) {
            result.oemInfo = oemInfo;
            methods.add(new DetectionMethod("出厂标识", oemInfo));
        }

        // 9. Detect battery technology
        String technology = detectTechnology();
        if (technology != null) {
            result.technology = technology;
            methods.add(new DetectionMethod("电池技术", technology));
        }

        // Analyze
        boolean isOriginal = analyzeOriginal(result);
        result.isOriginal = isOriginal;
        result.confidence = calculateConfidence(result);
        result.conclusion = generateConclusion(result);

        result.detectionMethods = methods;

        return result;
    }

    private File findBatteryDir() {
        for (String path : BATTERY_SYSFS_PATHS) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private String readBatteryInfo() {
        StringBuilder info = new StringBuilder();
        File batteryDir = findBatteryDir();
        if (batteryDir == null) return null;

        String[] importantFiles = {
                "uevent", "manufacturer", "model_name", "serial_number",
                "date", "health", "technology", "type",
                "charge_full", "charge_full_design",
                "cycle_count", "temp", "voltage_now", "current_now",
                "batt_vol", "batt_temp", "batt_current", "batt_health",
                "batt_date", "batt_serial_number", "fg_type",
                "psy_info", "oem_info", "factory_serial",
                "constant_charge_current", "charge_type"
        };

        String[] files = batteryDir.list();
        if (files == null) return null;

        for (String file : files) {
            boolean isImportant = false;
            for (String important : importantFiles) {
                if (file.equals(important) || file.startsWith(important)) {
                    isImportant = true;
                    break;
                }
            }
            if (!isImportant) continue;

            try {
                File f = new File(batteryDir, file);
                if (!f.canRead()) continue;
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    if (line != null && !line.isEmpty()) {
                        info.append(file).append(": ").append(line).append("\n");
                    }
                }
            } catch (IOException ignored) {
            }
        }

        return info.length() > 0 ? info.toString().trim() : null;
    }

    private String detectManufacturer() {
        String[] manufacturerPaths = {
                "/sys/class/power_supply/battery/manufacturer",
                "/sys/class/power_supply/bms/manufacturer",
                "/sys/class/power_supply/battery/company",
                "/sys/class/power_supply/bms/company"
        };
        for (String path : manufacturerPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                return value.trim();
            }
        }
        return null;
    }

    private String detectManufactureDate(String batteryInfo) {
        // Try sysfs date files first
        String[] datePaths = {
                "/sys/class/power_supply/battery/date",
                "/sys/class/power_supply/battery/batt_date",
                "/sys/class/power_supply/battery/fg_date",
                "/sys/class/power_supply/bms/date",
                "/sys/class/power_supply/bms/fg_date",
                "/sys/class/power_supply/battery0/date"
        };
        for (String path : datePaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                return value.trim();
            }
        }

        // Try parsing from battery info text
        if (batteryInfo != null) {
            Pattern datePattern = Pattern.compile("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}|20\\d{2}\\d{2}\\d{2})");
            Matcher matcher = datePattern.matcher(batteryInfo);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String detectSerialNumber(String batteryInfo) {
        // Try sysfs serial files first
        String[] serialPaths = {
                "/sys/class/power_supply/battery/serial_number",
                "/sys/class/power_supply/battery/batt_serial_number",
                "/sys/class/power_supply/bms/serial_number",
                "/sys/class/power_supply/battery0/serial_number"
        };
        for (String path : serialPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")
                    && !value.equals("0") && !value.equals("0000000000")) {
                return value.trim();
            }
        }

        // Try parsing from battery info text
        if (batteryInfo != null) {
            Pattern serialPattern = Pattern.compile("serial[_-]?number?[:=]?\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = serialPattern.matcher(batteryInfo);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }

    private String detectHealthStatus() {
        String[] healthPaths = {
                "/sys/class/power_supply/battery/health",
                "/sys/class/power_supply/battery/batt_health",
                "/sys/class/power_supply/bms/health",
                "/sys/class/power_supply/battery0/health"
        };
        for (String path : healthPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                return value.trim().toUpperCase();
            }
        }

        // Fallback to BatteryDataManager
        if (batteryDataManager != null) {
            BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
            if (info != null) {
                String status = info.getHealthStatus();
                if (status != null && !status.isEmpty()) return status;
            }
        }
        return null;
    }

    private String detectCycleCount() {
        String[] cyclePaths = {
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle",
                "/sys/class/power_supply/battery/batt_cycle",
                "/sys/class/power_supply/bms/cycle_count",
                "/sys/class/power_supply/battery0/cycle_count"
        };
        for (String path : cyclePaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty()) {
                try {
                    int count = Integer.parseInt(value.trim());
                    if (count >= 0) return String.valueOf(count);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Fallback to BatteryDataManager
        if (batteryDataManager != null) {
            BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
            if (info != null && info.hasValidCycleCount()) {
                return String.valueOf(info.getCycleCount());
            }
        }
        return null;
    }

    private CapacityData detectCapacityData() {
        int designCapacity = 0;
        int currentCapacity = 0;

        // Read design capacity from sysfs
        String[] designPaths = {
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/battery/design_capacity",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/bms/design_capacity"
        };
        for (String path : designPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty()) {
                try {
                    int cap = Integer.parseInt(value.trim());
                    if (cap > 100) {
                        designCapacity = cap > 100000 ? cap / 1000 : cap;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Read current FCC from sysfs
        String[] fccPaths = {
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/bms/charge_full",
                "/sys/class/power_supply/maxfg/charge_full",
                "/sys/class/power_supply/battery/learned_capacity",
                "/sys/class/power_supply/bms/learned_capacity"
        };
        for (String path : fccPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty()) {
                try {
                    int cap = Integer.parseInt(value.trim());
                    if (cap > 100) {
                        currentCapacity = cap > 100000 ? cap / 1000 : cap;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Use BatteryDataManager as fallback
        if (batteryDataManager != null) {
            BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
            if (info != null) {
                if (designCapacity <= 0 && info.getDesignCapacity() > 0) {
                    designCapacity = info.getDesignCapacity();
                }
                if (currentCapacity <= 0 && info.getCurrentCapacity() > 0) {
                    currentCapacity = info.getCurrentCapacity();
                }
            }
        }

        // Use device database as last resort for design capacity
        if (designCapacity <= 0) {
            DeviceDatabaseManager db = DeviceDatabaseManager.getInstance(context);
            designCapacity = db.getDesignCapacity();
        }

        if (designCapacity > 0 || currentCapacity > 0) {
            return new CapacityData(designCapacity, currentCapacity);
        }

        return null;
    }

    private String detectOemInfo() {
        String[] oemPaths = {
                "/sys/class/power_supply/battery/psy_info",
                "/sys/class/power_supply/bms/psy_info",
                "/sys/class/power_supply/maxfg/psy_info",
                "/sys/class/power_supply/battery/oem_info",
                "/sys/class/power_supply/bms/oem_info",
                "/sys/class/power_supply/battery/factory_serial",
                "/sys/class/power_supply/bms/factory_serial",
                "/sys/class/power_supply/battery/oem-serial",
                "/sys/class/power_supply/bms/oem-serial"
        };
        for (String path : oemPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                // Truncate long values for display
                if (value.length() > 100) {
                    value = value.substring(0, 100) + "...";
                }
                return value.trim();
            }
        }
        return null;
    }

    private String detectTechnology() {
        String[] techPaths = {
                "/sys/class/power_supply/battery/technology",
                "/sys/class/power_supply/bms/technology",
                "/sys/class/power_supply/battery/type"
        };
        for (String path : techPaths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("unknown")) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean analyzeOriginal(OriginResult result) {
        int positiveSigns = 0;
        int negativeSigns = 0;

        // Signal 1: Serial number quality
        if (result.serialNumber != null) {
            if (result.serialNumber.length() >= 12) {
                positiveSigns += 2; // Strong signal
            } else if (result.serialNumber.length() >= 8) {
                positiveSigns += 1;
            } else if (result.serialNumber.length() < 4) {
                negativeSigns += 1;
            }
        } else {
            negativeSigns += 1; // No serial is suspicious
        }

        // Signal 2: Manufacturer is known OEM
        if (result.manufacturer != null) {
            String mfgLower = result.manufacturer.toLowerCase(Locale.ROOT);
            boolean isKnownOem = false;
            for (String oem : KNOWN_OEM_MANUFACTURERS) {
                if (mfgLower.contains(oem)) {
                    isKnownOem = true;
                    break;
                }
            }
            if (isKnownOem) {
                positiveSigns += 2;
            } else if (mfgLower.equals("unknown") || mfgLower.equals("0")) {
                negativeSigns += 1;
            }
        }

        // Signal 3: Health status
        if (result.healthStatus != null) {
            if ("GOOD".equals(result.healthStatus)) {
                positiveSigns += 1;
            } else if ("OVERHEAT".equals(result.healthStatus) || "DEAD".equals(result.healthStatus)) {
                negativeSigns += 2;
            }
        }

        // Signal 4: Cycle count reasonableness
        if (result.cycleCount != null) {
            try {
                int cycles = Integer.parseInt(result.cycleCount);
                if (cycles < 100) {
                    positiveSigns += 1;
                } else if (cycles > 1000) {
                    negativeSigns += 1;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Signal 5: Manufacture date exists
        if (result.manufactureDate != null) {
            positiveSigns += 1;
        }

        // Signal 6: OEM info / factory markings exist
        if (result.oemInfo != null && !result.oemInfo.isEmpty()) {
            positiveSigns += 2;
        }

        // Signal 7: Technology matches expected
        if (result.technology != null) {
            String techLower = result.technology.toLowerCase(Locale.ROOT);
            if (techLower.contains("li-ion") || techLower.contains("lipo") || techLower.contains("li-poly")) {
                positiveSigns += 1;
            }
        }

        // Signal 8: Capacity ratio — design vs current FCC (strong indicator of battery replacement)
        if (result.designCapacity > 0 && result.currentCapacity > 0) {
            float ratio = (result.currentCapacity * 100f) / result.designCapacity;
            if (ratio >= 85f && ratio <= 105f) {
                // Normal range: battery health consistent with original
                positiveSigns += 3; // Very strong signal
            } else if (ratio >= 70f && ratio < 85f) {
                // Moderate degradation: could be aged original
                positiveSigns += 1;
            } else if (ratio > 105f && ratio <= 115f) {
                // Slightly above design: some batteries exceed spec, still likely original
                positiveSigns += 2;
            } else if (ratio > 115f) {
                // Significantly above design: likely a different (larger) battery
                negativeSigns += 3; // Strong replacement signal
            } else if (ratio < 70f && ratio >= 50f) {
                // Significant degradation: uncertain
                negativeSigns += 1;
            } else if (ratio < 50f) {
                // Severe degradation or wrong battery
                negativeSigns += 2;
            }
        } else if (result.designCapacity > 0) {
            // Only have design capacity — check if device database matches
            DeviceDatabaseManager db = DeviceDatabaseManager.getInstance(context);
            int dbCapacity = db.getDesignCapacity();
            if (dbCapacity > 0 && result.designCapacity != dbCapacity) {
                // Design capacity doesn't match expected for this device model
                negativeSigns += 2;
            }
        }

        // Signal 9: Design capacity matches device database
        DeviceDatabaseManager db = DeviceDatabaseManager.getInstance(context);
        int dbCapacity = db.getDesignCapacity();
        if (dbCapacity > 0) {
            positiveSigns += 1;
        }

        return positiveSigns > negativeSigns;
    }

    private int calculateConfidence(OriginResult result) {
        int confidence = 30; // Base confidence

        // Serial number quality
        if (result.serialNumber != null) {
            if (result.serialNumber.length() >= 12) confidence += 20;
            else if (result.serialNumber.length() >= 8) confidence += 10;
        }

        // Known OEM manufacturer
        if (result.manufacturer != null) {
            String mfgLower = result.manufacturer.toLowerCase(Locale.ROOT);
            for (String oem : KNOWN_OEM_MANUFACTURERS) {
                if (mfgLower.contains(oem)) {
                    confidence += 15;
                    break;
                }
            }
        }

        // Manufacture date
        if (result.manufactureDate != null) confidence += 10;

        // OEM info
        if (result.oemInfo != null) confidence += 15;

        // Health status
        if (result.healthStatus != null && "GOOD".equals(result.healthStatus)) confidence += 5;

        // Cycle count
        if (result.cycleCount != null) {
            try {
                int cycles = Integer.parseInt(result.cycleCount);
                if (cycles < 50) confidence += 5;
                else if (cycles > 500) confidence -= 10;
            } catch (NumberFormatException ignored) {
            }
        }

        // Capacity ratio confidence
        if (result.designCapacity > 0 && result.currentCapacity > 0) {
            float ratio = (result.currentCapacity * 100f) / result.designCapacity;
            if (ratio >= 85f && ratio <= 105f) {
                confidence += 15; // Strong confirmation
            } else if (ratio > 115f || ratio < 50f) {
                confidence -= 15; // Strong disconfirmation
            }
        }

        // Device database match
        DeviceDatabaseManager db = DeviceDatabaseManager.getInstance(context);
        if (db.findDevice() != null) confidence += 5;

        return Math.min(100, Math.max(0, confidence));
    }

    private String generateConclusion(OriginResult result) {
        if (result.confidence >= 80) {
            return "电池极可能为原装，检测数据完整可靠";
        } else if (result.confidence >= 65) {
            return "电池大概率为原装，部分信息缺失但核心指标正常";
        } else if (result.confidence >= 45) {
            return "电池来源难以判断，建议通过官方渠道验证";
        } else if (result.confidence >= 30) {
            return "电池可能已更换，部分指标异常";
        } else {
            return "无法准确判断电池来源，建议前往售后检测";
        }
    }

    private String readSysfsFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                return reader.readLine();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public static class OriginResult {
        public String brand;
        public String model;
        public String batteryInfo;
        public String manufacturer;
        public String manufactureDate;
        public String serialNumber;
        public String healthStatus;
        public String cycleCount;
        public int designCapacity;
        public int currentCapacity;
        public String oemInfo;
        public String technology;
        public boolean isOriginal;
        public int confidence;
        public String conclusion;
        public List<DetectionMethod> detectionMethods;
    }

    public static class DetectionMethod {
        public String name;
        public String value;

        public DetectionMethod(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * 容量数据辅助类，存储设计容量和当前满充容量。
     */
    private static class CapacityData {
        final int designCapacity;
        final int currentCapacity;

        CapacityData(int designCapacity, int currentCapacity) {
            this.designCapacity = designCapacity;
            this.currentCapacity = currentCapacity;
        }

        String getDisplayText() {
            if (designCapacity > 0 && currentCapacity > 0) {
                float ratio = (currentCapacity * 100f) / designCapacity;
                return String.format(Locale.getDefault(), "设计 %d mAh / 当前 %d mAh（%.0f%%）",
                        designCapacity, currentCapacity, ratio);
            } else if (designCapacity > 0) {
                return String.format(Locale.getDefault(), "设计 %d mAh", designCapacity);
            } else if (currentCapacity > 0) {
                return String.format(Locale.getDefault(), "当前 %d mAh", currentCapacity);
            }
            return "--";
        }
    }
}
