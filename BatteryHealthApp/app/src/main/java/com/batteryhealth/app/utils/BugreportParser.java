package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.batteryhealth.app.data.model.AppUsageInfo;
import com.batteryhealth.app.data.model.BatteryHealthReport;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bugreport 本地解析器（基于 v2.1.16 Web 端解析算法移植）
 * 支持 ZIP / TXT / GZ 输入，自动识别小米、vivo、OPPO、华为、三星、魅族、努比亚等品牌，
 * 输出 BatteryHealthReport 对象。
 */
public class BugreportParser {
    private static final String TAG = "BugreportParser";

    // 单次解析最大读取行数，防止异常文件无限读取
    private static final long MAX_LINES = 2_000_000L;
    // 单行文最大长度，防止畸形行导致内存异常
    private static final int MAX_LINE_LENGTH = 8_192;
    // 内容采样阈值，用于品牌检测与原始片段保存
    private static final int BRAND_SAMPLE_LIMIT = 200_000;
    // 最大应用耗电条目数
    private static final int MAX_APP_CONSUMPTION = 10;

    /**
     * 解析 bugreport 文件（支持 .zip / .txt / .gz）。
     */
    public static BatteryHealthReport parse(File file) {
        return parse(null, file);
    }

    /**
     * 解析 bugreport 文件，使用本地机型数据库校准设计容量。
     */
    public static BatteryHealthReport parse(Context context, File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            Log.e(TAG, "Invalid bugreport file");
            return null;
        }
        try {
            ParsedResult result = parseFile(file);
            if (result == null || result.isEmpty()) {
                Log.e(TAG, "No battery data found in bugreport");
                return null;
            }
            return buildReport(context, result, file.getName());
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM while parsing bugreport", oom);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing bugreport", e);
            return null;
        }
    }

    /**
     * 直接解析输入流（ZIP / TXT / GZ）。
     *
     * @param is       输入流（调用方负责关闭）
     * @param fileName 原始文件名，用于判断压缩类型与展示
     * @param mimeType MIME 类型，可为 null
     */
    public static BatteryHealthReport parse(Context context, InputStream is, String fileName, String mimeType) {
        if (is == null) {
            Log.e(TAG, "Invalid input stream");
            return null;
        }
        try {
            ParsedResult result = parseStream(is, fileName, mimeType);
            if (result == null || result.isEmpty()) {
                Log.e(TAG, "No battery data found in bugreport stream");
                return null;
            }
            return buildReport(context, result, fileName != null ? fileName : "bugreport");
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM while parsing bugreport stream", oom);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing bugreport stream", e);
            return null;
        }
    }

    // region 文件/流解析入口

    private static ParsedResult parseFile(File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return parseZipFile(file);
        }
        boolean likelyGzip = name.endsWith(".gz") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
        try (InputStream fis = new FileInputStream(file);
             InputStream decoded = likelyGzip ? new GZIPInputStream(new BufferedInputStream(fis)) : wrapMaybeGzip(fis)) {
            return parseStream(decoded, file.getName(), null);
        }
    }

    private static InputStream wrapMaybeGzip(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        buffered.mark(4);
        byte[] header = new byte[2];
        int read = buffered.read(header);
        buffered.reset();
        if (read == 2 && (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
            return new GZIPInputStream(buffered);
        }
        return buffered;
    }

    private static ParsedResult parseZipFile(File zipFile) throws Exception {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry targetEntry = findBestTextEntry(zf);
            if (targetEntry == null) {
                Log.e(TAG, "No suitable txt entry found in zip");
                return null;
            }
            Log.d(TAG, "Parsing zip entry: " + targetEntry.getName() + " size=" + targetEntry.getSize());
            try (InputStream is = zf.getInputStream(targetEntry)) {
                String entryName = targetEntry.getName().toLowerCase(Locale.ROOT);
                if (entryName.endsWith(".gz")) {
                    return parseStream(new GZIPInputStream(new BufferedInputStream(is)), entryName, null);
                }
                return parseStream(is, entryName, null);
            }
        }
    }

    private static ZipEntry findBestTextEntry(ZipFile zf) {
        Enumeration<? extends ZipEntry> entries = zf.entries();
        ZipEntry best = null;
        int bestScore = -1;
        long bestSize = 0;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String entryName = entry.getName().toLowerCase(Locale.ROOT);

            boolean isText = entryName.endsWith(".txt") || entryName.endsWith(".log");
            boolean isGzipText = entryName.endsWith(".txt.gz") || entryName.endsWith(".log.gz");
            if (!isText && !isGzipText) continue;

            int score = 0;
            long size = entry.getSize();

            if (entryName.contains("bugreport")) {
                score += 200;
                if (entryName.startsWith("bugreport-")) score += 50;
            }
            if (entryName.contains("battery") || entryName.contains("power")) {
                score += 30;
            }
            if (isText) score += 10;

            boolean replace = score > bestScore || (score == bestScore && size > bestSize);
            if (replace) {
                best = entry;
                bestScore = score;
                bestSize = size;
            }
        }
        return best;
    }

    private static ParsedResult parseStream(InputStream is, String fileName, String mimeType) throws Exception {
        String content = readText(is);
        if (content == null || content.isEmpty()) {
            return null;
        }
        return parseContent(content, fileName);
    }

    private static String readText(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        char[] buffer = new char[8192];
        int read;
        long total = 0;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
            total += read;
            if (total > 50 * 1024 * 1024) { // 限制 50MB 文本
                Log.w(TAG, "Bugreport text exceeds 50MB, truncating");
                break;
            }
        }
        return sb.toString();
    }

    // endregion

    // region 内容解析与品牌分发

    private static ParsedResult parseContent(String content, String fileName) {
        String sample = content.length() > BRAND_SAMPLE_LIMIT
                ? content.substring(0, BRAND_SAMPLE_LIMIT)
                : content;

        List<String> entryNames = Collections.emptyList();
        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            entryNames = Collections.singletonList(fileName.toLowerCase(Locale.ROOT));
        }

        String brand = detectBrand(entryNames, sample);
        Log.d(TAG, "Detected brand: " + brand);

        ParsedResult result = parseGeneric(content);
        if (result == null) {
            result = new ParsedResult();
        }

        switch (brand) {
            case "xiaomi":
                result = parseXiaomi(content, result);
                break;
            case "vivo":
            case "iqoo":
                result = parseVivo(content, result);
                break;
            case "oppo":
            case "realme":
            case "oneplus":
                result = parseOPPO(content, result);
                break;
            case "huawei":
            case "honor":
                result = parseHuawei(content, result);
                break;
            case "samsung":
                result = parseSamsung(content, result);
                break;
            case "meizu":
                result = parseMeizu(content, result);
                break;
            case "nubia":
            case "redmagic":
                result = parseNubia(content, result);
                break;
            default:
                // generic 已在 parseGeneric 中处理
                break;
        }

        result.brand = brand;
        result.postProcess();

        if (result.cycleCount > 0) {
            result.healthGrade = calculateHealthGrade((int) result.cycleCount);
        }

        // 如果 brand-specific parser 未拿到容量，做一次 sysfs / power_supply 兜底
        if (result.currentCapacityMah <= 0) {
            fallbackSysfs(content, result);
        }

        // 保存原始内容片段
        if (result.rawContent == null || result.rawContent.isEmpty()) {
            result.rawContent = extractBatterySnippet(content);
        }

        return result;
    }

    /**
     * 根据文件条目名与内容特征识别品牌。
     */
    private static String detectBrand(List<String> entryNames, String contentSample) {
        String contentLower = contentSample.toLowerCase(Locale.ROOT);

        for (String name : entryNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("miui") || lower.contains("xiaomi")) return "xiaomi";
            if (lower.contains("vivo") || lower.contains("funtouch") || lower.contains("origin")) return "vivo";
            if (lower.contains("coloros") || lower.contains("oppo") || lower.contains("oneplus") || lower.contains("oos")) return "oppo";
            if (lower.contains("harmony") || lower.contains("emui") || lower.contains("hmos") || lower.contains("huawei") || lower.contains("honor")) return "huawei";
            if (lower.contains("flyme") || lower.contains("meizu")) return "meizu";
            if (lower.contains("nubia") || lower.contains("redmagic") || lower.contains("redmag")) return "nubia";
            if (lower.contains("samsung") || lower.contains("oneui")) return "samsung";
            if (lower.contains("realme") || lower.contains("realm")) return "realme";
            if (lower.contains("iqoo")) return "iqoo";
            if (lower.contains("zte") || lower.contains("axon")) return "zte";
            if (lower.contains("moto") || lower.contains("motorola")) return "motorola";
        }

        if (contentLower.contains("miui") || contentLower.contains("xiaomi")) return "xiaomi";
        if (contentLower.contains("funtouch") || contentLower.contains("originos") || contentLower.contains("vivo")) return "vivo";
        if (contentLower.contains("coloros") || contentLower.contains("oxygenos") || contentLower.contains("realmeui")) return "oppo";
        if (contentLower.contains("harmonyos") || contentLower.contains("emui") || contentLower.contains("magicui")) return "huawei";
        if (contentLower.contains("flyme")) return "meizu";
        if (contentLower.contains("redmagic") || contentLower.contains("nubia")) return "nubia";
        if (contentLower.contains("samsung") || contentLower.contains("one ui")) return "samsung";

        return "generic";
    }

    // endregion

    // region 通用解析器

    private static ParsedResult parseGeneric(String content) {
        ParsedResult result = new ParsedResult();
        String contentLower = content.toLowerCase(Locale.ROOT);

        // 1. charge_counter (uAh)
        long chargeCounter = matchFirstLong(content, CHARGE_COUNTER_PATTERNS);
        if (chargeCounter > 0 && chargeCounter < 10_000_000L) {
            result.chargeCounterUa = chargeCounter;
            result.currentCapacityMah = Math.round(chargeCounter / 1000f);
            result.confidence = 0.95f;
        }

        // 2. current_now (uA)
        long currentNow = matchFirstLong(content, CURRENT_NOW_PATTERNS);
        if (currentNow != Long.MIN_VALUE) {
            result.currentNowUa = currentNow;
        }

        // 3. capacity (%)
        long capacity = matchFirstLong(content, CAPACITY_PATTERNS);
        if (capacity >= 0 && capacity <= 100) {
            result.capacityPercent = (int) capacity;
        }

        // 4. health
        String health = matchFirstString(content, HEALTH_PATTERNS);
        if (health != null) {
            result.health = health.toLowerCase(Locale.ROOT);
        }

        // 5. cycle_count
        long cycleCount = matchFirstLong(content, CYCLE_COUNT_PATTERNS);
        if (cycleCount >= 0 && cycleCount < 10_000L) {
            result.cycleCount = cycleCount;
        }

        // 6. temperature
        float temp = matchFirstFloat(content, TEMP_PATTERNS);
        if (temp >= -20 && temp <= 80) {
            result.temperatureCelsius = temp;
        }

        // 7. voltage (mV)
        float voltage = parseVoltage(content);
        if (voltage >= 2500 && voltage <= 5000) {
            result.voltageNowMv = voltage;
        }

        // 8. technology
        String tech = matchFirstString(content, TECH_PATTERNS);
        if (tech != null) {
            result.technology = tech;
        }

        // 9. batterystats section
        Matcher bsMatcher = BATTERYCYCLE_PATTERN.matcher(content);
        if (bsMatcher.find() && result.cycleCount <= 0) {
            result.cycleCount = parseLongSafe(bsMatcher.group(1));
        }

        // 10. Battery Properties section
        Matcher bpMatcher = BATTERY_PROPS_PATTERN.matcher(content);
        if (bpMatcher.find()) {
            String section = bpMatcher.group(0);
            if (result.chargeCounterUa <= 0) {
                Matcher cc = Pattern.compile("Charge counter:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (cc.find()) {
                    result.chargeCounterUa = parseLongSafe(cc.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.9f;
                }
            }
            if (result.cycleCount <= 0) {
                Matcher cc = Pattern.compile("Cycle count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (cc.find()) {
                    result.cycleCount = parseLongSafe(cc.group(1));
                }
            }
            if (Float.isNaN(result.temperatureCelsius)) {
                Matcher tm = Pattern.compile("Temperature:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (tm.find()) {
                    float t = parseFloatSafe(tm.group(1));
                    result.temperatureCelsius = t > 100 ? t / 10f : t;
                }
            }
        }

        // 11. Full Charge Capacity / Battery Info fallback
        if (result.currentCapacityMah <= 0) {
            Matcher fcc = FCC_PATTERN.matcher(content);
            if (fcc.find()) {
                result.currentCapacityMah = (int) parseLongSafe(fcc.group(1));
                result.confidence = 0.7f;
            }
        }

        // 12. app consumption
        result.appConsumption = parseAppConsumption(content);

        // 13. screen on time
        result.screenOnTimeMinutes = parseScreenOnTime(content);

        return result.isEmpty() ? null : result;
    }

    // endregion

    // region 品牌专用解析器

    private static ParsedResult parseXiaomi(String content, ParsedResult result) {
        // Battery Service section
        Matcher sectionMatcher = Pattern.compile("Battery Service[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.chargeCounterUa <= 0) {
                Matcher m = Pattern.compile("Charge counter:\\s*(\\d+)\\s*uAh?", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    result.chargeCounterUa = parseLongSafe(m.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.95f;
                }
            }
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("Cycle count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (Float.isNaN(result.temperatureCelsius)) {
                Matcher m = Pattern.compile("Temperature:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    float t = parseFloatSafe(m.group(1));
                    result.temperatureCelsius = t > 100 ? t / 10f : t;
                }
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 2000));
            }
        }

        // ro.miui.battery props
        Matcher propMatcher = Pattern.compile("ro\\.miui\\.battery[\\s\\S]*?(?=\\nro\\.|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (propMatcher.find() && result.cycleCount <= 0) {
            Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(propMatcher.group(0));
            if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
        }

        // dumpstate_battery
        Matcher dumpMatcher = Pattern.compile("dumpstate_battery[\\s\\S]*?(?=\\n\\n|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (dumpMatcher.find() && result.currentCapacityMah <= 0) {
            String section = dumpMatcher.group(0);
            Matcher cap = Pattern.compile("capacity[:\\s]+(\\d+)\\s*mAh", Pattern.CASE_INSENSITIVE).matcher(section);
            Matcher cc = Pattern.compile("charge_counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
            if (cap.find()) {
                result.currentCapacityMah = (int) parseLongSafe(cap.group(1));
                result.confidence = 0.9f;
            } else if (cc.find()) {
                result.chargeCounterUa = parseLongSafe(cc.group(1));
                result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                result.confidence = 0.9f;
            }
        }

        result.brand = "xiaomi";
        return result;
    }

    private static ParsedResult parseVivo(String content, ParsedResult result) {
        long cycle = matchFirstLong(content, VIVO_CYCLE_PATTERNS);
        if (cycle >= 0 && cycle < 10000 && result.cycleCount <= 0) {
            result.cycleCount = cycle;
        }

        float temp = matchFirstFloat(content, VIVO_TEMP_PATTERNS);
        if (temp >= -20 && temp <= 80 && Float.isNaN(result.temperatureCelsius)) {
            result.temperatureCelsius = temp > 100 ? temp / 10f : temp;
        }

        // BatteryInfo section
        Matcher sectionMatcher = Pattern.compile("BatteryInfo[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.chargeCounterUa <= 0) {
                Matcher m = Pattern.compile("ChargeCounter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    result.chargeCounterUa = parseLongSafe(m.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.9f;
                }
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }

        result.brand = "vivo";
        return result;
    }

    private static ParsedResult parseOPPO(String content, ParsedResult result) {
        Matcher sectionMatcher = Pattern.compile("Battery Information[\\s\\S]*?(?=\\n\\n|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("Cycle Count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (Float.isNaN(result.temperatureCelsius)) {
                Matcher m = Pattern.compile("Temperature[:\\s]+(\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    float t = parseFloatSafe(m.group(1));
                    result.temperatureCelsius = t > 100 ? t / 10f : t;
                }
            }
            if (result.chargeCounterUa <= 0) {
                Matcher m = Pattern.compile("Charge Counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("FCC[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    result.chargeCounterUa = parseLongSafe(m.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.9f;
                }
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }

        // OxygenOS
        Matcher oxygenMatcher = Pattern.compile("OxygenOS Battery[\\s\\S]*?(?=\\n\\n|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (oxygenMatcher.find()) {
            String section = oxygenMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (result.currentCapacityMah <= 0) {
                Matcher m = Pattern.compile("capacity[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    result.chargeCounterUa = parseLongSafe(m.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.85f;
                }
            }
        }

        result.brand = "oppo";
        return result;
    }

    private static ParsedResult parseHuawei(String content, ParsedResult result) {
        Matcher sectionMatcher = Pattern.compile("Battery Stats[\\s\\S]*?(?=\\n\\n|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (!sectionMatcher.find()) {
            sectionMatcher = Pattern.compile("BatteryInfo[\\s\\S]*?(?=\\n\\n|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
            sectionMatcher.find();
        }
        if (sectionMatcher.group() != null) {
            String section = sectionMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("Charge cycles?[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (Float.isNaN(result.temperatureCelsius)) {
                Matcher m = Pattern.compile("Battery temp[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    float t = parseFloatSafe(m.group(1));
                    result.temperatureCelsius = t > 100 ? t / 10f : t;
                }
            }
            if (result.chargeCounterUa <= 0) {
                Matcher m = Pattern.compile("Charge counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("FCC[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("Full charge capacity[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) {
                    result.chargeCounterUa = parseLongSafe(m.group(1));
                    result.currentCapacityMah = Math.round(result.chargeCounterUa / 1000f);
                    result.confidence = 0.9f;
                }
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }

        // hw.battery props
        Matcher propMatcher = Pattern.compile("hw\\.battery[\\s\\S]*?(?=\\nhw\\.|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (propMatcher.find() && result.cycleCount <= 0) {
            Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(propMatcher.group(0));
            if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
        }

        result.brand = "huawei";
        return result;
    }

    private static ParsedResult parseSamsung(String content, ParsedResult result) {
        Matcher sectionMatcher = Pattern.compile("Battery[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("Cycle[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }
        result.brand = "samsung";
        return result;
    }

    private static ParsedResult parseMeizu(String content, ParsedResult result) {
        Matcher sectionMatcher = Pattern.compile("BatteryInfo[\\s\\S]*?(?=\\n\\n|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }
        result.brand = "meizu";
        return result;
    }

    private static ParsedResult parseNubia(String content, ParsedResult result) {
        Matcher sectionMatcher = Pattern.compile("Battery[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (sectionMatcher.find()) {
            String section = sectionMatcher.group(0);
            if (result.cycleCount <= 0) {
                Matcher m = Pattern.compile("cycle_count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (!m.find()) m = Pattern.compile("Cycle count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(section);
                if (m.find()) result.cycleCount = parseLongSafe(m.group(1));
            }
            if (result.rawContent == null) {
                result.rawContent = section.substring(0, Math.min(section.length(), 1500));
            }
        }
        result.brand = "nubia";
        return result;
    }

    // endregion

    // region sysfs / power_supply 兜底

    private static void fallbackSysfs(String content, ParsedResult result) {
        Matcher cc = Pattern.compile("/sys/class/power_supply/battery/charge_counter[\\s\\S]*?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (cc.find() && result.chargeCounterUa <= 0) {
            long v = parseLongSafe(cc.group(1));
            if (v > 0) {
                result.chargeCounterUa = v;
                result.currentCapacityMah = Math.round(v / 1000f);
                if (result.confidence < 0.85f) result.confidence = 0.85f;
            }
        }
        Matcher cy = Pattern.compile("/sys/class/power_supply/battery/cycle_count[\\s\\S]*?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (cy.find() && result.cycleCount <= 0) {
            result.cycleCount = parseLongSafe(cy.group(1));
        }
        Matcher tm = Pattern.compile("/sys/class/power_supply/battery/temp[\\s\\S]*?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (tm.find() && Float.isNaN(result.temperatureCelsius)) {
            float t = parseFloatSafe(tm.group(1));
            result.temperatureCelsius = t > 100 ? t / 10f : t;
        }
        Matcher vm = Pattern.compile("/sys/class/power_supply/battery/voltage_now[\\s\\S]*?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (vm.find() && result.voltageNowMv <= 0) {
            float v = parseFloatSafe(vm.group(1));
            result.voltageNowMv = v > 10000 ? v / 1000f : v;
        }
    }

    // endregion

    // region 正则工具与模式

    private static final Pattern[] CHARGE_COUNTER_PATTERNS = {
            Pattern.compile("charge[_\\s-]?counter[:\\s]+(\\d+)\\s*uah", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge[_\\s-]?counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CHARGE_COUNTER[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cc[:\\s]+(\\d+)\\s*uah", Pattern.CASE_INSENSITIVE),
            Pattern.compile("last[_\\s-]?full[_\\s-]?charge[_\\s-]?counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("full[_\\s-]?charge[_\\s-]?capacity[:\\s]+(\\d+)\\s*uah", Pattern.CASE_INSENSITIVE),
            Pattern.compile("fcc[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcharge_counter\\b[\\s:=]+(\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] CURRENT_NOW_PATTERNS = {
            Pattern.compile("current[_\\s-]?now[:\\s]+(-?\\d+)\\s*ua", Pattern.CASE_INSENSITIVE),
            Pattern.compile("current[_\\s-]?now[:\\s]+(-?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CURRENT_NOW[:\\s]+(-?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcurrent_now\\b[\\s:=]+(-?\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] CAPACITY_PATTERNS = {
            Pattern.compile("capacity[:\\s]+(\\d+)\\s*%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CAPACITY[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("level[:\\s]+(\\d+)\\s*%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcapacity\\b[\\s:=]+(\\d+)(?!\\s*mah)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] HEALTH_PATTERNS = {
            Pattern.compile("health[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("HEALTH[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("battery[_\\s-]?health[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bhealth\\b[\\s:=]+(\\w+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] CYCLE_COUNT_PATTERNS = {
            Pattern.compile("cycle[_\\s-]?count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("CYCLE_COUNT[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge[_\\s-]?cycle[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("battery[_\\s-]?cycle[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cycle[_\\s-]?counter[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cc[:\\s]+(\\d+)(?!\\s*uah)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge[_\\s-]?cycles[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("充电循环次数[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("循环次数[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("累计循环[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcycle_count\\b[\\s:=]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcycle\\b[\\s:=]+(\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] TEMP_PATTERNS = {
            Pattern.compile("temperature[:\\s]+(-?\\d+\\.?\\d*)\\s*°c", Pattern.CASE_INSENSITIVE),
            Pattern.compile("temperature[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TEMP[:\\s]+(-?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("battery[_\\s-]?temp[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("temp[:\\s]+(-?\\d+\\.?\\d*)(?!\\s*%)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("电池温度[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("温度[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btemperature\\b[\\s:=]+(-?\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] VOLTAGE_PATTERNS = {
            Pattern.compile("voltage[:\\s]+(\\d+\\.?\\d*)\\s*v", Pattern.CASE_INSENSITIVE),
            Pattern.compile("voltage[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("VOLTAGE[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("batt[_\\s-]?voltage[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bvoltage\\b[\\s:=]+(\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] TECH_PATTERNS = {
            Pattern.compile("technology[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("TECHNOLOGY[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("battery[_\\s-]?type[:\\s]+(\\w+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btechnology\\b[\\s:=]+(\\w+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern BATTERYCYCLE_PATTERN = Pattern.compile("Daily stats[\\s\\S]*?charge cycles:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BATTERY_PROPS_PATTERN = Pattern.compile("Battery Properties[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FCC_PATTERN = Pattern.compile("(?:full[_\\s-]?charge[_\\s-]?capacity|fcc|design[_\\s-]?capacity)[:\\s]+(\\d+)\\s*mah", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] VIVO_CYCLE_PATTERNS = {
            Pattern.compile("充电循环次数[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("循环次数[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge[_\\s-]?cycles[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cycle[_\\s-]?count[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] VIVO_TEMP_PATTERNS = {
            Pattern.compile("电池温度[:\\s]+(-?\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("温度[:\\s]+(-?\\d+\\.?\\d*)°C", Pattern.CASE_INSENSITIVE),
            Pattern.compile("battery[_\\s-]?temp[:\\s]+(-?\\d+)", Pattern.CASE_INSENSITIVE)
    };

    private static long matchFirstLong(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return parseLongSafe(m.group(1));
            }
        }
        return Long.MIN_VALUE;
    }

    private static String matchFirstString(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static float matchFirstFloat(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return parseFloatSafe(m.group(1));
            }
        }
        return Float.NaN;
    }

    private static float parseVoltage(String content) {
        for (Pattern p : VOLTAGE_PATTERNS) {
            Matcher m = p.matcher(content);
            if (m.find()) {
                float v = parseFloatSafe(m.group(1));
                if (v > 10000) v = v / 1000f;
                else if (v >= 3 && v <= 5) v = v * 1000f;
                if (v >= 2500 && v <= 5000) return v;
            }
        }
        return 0;
    }

    private static long parseLongSafe(String value) {
        if (value == null) return Long.MIN_VALUE;
        value = value.trim();
        if (value.isEmpty()) return Long.MIN_VALUE;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static float parseFloatSafe(String value) {
        if (value == null) return Float.NaN;
        value = value.trim();
        if (value.isEmpty()) return Float.NaN;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    // endregion

    // region 应用耗电与亮屏时间

    private static final Pattern ESTIMATED_POWER_PATTERN =
            Pattern.compile("^\\s*([#\\d\\.]+\\s*%?\\s+)?([\\w\\.]+):\\s+([\\d\\.]+)\\s*mAh", Pattern.CASE_INSENSITIVE);

    private static List<AppConsumptionItem> parseAppConsumption(String content) {
        List<AppConsumptionItem> list = new ArrayList<>();
        Matcher m = ESTIMATED_POWER_PATTERN.matcher(content);
        while (m.find() && list.size() < MAX_APP_CONSUMPTION) {
            String name = m.group(2);
            String mahStr = m.group(3);
            try {
                float mah = Float.parseFloat(mahStr);
                if (mah > 0 && name != null && !name.isEmpty()) {
                    list.add(new AppConsumptionItem(name, mah));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private static long parseScreenOnTime(String content) {
        Matcher m = Pattern.compile("Screen on time:\\s*(\\d+)\\s*min", Pattern.CASE_INSENSITIVE).matcher(content);
        if (m.find()) return parseLongSafe(m.group(1));
        m = Pattern.compile("Screen on:\\s*(\\d+)\\s*ms", Pattern.CASE_INSENSITIVE).matcher(content);
        if (m.find()) return parseLongSafe(m.group(1)) / 60000;
        return 0;
    }

    private static String extractBatterySnippet(String content) {
        Matcher m = Pattern.compile("Battery[\\s\\S]*?(?=\\n\\n[A-Z]|\\n[A-Z][a-z]+:|$)", Pattern.CASE_INSENSITIVE).matcher(content);
        if (m.find()) {
            String s = m.group(0);
            return s.substring(0, Math.min(s.length(), 1500));
        }
        return content.substring(0, Math.min(content.length(), 1500));
    }

    // endregion

    // region 健康等级

    static class HealthGrade {
        final String grade;
        final String color;
        final String description;
        final String estimatedHealth;

        HealthGrade(String grade, String color, String description, String estimatedHealth) {
            this.grade = grade;
            this.color = color;
            this.description = description;
            this.estimatedHealth = estimatedHealth;
        }
    }

    private static HealthGrade calculateHealthGrade(int cycleCount) {
        if (cycleCount <= 100) {
            return new HealthGrade("A+", "#2ecc71", "电池状态极佳，几乎无损耗", "95-100%");
        } else if (cycleCount <= 200) {
            return new HealthGrade("A", "#27ae60", "电池状态优秀，轻微使用", "90-95%");
        } else if (cycleCount <= 300) {
            return new HealthGrade("B+", "#3498db", "电池状态良好，正常使用", "85-90%");
        } else if (cycleCount <= 500) {
            return new HealthGrade("B", "#2980b9", "电池状态正常，常规老化", "80-85%");
        } else if (cycleCount <= 700) {
            return new HealthGrade("C", "#f39c12", "电池轻度老化，续航下降", "75-80%");
        } else if (cycleCount <= 900) {
            return new HealthGrade("D", "#e67e22", "电池中度老化，建议关注", "70-75%");
        } else {
            return new HealthGrade("E", "#e74c3c", "电池严重老化，建议更换", "<70%");
        }
    }

    // endregion

    // region 报告构建

    private static BatteryHealthReport buildReport(Context context, ParsedResult r, String fileName) {
        DeviceDatabaseManager db = null;
        if (context != null) {
            db = DeviceDatabaseManager.getInstance(context);
        }

        int designCapacity = 0;
        String brandName = formatBrand(r.brand);
        if (db != null) {
            // 优先使用解析出的品牌+型号查询数据库
            String model = Build.MODEL;
            designCapacity = db.getCapacity(brandName, model);
            if (designCapacity <= 0) {
                designCapacity = db.getDesignCapacity();
            }
        }

        int currentCapacityMah = r.currentCapacityMah;
        int chargeCounterUa = (int) Math.max(0, r.chargeCounterUa);

        float healthPercentage = 0f;
        if (designCapacity > 0 && currentCapacityMah > 0) {
            healthPercentage = Math.min(100f, Math.max(0f, currentCapacityMah * 100f / designCapacity));
        }

        String healthLevel = getHealthLevel(healthPercentage);
        String healthGrade = r.healthGrade != null ? r.healthGrade.grade : null;

        BatteryHealthReport report = new BatteryHealthReport();
        report.setBrand(brandName);
        report.setModel(Build.MODEL);
        report.setBatteryHealthPercentage(healthPercentage);
        report.setBatteryHealthLevel(healthLevel);
        report.setHealthGrade(healthGrade != null ? healthGrade : healthLevel);
        report.setDesignCapacityMah(designCapacity);
        report.setCurrentCapacityMah(currentCapacityMah);
        report.setCycleCount((int) r.cycleCount);
        report.setCapacityPercent(r.capacityPercent);
        report.setChargeCounter(chargeCounterUa);
        report.setCurrentNow((int) r.currentNowUa);
        report.setVoltageNowUv((long) (r.voltageNowMv * 1000));
        report.setTemperatureNowCelsius(Float.isNaN(r.temperatureCelsius) ? 0f : r.temperatureCelsius);
        report.setTechnology(r.technology != null ? r.technology : "Li-ion");
        report.setBatterySource("Bugreport解析 · " + brandName + (r.health != null ? " · " + r.health : ""));
        report.setBatterySourceConfidence(r.confidence);
        report.setRawContentSnippet(r.rawContent);
        report.setScreenOnTimeMinutes(r.screenOnTimeMinutes);
        report.setParsedAt(new Date());
        report.setAppUsageList(convertAppConsumption(r.appConsumption));
        report.setAppConsumption(convertAppConsumptionList(r.appConsumption));
        report.setRecommendations(buildRecommendations(r, healthPercentage, report.getBatterySource()));

        return report;
    }

    private static String formatBrand(String brand) {
        if (brand == null) return "通用";
        switch (brand.toLowerCase(Locale.ROOT)) {
            case "xiaomi": return "小米";
            case "redmi": return "红米";
            case "vivo": return "vivo";
            case "iqoo": return "iQOO";
            case "oppo": return "OPPO";
            case "realme": return "realme";
            case "oneplus": return "一加";
            case "huawei": return "华为";
            case "honor": return "荣耀";
            case "samsung": return "三星";
            case "meizu": return "魅族";
            case "nubia": return "努比亚";
            case "redmagic": return "红魔";
            default: return brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1).toLowerCase(Locale.ROOT);
        }
    }

    private static String getHealthLevel(float percentage) {
        if (percentage <= 0) return "未知";
        if (percentage >= 90) return "优秀";
        if (percentage >= 80) return "良好";
        if (percentage >= 70) return "一般";
        return "较差";
    }

    private static List<AppUsageInfo> convertAppConsumption(List<AppConsumptionItem> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        List<AppUsageInfo> list = new ArrayList<>();
        for (AppConsumptionItem item : items) {
            list.add(new AppUsageInfo(item.name, item.name, 0L, item.mah, 0));
        }
        return list;
    }

    private static List<BatteryHealthReport.AppConsumption> convertAppConsumptionList(List<AppConsumptionItem> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        float total = 0;
        for (AppConsumptionItem item : items) total += item.mah;
        List<BatteryHealthReport.AppConsumption> list = new ArrayList<>();
        for (AppConsumptionItem item : items) {
            float percent = total > 0 ? (item.mah / total) * 100f : 0f;
            int minutes = (int) (item.mah * 10);
            list.add(new BatteryHealthReport.AppConsumption(item.name, item.name, minutes, percent));
        }
        return list;
    }

    private static List<BatteryHealthReport.Recommendation> buildRecommendations(ParsedResult r, float healthPercentage, String source) {
        List<BatteryHealthReport.Recommendation> list = new ArrayList<>();
        if (r.healthGrade != null) {
            list.add(new BatteryHealthReport.Recommendation(
                    "健康等级 " + r.healthGrade.grade,
                    r.healthGrade.description + "（估算健康度 " + r.healthGrade.estimatedHealth + "）",
                    healthPercentage < 80 ? "high" : "low"));
        }
        if (healthPercentage > 0 && healthPercentage < 80) {
            list.add(new BatteryHealthReport.Recommendation(
                    "健康度偏低",
                    "当前估算健康度为 " + String.format(Locale.getDefault(), "%.1f%%", healthPercentage) + "，建议前往官方售后检测电池。",
                    "high"));
        }
        if (!Float.isNaN(r.temperatureCelsius) && r.temperatureCelsius > 42) {
            list.add(new BatteryHealthReport.Recommendation(
                    "温度过高",
                    "电池温度 " + String.format(Locale.getDefault(), "%.1f°C", r.temperatureCelsius) + "，建议避免边充边玩并保持通风。",
                    "high"));
        }
        if (r.cycleCount > 500) {
            list.add(new BatteryHealthReport.Recommendation(
                    "循环次数较多",
                    "电池循环次数约 " + r.cycleCount + " 次，注意保养以延长寿命。",
                    "medium"));
        }
        if (list.isEmpty()) {
            list.add(new BatteryHealthReport.Recommendation(
                    "解析完成",
                    "已从 bugreport 提取基础电池信息，数据来源：" + source + "。",
                    "low"));
        }
        return list;
    }

    // endregion

    // region 中间结果容器

    static class ParsedResult {
        String brand = "generic";
        long chargeCounterUa = 0;
        long currentNowUa = 0;
        int capacityPercent = -1;
        String health;
        long cycleCount = 0;
        float temperatureCelsius = Float.NaN;
        float voltageNowMv = 0;
        String technology;
        String rawContent;
        float confidence = 0f;
        HealthGrade healthGrade;
        int currentCapacityMah = 0;
        List<AppConsumptionItem> appConsumption = new ArrayList<>();
        long screenOnTimeMinutes = 0;

        void postProcess() {
            if (currentCapacityMah <= 0 && chargeCounterUa > 0) {
                currentCapacityMah = Math.round(chargeCounterUa / 1000f);
            }
        }

        boolean isEmpty() {
            return currentCapacityMah <= 0 && chargeCounterUa <= 0 && cycleCount <= 0
                    && voltageNowMv <= 0 && Float.isNaN(temperatureCelsius)
                    && (appConsumption == null || appConsumption.isEmpty());
        }
    }

    static class AppConsumptionItem {
        final String name;
        final float mah;

        AppConsumptionItem(String name, float mah) {
            this.name = name;
            this.mah = mah;
        }
    }

    // endregion
}
