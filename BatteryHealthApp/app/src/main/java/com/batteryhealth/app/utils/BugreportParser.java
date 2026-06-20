package com.batteryhealth.app.utils;

import android.util.Log;

import com.batteryhealth.app.data.model.BatteryHealthReport;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bugreport 本地解析器
 * 从 bugreport ZIP 或 TXT 文件中流式提取电池健康相关信息，不依赖后端服务。
 *
 * 解析字段覆盖：
 * - 设计容量 / 当前容量（charge_full / charge_full_design / charge_counter / charge_now）
 * - 循环次数（cycle_count）
 * - 实时电压 / 电流 / 温度
 * - 电池健康状态（health）
 * - 应用耗电排行（Estimated power use / per-app）
 *
 * 对于大体积 bugreport ZIP，采用逐行流式读取，避免一次加载整个文件导致 OOM。
 */
public class BugreportParser {
    private static final String TAG = "BugreportParser";

    // 单次解析最大读取行数，防止异常文件无限读取
    private static final long MAX_LINES = 2_000_000L;
    // 单行文最大长度，防止畸形行导致内存异常
    private static final int MAX_LINE_LENGTH = 8_192;

    /**
     * 解析 bugreport 文件（支持 .zip / .txt / .gz），返回电池健康报告。
     */
    public static BatteryHealthReport parse(File file) {
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
            return new ParsedBatteryHealthReport(result, file.getName());
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM while parsing bugreport", oom);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing bugreport", e);
            return null;
        }
    }

    private static ParsedResult parseFile(File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return parseZip(file);
        }
        // 根据扩展名决定流类型，同时通过 magic 自动探测 gzip
        boolean likelyGzip = name.endsWith(".gz") || name.endsWith(".tar.gz") || name.endsWith(".tgz");
        try (InputStream fis = new FileInputStream(file);
             InputStream decoded = likelyGzip ? new GZIPInputStream(new BufferedInputStream(fis)) : wrapMaybeGzip(fis)) {
            return parseStream(decoded);
        }
    }

    /**
     * 通过 magic 头自动探测 gzip，处理扩展名不准的情况。
     */
    private static InputStream wrapMaybeGzip(InputStream in) throws Exception {
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

    private static ParsedResult parseZip(File zipFile) throws Exception {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry targetEntry = findBestTextEntry(zf);
            if (targetEntry == null) {
                Log.e(TAG, "No suitable txt entry found in zip");
                return null;
            }
            Log.d(TAG, "Parsing zip entry: " + targetEntry.getName()
                    + " size=" + targetEntry.getSize());
            try (InputStream is = zf.getInputStream(targetEntry)) {
                String entryName = targetEntry.getName().toLowerCase(Locale.ROOT);
                if (entryName.endsWith(".gz")) {
                    return parseStream(new GZIPInputStream(new BufferedInputStream(is)));
                }
                return parseStream(is);
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

            // 只考虑文本/日志/压缩后的文本条目
            boolean isText = entryName.endsWith(".txt") || entryName.endsWith(".log");
            boolean isGzipText = entryName.endsWith(".txt.gz") || entryName.endsWith(".log.gz");
            if (!isText && !isGzipText) continue;

            int score = 0;
            long size = entry.getSize();

            // 优先选择 bugreport-*.txt 主文件
            if (entryName.contains("bugreport")) {
                score += 200;
                // bugreport-*.txt 在根目录或主目录时优先级更高
                if (entryName.startsWith("bugreport-")) score += 50;
            }
            // 次优先：dumpsys 电池相关日志
            if (entryName.contains("battery") || entryName.contains("power")) {
                score += 30;
            }
            // 纯 txt 比 gzip 文本解析更快，略优先
            if (isText) score += 10;

            // 同分选择更大的文件（内容更完整）
            boolean replace = score > bestScore || (score == bestScore && size > bestSize);
            if (replace) {
                best = entry;
                bestScore = score;
                bestSize = size;
            }
        }
        return best;
    }

    private static ParsedResult parseStream(InputStream is) throws Exception {
        ParsedResult result = new ParsedResult();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        long lineCount = 0;

        while ((line = reader.readLine()) != null && lineCount < MAX_LINES) {
            lineCount++;
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH);
            }
            parseLine(line, result);
        }

        result.postProcess();
        return result;
    }

    private static void parseLine(String line, ParsedResult result) {
        if (line == null || line.isEmpty()) return;

        // 1. 标准 power_supply uevent 行：POWER_SUPPLY_XXX=YYYY
        if (line.startsWith("POWER_SUPPLY_")) {
            parsePowerSupplyLine(line, result);
            return;
        }

        // 2. sysfs 路径中附带数值的行：/sys/class/power_supply/battery/XXXX: YYYY
        parseSysfsPathLine(line, result);

        // 3. 历史 dump 中常见的 key: value 行
        parseKeyValueLine(line, result);

        // 4. 应用耗电统计
        parseAppConsumptionLine(line, result);
    }

    private static void parsePowerSupplyLine(String line, ParsedResult r) {
        int eq = line.indexOf('=');
        if (eq <= 0 || eq >= line.length() - 1) return;
        String key = line.substring(0, eq).trim();
        String value = line.substring(eq + 1).trim();
        if (value.isEmpty()) return;

        switch (key) {
            case "POWER_SUPPLY_CHARGE_COUNTER":
                r.chargeCounterUa = firstValid(r.chargeCounterUa, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_CHARGE_NOW":
                r.chargeNowUa = firstValid(r.chargeNowUa, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_CHARGE_FULL":
                r.chargeFullUa = firstValid(r.chargeFullUa, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_CHARGE_FULL_DESIGN":
                r.chargeFullDesignUa = firstValid(r.chargeFullDesignUa, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_VOLTAGE_NOW":
                r.voltageNowUv = firstValid(r.voltageNowUv, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_CURRENT_NOW":
                r.currentNowUa = firstValid(r.currentNowUa, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_TEMP":
                r.tempDeciCelsius = firstValid(r.tempDeciCelsius, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_CYCLE_COUNT":
                r.cycleCount = firstValid(r.cycleCount, parseLongSafe(value));
                break;
            case "POWER_SUPPLY_HEALTH":
                if (r.health == null || r.health.equalsIgnoreCase("unknown")) {
                    r.health = value;
                }
                break;
            case "POWER_SUPPLY_TECHNOLOGY":
                if (r.technology == null) r.technology = value;
                break;
            case "POWER_SUPPLY_STATUS":
                if (r.status == null) r.status = value;
                break;
        }
    }

    private static void parseSysfsPathLine(String line, ParsedResult r) {
        // 形如：/sys/class/power_supply/battery/voltage_now: 4200000
        if (!line.contains("/sys/class/power_supply/battery/")) return;
        int colon = line.lastIndexOf(':');
        if (colon <= 0 || colon >= line.length() - 1) return;
        String value = line.substring(colon + 1).trim();
        String lower = line.toLowerCase(Locale.ROOT);

        if (lower.contains("charge_counter")) {
            r.chargeCounterUa = firstValid(r.chargeCounterUa, parseLongSafe(value));
        } else if (lower.contains("charge_full_design")) {
            r.chargeFullDesignUa = firstValid(r.chargeFullDesignUa, parseLongSafe(value));
        } else if (lower.contains("charge_full")) {
            r.chargeFullUa = firstValid(r.chargeFullUa, parseLongSafe(value));
        } else if (lower.contains("voltage_now")) {
            r.voltageNowUv = firstValid(r.voltageNowUv, parseLongSafe(value));
        } else if (lower.contains("current_now")) {
            r.currentNowUa = firstValid(r.currentNowUa, parseLongSafe(value));
        } else if (lower.contains("temp")) {
            r.tempDeciCelsius = firstValid(r.tempDeciCelsius, parseLongSafe(value));
        } else if (lower.contains("cycle_count")) {
            r.cycleCount = firstValid(r.cycleCount, parseLongSafe(value));
        }
    }

    private static final Pattern[] CAPACITY_PATTERNS = {
            Pattern.compile("charge_full_design[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge_full[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("capacity[^0-9-]*([-]?\\d+)\\s*m?[Aa][Hh]", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern[] CURRENT_CAPACITY_PATTERNS = {
            Pattern.compile("charge_counter[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("charge_now[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern CYCLE_COUNT_PATTERN =
            Pattern.compile("cycle_count[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMP_PATTERN =
            Pattern.compile("(?:temp|temperature)[^0-9-]*([-]?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALTH_PATTERN =
            Pattern.compile("(?:battery|power_supply).*health[^:]*:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

    private static void parseKeyValueLine(String line, ParsedResult r) {
        // 在已有 sysfs/uevent 数据足够时，避免正则回退造成误匹配
        if (r.chargeFullDesignUa <= 0) {
            for (Pattern p : CAPACITY_PATTERNS) {
                long v = parseFirstGroup(p, line);
                if (v > 0) {
                    r.chargeFullDesignUa = v;
                    break;
                }
            }
        }
        if (r.chargeCounterUa <= 0) {
            for (Pattern p : CURRENT_CAPACITY_PATTERNS) {
                long v = parseFirstGroup(p, line);
                if (v > 0) {
                    r.chargeCounterUa = v;
                    break;
                }
            }
        }
        if (r.cycleCount <= 0) {
            r.cycleCount = parseFirstGroup(CYCLE_COUNT_PATTERN, line);
        }
        if (r.tempDeciCelsius == Long.MIN_VALUE) {
            r.tempDeciCelsius = parseFirstGroup(TEMP_PATTERN, line);
        }
        if (r.health == null || r.health.equalsIgnoreCase("unknown")) {
            Matcher m = HEALTH_PATTERN.matcher(line);
            if (m.find()) {
                r.health = m.group(1);
            }
        }
    }

    // BatteryStats 应用耗电解析
    private static final Pattern ESTIMATED_POWER_PATTERN =
            Pattern.compile("^\\s*([#\\d\\.]+\\s*%?\\s+)?([\\w\\.]+):\\s+([\\d\\.]+)\\s*mAh", Pattern.CASE_INSENSITIVE);
    private static final Pattern UID_PACKAGE_PATTERN =
            Pattern.compile("uid\\s+u0a(\\d+)", Pattern.CASE_INSENSITIVE);

    private static void parseAppConsumptionLine(String line, ParsedResult r) {
        if (r.appConsumption.size() >= 10) return; // 只取前 10

        Matcher m = ESTIMATED_POWER_PATTERN.matcher(line);
        if (m.find()) {
            String name = m.group(2);
            String mahStr = m.group(3);
            try {
                float mah = Float.parseFloat(mahStr);
                if (mah > 0 && name != null && !name.isEmpty()) {
                    r.appConsumption.add(new AppConsumptionItem(name, mah));
                }
            } catch (NumberFormatException ignored) {}
        }
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

    private static long parseFirstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return Long.MIN_VALUE;
    }

    private static long firstValid(long current, long candidate) {
        return (candidate != Long.MIN_VALUE && candidate != 0) ? candidate : current;
    }

    /**
     * 解析中间结果容器
     */
    static class ParsedResult {
        long chargeCounterUa = Long.MIN_VALUE;
        long chargeNowUa = Long.MIN_VALUE;
        long chargeFullUa = Long.MIN_VALUE;
        long chargeFullDesignUa = Long.MIN_VALUE;
        long voltageNowUv = Long.MIN_VALUE;
        long currentNowUa = Long.MIN_VALUE;
        long tempDeciCelsius = Long.MIN_VALUE;
        long cycleCount = Long.MIN_VALUE;
        String health;
        String technology;
        String status;
        final List<AppConsumptionItem> appConsumption = new ArrayList<>();

        void postProcess() {
            // 如果 design 没有取到，用 full 兜底
            if (chargeFullDesignUa <= 0 && chargeFullUa > 0) {
                chargeFullDesignUa = chargeFullUa;
            }
            // 如果 current 没有取到，用 counter 兜底（counter 单位也是 µAh）
            if (chargeNowUa <= 0 && chargeCounterUa > 0) {
                chargeNowUa = chargeCounterUa;
            }
            // 某些厂商 temp 已经是摄氏度，这里不做额外缩放，由展示层判断
        }

        boolean isEmpty() {
            return chargeCounterUa <= 0 && chargeNowUa <= 0 && chargeFullUa <= 0
                    && chargeFullDesignUa <= 0 && voltageNowUv <= 0 && cycleCount <= 0
                    && tempDeciCelsius == Long.MIN_VALUE && appConsumption.isEmpty();
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

    /**
     * 本地解析后的电池健康报告，覆盖 getter 以返回从 bugreport 提取的数值。
     */
    public static class ParsedBatteryHealthReport extends BatteryHealthReport {
        private final int currentCapacityMah;
        private final int designCapacityMah;
        private final int cycleCount;
        private final float voltageNowV;
        private final float currentNowA;
        private final float temperatureCelsius;
        private final String batterySource;
        private final float healthPercentage;
        private final List<Recommendation> recommendations;
        private final List<AppConsumption> appConsumptionList;

        ParsedBatteryHealthReport(ParsedResult r, String fileName) {
            long chargeNow = r.chargeNowUa > 0 ? r.chargeNowUa : r.chargeCounterUa;
            this.currentCapacityMah = chargeNow > 0 ? (int) (chargeNow / 1000) : 0;
            this.designCapacityMah = r.chargeFullDesignUa > 0 ? (int) (r.chargeFullDesignUa / 1000)
                    : (r.chargeFullUa > 0 ? (int) (r.chargeFullUa / 1000) : 0);
            this.cycleCount = r.cycleCount > 0 ? (int) r.cycleCount : 0;
            this.voltageNowV = r.voltageNowUv > 0 ? r.voltageNowUv / 1_000_000.0f : 0;
            this.currentNowA = r.currentNowUa != Long.MIN_VALUE ? r.currentNowUa / 1_000_000.0f : 0;

            if (r.tempDeciCelsius != Long.MIN_VALUE) {
                float t = r.tempDeciCelsius;
                // bugreport 中 temp 可能是摄氏度（0-60）或十分之一摄氏度（0-600）
                this.temperatureCelsius = t > 100 ? t / 10.0f : t;
            } else {
                this.temperatureCelsius = 0;
            }

            String h = r.health;
            if (h == null || h.equalsIgnoreCase("unknown")) {
                h = inferHealthFromData();
            }
            this.batterySource = "本地解析" + (h != null ? " · " + h : "");

            if (designCapacityMah > 0 && currentCapacityMah > 0) {
                this.healthPercentage = Math.min(100f,
                        Math.max(0f, currentCapacityMah * 100.0f / designCapacityMah));
            } else if (designCapacityMah > 0 && r.chargeFullUa > 0) {
                // 用 charge_full 与 design 估算
                this.healthPercentage = Math.min(100f,
                        Math.max(0f, (r.chargeFullUa / 1000) * 100.0f / designCapacityMah));
            } else {
                this.healthPercentage = 0;
            }

            this.appConsumptionList = buildAppConsumptionList(r.appConsumption);
            this.recommendations = buildRecommendations();
        }

        private String inferHealthFromData() {
            if (healthPercentage > 0 && healthPercentage < 80) return "较差";
            if (healthPercentage >= 90) return "优秀";
            if (healthPercentage > 0) return "良好";
            return "未知";
        }

        private List<AppConsumption> buildAppConsumptionList(List<AppConsumptionItem> items) {
            if (items == null || items.isEmpty()) return Collections.emptyList();
            float total = 0;
            for (AppConsumptionItem item : items) total += item.mah;
            List<AppConsumption> list = new ArrayList<>();
            for (AppConsumptionItem item : items) {
                float percent = total > 0 ? (item.mah / total) * 100f : 0f;
                int minutes = (int) (item.mah * 10); // 粗略估算：10 min / mAh
                list.add(new AppConsumption(item.name, item.name, minutes, percent));
            }
            return list;
        }

        private List<Recommendation> buildRecommendations() {
            List<Recommendation> list = new ArrayList<>();
            if (healthPercentage > 0 && healthPercentage < 80) {
                list.add(createRecommendation("健康度偏低",
                        "当前估算健康度为 " + String.format(Locale.getDefault(), "%.1f%%", healthPercentage)
                                + "，建议前往官方售后检测电池。", "high"));
            }
            if (temperatureCelsius > 42) {
                list.add(createRecommendation("温度过高",
                        "电池温度 " + String.format(Locale.getDefault(), "%.1f°C", temperatureCelsius)
                                + "，建议避免边充边玩并保持通风。", "high"));
            }
            if (cycleCount > 500) {
                list.add(createRecommendation("循环次数较多",
                        "电池循环次数约 " + cycleCount + " 次，注意保养以延长寿命。", "medium"));
            }
            if (appConsumptionList != null && !appConsumptionList.isEmpty()) {
                AppConsumption top = appConsumptionList.get(0);
                String name = top.getAppName() != null ? top.getAppName() : top.getPackageName();
                list.add(createRecommendation("耗电大户",
                        String.format(Locale.getDefault(), "%s 耗电最多（%.1f%%），可适当限制后台运行。",
                                name != null ? name : "未知应用", top.getConsumptionPercent()), "medium"));
            }
            if (list.isEmpty()) {
                list.add(createRecommendation("本地解析完成",
                        "已从 bugreport 提取基础电池信息，详细数据以上传后端分析为准。", "low"));
            }
            return list;
        }

        private Recommendation createRecommendation(String title, String content, String priority) {
            return new Recommendation(title, content, priority);
        }

        @Override
        public float getBatteryHealthPercentage() { return healthPercentage; }

        @Override
        public String getBatteryHealthLevel() {
            if (healthPercentage >= 90) return "优秀";
            if (healthPercentage >= 80) return "良好";
            if (healthPercentage >= 70) return "一般";
            return "较差";
        }

        @Override
        public int getDesignCapacityMah() { return designCapacityMah; }

        @Override
        public int getCurrentCapacityMah() { return currentCapacityMah; }

        @Override
        public int getCycleCount() { return cycleCount; }

        @Override
        public long getVoltageNowUv() { return (long) (voltageNowV * 1_000_000); }

        @Override
        public long getCurrentNowUa() { return (long) (currentNowA * 1_000_000); }

        @Override
        public float getTemperatureNowCelsius() { return temperatureCelsius; }

        @Override
        public String getBatterySource() { return batterySource; }

        @Override
        public List<Recommendation> getRecommendations() { return recommendations; }

        @Override
        public List<AppConsumption> getAppConsumption() { return appConsumptionList; }
    }
}
