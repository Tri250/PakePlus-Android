package com.batteryhealth.app;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 电池健康度解析器 v2.1.10 - 彻底重写
 *
 * 核心设计变更（vs v2.1.9）：
 * 1. Section-based 解析：先用 indexOf 快速定位电池相关段落，再只解析该段落
 * 2. 通用 key-value 提取：逐行解析 key=value / key: value / key value，
 *    不再依赖几十个脆弱的正则表达式
 * 3. 灵活 key 映射：将各种 key 名称（charge_counter / CHARGE_COUNTER / cc 等）
 *    统一映射到内部字段，用值域判断区分 cc 是循环次数还是充电计数
 * 4. 性能优化：只处理 dumpstate 主体文件，跳过 FS/ 等无关目录，
 *    读取上限从 30MB 降到 10MB，电池段通常在前 5MB 内
 */
public class BatteryParser {

    private static final String TAG = "BatteryParser";

    /** 单个 entry 最大读取字节数：50MB（部分 bugreport 主体可能很大） */
    private static final long MAX_ENTRY_SIZE = 50L * 1024L * 1024L;

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
        public String debugInfo;        // 调试信息（entry 列表等）
        public String dataSource;       // 数据来源（哪个 entry）
        public String kvMapDump;        // 提取的 key-value 映射
        public String capacitySource;   // 容量数据来源字段
        public String cycleSource;      // 循环次数数据来源字段
        public String tempSource;       // 温度数据来源字段

        public boolean hasCapacity() { return currentCapacity > 0; }
    }

    // ============= 电池段落起始标记 =============
    private static final String[] BATTERY_SECTION_STARTS = {
        "DUMP OF SERVICE batterystats",
        "DUMP OF SERVICE battery",
        "healthd:",
        "Healthd:",
        "HEALTHD:",
        "Battery Service:",
        "Battery Stats",
        "Battery Health",
        "Battery Info",
        "BatteryInfo",
        "Battery Properties",
        "Battery Information",
        "dumpstate_battery",
        "Power Supply",
        "Current Battery Service state",
        "------ battery",
    };

    // ============= 电池段落结束标记 =============
    private static final String[] BATTERY_SECTION_ENDS = {
        "------ DUMP OF SERVICE",
        "------ END",
        "====== END",
        "DUMP OF SERVICE",
    };

    // ============= 品牌识别 =============
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

    // ============= 入口方法 =============

    public static BatteryInfo processZipStream(InputStream inputStream,
                                                ProgressCallback progress) {
        BatteryInfo bestInfo = null;
        int processed = 0;
        int scanned = 0;
        StringBuilder debugBuilder = new StringBuilder();
        debugBuilder.append("Entries scanned: ");

        ZipInputStream zis = new ZipInputStream(inputStream);
        try {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                scanned++;
                String name = entry.getName();
                long size = entry.getSize();

                try {
                    // 记录 entry 名称（调试用）
                    if (scanned <= 20) {
                        debugBuilder.append("\n  ").append(name).append(" (").append(size).append("B)");
                    }

                    // 跳过太大的文件
                    if (size > MAX_ENTRY_SIZE && size > 0) {
                        Log.d(TAG, "Skip large: " + name);
                        continue;
                    }

                    // 跳过明显的二进制文件
                    if (isBinaryFile(name)) {
                        continue;
                    }

                    // 读取内容
                    String content = readLimited(zis, MAX_ENTRY_SIZE);
                    if (content == null || content.length() < 20) {
                        continue;
                    }

                    processed++;

                    // 品牌检测
                    String brand = detectBrand(name, content);

                    // 解析电池信息
                    BatteryInfo info = parseContent(content, brand);
                    if (info != null) {
                        info.brand = brand;
                        info.rawContent = extractRawContent(content);
                        info.dataSource = name;  // 记录数据来源

                        if (bestInfo == null || info.confidence > bestInfo.confidence) {
                            bestInfo = info;
                            Log.d(TAG, "Found battery info in " + name
                                + " (conf=" + String.format("%.2f", info.confidence)
                                + ", cap=" + info.currentCapacity
                                + ", cyc=" + info.cycleCount
                                + ", temp=" + info.batteryTemp + ")");
                            Log.d(TAG, "Data source: " + name);
                            Log.d(TAG, "KV map: " + (info.kvMapDump != null ? info.kvMapDump : "null"));
                        }
                    }

                    // 进度回调
                    if (progress != null) {
                        progress.onProgress(processed, scanned, name, bestInfo);
                    }

                    // 早退
                    if (bestInfo != null && bestInfo.confidence >= 0.7
                            && bestInfo.cycleCount > 0 && bestInfo.hasCapacity()) {
                        break;
                    }

                } finally {
                    try { zis.closeEntry(); } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "processZipStream error", e);
            debugBuilder.append("\n  ERROR: ").append(e.getMessage());
        }

        if (scanned > 20) {
            debugBuilder.append("\n  ... and ").append(scanned - 20).append(" more entries");
        }
        debugBuilder.append("\nTotal: ").append(scanned).append(" entries, ").append(processed).append(" processed");

        if (bestInfo == null) {
            Log.w(TAG, "No battery info found after scanning " + scanned + " entries");
            // 创建调试结果
            bestInfo = new BatteryInfo();
            bestInfo.brand = "generic";
            bestInfo.confidence = 0.0;
            bestInfo.debugInfo = debugBuilder.toString();
        } else {
            bestInfo.debugInfo = debugBuilder.toString();
        }

        return bestInfo;
    }

    // ============= 文件过滤 =============

    /** 跳过明显的二进制文件 */
    private static boolean isBinaryFile(String name) {
        if (name == null) return false;
        String low = name.toLowerCase();
        return low.endsWith(".bin") || low.endsWith(".png") || low.endsWith(".jpg")
            || low.endsWith(".jpeg") || low.endsWith(".gif") || low.endsWith(".webp")
            || low.endsWith(".so") || low.endsWith(".dex") || low.endsWith(".apk")
            || low.endsWith(".jar") || low.endsWith(".oat") || low.endsWith(".vdex")
            || low.endsWith(".zip") || low.endsWith(".dat") || low.endsWith(".db")
            || low.endsWith(".proto") || low.endsWith(".prof") || low.endsWith(".profm")
            || low.endsWith(".mp4") || low.endsWith(".mp3") || low.endsWith(".wav")
            || low.endsWith(".pdf") || low.endsWith(".ttf") || low.endsWith(".otf")
            || low.endsWith(".ogg") || low.endsWith(".flac") || low.endsWith(".mid")
            || low.endsWith(".xml") || low.endsWith(".json") || low.endsWith(".html")
            || low.endsWith(".css") || low.endsWith(".js") || low.endsWith(".svg");
    }

    // ============= 读取内容 =============

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
        String s = baos.toString("UTF-8");
        if (truncated) {
            s = s + "\n... [truncated]";
        }
        return s;
    }

    // ============= 品牌检测 =============

    private static String detectBrand(String fileName, String content) {
        String lowName = fileName == null ? "" : fileName.toLowerCase();
        if (lowName.contains("miui") || lowName.contains("xiaomi")) return "xiaomi";
        if (lowName.contains("vivo") || lowName.contains("funtouch") || lowName.contains("originos") || lowName.contains("iqoo")) return "vivo";
        if (lowName.contains("coloros") || lowName.contains("oppo") || lowName.contains("oneplus") || lowName.contains("oos") || lowName.contains("realme") || lowName.contains("oxygenos")) return "oppo";
        if (lowName.contains("harmony") || lowName.contains("emui") || lowName.contains("hmos") || lowName.contains("huawei") || lowName.contains("honor")) return "huawei";
        if (lowName.contains("flyme") || lowName.contains("meizu")) return "meizu";
        if (lowName.contains("nubia") || lowName.contains("redmagic")) return "nubia";
        if (lowName.contains("samsung") || lowName.contains("oneui")) return "samsung";

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

    private static boolean matchAny(String text, java.util.regex.Pattern p) {
        return p.matcher(text == null ? "" : text).find();
    }

    // ============= 核心解析：section-based + key-value =============

    /**
     * 主解析方法（v2.1.10 彻底重写）
     *
     * 策略：
     * 1. 先用 indexOf 快速定位电池相关段落
     * 2. 逐行解析 key-value 对
     * 3. 用灵活的 key 映射将各种 key 名称统一到内部字段
     * 4. 用值域判断区分 cc 是循环次数还是充电计数
     */
    private static BatteryInfo parseContent(String content, String brand) {
        BatteryInfo r = new BatteryInfo();
        r.confidence = 0.0;

        // ==== 第1步：提取电池段落 ====
        List<String> sections = findBatterySections(content);

        // ==== 第2步：从每个段落提取 key-value 对 ====
        // 用 Map<标准化key, 值列表> 收集所有匹配
        Map<String, List<String>> kvMap = new LinkedHashMap<>();

        // 先解析电池段落（优先级高）
        for (String section : sections) {
            extractKeyValuePairs(section, kvMap);
        }

        // 再解析全文（兜底，防止遗漏不在已知段落内的数据）
        extractKeyValuePairs(content, kvMap);

        // ==== 第3步：映射到 BatteryInfo ====
        mapToBatteryInfo(r, kvMap, content, brand);

        // ==== 第4步：低置信度兜底 — 即使没找到段落，也尝试提取 ====
        if (!r.hasCapacity() && r.cycleCount == 0 && r.batteryTemp == 0) {
            // 直接用全文搜电池相关数字
            fallbackExtractFromText(r, content);
        }

        // ==== 记录 kvMap 用于调试 ====
        if (r.hasCapacity() || r.cycleCount > 0 || r.batteryTemp > 0) {
            StringBuilder kvDump = new StringBuilder();
            kvDump.append("Extracted keys (").append(kvMap.size()).append("): ");
            int count = 0;
            for (Map.Entry<String, List<String>> e : kvMap.entrySet()) {
                if (count++ < 10) {
                    kvDump.append(e.getKey()).append("=").append(e.getValue().get(0)).append(" ");
                }
            }
            r.kvMapDump = kvDump.toString();
        }

        // ==== 至少要有容量、循环次数或温度中的一项 ====
        if (!r.hasCapacity() && r.cycleCount == 0 && r.batteryTemp == 0) {
            return null;
        }
        return r;
    }

    /**
     * 查找电池相关段落
     * 使用 indexOf 快速定位，比正则快 10x+
     */
    private static List<String> findBatterySections(String content) {
        List<String> sections = new ArrayList<>();
        String lowContent = content.toLowerCase();

        for (String startMarker : BATTERY_SECTION_STARTS) {
            int startIdx = lowContent.indexOf(startMarker.toLowerCase());
            while (startIdx >= 0) {
                // 找到段落结束位置
                int endIdx = content.length();
                for (String endMarker : BATTERY_SECTION_ENDS) {
                    int eIdx = lowContent.indexOf(endMarker.toLowerCase(), startIdx + startMarker.length());
                    if (eIdx > startIdx && eIdx < endIdx) {
                        endIdx = eIdx;
                    }
                }

                // 提取段落（最多 5000 字符，足够覆盖电池数据）
                int sectionLen = Math.min(endIdx - startIdx, 5000);
                if (sectionLen > 50) {
                    sections.add(content.substring(startIdx, startIdx + sectionLen));
                }

                // 继续查找下一个同类型段落
                startIdx = lowContent.indexOf(startMarker.toLowerCase(), startIdx + 1);
            }
        }

        return sections;
    }

    /**
     * 从文本中提取 key-value 对
     *
     * 支持格式：
     * - key: value
     * - key=value
     * - key value（空格分隔，key 必须是已知的电池 key）
     * - key：value（中文冒号）
     * - mKey: value（Android 成员变量格式）
     * - /sys/path/key: value（sysfs 格式）
     * - key=value,key2=value2（逗号分隔，healthd 格式）
     */
    private static void extractKeyValuePairs(String text, Map<String, List<String>> kvMap) {
        if (text == null) return;

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            // 处理 healthd 格式的逗号分隔行：l=80 v=4000000 t=285 h=2 st=2 c=6300000
            if (line.contains("=") && (line.contains(",") || isHealthdLine(line))) {
                String[] parts = line.split("[,\\s]+");
                for (String part : parts) {
                    int eqIdx = part.indexOf('=');
                    if (eqIdx > 0 && eqIdx < part.length() - 1) {
                        String key = normalizeKey(part.substring(0, eqIdx));
                        String value = part.substring(eqIdx + 1);
                        addToMap(kvMap, key, value);
                    }
                }
                continue;
            }

            // 处理标准 key-value 行
            // 尝试多种分隔符：:= ：= =
            String[] kv = splitKeyValue(line);
            if (kv != null) {
                String key = normalizeKey(kv[0]);
                String value = kv[1].trim();
                addToMap(kvMap, key, value);
            }
        }
    }

    /** 判断是否是 healthd 格式的行 */
    private static boolean isHealthdLine(String line) {
        String low = line.toLowerCase();
        return low.contains("l=") && low.contains("v=") && (low.contains("t=") || low.contains("c="));
    }

    /** 分割 key-value 行 */
    private static String[] splitKeyValue(String line) {
        // 尝试分隔符优先级：: = ： =
        // 先找冒号（中文或英文），尝试每个冒号位置，优先匹配已知 key
        List<int[]> colonPositions = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ':' || c == '：') {
                colonPositions.add(new int[]{i, c == '：' ? 1 : 0});
            }
        }

        // 如果有多个冒号，优先尝试后面的（更可能是真正的 key-value 分隔符）
        for (int i = colonPositions.size() - 1; i >= 0; i--) {
            int pos = colonPositions.get(i)[0];
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                String normKey = normalizeKey(key);
                // 优先匹配已知电池 key
                if (BATTERY_KEY_SET.contains(normKey)) {
                    return new String[]{key, value};
                }
                // 检查 key 是否包含内嵌冒号，提取内部 key
                // 例如 "Battery Info: Cycle count" → 提取 "Cycle count"
                int innerColon = key.lastIndexOf(':');
                if (innerColon < 0) innerColon = key.lastIndexOf('：');
                if (innerColon >= 0) {
                    String innerKey = key.substring(innerColon + 1).trim();
                    String innerNormKey = normalizeKey(innerKey);
                    if (BATTERY_KEY_SET.contains(innerNormKey)) {
                        return new String[]{innerKey, value};
                    }
                }
            }
        }
        // 没有匹配已知 key，用第一个冒号
        if (!colonPositions.isEmpty()) {
            int pos = colonPositions.get(0)[0];
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                return new String[]{key, value};
            }
        }

        // 再找等号
        int eqIdx = line.indexOf('=');
        if (eqIdx > 0 && eqIdx < line.length() - 1) {
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                return new String[]{key, value};
            }
        }
        // 最后尝试空格分隔（仅对已知 key）
        int spIdx = line.indexOf(' ');
        if (spIdx > 0 && spIdx < line.length() - 1) {
            String key = line.substring(0, spIdx).trim();
            String value = line.substring(spIdx + 1).trim();
            if (isKnownBatteryKey(key) && !value.isEmpty()) {
                return new String[]{key, value};
            }
        }
        return null;
    }

    /** 标准化 key 名称：去前缀、转小写、去下划线/连字符 */
    private static String normalizeKey(String key) {
        if (key == null) return "";
        String k = key.trim();

        // 去掉 Android 成员变量前缀 m / m_（必须在 toLowerCase 之前）
        if (k.startsWith("m_")) k = k.substring(2);
        else if (k.length() > 1 && k.startsWith("m") && Character.isUpperCase(k.charAt(1))) {
            k = Character.toLowerCase(k.charAt(1)) + k.substring(2);
        }

        // 转小写
        k = k.toLowerCase();

        // 去掉 Battery 前缀
        if (k.startsWith("battery_")) k = k.substring(8);
        else if (k.startsWith("battery ")) k = k.substring(8);
        else if (k.startsWith("battery")) k = k.substring(7);

        // 去掉 sysfs 路径前缀
        int lastSlash = k.lastIndexOf('/');
        if (lastSlash >= 0) k = k.substring(lastSlash + 1);

        // 统一分隔符
        k = k.replace("-", "_").replace(" ", "_").replace("：", "").replace(":", "");

        // 去掉前后下划线
        while (k.startsWith("_")) k = k.substring(1);
        while (k.endsWith("_")) k = k.substring(0, k.length() - 1);

        return k;
    }

    /** 判断是否是已知的电池 key（用于空格分隔的行） */
    private static boolean isKnownBatteryKey(String key) {
        String k = normalizeKey(key);
        return BATTERY_KEY_SET.contains(k);
    }

    /** 已知电池 key 集合 */
    private static final java.util.Set<String> BATTERY_KEY_SET = new java.util.HashSet<>();
    static {
        // 容量相关
        String[] capKeys = {
            "charge_counter", "chargecounter", "full_charge_capacity", "fullchargecapacity",
            "design_capacity", "designcapacity", "nominal_capacity", "nominalcapacity",
            "rated_capacity", "ratedcapacity", "actual_capacity", "actualcapacity",
            "current_capacity", "currentcapacity", "learned_battery_capacity", "learnedbatterycapacity",
            "min_learned_battery_capacity", "minlearnedbatterycapacity", "min_learned_capacity",
            "max_learned_battery_capacity", "maxlearnedbatterycapacity", "max_learned_capacity",
            "battery_capacity", "batterycapacity", "capacity", "fcc",
            // 中文
            "设计容量", "标称容量", "当前容量", "实际容量", "满充容量", "电池容量",
            // healthd 短格式
            "c", "cc",
        };
        for (String k : capKeys) BATTERY_KEY_SET.add(k);

        // 循环次数
        String[] cycleKeys = {
            "cycle_count", "cyclecount", "charge_cycle", "chargecycle",
            "battery_cycle", "batterycycle", "cycle_counter", "cyclecounter",
            "charge_cycles", "chargecycles", "cycle",
            // 中文
            "充电循环次数", "循环次数", "累计循环", "充电次数",
        };
        for (String k : cycleKeys) BATTERY_KEY_SET.add(k);

        // 温度
        String[] tempKeys = {
            "temperature", "temp", "battery_temp", "batterytemp",
            "battery_temperature", "batterytemperature",
            // 中文
            "电池温度", "温度",
            // healthd 短格式
            "t",
        };
        for (String k : tempKeys) BATTERY_KEY_SET.add(k);

        // 电压
        String[] voltKeys = {
            "voltage", "voltage_now", "batt_voltage", "battvoltage",
            "battery_voltage", "batteryvoltage",
            // 中文
            "电压",
            // healthd 短格式
            "v",
        };
        for (String k : voltKeys) BATTERY_KEY_SET.add(k);

        // 技术
        String[] techKeys = {
            "technology", "battery_type", "batterytype", "type",
            // 中文
            "电池类型",
        };
        for (String k : techKeys) BATTERY_KEY_SET.add(k);

        // Android 成员变量格式（normalizeKey 会去掉 m 前缀）
        // mChargeCounter → chargeCounter → chargecounter
        // mDesignCapacity → designCapacity → designcapacity
        // mCycleCount → cycleCount → cyclecount
        // mBatteryTemperature → batteryTemperature → batterytemperature
        // 这些已经在上面的数组中了，因为 normalizeKey 会处理 m 前缀
    }

    private static void addToMap(Map<String, List<String>> kvMap, String key, String value) {
        if (key.isEmpty() || value.isEmpty()) return;
        List<String> list = kvMap.get(key);
        if (list == null) {
            list = new ArrayList<>();
            kvMap.put(key, list);
        }
        list.add(value);
    }

    // ============= 映射 key-value 到 BatteryInfo =============

    private static void mapToBatteryInfo(BatteryInfo r, Map<String, List<String>> kvMap,
                                          String content, String brand) {

        // ==== 设计容量 ====
        int designCap = findCapacityValue(kvMap, new String[]{
            "design_capacity", "designcapacity", "battery_design_capacity",
            "nominal_capacity", "nominalcapacity", "rated_capacity", "ratedcapacity",
            "设计容量", "标称容量",
        }, 1000, 30000);
        if (designCap > 0) {
            r.designCapacity = designCap;
            r.confidence = Math.max(r.confidence, 0.7);
        }

        // ==== 当前容量（多种来源，按优先级） ====
        // 1. full_charge_capacity（最准确的当前容量）
        int fullChargeCap = findCapacityValue(kvMap, new String[]{
            "full_charge_capacity", "fullchargecapacity", "fcc",
            "满充容量",
        }, 500, 30000);

        // 2. min_learned（最准确的健康指标）
        int minLearned = findCapacityValue(kvMap, new String[]{
            "min_learned_battery_capacity", "minlearnedbatterycapacity",
            "min_learned_capacity",
        }, 500, 30000);

        // 3. actual_capacity
        int actualCap = findCapacityValue(kvMap, new String[]{
            "actual_capacity", "actualcapacity", "实际容量",
        }, 500, 30000);

        // 4. current_capacity
        int currentCap = findCapacityValue(kvMap, new String[]{
            "current_capacity", "currentcapacity", "当前容量",
        }, 500, 30000);

        // 5. max_learned
        int maxLearned = findCapacityValue(kvMap, new String[]{
            "max_learned_battery_capacity", "maxlearnedbatterycapacity",
            "max_learned_capacity",
        }, 500, 30000);

        // 6. charge_counter（需要单位转换）
        // 注意：cc 键可能是循环次数也可能是充电计数，用值域区分
        int ccMah = 0;
        long ccRaw = findLongValue(kvMap, new String[]{
            "charge_counter", "chargecounter", "c",
        });
        if (ccRaw > 0) {
            ccMah = convertToMah(ccRaw);
            r.chargeCounter = (int) ccRaw;
        }
        // cc 键特殊处理：大值(>=100000)是充电计数，小值是循环次数
        if (ccRaw == 0) {
            List<String> ccValues = kvMap.get("cc");
            if (ccValues != null) {
                for (String v : ccValues) {
                    try {
                        long val = Long.parseLong(v.trim().replaceAll("[^0-9]", ""));
                        if (val >= 100000) {
                            // 大值 → charge_counter
                            ccRaw = val;
                            ccMah = convertToMah(ccRaw);
                            r.chargeCounter = (int) ccRaw;
                            break;
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }

        // 7. battery_capacity / capacity（兜底）
        int batteryCap = findCapacityValue(kvMap, new String[]{
            "battery_capacity", "batterycapacity", "capacity", "电池容量",
        }, 500, 30000);

        // 智能选择：fullChargeCap > minLearned > actualCap > currentCap > maxLearned > ccMah > batteryCap
        // 注意：ccMah < 500 不应作为容量（可能是循环次数被误识别）
        if (ccMah > 0 && ccMah < 500) ccMah = 0;
        int chosenMah = 0;
        String capacitySource = "";
        if (fullChargeCap > 0) {
            chosenMah = fullChargeCap;
            capacitySource = "full_charge_capacity";
            r.confidence = Math.max(r.confidence, 0.90);
        } else if (minLearned > 0) {
            chosenMah = minLearned;
            capacitySource = "min_learned_capacity";
            r.confidence = Math.max(r.confidence, 0.93);
        } else if (actualCap > 0) {
            chosenMah = actualCap;
            capacitySource = "actual_capacity";
            r.confidence = Math.max(r.confidence, 0.88);
        } else if (currentCap > 0) {
            chosenMah = currentCap;
            capacitySource = "current_capacity";
            r.confidence = Math.max(r.confidence, 0.85);
        } else if (maxLearned > 0) {
            chosenMah = maxLearned;
            capacitySource = "max_learned_capacity";
            r.confidence = Math.max(r.confidence, 0.82);
        } else if (ccMah > 0) {
            chosenMah = ccMah;
            capacitySource = "charge_counter";
            r.confidence = Math.max(r.confidence, 0.80);
        } else if (batteryCap > 0) {
            chosenMah = batteryCap;
            capacitySource = "battery_capacity";
            r.confidence = Math.max(r.confidence, 0.70);
        }
        r.currentCapacity = chosenMah;
        r.capacitySource = capacitySource;

        // ==== 循环次数 ====
        int cycleCount = findIntValue(kvMap, new String[]{
            "cycle_count", "cyclecount", "charge_cycle", "chargecycle",
            "battery_cycle", "batterycycle", "cycle_counter", "cyclecounter",
            "charge_cycles", "chargecycles", "cycle",
            "充电循环次数", "循环次数", "累计循环", "充电次数",
        }, 0, 10000);
        String cycleSource = "cycle_count";

        // 特殊处理：cc 字段可能是循环次数也可能是充电计数
        // 如果 cc 的值 < 10000，且没有找到明确的 cycle_count，则 cc 可能是循环次数
        if (cycleCount == 0) {
            List<String> ccValues = kvMap.get("cc");
            if (ccValues != null) {
                for (String v : ccValues) {
                    try {
                        int val = Integer.parseInt(v.trim().replaceAll("[^0-9]", ""));
                        if (val > 0 && val < 10000) {
                            cycleCount = val;
                            cycleSource = "cc";
                            break;
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }

        if (cycleCount > 0) {
            r.cycleCount = cycleCount;
            r.cycleSource = cycleSource;
            r.confidence = Math.max(r.confidence, 0.8);
        }

        // ==== 温度 ====
        double temp = findDoubleValue(kvMap, new String[]{
            "temperature", "battery_temperature", "batterytemperature",
            "battery_temp", "batterytemp", "temp", "t",
            "电池温度", "温度",
        });
        String tempSource = "temperature";
        if (temp != 0) {
            int intVal = (int) Math.round(temp);
            if (intVal > 800 && intVal < 5000) {
                r.batteryTemp = intVal / 100.0;          // 0.01°C
            } else if (intVal > 100 && intVal <= 800) {
                r.batteryTemp = intVal / 10.0;           // 0.1°C
            } else if (temp >= -30 && temp <= 80) {
                r.batteryTemp = temp;                    // already °C
            } else {
                r.batteryTemp = 0;
            }
            if (r.batteryTemp >= -20 && r.batteryTemp <= 80) {
                r.confidence = Math.max(r.confidence, 0.6);
                r.tempSource = tempSource;
            } else {
                r.batteryTemp = 0;
            }
        }

        // ==== 电压 ====
        double volt = findDoubleValue(kvMap, new String[]{
            "voltage", "voltage_now", "batt_voltage", "battvoltage",
            "battery_voltage", "batteryvoltage", "v", "电压",
        });
        if (volt != 0) {
            if (volt > 10000) volt = volt / 1000.0;     // uV → mV
            else if (volt >= 3 && volt <= 5) volt = volt * 1000.0;  // V → mV
            if (volt >= 2500 && volt <= 5000) {
                r.voltage = (int) volt;
                r.confidence = Math.max(r.confidence, 0.5);
            }
        }

        // ==== 电池技术 ====
        String tech = findStringValue(kvMap, new String[]{
            "technology", "battery_type", "batterytype", "type", "电池类型",
        });
        if (tech != null && !tech.isEmpty()) {
            // 清理值：去掉单位、逗号等
            tech = tech.replaceAll("[,;].*$", "").trim();
            if (!tech.isEmpty()) {
                r.technology = tech;
            }
        }
        // 启发式
        if (r.technology == null || r.technology.isEmpty()) {
            String lowContent = content.toLowerCase();
            if (lowContent.contains("li-poly") || lowContent.contains("li poly") || lowContent.contains("li_po")) {
                r.technology = "Li-poly";
            } else if (lowContent.contains("li-ion") || lowContent.contains("li ion") || lowContent.contains("li_ion")) {
                r.technology = "Li-ion";
            } else if (lowContent.contains("li-po")) {
                r.technology = "Li-po";
            }
        }

        // ==== 品牌专属段二次补充 ====
        applyBrandSpecificSection(r, content, brand, kvMap);
    }

    // ============= 值提取工具方法 =============

    /** 从 kvMap 中查找容量值（mAh），在指定范围内 */
    private static int findCapacityValue(Map<String, List<String>> kvMap, String[] keys, int min, int max) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String trimmed = v.trim();
                        // 排除明显是电压的值（包含 mV 或 V 单位）
                        String low = trimmed.toLowerCase();
                        if (low.contains("mv") || low.contains("v ") || low.endsWith("v")) {
                            continue;
                        }
                        // 排除电压范围内的值（2500-5000 可能是 mV）
                        // 提取数字部分（去掉单位等后缀）
                        String numStr = trimmed.replaceAll("[^0-9.]", "");
                        if (numStr.isEmpty()) continue;
                        double val = Double.parseDouble(numStr);
                        // 排除电压值范围（2500-5000 mV 或 3-5 V）
                        if (val >= 2500 && val <= 5000) {
                            // 可能是电压值（mV），跳过
                            continue;
                        }
                        if (val >= 3 && val <= 5) {
                            // 可能是电压值（V），跳过
                            continue;
                        }
                        if (val >= min && val <= max) {
                            return (int) val;
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return 0;
    }

    /** 从 kvMap 中查找整数值，在指定范围内 */
    private static int findIntValue(Map<String, List<String>> kvMap, String[] keys, int min, int max) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9\\-]", "");
                        if (numStr.isEmpty()) continue;
                        int val = Integer.parseInt(numStr);
                        if (val >= min && val <= max) {
                            return val;
                        }
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return 0;
    }

    /** 从 kvMap 中查找长整数值 */
    private static long findLongValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9]", "");
                        if (numStr.isEmpty()) continue;
                        return Long.parseLong(numStr);
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return 0;
    }

    /** 从 kvMap 中查找浮点数值 */
    private static double findDoubleValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9.\\-]", "");
                        if (numStr.isEmpty()) continue;
                        return Double.parseDouble(numStr);
                    } catch (NumberFormatException ignore) {}
                }
            }
        }
        return 0;
    }

    /** 从 kvMap 中查找字符串值 */
    private static String findStringValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    // ============= 智能单位转换 =============

    /** charge_counter → mAh */
    private static int convertToMah(long value) {
        if (value <= 0) return 0;
        if (value < 1000) return (int) value;
        if (value >= 1000000) return (int) (value / 1000);
        long divided = value / 1000;
        if (divided >= 1000 && divided <= 20000) return (int) divided;
        if (value >= 1000 && value <= 20000) return (int) value;
        return (int) divided;
    }

    // ============= 品牌专属段补充 =============

    private static void applyBrandSpecificSection(BatteryInfo r, String content, String brand,
                                                   Map<String, List<String>> kvMap) {
        // 华为 healthd 段
        if (brand == null || brand.equals("huawei") || brand.equals("generic")
                || content.toLowerCase().contains("healthd") || content.contains("EMUI") || content.contains("HarmonyOS")) {
            // healthd 短格式行：l=80 v=4000000 t=285 h=2 st=2 c=6300000 chg=a
            String lowContent = content.toLowerCase();
            int healthdIdx = lowContent.indexOf("healthd");
            if (healthdIdx >= 0) {
                // 提取 healthd 段落
                int sectionEnd = Math.min(content.length(), healthdIdx + 2000);
                String section = content.substring(healthdIdx, sectionEnd);

                // 重新解析 healthd 段落
                Map<String, List<String>> healthdKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, healthdKv);

                // 补充缺失数据
                if (r.cycleCount == 0) {
                    int cyc = findIntValue(healthdKv, new String[]{
                        "cycle_count", "cyclecount", "cc", "充电循环次数", "循环次数",
                    }, 0, 10000);
                    if (cyc > 0) {
                        r.cycleCount = cyc;
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
                if (r.currentCapacity == 0) {
                    long ccRaw = findLongValue(healthdKv, new String[]{"c", "cc", "charge_counter", "chargecounter"});
                    if (ccRaw > 0) {
                        int mah = convertToMah(ccRaw);
                        if (mah > 0) {
                            r.chargeCounter = (int) ccRaw;
                            r.currentCapacity = mah;
                            r.confidence = Math.max(r.confidence, 0.85);
                        }
                    }
                    // 也尝试 full_charge_capacity
                    if (r.currentCapacity == 0) {
                        int fcc = findCapacityValue(healthdKv, new String[]{
                            "full_charge_capacity", "fullchargecapacity", "fcc", "满充容量",
                        }, 500, 30000);
                        if (fcc > 0) {
                            r.currentCapacity = fcc;
                            r.confidence = Math.max(r.confidence, 0.85);
                        }
                    }
                }
                if (r.designCapacity == 0) {
                    int dc = findCapacityValue(healthdKv, new String[]{
                        "design_capacity", "designcapacity", "battery_design_capacity",
                        "设计容量",
                    }, 1000, 30000);
                    if (dc > 0) {
                        r.designCapacity = dc;
                    }
                }
                if (r.batteryTemp == 0) {
                    double t = findDoubleValue(healthdKv, new String[]{"t", "temp", "temperature", "电池温度"});
                    if (t != 0) {
                        int intVal = (int) Math.round(t);
                        if (intVal > 800 && intVal < 5000) {
                            r.batteryTemp = intVal / 100.0;
                        } else if (intVal > 100 && intVal <= 800) {
                            r.batteryTemp = intVal / 10.0;
                        } else if (t >= -30 && t <= 80) {
                            r.batteryTemp = t;
                        }
                        if (r.batteryTemp >= -20 && r.batteryTemp <= 80) {
                            r.confidence = Math.max(r.confidence, 0.6);
                        } else {
                            r.batteryTemp = 0;
                        }
                    }
                }
                if (r.voltage == 0) {
                    double v = findDoubleValue(healthdKv, new String[]{"v", "voltage", "电压"});
                    if (v != 0) {
                        if (v > 10000) v = v / 1000.0;
                        else if (v >= 3 && v <= 5) v = v * 1000.0;
                        if (v >= 2500 && v <= 5000) {
                            r.voltage = (int) v;
                        }
                    }
                }
            }
        }

        // 小米 MIUI
        if (brand == null || brand.equals("xiaomi") || brand.equals("generic")
                || content.contains("MIUI") || content.contains("miui")) {
            int miIdx = content.toLowerCase().indexOf("battery stats");
            if (miIdx >= 0) {
                int sectionEnd = Math.min(content.length(), miIdx + 3000);
                String section = content.substring(miIdx, sectionEnd);
                Map<String, List<String>> miKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, miKv);

                if (r.cycleCount == 0) {
                    int cyc = findIntValue(miKv, new String[]{
                        "cycle_count", "cyclecount", "cc", "充电循环次数", "循环次数",
                    }, 0, 10000);
                    if (cyc > 0) {
                        r.cycleCount = cyc;
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
                if (r.currentCapacity == 0) {
                    int fcc = findCapacityValue(miKv, new String[]{
                        "full_charge_capacity", "fullchargecapacity", "fcc",
                    }, 500, 30000);
                    if (fcc > 0) {
                        r.currentCapacity = fcc;
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
                if (r.designCapacity == 0) {
                    int dc = findCapacityValue(miKv, new String[]{
                        "design_capacity", "designcapacity", "设计容量",
                    }, 1000, 30000);
                    if (dc > 0) {
                        r.designCapacity = dc;
                    }
                }
            }
        }

        // OPPO ColorOS
        if (brand == null || brand.equals("oppo") || brand.equals("generic")) {
            int oppoIdx = content.toLowerCase().indexOf("battery health");
            if (oppoIdx >= 0) {
                int sectionEnd = Math.min(content.length(), oppoIdx + 1500);
                String section = content.substring(oppoIdx, sectionEnd);
                Map<String, List<String>> oppoKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, oppoKv);

                if (r.cycleCount == 0) {
                    int cyc = findIntValue(oppoKv, new String[]{
                        "cycle_count", "cyclecount", "cc", "充电循环次数", "循环次数",
                    }, 0, 10000);
                    if (cyc > 0) {
                        r.cycleCount = cyc;
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
            }
        }
    }

    // ============= 提取 rawContent =============

    private static String extractRawContent(String content) {
        String[] sectionStarts = {
            "Healthd:", "healthd:", "Battery Service", "BatteryInfo",
            "Battery Stats", "Battery Information", "Battery Properties",
            "Battery Health", "dumpstate_battery", "DUMP OF SERVICE batterystats"
        };
        for (String key : sectionStarts) {
            int idx = content.indexOf(key);
            if (idx >= 0) {
                int end = Math.min(content.length(), idx + 1500);
                return content.substring(idx, end);
            }
        }
        // 找 charge_counter 行附近
        int ccIdx = content.toLowerCase().indexOf("charge_counter");
        if (ccIdx >= 0) {
            int start = Math.max(0, ccIdx - 200);
            int end = Math.min(content.length(), ccIdx + 1000);
            return content.substring(start, end);
        }
        return content.length() > 1500 ? content.substring(0, 1500) : content;
    }

    // ============= 兜底提取 =============

    /**
     * 当 key-value 解析失败时，最后一次兜底扫描
     * 用最宽松的正则从全文中提取任何可能的电池数据
     */
    private static void fallbackExtractFromText(BatteryInfo r, String content) {
        if (content == null || content.isEmpty()) return;

        String lowContent = content.toLowerCase();

        // ==== 容量兜底 ====
        if (r.currentCapacity == 0) {
            // 查找任何 charge_counter 数字（uAh → mAh）
            int ccIdx = lowContent.indexOf("charge_counter");
            if (ccIdx >= 0) {
                int colonIdx = content.indexOf(':', ccIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', ccIdx);
                if (colonIdx >= 0) {
                    int endIdx = Math.min(content.length(), colonIdx + 30);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        long val = Long.parseLong(numStr);
                        int mah = convertToMah(val);
                        if (mah > 500 && mah < 30000) {
                            r.currentCapacity = mah;
                            r.chargeCounter = (int) val;
                            r.confidence = Math.max(r.confidence, 0.65);
                        }
                    }
                }
            }
        }

        // ==== 循环次数兜底 ====
        if (r.cycleCount == 0) {
            // 查找 cycle_count / cycle count
            int cycIdx = -1;
            String[] cycKeywords = {"cycle_count", "cycle count", "充电循环次数", "循环次数"};
            for (String kw : cycKeywords) {
                cycIdx = lowContent.indexOf(kw.toLowerCase());
                if (cycIdx >= 0) break;
            }
            if (cycIdx >= 0) {
                int colonIdx = content.indexOf(':', cycIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', cycIdx);
                if (colonIdx >= 0) {
                    int endIdx = Math.min(content.length(), colonIdx + 20);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int val = Integer.parseInt(numStr);
                        if (val > 0 && val < 100000) {
                            r.cycleCount = val;
                            r.confidence = Math.max(r.confidence, 0.65);
                        }
                    }
                }
            }
        }

        // ==== 温度兜底 ====
        if (r.batteryTemp == 0) {
            int tempIdx = -1;
            String[] tempKeywords = {"temperature", "battery_temp", "电池温度"};
            for (String kw : tempKeywords) {
                tempIdx = lowContent.indexOf(kw.toLowerCase());
                if (tempIdx >= 0) break;
            }
            if (tempIdx >= 0) {
                int colonIdx = content.indexOf(':', tempIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', tempIdx);
                if (colonIdx >= 0) {
                    int endIdx = Math.min(content.length(), colonIdx + 15);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9.]", "");
                    if (!numStr.isEmpty()) {
                        try {
                            double val = Double.parseDouble(numStr);
                            int intVal = (int) Math.round(val);
                            if (intVal > 800 && intVal < 5000) r.batteryTemp = intVal / 100.0;
                            else if (intVal > 100 && intVal <= 800) r.batteryTemp = intVal / 10.0;
                            else if (val >= -30 && val <= 80) r.batteryTemp = val;
                            if (r.batteryTemp >= -20 && r.batteryTemp <= 80) {
                                r.confidence = Math.max(r.confidence, 0.5);
                            } else {
                                r.batteryTemp = 0;
                            }
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }

        // ==== 电压兜底 ====
        if (r.voltage == 0) {
            int voltIdx = lowContent.indexOf("voltage");
            if (voltIdx >= 0) {
                int colonIdx = content.indexOf(':', voltIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', voltIdx);
                if (colonIdx >= 0) {
                    int endIdx = Math.min(content.length(), colonIdx + 15);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9.]", "");
                    if (!numStr.isEmpty()) {
                        try {
                            double val = Double.parseDouble(numStr);
                            if (val > 10000) val = val / 1000.0;
                            else if (val >= 3 && val <= 5) val = val * 1000.0;
                            if (val >= 2500 && val <= 5000) {
                                r.voltage = (int) val;
                                r.confidence = Math.max(r.confidence, 0.4);
                            }
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }

        // ==== 设计容量兜底 ====
        if (r.designCapacity == 0) {
            int dcIdx = lowContent.indexOf("design_capacity");
            if (dcIdx >= 0) {
                int colonIdx = content.indexOf(':', dcIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', dcIdx);
                if (colonIdx >= 0) {
                    int endIdx = Math.min(content.length(), colonIdx + 20);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int val = Integer.parseInt(numStr);
                        if (val >= 500 && val <= 30000) {
                            r.designCapacity = val;
                        }
                    }
                }
            }
        }
    }
}
