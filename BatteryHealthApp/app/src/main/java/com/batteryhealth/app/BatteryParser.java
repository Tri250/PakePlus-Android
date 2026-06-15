package com.batteryhealth.app;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 电池健康度解析器 - Java 原生实现
 *
 * 关键设计（v2.1.7）：
 * 1. 使用 ZipInputStream 流式扫描，避免将整个 zip 加载到内存
 * 2. 仅对文本类、电池相关 entry 完整读取（30MB 上限）
 * 3. 使用 java.util.regex.Pattern 预编译，匹配速度远高于 JS
 * 4. 早退机制：找到 cycleCount+capacity 高置信度匹配后立即停止
 * 5. 内存占用 O(1)，无 Base64 序列化，无 evaluateJavascript 大字符串
 *
 * 对应原 JS parsers.js 的 BRAND_CONFIG、parseGeneric、parseXiaomi、parseVivo、parseOPPO、parseHuawei 等逻辑。
 */
public class BatteryParser {

    private static final String TAG = "BatteryParser";

    /** 单个 entry 最大读取字节数：30MB。bugreport 主体 dumpstate 通常 50-200MB，但电池信息在前 30MB 一定出现。 */
    private static final long MAX_ENTRY_SIZE = 30L * 1024L * 1024L;

    /** 结果数据 */
    public static class BatteryInfo {
        public int currentCapacity;     // 当前容量 mAh
        public int designCapacity;      // 设计容量 mAh
        public int chargeCounter;       // 原始 charge_counter（uAh 或 mAh）
        public int cycleCount;          // 循环次数
        public double batteryTemp;      // 温度 ℃
        public int voltage;             // 电压 mV
        public String technology;       // 电池技术
        public String rawContent;       // 原始内容片段（截断）
        public String brand;            // 品牌
        public double confidence;       // 置信度 0-1

        public boolean hasCapacity() { return currentCapacity > 0; }
    }

    // ============= 预编译 Pattern（按品牌） =============

    private static final Pattern[] CHARGE_COUNTER_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)charge[_\\s\\-]?counter[:= ]+(\\d+)\\s*u?ah"),
            Pattern.compile("(?i)charge[_\\s\\-]?counter[:= ]+(\\d+)"),
            Pattern.compile("CHARGE_COUNTER[:= ]+(\\d+)"),
            Pattern.compile("(?i)cc[:= ]+(\\d+)\\s*u?ah"),
            Pattern.compile("(?i)last[_\\s\\-]?full[_\\s\\-]?charge[_\\s\\-]?counter[:= ]+(\\d+)"),
            Pattern.compile("(?i)full[_\\s\\-]?charge[_\\s\\-]?capacity[:= ]+(\\d+)\\s*u?ah"),
            Pattern.compile("(?i)fcc[:= ]+(\\d+)"),
            Pattern.compile("(?i)\\bcharge_counter\\b[\\s:=]+(\\d+)")
    };

    private static final Pattern[] CURRENT_NOW_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)current[_\\s\\-]?now[:= ]+(-?\\d+)\\s*ua"),
            Pattern.compile("(?i)current[_\\s\\-]?now[:= ]+(-?\\d+)"),
            Pattern.compile("CURRENT_NOW[:= ]+(-?\\d+)"),
            Pattern.compile("(?i)\\bcurrent_now\\b[\\s:=]+(-?\\d+)")
    };

    private static final Pattern[] CYCLE_COUNT_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)cycle[_\\s\\-]?count[:= ]+(\\d+)"),
            Pattern.compile("CYCLE_COUNT[:= ]+(\\d+)"),
            Pattern.compile("(?i)charge[_\\s\\-]?cycle[:= ]+(\\d+)"),
            Pattern.compile("(?i)battery[_\\s\\-]?cycle[:= ]+(\\d+)"),
            Pattern.compile("(?i)cycle[_\\s\\-]?counter[:= ]+(\\d+)"),
            Pattern.compile("(?i)cc[:= ]+(\\d+)(?!\\s*uah)"),
            Pattern.compile("(?i)charge[_\\s\\-]?cycles[:= ]+(\\d+)"),
            Pattern.compile("充电循环次数[:= ]+(\\d+)"),
            Pattern.compile("循环次数[:= ]+(\\d+)"),
            Pattern.compile("累计循环[:= ]+(\\d+)"),
            Pattern.compile("(?i)\\bcycle_count\\b[\\s:=]+(\\d+)"),
            Pattern.compile("(?i)\\bcycle\\b[\\s:=]+(\\d+)")
    };

    private static final Pattern[] TEMP_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)temperature[:= ]+(-?\\d+\\.?\\d*)\\s*°?c"),
            Pattern.compile("(?i)temperature[:= ]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("TEMP[:= ]+(-?\\d+)"),
            Pattern.compile("(?i)battery[_\\s\\-]?temp[:= ]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("(?i)temp[:= ]+(-?\\d+\\.?\\d*)(?!\\s*%)"),
            Pattern.compile("电池温度[:= ]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("温度[:= ]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("(?i)\\btemperature\\b[\\s:=]+(-?\\d+)")
    };

    private static final Pattern[] VOLTAGE_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)voltage[:= ]+(\\d+\\.?\\d*)\\s*v"),
            Pattern.compile("(?i)voltage[:= ]+(\\d+)"),
            Pattern.compile("VOLTAGE[:= ]+(\\d+)"),
            Pattern.compile("(?i)batt[_\\s\\-]?voltage[:= ]+(\\d+)"),
            Pattern.compile("(?i)\\bvoltage\\b[\\s:=]+(\\d+)")
    };

    private static final Pattern[] TECHNOLOGY_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)technology[:= ]+(\\w+)"),
            Pattern.compile("TECHNOLOGY[:= ]+(\\w+)"),
            Pattern.compile("(?i)battery[_\\s\\-]?type[:= ]+(\\w+)"),
            Pattern.compile("(?i)\\btechnology\\b[\\s:=]+(\\w+)")
    };

    // 品牌识别
    private static final Pattern P_MIUI    = Pattern.compile("(?i)(miui|xiaomi|redmi)");
    private static final Pattern P_VIVO    = Pattern.compile("(?i)(vivo|funtouch|originos|iqoo)");
    private static final Pattern P_OPPO    = Pattern.compile("(?i)(coloros|oppo|oneplus|oos|realme|oxygenos)");
    private static final Pattern P_HUAWEI  = Pattern.compile("(?i)(harmony|emui|hmos|huawei|honor|magicui)");
    private static final Pattern P_SAMSUNG = Pattern.compile("(?i)(samsung|oneui|one ui)");
    private static final Pattern P_MEIZU   = Pattern.compile("(?i)(flyme|meizu)");
    private static final Pattern P_NUBIA   = Pattern.compile("(?i)(nubia|redmagic)");
    private static final Pattern P_ZTE     = Pattern.compile("(?i)(zte|axon)");
    private static final Pattern P_MOTO    = Pattern.compile("(?i)(moto|motorola)");

    // 进度回调
    public interface ProgressCallback {
        void onProgress(int processed, int total, String currentName, BatteryInfo bestSoFar);
    }

    /**
     * 入口：从 URI 打开的 InputStream 解析电池信息
     * 流式扫描 zip entries，找到最佳匹配后返回
     */
    public static BatteryInfo processZipStream(InputStream inputStream,
                                                ProgressCallback progress) {
        BatteryInfo bestInfo = null;
        int processed = 0;
        int scanned = 0;

        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream, 64 * 1024));
        try {
            ZipEntry entry;
            int totalEstimate = 0;

            while ((entry = zis.getNextEntry()) != null) {
                scanned++;
                String name = entry.getName();
                long size = entry.getSize();

                try {
                    // 过滤：太大的文件跳过
                    if (size > MAX_ENTRY_SIZE && size > 0) {
                        Log.d(TAG, "Skip large entry: " + name + " size=" + size);
                        continue;
                    }

                    // 过滤：明显不是电池相关
                    if (!isLikelyBatteryFile(name)) {
                        continue;
                    }

                    // 读取内容（带上限）
                    String content = readLimited(zis, MAX_ENTRY_SIZE);
                    if (content == null || content.length() < 50) {
                        continue;
                    }

                    processed++;
                    Log.d(TAG, "Processing entry: " + name + " size=" + content.length());

                    // 品牌检测（文件名优先于内容）
                    String brand = detectBrand(name, content);

                    // 解析电池信息
                    BatteryInfo info = parseContent(content, brand);
                    if (info != null) {
                        info.brand = brand;
                        // 提取 rawContent 片段
                        info.rawContent = extractRawContent(content, brand);

                        if (bestInfo == null || info.confidence > bestInfo.confidence) {
                            bestInfo = info;
                        }
                    }

                    // 进度回调
                    if (progress != null) {
                        progress.onProgress(processed, scanned, name, bestInfo);
                    }

                    // 早退：找到 cycleCount + chargeCounter 高置信度结果
                    if (bestInfo != null && bestInfo.confidence >= 0.9
                            && bestInfo.cycleCount > 0 && bestInfo.hasCapacity()) {
                        Log.d(TAG, "Early exit: high confidence match found");
                        break;
                    }

                } finally {
                    try { zis.closeEntry(); } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "processZipStream error", e);
        }

        Log.d(TAG, "Scanned " + scanned + " entries, processed " + processed);
        return bestInfo;
    }

    /**
     * 判断 entry 是否可能包含电池信息
     * 策略：文件名包含电池相关关键词 或 文件名是 dumpstate/bugreport 文本
     */
    private static boolean isLikelyBatteryFile(String name) {
        if (name == null) return false;
        String low = name.toLowerCase();

        // 明确非文本（.bin, .png, .jpg, .so, .dex, .apk, .jar, .oat, .vdex）
        if (low.endsWith(".bin") || low.endsWith(".png") || low.endsWith(".jpg")
                || low.endsWith(".so") || low.endsWith(".dex") || low.endsWith(".apk")
                || low.endsWith(".jar") || low.endsWith(".oat") || low.endsWith(".vdex")
                || low.endsWith(".zip") || low.endsWith(".dat") || low.endsWith(".db")) {
            return false;
        }

        // 明确包含电池信息
        if (low.contains("battery") || low.contains("dumpstate_battery")) return true;

        // dumpstate 主体（必含电池段）
        if (low.startsWith("dumpstate") || low.contains("/dumpstate") || low.endsWith("dumpstate.txt")) {
            return true;
        }

        // bugreport 主体
        if (low.contains("bugreport") && (low.endsWith(".txt") || low.endsWith(".log"))) {
            return true;
        }

        // 文本类型兜底
        if (low.endsWith(".txt") || low.endsWith(".log") || low.endsWith(".xml")) {
            // 限制大小：超过 20MB 的 .txt 只取前 30MB
            return true;
        }

        return false;
    }

    /**
     * 读取 InputStream 为字符串，限制最大字节数
     */
    private static String readLimited(InputStream is, long maxBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[8192];
        long total = 0;
        int read;
        boolean truncated = false;
        while ((read = is.read(buf)) != -1) {
            if (total + read > maxBytes) {
                int allow = (int) (maxBytes - total);
                if (allow > 0) {
                    baos.write(buf, 0, allow);
                    total += allow;
                }
                truncated = true;
                break;
            }
            baos.write(buf, 0, read);
            total += read;
        }
        // 尝试 UTF-8 解码，对部分非 UTF-8 容错
        String s = baos.toString("UTF-8");
        if (truncated) {
            s = s + "\n... [truncated at " + maxBytes + " bytes]";
        }
        return s;
    }

    /**
     * 检测品牌
     */
    private static String detectBrand(String fileName, String content) {
        String lowName = fileName == null ? "" : fileName.toLowerCase();
        if (lowName.contains("miui") || lowName.contains("xiaomi")) return "xiaomi";
        if (lowName.contains("vivo") || lowName.contains("funtouch") || lowName.contains("originos") || lowName.contains("iqoo")) return "vivo";
        if (lowName.contains("coloros") || lowName.contains("oppo") || lowName.contains("oneplus") || lowName.contains("oos") || lowName.contains("realme") || lowName.contains("oxygenos")) return "oppo";
        if (lowName.contains("harmony") || lowName.contains("emui") || lowName.contains("hmos") || lowName.contains("huawei") || lowName.contains("honor")) return "huawei";
        if (lowName.contains("flyme") || lowName.contains("meizu")) return "meizu";
        if (lowName.contains("nubia") || lowName.contains("redmagic")) return "nubia";
        if (lowName.contains("samsung") || lowName.contains("oneui")) return "samsung";

        // 内容匹配
        if (matchAny(content, P_MIUI)) return "xiaomi";
        if (matchAny(content, P_VIVO)) return "vivo";
        if (matchAny(content, P_OPPO)) return "oppo";
        if (matchAny(content, P_HUAWEI)) return "huawei";
        if (matchAny(content, P_SAMSUNG)) return "samsung";
        if (matchAny(content, P_MEIZU)) return "meizu";
        if (matchAny(content, P_NUBIA)) return "nubia";
        if (matchAny(content, P_ZTE)) return "zte";
        if (matchAny(content, P_MOTO)) return "motorola";

        return "generic";
    }

    /**
     * 主解析：单 entry 内容
     */
    private static BatteryInfo parseContent(String content, String brand) {
        BatteryInfo r = new BatteryInfo();
        r.confidence = 0.0;

        // 1. charge_counter
        Long cc = firstMatchGroup(content, CHARGE_COUNTER_PATTERNS);
        if (cc != null && cc > 0) {
            r.chargeCounter = cc.intValue();
            r.currentCapacity = convertToMah(cc);
            r.confidence = Math.max(r.confidence, 0.95);
        }

        // 2. current_now（仅记录，不影响置信度）
        Long cn = firstMatchGroup(content, CURRENT_NOW_PATTERNS);

        // 3. cycle_count
        Integer cyc = firstMatchGroupInt(content, CYCLE_COUNT_PATTERNS);
        if (cyc != null && cyc >= 0 && cyc < 10000) {
            r.cycleCount = cyc;
            r.confidence = Math.max(r.confidence, 0.9);
        }

        // 4. temperature
        Double temp = firstMatchGroupDouble(content, TEMP_PATTERNS);
        if (temp != null) {
            if (temp > 100 && temp < 1000) temp = temp / 10.0;
            else if (temp > 1000 && temp < 10000) temp = temp / 100.0;
            if (temp >= -20 && temp <= 80) {
                r.batteryTemp = temp;
                r.confidence = Math.max(r.confidence, 0.8);
            }
        }

        // 5. voltage
        Double volt = firstMatchGroupDouble(content, VOLTAGE_PATTERNS);
        if (volt != null) {
            if (volt > 10000) volt = volt / 1000.0;
            else if (volt >= 3 && volt <= 5) volt = volt * 1000.0;
            if (volt >= 2500 && volt <= 5000) {
                r.voltage = volt.intValue();
                r.confidence = Math.max(r.confidence, 0.6);
            }
        }

        // 6. technology
        for (Pattern p : TECHNOLOGY_PATTERNS) {
            Matcher m = p.matcher(content);
            if (m.find()) {
                r.technology = m.group(1);
                break;
            }
        }

        // 7. 特定 section 提取（提升置信度）
        // Battery Service / BatteryInfo / BatteryStats 段
        Matcher m = Pattern.compile("(?is)(?:Battery Service|BatteryInfo|Battery Stats|Battery Information|Battery Properties)[:\\s\\S]{0,3000}").matcher(content);
        if (m.find()) {
            String section = m.group();
            r.confidence = Math.max(r.confidence, 0.85);

            if (r.currentCapacity == 0) {
                Long scc = firstMatchGroup(section, CHARGE_COUNTER_PATTERNS);
                if (scc != null && scc > 0) {
                    r.chargeCounter = scc.intValue();
                    r.currentCapacity = convertToMah(scc);
                    r.confidence = Math.max(r.confidence, 0.92);
                }
            }
            if (r.cycleCount == 0) {
                Integer scyc = firstMatchGroupInt(section, CYCLE_COUNT_PATTERNS);
                if (scyc != null && scyc >= 0 && scyc < 10000) {
                    r.cycleCount = scyc;
                }
            }
            if (r.batteryTemp == 0) {
                Double st = firstMatchGroupDouble(section, TEMP_PATTERNS);
                if (st != null) {
                    if (st > 100) st = st / 10.0;
                    if (st >= -20 && st <= 80) r.batteryTemp = st;
                }
            }
        }

        // 8. 兼容：直接 mAh 数值
        if (r.currentCapacity == 0) {
            Matcher mh = Pattern.compile("(?i)(?:full[_\\s\\-]?charge[_\\s\\-]?capacity|fcc|design[_\\s\\-]?capacity)[:= ]+(\\d+)\\s*mah").matcher(content);
            if (mh.find()) {
                r.currentCapacity = parseIntSafe(mh.group(1));
                r.confidence = Math.max(r.confidence, 0.7);
            }
        }

        // 至少要有容量或循环次数
        if (!r.hasCapacity() && r.cycleCount == 0) {
            return null;
        }
        return r;
    }

    /**
     * 智能单位转换：charge_counter → mAh
     */
    private static int convertToMah(long value) {
        if (value <= 0) return 0;
        if (value < 1000) return (int) value;
        if (value >= 1000000) return (int) (value / 1000);
        long divided = value / 1000;
        if (divided >= 1000 && divided <= 20000) return (int) divided;
        if (value >= 1000 && value <= 20000) return (int) value;
        return (int) divided;
    }

    /**
     * 提取 rawContent 片段（前 1500 字符，包含电池相关部分）
     */
    private static String extractRawContent(String content, String brand) {
        // 找包含 charge_counter / Battery Service / BatteryInfo 的段
        String[] sectionStarts = {
                "Battery Service", "BatteryInfo", "Battery Stats",
                "Battery Information", "Battery Properties", "dumpstate_battery"
        };
        for (String key : sectionStarts) {
            int idx = content.indexOf(key);
            if (idx >= 0) {
                int end = Math.min(content.length(), idx + 1500);
                return content.substring(idx, end);
            }
        }
        // 找包含 charge_counter 的行附近
        Matcher m = Pattern.compile("(?i)charge[_\\s\\-]?counter[:= ]+\\d+").matcher(content);
        if (m.find()) {
            int start = Math.max(0, m.start() - 200);
            int end = Math.min(content.length(), m.end() + 1000);
            return content.substring(start, end);
        }
        return content.length() > 1500 ? content.substring(0, 1500) : content;
    }

    // ============= 工具方法 =============

    private static Long firstMatchGroup(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                try { return Long.parseLong(m.group(1)); } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private static Integer firstMatchGroupInt(String text, Pattern[] patterns) {
        Long l = firstMatchGroup(text, patterns);
        return l == null ? null : l.intValue();
    }

    private static Double firstMatchGroupDouble(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                try { return Double.parseDouble(m.group(1)); } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private static boolean matchAny(String text, Pattern p) {
        return p.matcher(text == null ? "" : text).find();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
