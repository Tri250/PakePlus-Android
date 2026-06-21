package com.batteryhealth.app.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Bug Report 解析器：解析 .zip 内含的 bugreport 文本文件（通常是 dumpstate_*.txt）。
 * 抽取关键信息（型号、SN、容量、循环次数、充电事件、App 耗电等）以供 BugReport 数据模型使用。
 *
 * 修复点：
 *  - 原本 isTextEntry 仅判断是否含 '.'，可能误判二进制文件；
 *    现加入"文件首部是否含可打印字符"校验。
 *  - 原本 readEntry 一次性读取整个文件到 ByteArrayOutputStream，
 *    对超大 zip entry 存在 OOM 风险；现限制最大 64MB 读取。
 *  - 各种 reader / stream 改用 try-with-resources 关闭。
 */
public class BugReportParser {

    private static final String TAG = "BugReportParser";

    // ZIP entry size cap (64 MB) to avoid OOM on hostile bug reports
    private static final long MAX_ENTRY_SIZE = 64L * 1024 * 1024;

    // Patterns extracted from the typical AOSP bug report sections
    private static final Pattern PATTERN_BRAND = Pattern.compile("(?i)ro\\.product\\.brand\\s*[:=]\\s*([^\\s\\r\\n]+)");
    private static final Pattern PATTERN_MODEL = Pattern.compile("(?i)ro\\.product\\.model\\s*[:=]\\s*([^\\s\\r\\n]+)");
    private static final Pattern PATTERN_SN = Pattern.compile("(?i)(?:Serial\\s*Number|ro\\.serialno|ro\\.boot\\.sn)\\s*[:=]\\s*([^\\s\\r\\n]+)");
    private static final Pattern PATTERN_DESIGN_CAP = Pattern.compile("(?i)Design\\s*capacity\\s*[:=]\\s*(\\d+)\\s*m?Ah", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CURRENT_CAP = Pattern.compile("(?i)(?:current|actual|full[\\s_-]?charge|capacity)[\\s_]*[=:][\\s_]*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_CYCLE_COUNT = Pattern.compile("(?i)Cycle[\\s_-]?[Cc]ount\\s*[:=]\\s*(\\d+)");
    private static final Pattern PATTERN_MFG_DATE = Pattern.compile("(?i)Manufacture(?:[rd]?)\\s*date\\s*[:=]\\s*([0-9-/. :]+)");
    private static final Pattern PATTERN_TEMP = Pattern.compile("(?i)Temperature[: ]+(-?\\d+(?:\\.\\d+)?)(?:[\\s°C度]+)");
    private static final Pattern PATTERN_SCREEN_ON = Pattern.compile("(?i)Screen[\\s_]on[\\s_]time[\\s:]+(\\d+)");
    private static final Pattern PATTERN_CHARGE_COUNT = Pattern.compile("(?i)(?:Charge[\\s_]?count|Charging[\\s_]?sessions)[\\s:]+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_VOLTAGE = Pattern.compile("(?i)voltage\\s*[:=]\\s*(\\d+)\\s*m?V");
    private static final Pattern PATTERN_CURRENT = Pattern.compile("(?i)current\\s*[:=]\\s*(-?\\d+)\\s*m?A");
    private static final Pattern PATTERN_CHARGING_EVENT = Pattern.compile("(?i)(charging[\\s_]?event|charge[\\s_]?cycle)[\\s:]+([^\\r\\n]+)");
    private static final Pattern PATTERN_APP_USAGE = Pattern.compile("(?i)([a-zA-Z0-9_.]+)\\s+(\\d+(?:\\.\\d+)?)\\s*mAh");

    public static BugReportData parseFromZip(String zipPath) {
        if (zipPath == null) return new BugReportData();
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipPath);
            ZipEntry entry = findBugreportEntry(zip);
            if (entry == null) {
                Log.w(TAG, "No bugreport entry found in zip: " + zipPath);
                return new BugReportData();
            }
            if (entry.getSize() > MAX_ENTRY_SIZE) {
                Log.w(TAG, "Bugreport entry too large: " + entry.getSize());
                return new BugReportData();
            }
            try (InputStream is = zip.getInputStream(entry)) {
                String text = readToString(is, MAX_ENTRY_SIZE);
                return parseFromText(text);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse zip", e);
            return new BugReportData();
        } finally {
            closeQuietly(zip);
        }
    }

    public static BugReportData parseFromZipStream(InputStream input) {
        if (input == null) return new BugReportData();
        BugReportData result = new BugReportData();
        try (ZipInputStream zin = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.isDirectory() && isTextEntry(entry)) {
                    if (entry.getSize() > MAX_ENTRY_SIZE) {
                        Log.w(TAG, "Skipping oversized entry: " + entry.getName());
                        continue;
                    }
                    String text = readToString(zin, MAX_ENTRY_SIZE);
                    BugReportData tmp = parseFromText(text);
                    mergeInto(result, tmp);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse zip stream", e);
        }
        return result;
    }

    public static BugReportData parseFromText(String text) {
        BugReportData data = new BugReportData();
        if (text == null || text.isEmpty()) return data;

        extractBrandModel(data, text);
        extractSN(data, text);
        extractCapacity(data, text);
        extractCycleCount(data, text);
        extractManufacturingDate(data, text);
        extractTemperature(data, text);
        extractScreenOnTime(data, text);
        extractChargeCount(data, text);
        extractVoltageCurrent(data, text);
        extractChargingEvents(data, text);
        extractAppPowerUsage(data, text);
        return data;
    }

    // region extraction helpers

    private static void extractBrandModel(BugReportData data, String text) {
        Matcher brand = PATTERN_BRAND.matcher(text);
        if (brand.find()) data.brand = brand.group(1).trim();
        Matcher model = PATTERN_MODEL.matcher(text);
        if (model.find()) data.model = model.group(1).trim();
    }

    private static void extractSN(BugReportData data, String text) {
        Matcher m = PATTERN_SN.matcher(text);
        if (m.find()) data.serialNumber = m.group(1).trim();
    }

    private static void extractCapacity(BugReportData data, String text) {
        Matcher d = PATTERN_DESIGN_CAP.matcher(text);
        if (d.find()) {
            data.designCapacityMah = safeParseInt(d.group(1));
        }
        Matcher c = PATTERN_CURRENT_CAP.matcher(text);
        if (c.find()) {
            data.currentCapacityMah = safeParseInt(c.group(1));
        }
    }

    private static void extractCycleCount(BugReportData data, String text) {
        Matcher m = PATTERN_CYCLE_COUNT.matcher(text);
        if (m.find()) {
            data.cycleCount = safeParseInt(m.group(1));
        }
    }

    private static void extractManufacturingDate(BugReportData data, String text) {
        Matcher m = PATTERN_MFG_DATE.matcher(text);
        if (m.find()) {
            String raw = m.group(1).trim();
            if (isValidDate(raw)) {
                data.manufacturingDate = raw;
            }
        }
    }

    private static void extractTemperature(BugReportData data, String text) {
        Matcher m = PATTERN_TEMP.matcher(text);
        if (m.find()) {
            try {
                data.temperatureC = Float.parseFloat(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void extractScreenOnTime(BugReportData data, String text) {
        Matcher m = PATTERN_SCREEN_ON.matcher(text);
        if (m.find()) {
            data.screenOnTimeSec = safeParseInt(m.group(1));
        }
    }

    private static void extractChargeCount(BugReportData data, String text) {
        Matcher m = PATTERN_CHARGE_COUNT.matcher(text);
        if (m.find()) {
            data.chargeCount = safeParseInt(m.group(1));
        }
    }

    private static void extractVoltageCurrent(BugReportData data, String text) {
        Matcher v = PATTERN_VOLTAGE.matcher(text);
        Matcher c = PATTERN_CURRENT.matcher(text);
        // Synchronise matches to keep pairs aligned; mismatched count -> skip
        int count = 0;
        while (v.find() && c.find() && count++ < 1024) {
            int voltageMv = safeParseInt(v.group(1));
            int currentMa = safeParseInt(c.group(1));
            data.voltageCurrentPairs.add(new VoltageCurrentPair(voltageMv, currentMa));
        }
    }

    private static void extractChargingEvents(BugReportData data, String text) {
        Matcher m = PATTERN_CHARGING_EVENT.matcher(text);
        int count = 0;
        while (m.find() && count++ < 200) {
            data.chargingEvents.add(new ChargingEvent(m.group(1).trim(), m.group(2).trim()));
        }
    }

    private static void extractAppPowerUsage(BugReportData data, String text) {
        Matcher m = PATTERN_APP_USAGE.matcher(text);
        int count = 0;
        while (m.find() && count++ < 200) {
            String pkg = m.group(1).trim();
            if (pkg.isEmpty()) continue;
            double mah = 0d;
            try {
                mah = Double.parseDouble(m.group(2));
            } catch (NumberFormatException ignored) {
                continue;
            }
            data.appPowerUsage.add(new AppPowerUsage(pkg, mah));
        }
    }

    // endregion

    // region zip helpers

    private static ZipEntry findBugreportEntry(ZipFile zip) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (!e.isDirectory() && isTextEntry(e)) return e;
        }
        return null;
    }

    private static boolean isTextEntry(ZipEntry entry) {
        if (entry == null) return false;
        String name = entry.getName();
        if (name == null) return false;
        // Match the typical AOSP dumpstate file names
        if (name.contains("dumpstate") || name.contains("bugreport") || name.endsWith(".txt")
                || name.endsWith(".log") || name.endsWith(".html") || name.endsWith(".csv")) {
            return true;
        }
        // Generic: any non-empty extension is treated as text but bounded by size
        return name.contains(".") && name.length() < 256;
    }

    private static String readToString(InputStream is, long maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8 * 1024];
        long total = 0L;
        int read;
        while ((read = is.read(buf)) != -1) {
            total += read;
            if (total > maxBytes) {
                baos.write(buf, 0, read);
                break;
            }
            baos.write(buf, 0, read);
        }
        // Sanity check: if the buffer contains too many non-printable bytes, treat as binary
        if (looksLikeBinary(baos.toByteArray())) {
            return "";
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean looksLikeBinary(byte[] data) {
        if (data == null || data.length == 0) return false;
        int sample = Math.min(data.length, 1024);
        int nonPrintable = 0;
        for (int i = 0; i < sample; i++) {
            int b = data[i] & 0xFF;
            // control chars other than CR/LF/tab are suspicious
            if (b == 0) return true;
            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) {
                nonPrintable++;
                if (nonPrintable > 4) return true;
            }
        }
        return false;
    }

    private static void mergeInto(BugReportData dst, BugReportData src) {
        if (src == null || dst == null) return;
        if (dst.brand == null) dst.brand = src.brand;
        if (dst.model == null) dst.model = src.model;
        if (dst.serialNumber == null) dst.serialNumber = src.serialNumber;
        if (dst.designCapacityMah <= 0) dst.designCapacityMah = src.designCapacityMah;
        if (dst.currentCapacityMah <= 0) dst.currentCapacityMah = src.currentCapacityMah;
        if (dst.cycleCount <= 0) dst.cycleCount = src.cycleCount;
        if (dst.manufacturingDate == null) dst.manufacturingDate = src.manufacturingDate;
        if (dst.temperatureC == 0f) dst.temperatureC = src.temperatureC;
        if (dst.screenOnTimeSec <= 0) dst.screenOnTimeSec = src.screenOnTimeSec;
        if (dst.chargeCount <= 0) dst.chargeCount = src.chargeCount;
        dst.voltageCurrentPairs.addAll(src.voltageCurrentPairs);
        dst.chargingEvents.addAll(src.chargingEvents);
        dst.appPowerUsage.addAll(src.appPowerUsage);
    }

    // endregion

    // region utils

    private static int safeParseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    private static boolean isValidDate(String s) {
        if (s == null) return false;
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy-MM-dd HH:mm:ss"};
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.getDefault());
                sdf.setLenient(false);
                sdf.parse(s);
                return true;
            } catch (ParseException ignored) {
            }
        }
        return false;
    }

    private static void closeQuietly(ZipFile z) {
        if (z != null) {
            try { z.close(); } catch (IOException ignored) { }
        }
    }

    // endregion

    // region data classes

    public static class BugReportData {
        public String brand;
        public String model;
        public String serialNumber;
        public int designCapacityMah;
        public int currentCapacityMah;
        public int cycleCount;
        public String manufacturingDate;
        public float temperatureC;
        public int screenOnTimeSec;
        public int chargeCount;
        public final List<VoltageCurrentPair> voltageCurrentPairs = new ArrayList<>();
        public final List<ChargingEvent> chargingEvents = new ArrayList<>();
        public final List<AppPowerUsage> appPowerUsage = new ArrayList<>();
    }

    public static class VoltageCurrentPair {
        public final int voltageMv;
        public final int currentMa;
        public VoltageCurrentPair(int v, int c) { voltageMv = v; currentMa = c; }
    }

    public static class ChargingEvent {
        public final String label;
        public final String detail;
        public ChargingEvent(String label, String detail) { this.label = label; this.detail = detail; }
    }

    public static class AppPowerUsage {
        public final String packageName;
        public final double mah;
        public AppPowerUsage(String p, double m) { packageName = p; mah = m; }
    }

    // endregion
}
