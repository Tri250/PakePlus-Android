package com.batteryhealth.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bugreport 本地解析器。
 *
 * 支持解析 Android bugreport ZIP（通常包含 bugreport-xxx.txt 及 FS 数据文件），
 * 从中提取电池、充电、设备、性能相关字段，供后续模块使用。
 *
 * 支持品牌差异化解析：小米/Redmi、OPPO/OnePlus/realme、vivo/iQOO、
 * 华为/荣耀、三星，以及通用 Android 16+ 字段。
 */
public class BugreportParser {

    private static final String TAG = "BugreportParser";

    // 品牌常量
    private static final String BRAND_XIAOMI = "Xiaomi";
    private static final String BRAND_OPPO = "OPPO";
    private static final String BRAND_VIVO = "vivo";
    private static final String BRAND_HUAWEI = "Huawei";
    private static final String BRAND_SAMSUNG = "Samsung";
    private static final String BRAND_GENERIC = "Generic";

    private final Context context;

    public BugreportParser(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 解析用户选择的 bugreport 文件。
     *
     * @param uri 用户通过文件选择器返回的 URI
     * @return 解析后的原始数据
     * @throws IOException 读取或解析失败时抛出
     */
    public ParsedResult parse(Uri uri) throws IOException {
        ParsedResult result = new ParsedResult();
        result.fileName = getFileName(uri);

        StringBuilder fullText = new StringBuilder();
        List<BatterySnapshot> batterySnapshots = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            if (is == null) throw new IOException("无法打开文件");

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (entry.isDirectory()) continue;

                // 只读取文本类文件
                if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
                    String text = readAllText(zis);
                    fullText.append(text).append("\n");

                    // 主 bugreport 文本
                    if (name.contains("bugreport") && name.endsWith(".txt")) {
                        result.mainBugreportText = text;
                    }

                    // 电池历史数据（batterystats 相关）
                    if (name.contains("batterystats") || name.contains("battery")) {
                        batterySnapshots.addAll(extractBatterySnapshots(text));
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            // 可能不是 ZIP，尝试按纯文本读取
            try (InputStream fallback = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fallback, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) fullText.append(line).append("\n");
                result.mainBugreportText = fullText.toString();
                batterySnapshots.addAll(extractBatterySnapshots(result.mainBugreportText));
            }
        }

        result.fullText = fullText.toString();
        result.batterySnapshots = batterySnapshots;
        extractKeyFields(result);
        return result;
    }

    private String getFileName(Uri uri) {
        String name = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = cursor.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (name == null) name = uri.getLastPathSegment();
        return name != null ? name : "unknown";
    }

    private String readAllText(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int len;
        while ((len = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, len);
        }
        return sb.toString();
    }

    private List<BatterySnapshot> extractBatterySnapshots(String text) {
        List<BatterySnapshot> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;

        // 匹配 batterystats 中类似：
        // "0 (screen off) 0 (screen on) ... health: GOOD status: 2 plug: 2 temp: 310 volt: 4210 ..."
        Pattern p = Pattern.compile(
                "status:\\s*(\\d+).*?plug:\\s*(\\d+).*?temp:\\s*(\\d+).*?volt:\\s*(\\d+).*?level:\\s*(\\d+).*?health:\\s*(\\w+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            try {
                BatterySnapshot s = new BatterySnapshot();
                s.status = Integer.parseInt(m.group(1));
                s.plugged = Integer.parseInt(m.group(2));
                s.temperatureDeciC = Integer.parseInt(m.group(3));
                s.voltageMv = Integer.parseInt(m.group(4));
                s.level = Integer.parseInt(m.group(5));
                s.health = m.group(6);
                list.add(s);
            } catch (Exception ignored) {
            }
        }

        // 备选：DUMP OF SERVICE batterystats 中的 "Battery" 段
        if (list.isEmpty()) {
            extractBatteryDump(text, list);
        }
        return list;
    }

    private void extractBatteryDump(String text, List<BatterySnapshot> list) {
        // 匹配 "Capacity: 4450" "Charge counter: ..." "status: 5" 等
        Pattern capP = Pattern.compile("Capacity:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher capM = capP.matcher(text);
        if (capM.find()) {
            BatterySnapshot s = new BatterySnapshot();
            s.level = Integer.parseInt(capM.group(1));

            s.voltageMv = findIntPattern(text, "voltage:\\s*(\\d+)");
            s.temperatureDeciC = findIntPattern(text, "temperature:\\s*(\\d+)");
            s.status = findIntPattern(text, "status:\\s*(\\d+)");
            s.plugged = findIntPattern(text, "plugged:\\s*(\\d+)");
            list.add(s);
        }
    }

    private int findIntPattern(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String findStrPattern(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 尝试多个正则匹配，返回第一个找到的整数值。
     */
    private int findFirstIntMatch(String text, String... regexes) {
        for (String regex : regexes) {
            int val = findIntPattern(text, regex);
            if (val > 0) return val;
        }
        return 0;
    }

    /**
     * 尝试多个正则匹配，返回第一个找到的字符串值。
     */
    private String findFirstStrMatch(String text, String... regexes) {
        for (String regex : regexes) {
            String val = findStrPattern(text, regex);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 检测品牌。通过 Build.MANUFACTURER 和 bugreport 文件名综合判断。
     */
    private String detectBrand(String fileName) {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String fn = fileName != null ? fileName.toLowerCase() : "";

        // 小米/Redmi
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
                || fn.contains("xiaomi") || fn.contains("redmi")) {
            return BRAND_XIAOMI;
        }

        // OPPO/OnePlus/realme
        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus")
                || manufacturer.contains("realme")
                || fn.contains("oppo") || fn.contains("oneplus") || fn.contains("realme")) {
            return BRAND_OPPO;
        }

        // vivo/iQOO
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")
                || fn.contains("vivo") || fn.contains("iqoo")) {
            return BRAND_VIVO;
        }

        // 华为/荣耀
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")
                || fn.contains("huawei") || fn.contains("honor")) {
            return BRAND_HUAWEI;
        }

        // 三星
        if (manufacturer.contains("samsung") || fn.contains("samsung")) {
            return BRAND_SAMSUNG;
        }

        return BRAND_GENERIC;
    }

    /**
     * 按品牌提取健康度，返回 0-100 的整数值，0 表示未找到。
     */
    private int extractHealthByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstIntMatch(text,
                        "mBatteryHealth\\s*[=:]\\s*(\\d+)",
                        "battery_health\\s*[=:]\\s*(\\d+)",
                        "health_percentage\\s*[=:]\\s*(\\d+)");

            case BRAND_OPPO:
                return findFirstIntMatch(text,
                        "battery_health\\s*[=:]\\s*(\\d+)",
                        "health_status\\s*[=:]\\s*(\\d+)",
                        "battery_capacity_ratio\\s*[=:]\\s*(\\d+)");

            case BRAND_VIVO:
                return findFirstIntMatch(text,
                        "battery_health\\s*[=:]\\s*(\\d+)",
                        "bms_health_percent\\s*[=:]\\s*(\\d+)");

            case BRAND_HUAWEI:
                return findFirstIntMatch(text,
                        "battery_health\\s*[=:]\\s*(\\d+)",
                        "health_percent\\s*[=:]\\s*(\\d+)",
                        "battery_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_SAMSUNG:
                return findFirstIntMatch(text,
                        "battery_health\\s*[=:]\\s*(\\d+)",
                        "batt_health_percent\\s*[=:]\\s*(\\d+)");

            default:
                return 0;
        }
    }

    /**
     * 通用健康度提取（兜底）。
     */
    private int extractHealthGeneric(String text) {
        // Android 16+ 专属
        int val = findFirstIntMatch(text,
                "BATTERY_PROPERTY_BATTERY_HEALTH\\s*[=:]\\s*(\\d+)",
                "battery health\\s*[=:]\\s*(\\d+)");
        if (val > 0) return val;

        // 通用
        val = findIntPattern(text, "battery_health\\s*[=:]\\s*(\\d+)");
        if (val > 0) return val;

        val = findIntPattern(text, "health\\s*[=:]\\s*(\\d+)");
        return val;
    }

    /**
     * 按品牌提取循环次数。
     */
    private int extractCycleCountByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstIntMatch(text,
                        "mChargeCycleCount\\s*[=:]\\s*(\\d+)",
                        "charge_cycle\\s*[=:]\\s*(\\d+)");

            case BRAND_OPPO:
                return findFirstIntMatch(text,
                        "charge_cycle_count\\s*[=:]\\s*(\\d+)",
                        "cycle_count\\s*[=:]\\s*(\\d+)");

            case BRAND_VIVO:
                return findFirstIntMatch(text,
                        "charge_cycle\\s*[=:]\\s*(\\d+)",
                        "bms_cycle_count\\s*[=:]\\s*(\\d+)");

            case BRAND_HUAWEI:
                return findFirstIntMatch(text,
                        "charge_cycles\\s*[=:]\\s*(\\d+)",
                        "cycle_count\\s*[=:]\\s*(\\d+)");

            case BRAND_SAMSUNG:
                return findFirstIntMatch(text,
                        "charge_cycle\\s*[=:]\\s*(\\d+)",
                        "batt_cycle_count\\s*[=:]\\s*(\\d+)");

            default:
                return 0;
        }
    }

    /**
     * 通用循环次数提取（兜底）。
     */
    private int extractCycleCountGeneric(String text) {
        int val = findFirstIntMatch(text,
                "BATTERY_PROPERTY_CYCLE_COUNT\\s*[=:]\\s*(\\d+)",
                "cycle_count\\s*[=:]\\s*(\\d+)");
        return val;
    }

    /**
     * 按品牌提取设计容量。
     */
    private int extractDesignCapacityByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstIntMatch(text,
                        "design_capacity\\s*[=:]\\s*(\\d+)",
                        "charge_full_design\\s*[=:]\\s*(\\d+)");

            case BRAND_OPPO:
                return findFirstIntMatch(text,
                        "design_capacity\\s*[=:]\\s*(\\d+)",
                        "rated_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_VIVO:
                return findFirstIntMatch(text,
                        "design_capacity\\s*[=:]\\s*(\\d+)",
                        "nominal_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_HUAWEI:
                return findFirstIntMatch(text,
                        "design_capacity\\s*[=:]\\s*(\\d+)",
                        "rated_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_SAMSUNG:
                return findFirstIntMatch(text,
                        "design_capacity\\s*[=:]\\s*(\\d+)");

            default:
                return 0;
        }
    }

    /**
     * 通用设计容量提取（兜底）。
     */
    private int extractDesignCapacityGeneric(String text) {
        return findFirstIntMatch(text,
                "charge_full_design\\s*[=:]\\s*(\\d+)",
                "design_capacity\\s*[=:]\\s*(\\d+)");
    }

    /**
     * 按品牌提取实际容量。
     */
    private int extractFullCapacityByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstIntMatch(text,
                        "charge_full\\s*[=:]\\s*(\\d+)",
                        "bms_cycle_count\\s*[=:]\\s*(\\d+)");

            case BRAND_OPPO:
                return findFirstIntMatch(text,
                        "full_charge_capacity\\s*[=:]\\s*(\\d+)",
                        "learned_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_VIVO:
                return findFirstIntMatch(text,
                        "full_capacity\\s*[=:]\\s*(\\d+)",
                        "learned_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_HUAWEI:
                return findFirstIntMatch(text,
                        "full_capacity\\s*[=:]\\s*(\\d+)",
                        "learned_capacity\\s*[=:]\\s*(\\d+)");

            case BRAND_SAMSUNG:
                return findFirstIntMatch(text,
                        "full_charge_capacity\\s*[=:]\\s*(\\d+)");

            default:
                return 0;
        }
    }

    /**
     * 通用实际容量提取（兜底）。
     */
    private int extractFullCapacityGeneric(String text) {
        return findFirstIntMatch(text,
                "charge_full\\s*[=:]\\s*(\\d+)",
                "full_charge_capacity\\s*[=:]\\s*(\\d+)",
                "full_capacity\\s*[=:]\\s*(\\d+)");
    }

    /**
     * 按品牌提取电池序列号。
     */
    private String extractBatterySerialByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstStrMatch(text,
                        "battery_serial\\s*[=:]\\s*([^\\s\\n]+)",
                        "bms_serial\\s*[=:]\\s*([^\\s\\n]+)");

            case BRAND_SAMSUNG:
                return findFirstStrMatch(text,
                        "batt_serial\\s*[=:]\\s*([^\\s\\n]+)");

            default:
                return findFirstStrMatch(text,
                        "battery_serial\\s*[=:]\\s*([^\\s\\n]+)",
                        "bms_serial\\s*[=:]\\s*([^\\s\\n]+)",
                        "batt_serial\\s*[=:]\\s*([^\\s\\n]+)");
        }
    }

    /**
     * 按品牌提取电池制造商。
     */
    private String extractBatteryManufacturerByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_XIAOMI:
                return findFirstStrMatch(text,
                        "battery_maker\\s*[=:]\\s*([^\\n]+)",
                        "battery_manufacturer\\s*[=:]\\s*([^\\n]+)");

            default:
                return findFirstStrMatch(text,
                        "battery_manufacturer\\s*[=:]\\s*([^\\n]+)",
                        "battery_maker\\s*[=:]\\s*([^\\n]+)",
                        "manufacturer\\s*[=:]\\s*([^\\n]+)");
        }
    }

    /**
     * 按品牌提取充电协议。
     */
    private String extractChargeProtocolByBrand(String brand, String text) {
        switch (brand) {
            case BRAND_OPPO:
                return findFirstStrMatch(text,
                        "vooc_charging\\s*[=:]\\s*([^\\n]+)",
                        "fastcharge_mode\\s*[=:]\\s*([^\\n]+)");

            case BRAND_VIVO:
                return findFirstStrMatch(text,
                        "flash_charging\\s*[=:]\\s*([^\\n]+)");

            case BRAND_HUAWEI:
                return findFirstStrMatch(text,
                        "scp_charging\\s*[=:]\\s*([^\\n]+)",
                        "super_charge\\s*[=:]\\s*([^\\n]+)");

            default:
                return null;
        }
    }

    /**
     * 计算健康度置信度。
     * 基于数据来源的可靠性：直接读取的健康度 > 容量计算的健康度 > 无数据。
     */
    private float calculateHealthConfidence(ParsedResult result) {
        float confidence = 0f;

        // 有品牌专属字段匹配到的健康度，置信度更高
        if (result.batteryHealthPercent > 0 && result.batteryHealthPercent <= 100) {
            confidence += 0.5f;
        }

        // 有设计容量和实际容量，可以交叉验证
        if (result.designCapacityMah > 0 && result.fullCapacityMah > 0) {
            confidence += 0.3f;
        }

        // 有循环次数，辅助验证
        if (result.cycleCount >= 0) {
            confidence += 0.1f;
        }

        // 品牌检测成功，说明数据更可靠
        if (!BRAND_GENERIC.equals(result.detectedBrand)) {
            confidence += 0.1f;
        }

        return Math.min(confidence, 1.0f);
    }

    private void extractKeyFields(ParsedResult result) {
        String text = result.mainBugreportText != null ? result.mainBugreportText : result.fullText;
        if (text == null) return;

        // 1. 品牌检测
        String brand = detectBrand(result.fileName);
        result.detectedBrand = brand;

        // 2. 健康度：品牌优先，通用兜底
        int health = extractHealthByBrand(brand, text);
        if (health <= 0 || health > 100) {
            health = extractHealthGeneric(text);
        }
        if (health > 0 && health <= 100) {
            result.batteryHealthPercent = health;
        }

        // 3. 循环次数：品牌优先，通用兜底
        int cycles = extractCycleCountByBrand(brand, text);
        if (cycles <= 0) {
            cycles = extractCycleCountGeneric(text);
        }
        if (cycles > 0) {
            result.cycleCount = cycles;
        }

        // 4. 设计容量：品牌优先，通用兜底
        int designCap = extractDesignCapacityByBrand(brand, text);
        if (designCap <= 0) {
            designCap = extractDesignCapacityGeneric(text);
        }
        if (designCap > 1_000_000) designCap /= 1000;
        if (designCap > 0) {
            result.designCapacityMah = designCap;
        }

        // 5. 实际容量：品牌优先，通用兜底
        int fullCap = extractFullCapacityByBrand(brand, text);
        if (fullCap <= 0) {
            fullCap = extractFullCapacityGeneric(text);
        }
        if (fullCap > 1_000_000) fullCap /= 1000;
        if (fullCap > 0) {
            result.fullCapacityMah = fullCap;
        }

        // 6. 电池序列号
        result.batterySerial = extractBatterySerialByBrand(brand, text);

        // 7. 电池制造商
        result.batteryManufacturer = extractBatteryManufacturerByBrand(brand, text);

        // 8. 充电协议
        result.chargeProtocol = extractChargeProtocolByBrand(brand, text);

        // 9. 电压 / 温度（取快照平均值）
        if (!result.batterySnapshots.isEmpty()) {
            BatterySnapshot last = result.batterySnapshots.get(result.batterySnapshots.size() - 1);
            result.voltageMv = last.voltageMv;
            result.temperatureC = last.temperatureDeciC / 10.0;
            // 如果健康度仍未获取，通过容量比计算
            if (result.batteryHealthPercent <= 0 && result.designCapacityMah > 0 && result.fullCapacityMah > 0) {
                result.batteryHealthPercent = (int) (result.fullCapacityMah * 100.0 / result.designCapacityMah);
            }
        }

        // 10. 电池技术
        Matcher techM = Pattern.compile("technology\\s*[=:]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (techM.find()) result.technology = techM.group(1).trim();

        // 11. 充电策略
        Matcher policyM = Pattern.compile("charging policy\\s*[=:]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (policyM.find()) result.chargingPolicy = policyM.group(1).trim();

        // 12. 计算健康度置信度
        result.healthConfidence = calculateHealthConfidence(result);

        Log.d(TAG, "Bugreport parsed: brand=" + result.detectedBrand
                + " design=" + result.designCapacityMah
                + " full=" + result.fullCapacityMah
                + " cycles=" + result.cycleCount
                + " health=" + result.batteryHealthPercent
                + " confidence=" + result.healthConfidence
                + " serial=" + result.batterySerial
                + " manufacturer=" + result.batteryManufacturer
                + " protocol=" + result.chargeProtocol);
    }

    /**
     * 解析结果容器。
     */
    public static class ParsedResult {
        public String fileName;
        public String mainBugreportText;
        public String fullText;
        public List<BatterySnapshot> batterySnapshots = new ArrayList<>();

        public int designCapacityMah;
        public int fullCapacityMah;
        public int cycleCount = -1;
        public int batteryHealthPercent = -1;
        public int voltageMv;
        public double temperatureC;
        public String technology;
        public String chargingPolicy;

        /** 电池制造商 */
        public String batteryManufacturer;
        /** 电池序列号 */
        public String batterySerial;
        /** 充电协议（如 VOOC、FlashCharge、SCP 等） */
        public String chargeProtocol;
        /** 检测到的品牌 */
        public String detectedBrand;
        /** 健康度置信度 0-1 */
        public float healthConfidence;
    }

    /**
     * Bugreport 中某个时间点的电池快照。
     */
    public static class BatterySnapshot {
        public int status;
        public int plugged;
        public int level;
        public int voltageMv;
        public int temperatureDeciC;
        public String health;
    }
}
