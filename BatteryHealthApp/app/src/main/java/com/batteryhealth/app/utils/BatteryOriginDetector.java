package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.BatteryOriginRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电池来源检测器（统一版本 v5.0.0）。
 *
 * 本类是电池来源检测的唯一权威来源，所有置信度计算均通过
 * {@link #calculateConfidence(OriginResult)} 完成，不依赖外部计算。
 *
 * 检测维度（9 维）：
 *  1. 电池综合信息（uevent / manufacturer / serial 等）
 *  2. 电池厂商
 *  3. 生产日期
 *  4. 序列号
 *  5. 健康状态
 *  6. 循环次数
 *  7. 设计容量 vs 当前满充容量
 *  8. 出厂标识（psy_info / oem_info / factory_serial）
 *  9. 电池技术类型
 *
 * 数据源优先级（Android 16+）：
 *  1. BatteryManager 原生 API（BATTERY_PROPERTY_BATTERY_HEALTH、
 *     BATTERY_PROPERTY_CHARGE_FULL_DESIGN、getBatterySerialNumber 反射）
 *  2. sysfs 节点读取
 *  3. BatteryDataManager 回退
 *  4. 设备数据库兜底
 */
public class BatteryOriginDetector {

    private static final String TAG = "BatteryOriginDetector";

    /**
     * 反射读取 BatteryManager Android 16+ 隐藏常量，fallback 与 BatteryDataManager 保持一致。
     * 避免硬编码 8/9 在不同 OEM ROM 上可能错位。
     */
    private static final int BATTERY_PROP_BATTERY_HEALTH =
            getBatteryIntConstant("BATTERY_PROPERTY_BATTERY_HEALTH", 6);
    private static final int BATTERY_PROP_CHARGE_FULL_DESIGN =
            getBatteryIntConstant("BATTERY_PROPERTY_CHARGE_FULL_DESIGN", 7);

    private static int getBatteryIntConstant(String name, int fallback) {
        try {
            return BatteryManager.class.getField(name).getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // Primary battery sysfs paths (most common first)
    private static final String[] BATTERY_SYSFS_PATHS = {
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/bms",
            "/sys/class/power_supply/battery0",
            "/sys/class/power_supply/maxfg"
    };

    // OEM battery manufacturers — 扩展列表（含中英文别名）
    private static final String[] KNOWN_OEM_MANUFACTURERS = {
            "coslight",      // 光宇
            "sunwoda",       // 新能源科技
            "desay",         // 德赛
            "scud",          // 飞毛腿
            "byd",           // 比亚迪
            "atlb",          // 新能源
            "lg",            // 乐金
            "chem",          // 化学
            "sanyo",         // 三洋
            "tdk",
            "samsung",       // 三星
            "murata",        // 村田
            "lishen",        // 力神
            "farasis",       // 法拉帝
            "amperex",       // 新能源科技
            "atl",           // 新能源
            "bak",           // 比克
            "eve",           // 亿纬
            "tenpower",      // 天鹏
            "cosmx",         // 冠明
            "guoguang",      // 冠宇
            "zhuhai",        // 珠海冠宇
            "haopeng",       // 豪鹏
            "swb",           // 三沃
            "jianghai",      // 江海
            "sinowatt",      // 三洋
            "sunwatt",       // 阳光
            "huanyu",        // 环宇
            "chnt"           // 正泰
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

    /**
     * 执行电池来源检测，返回包含全部 9 维信息的 {@link OriginResult}。
     * 本方法是整个 App 中电池来源检测的唯一入口。
     */
    public OriginResult detect() {
        OriginResult result = new OriginResult();
        result.brand = Build.BRAND;
        result.model = Build.MODEL;
        result.detectionMethods = new ArrayList<>();

        List<DetectionMethod> methods = new ArrayList<>();
        List<String> sourceTags = new ArrayList<>();

        // ── Android 16 原生 API 优先 ──────────────────────────────
        boolean android16NativeUsed = tryAndroid16NativeApi(result, methods);
        if (android16NativeUsed) {
            sourceTags.add("android16_native");
        }

        // ── 1. 读取综合电池信息 ──────────────────────────────────
        String batteryInfo = readBatteryInfo();
        if (batteryInfo != null) {
            methods.add(new DetectionMethod("电池信息", batteryInfo));
            result.batteryInfo = batteryInfo;
        }

        // ── 2. 检测厂商 ──────────────────────────────────────────
        // Android 16 API 可能已设置 manufacturer，仅当为空时才走 sysfs
        if (result.manufacturer == null) {
            String manufacturer = detectManufacturer();
            if (manufacturer != null) {
                result.manufacturer = manufacturer;
                methods.add(new DetectionMethod("电池厂商", manufacturer));
            }
        }

        // ── 3. 检测生产日期 ──────────────────────────────────────
        String manufactureDate = detectManufactureDate(batteryInfo);
        if (manufactureDate != null) {
            result.manufactureDate = manufactureDate;
            methods.add(new DetectionMethod("生产日期", manufactureDate));
        }

        // ── 4. 检测序列号 ────────────────────────────────────────
        // Android 16 反射可能已设置 serialNumber，仅当为空时才走 sysfs
        if (result.serialNumber == null) {
            String serialNumber = detectSerialNumber(batteryInfo);
            if (serialNumber != null) {
                result.serialNumber = serialNumber;
                methods.add(new DetectionMethod("序列号", serialNumber));
            }
        }

        // ── 5. 检测健康状态 ──────────────────────────────────────
        // Android 16 API 可能已设置 healthStatus，仅当为空时才走 sysfs
        if (result.healthStatus == null) {
            String healthStatus = detectHealthStatus();
            if (healthStatus != null) {
                result.healthStatus = healthStatus;
                methods.add(new DetectionMethod("健康状态", healthStatus));
            }
        }

        // ── 6. 检测循环次数 ──────────────────────────────────────
        String cycleCount = detectCycleCount();
        if (cycleCount != null) {
            result.cycleCount = cycleCount;
            methods.add(new DetectionMethod("循环次数", cycleCount));
        }

        // ── 7. 检测设计容量 vs 当前满充容量 ──────────────────────
        CapacityData capacityData = detectCapacityData();
        if (capacityData != null) {
            // Android 16 API 可能已设置 designCapacity，优先使用 API 值
            if (result.designCapacity <= 0) {
                result.designCapacity = capacityData.designCapacity;
            }
            if (result.currentCapacity <= 0) {
                result.currentCapacity = capacityData.currentCapacity;
            }
            String capacityInfo = capacityData.getDisplayText();
            methods.add(new DetectionMethod("容量信息", capacityInfo));
        }

        // ── 8. 检测出厂标识 ──────────────────────────────────────
        String oemInfo = detectOemInfo();
        if (oemInfo != null) {
            result.oemInfo = oemInfo;
            methods.add(new DetectionMethod("出厂标识", oemInfo));
        }

        // ── 9. 检测电池技术 ──────────────────────────────────────
        String technology = detectTechnology();
        if (technology != null) {
            result.technology = technology;
            methods.add(new DetectionMethod("电池技术", technology));
        }

        // ── 来源标签 ──────────────────────────────────────────────
        boolean sysfsUsed = (batteryInfo != null || result.manufacturer != null
                || result.serialNumber != null || result.healthStatus != null
                || result.cycleCount != null || result.designCapacity > 0
                || result.oemInfo != null || result.technology != null);
        if (sysfsUsed) {
            sourceTags.add("sysfs");
        }
        boolean fallbackUsed = (batteryDataManager != null);
        if (fallbackUsed) {
            sourceTags.add("fallback");
        }
        if (sourceTags.isEmpty()) {
            sourceTags.add("fallback_only");
        }
        result.sourceTag = joinSourceTags(sourceTags);

        // ── 分析 ──────────────────────────────────────────────────
        boolean isOriginal = analyzeOriginal(result);
        result.isOriginal = isOriginal;
        result.confidence = calculateConfidence(result);
        result.conclusion = generateConclusion(result);

        result.detectionMethods = methods;
        result.detectionMethodsJson = serializeMethods(methods);

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Android 16 原生 API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 尝试通过 Android 16 (API 36+) 原生 BatteryManager API 获取电池数据。
     * 成功使用任一 API 即返回 true。
     */
    private boolean tryAndroid16NativeApi(OriginResult result, List<DetectionMethod> methods) {
        if (Build.VERSION.SDK_INT < 36) {
            return false;
        }

        boolean anyUsed = false;
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) {
            return false;
        }

        // BATTERY_PROPERTY_BATTERY_HEALTH (API 36+) — 反射取常量，与 BatteryDataManager 对齐
        try {
            int healthProp = bm.getIntProperty(BATTERY_PROP_BATTERY_HEALTH);
            if (healthProp > 0) {
                String healthStr = healthIntToString(healthProp);
                if (healthStr != null) {
                    result.healthStatus = healthStr;
                    methods.add(new DetectionMethod("健康状态(API36)", healthStr));
                    anyUsed = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "BATTERY_PROPERTY_BATTERY_HEALTH 读取失败", e);
        }

        // BATTERY_PROPERTY_CHARGE_FULL_DESIGN (API 36+) — 反射取常量，与 BatteryDataManager 对齐
        try {
            long designCap = bm.getLongProperty(BATTERY_PROP_CHARGE_FULL_DESIGN);
            if (designCap > 0) {
                // BatteryManager 返回值单位为 μAh，转换为 mAh
                int designMah = (int) (designCap > 100000 ? designCap / 1000 : designCap);
                if (designMah > 100) {
                    result.designCapacity = designMah;
                    methods.add(new DetectionMethod("设计容量(API36)", designMah + " mAh"));
                    anyUsed = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "BATTERY_PROPERTY_CHARGE_FULL_DESIGN 读取失败", e);
        }

        // getBatterySerialNumber via reflection (API 36+)
        try {
            Method getSerial = BatteryManager.class.getMethod("getBatterySerialNumber");
            Object serialObj = getSerial.invoke(bm);
            if (serialObj instanceof String) {
                String serial = ((String) serialObj).trim();
                if (!serial.isEmpty() && !serial.equalsIgnoreCase("unknown")
                        && !serial.equals("0") && !serial.equals("0000000000")) {
                    result.serialNumber = serial;
                    methods.add(new DetectionMethod("序列号(API36)", serial));
                    anyUsed = true;
                }
            }
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "getBatterySerialNumber 方法不存在（非 API 36+ 设备）");
        } catch (Exception e) {
            Log.w(TAG, "getBatterySerialNumber 反射调用失败", e);
        }

        return anyUsed;
    }

    /**
     * 将 BatteryManager 健康度整数值转为可读字符串。
     */
    private static String healthIntToString(int health) {
        switch (health) {
            case 2: return "GOOD";
            case 3: return "OVERHEAT";
            case 4: return "DEAD";
            case 5: return "OVER_VOLTAGE";
            case 6: return "UNSPECIFIED_FAILURE";
            case 7: return "COLD";
            default: return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // sysfs 读取
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // 分析逻辑
    // ═══════════════════════════════════════════════════════════════

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

        // Enhanced: Android 16 native API used is itself a positive signal
        if (result.sourceTag != null && result.sourceTag.contains("android16_native")) {
            positiveSigns += 1;
        }

        return positiveSigns > negativeSigns;
    }

    /**
     * 统一置信度计算（0–100）。本方法是整个 App 中唯一计算置信度的地方。
     * <p>
     * 算法：
     *  基础分 30，根据各维度数据质量加减分，最终 clamp 到 [0, 100]。
     */
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

        // Android 16 native API bonus
        if (result.sourceTag != null && result.sourceTag.contains("android16_native")) {
            confidence += 5;
        }

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

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 读取 sysfs 单行文件。Android 16+ 上若返回 null/空，记录 SELinux 限制警告。
     */
    private String readSysfsFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            if (!f.canRead()) {
                if (Build.VERSION.SDK_INT >= 36) {
                    Log.w(TAG, "sysfs 不可读（可能受 SELinux 限制）: " + path);
                }
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) {
                    if (Build.VERSION.SDK_INT >= 36) {
                        Log.w(TAG, "sysfs 返回空值（可能受 SELinux 限制）: " + path);
                    }
                    return null;
                }
                return line;
            }
        } catch (IOException e) {
            if (Build.VERSION.SDK_INT >= 36) {
                Log.w(TAG, "sysfs 读取异常（可能受 SELinux 限制）: " + path, e);
            }
            return null;
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= 36) {
                Log.w(TAG, "sysfs 读取被安全策略拒绝（SELinux 限制）: " + path, e);
            }
            return null;
        }
    }

    /**
     * 将来源标签列表拼接为 sourceTag 字符串，用 "+" 连接。
     */
    private static String joinSourceTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "fallback_only";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append("+");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    /**
     * 将 DetectionMethod 列表序列化为 JSON 字符串。
     * 使用简单字符串拼接，不依赖 Gson。
     * <p>
     * 格式：[{"name":"电池厂商","value":"coslight"},{"name":"序列号","value":"ABC123"}]
     */
    public static String serializeMethods(List<DetectionMethod> methods) {
        if (methods == null || methods.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < methods.size(); i++) {
            if (i > 0) sb.append(",");
            DetectionMethod m = methods.get(i);
            sb.append("{\"name\":\"");
            sb.append(escapeJson(m.name));
            sb.append("\",\"value\":\"");
            sb.append(escapeJson(m.value));
            sb.append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 简单 JSON 字符串转义，处理引号、反斜杠和换行。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部类
    // ═══════════════════════════════════════════════════════════════

    /**
     * 电池来源检测结果。本类是来源检测的唯一输出结构。
     */
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

        /** 记录实际使用的数据来源，如 "android16_native+sysfs"、"sysfs_only"、"fallback_only" */
        public String sourceTag;

        /** 检测方法序列化后的 JSON 字符串 */
        public String detectionMethodsJson;

        /**
         * 将本结果转换为 {@link BatteryOriginRecord} 以便持久化存储。
         */
        public BatteryOriginRecord toRecord() {
            BatteryOriginRecord record = new BatteryOriginRecord();
            record.timestamp = System.currentTimeMillis();
            record.isOriginal = this.isOriginal;
            record.confidence = this.confidence;
            record.conclusion = this.conclusion;
            record.manufacturer = this.manufacturer;
            record.manufactureDate = this.manufactureDate;
            record.serialNumber = this.serialNumber;
            record.oemInfo = this.oemInfo;
            record.technology = this.technology;
            record.healthStatus = this.healthStatus;
            record.cycleCount = this.cycleCount;
            record.designCapacity = this.designCapacity;
            record.currentCapacity = this.currentCapacity;
            record.batteryInfoRaw = this.batteryInfo;
            record.deviceBrand = this.brand;
            record.deviceModel = this.model;
            record.detectionMethodsJson = this.detectionMethodsJson;
            record.sourceTag = this.sourceTag;
            return record;
        }
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
