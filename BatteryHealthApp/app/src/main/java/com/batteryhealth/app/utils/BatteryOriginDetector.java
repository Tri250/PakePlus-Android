package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatteryOriginDetector {

    private static final String TAG = "BatteryOriginDetector";

    private final Context context;

    public BatteryOriginDetector(Context context) {
        this.context = context;
    }

    public OriginResult detect() {
        OriginResult result = new OriginResult();
        result.brand = Build.BRAND;
        result.model = Build.MODEL;
        result.detectionMethods = new ArrayList<>();

        List<DetectionMethod> methods = new ArrayList<>();

        try {
            String batteryInfo = readBatteryInfo();
            if (batteryInfo != null) {
                methods.add(new DetectionMethod("电池信息", batteryInfo));
                result.batteryInfo = batteryInfo;
            }

            String manufactureDate = detectManufactureDate(batteryInfo);
            if (manufactureDate != null) {
                result.manufactureDate = manufactureDate;
                methods.add(new DetectionMethod("生产日期", manufactureDate));
            }

            String serialNumber = detectSerialNumber(batteryInfo);
            if (serialNumber != null) {
                result.serialNumber = serialNumber;
                methods.add(new DetectionMethod("序列号", serialNumber));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error in basic detection: " + e.getMessage());
        }

        // 健康状态和循环次数涉及 BatteryDataManager，需独立 try-catch
        try {
            String healthStatus = detectHealthStatus();
            if (healthStatus != null) {
                result.healthStatus = healthStatus;
                methods.add(new DetectionMethod("健康状态", healthStatus));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting health status: " + e.getMessage());
        }

        try {
            String cycleCount = detectCycleCount();
            if (cycleCount != null) {
                result.cycleCount = cycleCount;
                methods.add(new DetectionMethod("循环次数", cycleCount));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting cycle count: " + e.getMessage());
        }

        try {
            String designCapacity = detectDesignCapacity();
            if (designCapacity != null) {
                result.designCapacity = designCapacity;
                methods.add(new DetectionMethod("设计容量", designCapacity));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting design capacity: " + e.getMessage());
        }

        try {
            String currentCapacity = detectCurrentCapacity();
            if (currentCapacity != null) {
                result.currentCapacity = currentCapacity;
                methods.add(new DetectionMethod("当前容量", currentCapacity));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting current capacity: " + e.getMessage());
        }

        try {
            String technology = detectTechnology();
            if (technology != null) {
                result.technology = technology;
                methods.add(new DetectionMethod("电池技术", technology));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting technology: " + e.getMessage());
        }

        try {
            String manufacturer = detectManufacturer();
            if (manufacturer != null) {
                result.manufacturer = manufacturer;
                methods.add(new DetectionMethod("制造商", manufacturer));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting manufacturer: " + e.getMessage());
        }

        boolean isOriginal = analyzeOriginal(result);
        result.isOriginal = isOriginal;
        result.confidence = calculateConfidence(result);
        result.conclusion = generateConclusion(result);

        result.detectionMethods = methods;

        return result;
    }

    private String readBatteryInfo() {
        StringBuilder info = new StringBuilder();

        try {
            File batteryDir = new File("/sys/class/power_supply/battery");
            if (!batteryDir.exists()) {
                batteryDir = new File("/sys/class/power_supply/battery0");
            }
            if (!batteryDir.exists()) {
                batteryDir = new File("/sys/class/power_supply/bms");
            }

            if (batteryDir.exists()) {
                String[] files = batteryDir.list();
                if (files != null) {
                    for (String file : files) {
                        if (file.startsWith("uevent") || file.startsWith("manufacturer") ||
                            file.startsWith("model_name") || file.startsWith("serial") ||
                            file.startsWith("date") || file.startsWith("health")) {
                            try {
                                File f = new File(batteryDir, file);
                                BufferedReader reader = new BufferedReader(new FileReader(f));
                                String line = reader.readLine();
                                reader.close();
                                if (line != null && !line.isEmpty()) {
                                    info.append(file).append(": ").append(line).append("\n");
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading battery info: " + e.getMessage());
        }

        return info.length() > 0 ? info.toString().trim() : null;
    }

    private String detectManufactureDate(String batteryInfo) {
        if (batteryInfo == null) return null;

        Pattern datePattern = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2}|20\\d{2}/\\d{2}/\\d{2}|20\\d{2}\\d{4})");
        Matcher matcher = datePattern.matcher(batteryInfo);
        if (matcher.find()) {
            return matcher.group(1);
        }

        try {
            File dateFile = new File("/sys/class/power_supply/battery0/date");
            if (!dateFile.exists()) {
                dateFile = new File("/sys/class/power_supply/bms/date");
            }
            if (dateFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(dateFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    private String detectSerialNumber(String batteryInfo) {
        if (batteryInfo == null) return null;

        Pattern serialPattern = Pattern.compile("serial[_-]?number?[:=]?\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = serialPattern.matcher(batteryInfo);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        try {
            File serialFile = new File("/sys/class/power_supply/battery0/serial_number");
            if (!serialFile.exists()) {
                serialFile = new File("/sys/class/power_supply/bms/serial_number");
            }
            if (serialFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(serialFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    private String detectHealthStatus() {
        // Try primary paths first (most common on real devices)
        String[] healthPaths = {
            "/sys/class/power_supply/battery/health",
            "/sys/class/power_supply/bms/health",
            "/sys/class/power_supply/battery0/health",
            "/sys/class/power_supply/maxfg/health"
        };
        for (String path : healthPaths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        return line.trim().toUpperCase();
                    }
                }
            } catch (IOException ignored) {}
        }

        try {
            BatteryDataManager bdm = new BatteryDataManager(context);
            BatteryInfo info = bdm.getCurrentBatteryInfo();
            return info != null ? info.getHealthStatus() : null;
        } catch (Exception e) {
            Log.w(TAG, "Error getting health status from BatteryDataManager: " + e.getMessage());
            return null;
        }
    }

    private String detectCycleCount() {
        String[] cyclePaths = {
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/battery0/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle_count",
            "/sys/class/power_supply/battery/charge_cycle",
            "/sys/class/power_supply/battery/cycle_count_complete",
            "/sys/class/power_supply/battery/batt_cycle",
            "/sys/class/power_supply/battery/fg_cycle_count"
        };
        for (String path : cyclePaths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        String trimmed = line.trim();
                        try {
                            int val = Integer.parseInt(trimmed);
                            if (val >= 0 && val < 100000) return trimmed;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
        }

        try {
            BatteryDataManager bdm = new BatteryDataManager(context);
            BatteryInfo info = bdm.getCurrentBatteryInfo();
            int cycles = info != null ? info.getCycleCount() : 0;
            return cycles > 0 ? String.valueOf(cycles) : null;
        } catch (Exception e) {
            Log.w(TAG, "Error getting cycle count from BatteryDataManager: " + e.getMessage());
            return null;
        }
    }

    private String detectDesignCapacity() {
        String[] paths = {
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
            "/sys/class/power_supply/battery/design_capacity",
            "/sys/class/power_supply/bms/design_capacity",
            "/sys/class/power_supply/battery/batt_design_capacity",
            "/sys/class/power_supply/battery/fg_design_capacity"
        };
        for (String path : paths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        String trimmed = line.trim();
                        try {
                            int val = Integer.parseInt(trimmed);
                            if (val > 100 && val < 20000) return trimmed + " mAh";
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
        }
        // Try device database
        try {
            DeviceDatabaseManager db = DeviceDatabaseManager.getInstance(context);
            int cap = db.getDesignCapacity();
            if (cap > 0) return cap + " mAh";
        } catch (Exception ignored) {}
        return null;
    }

    private String detectCurrentCapacity() {
        String[] paths = {
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/maxfg/charge_full",
            "/sys/class/power_supply/battery/learned_full_capacity",
            "/sys/class/power_supply/bms/learned_full_capacity",
            "/sys/class/power_supply/battery/fg_full_capacity",
            "/sys/class/power_supply/battery/fcc"
        };
        for (String path : paths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        String trimmed = line.trim();
                        try {
                            int val = Integer.parseInt(trimmed);
                            if (val > 100 && val < 20000) return trimmed + " mAh";
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private String detectTechnology() {
        String[] paths = {
            "/sys/class/power_supply/battery/technology",
            "/sys/class/power_supply/bms/technology"
        };
        for (String path : paths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) return line.trim();
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private String detectManufacturer() {
        String[] paths = {
            "/sys/class/power_supply/battery/manufacturer",
            "/sys/class/power_supply/bms/manufacturer",
            "/sys/class/power_supply/battery/company",
            "/sys/class/power_supply/bms/company"
        };
        for (String path : paths) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty() && !line.trim().equals("0")
                            && !line.trim().equalsIgnoreCase("unknown")) {
                        return line.trim();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private boolean analyzeOriginal(OriginResult result) {
        int positiveSigns = 0;
        int negativeSigns = 0;

        if (result.serialNumber != null && result.serialNumber.length() >= 8) {
            positiveSigns++;
        }

        if (result.healthStatus != null && "GOOD".equals(result.healthStatus)) {
            positiveSigns++;
        }

        if (result.cycleCount != null) {
            try {
                int cycles = Integer.parseInt(result.cycleCount);
                if (cycles < 100) {
                    positiveSigns++;
                } else if (cycles > 1000) {
                    negativeSigns++;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (result.manufactureDate != null) {
            positiveSigns++;
        }

        return positiveSigns > negativeSigns;
    }

    private int calculateConfidence(OriginResult result) {
        int confidence = 50;

        if (result.serialNumber != null && result.serialNumber.length() >= 12) {
            confidence += 20;
        } else if (result.serialNumber != null) {
            confidence += 10;
        }

        if (result.manufactureDate != null) {
            confidence += 15;
        }

        if (result.healthStatus != null && "GOOD".equals(result.healthStatus)) {
            confidence += 10;
        }

        if (result.cycleCount != null) {
            try {
                int cycles = Integer.parseInt(result.cycleCount);
                if (cycles < 50) {
                    confidence += 10;
                } else if (cycles > 500) {
                    confidence -= 10;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (result.designCapacity != null) {
            confidence += 10;
        }

        if (result.manufacturer != null) {
            // Known battery manufacturers
            String lower = result.manufacturer.toLowerCase();
            if (lower.contains("coslight") || lower.contains("sunwoda") || lower.contains("byd")
                    || lower.contains("desay") || lower.contains("scud") || lower.contains("lg")
                    || lower.contains("sanyo") || lower.contains("tdk") || lower.contains("at")
                    || lower.contains("atl") || lower.contains("lishen") || lower.contains("bak")
                    || lower.contains("farasis") || lower.contains("catl")) {
                confidence += 15;
            } else {
                confidence += 5;
            }
        }

        // Capacity deviation check
        if (result.designCapacity != null && result.currentCapacity != null) {
            try {
                int design = extractNumber(result.designCapacity);
                int current = extractNumber(result.currentCapacity);
                if (design > 0 && current > 0) {
                    float ratio = current / (float) design;
                    if (ratio >= 0.85f && ratio <= 1.05f) {
                        confidence += 10; // Normal capacity, likely original
                    } else if (ratio < 0.7f || ratio > 1.2f) {
                        confidence -= 15; // Abnormal deviation, possibly replaced
                    }
                }
            } catch (Exception ignored) {}
        }

        return Math.min(100, Math.max(0, confidence));
    }

    private int extractNumber(String s) {
        if (s == null) return -1;
        try {
            Matcher m = Pattern.compile("(\\d+)").matcher(s);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return -1;
    }

    private String generateConclusion(OriginResult result) {
        if (result.confidence >= 80) {
            return "电池极可能为原装，检测数据完整可靠";
        } else if (result.confidence >= 60) {
            return "电池大概率为原装，但部分信息缺失";
        } else if (result.confidence >= 40) {
            return "电池可能已更换，建议通过官方渠道验证";
        } else {
            return "无法准确判断电池来源，建议前往售后检测";
        }
    }

    public static class OriginResult {
        public String brand;
        public String model;
        public String batteryInfo;
        public String manufactureDate;
        public String serialNumber;
        public String healthStatus;
        public String cycleCount;
        public String designCapacity;    // 设计容量
        public String currentCapacity;   // 当前满充容量
        public String technology;        // 电池技术（Li-ion, Li-poly等）
        public String manufacturer;      // 电池制造商
        public String temperature;       // 电池温度
        public String voltage;           // 电池电压
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
}