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
        try {
            File healthFile = new File("/sys/class/power_supply/battery0/health");
            if (!healthFile.exists()) {
                healthFile = new File("/sys/class/power_supply/bms/health");
            }
            if (healthFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(healthFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && !line.isEmpty()) {
                    return line.trim().toUpperCase();
                }
            }
        } catch (IOException ignored) {
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
        try {
            File cycleFile = new File("/sys/class/power_supply/battery0/cycle_count");
            if (!cycleFile.exists()) {
                cycleFile = new File("/sys/class/power_supply/bms/cycle_count");
            }
            if (cycleFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(cycleFile));
                String line = reader.readLine();
                reader.close();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (IOException ignored) {
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

        return Math.min(100, Math.max(0, confidence));
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