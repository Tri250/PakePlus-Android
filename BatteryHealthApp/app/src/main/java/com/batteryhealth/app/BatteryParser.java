package com.batteryhealth.app;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 电池健康度解析器 v2.1.14 - 全面重写
 *
 * 根因修复（v2.1.14）：
 * 1. healthd 格式字段含义纠正：
 *    - c = 电流(current, uA)，不是 charge_counter！
 *    - fc = 满充容量(full charge, uAh)，这才是当前容量
 *    - cc = 循环次数(cycle count)
 *    - v = 电压(mV)
 *    - t = 温度(°C，格式 X.Y)
 *    - l = 电量百分比(level)
 * 2. 添加字段交叉验证和合理性检查
 * 3. 修复电压范围误排除问题（2500-5000mAh 电池不应被跳过）
 * 4. 添加详细的字段来源追踪
 *
 * 参考 Android 源码 healthd/BatteryMonitor.cpp：
 *   snprintf(dmesgline, "battery l=%d v=%d t=%s%d.%d h=%d st=%d c=%d fc=%d cc=%d chg=%s")
 *   l=level v=voltage(mV) t=temp(°C) h=health st=status c=current(uA) fc=fullCharge(uAh) cc=cycleCount
 */
public class BatteryParser {

    private static final String TAG = "BatteryParser";

    /** 单个 entry 最大读取字节数：50MB */
    private static final long MAX_ENTRY_SIZE = 50L * 1024L * 1024L;

    /** 结果数据 */
    public static class BatteryInfo {
        public int currentCapacity;     // 当前容量 mAh
        public int designCapacity;      // 设计容量 mAh
        public int chargeCounter;       // 原始 charge_counter（uAh）
        public int cycleCount;          // 循环次数
        public double batteryTemp;      // 温度 ℃
        public int voltage;             // 电压 mV
        public String technology;       // 电池技术
        public String rawContent;       // 原始内容片段（截断）
        public String brand;            // 品牌
        public double confidence;       // 置信度 0-1
        public String debugInfo;        // 调试信息
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
                    if (scanned <= 20) {
                        debugBuilder.append("\n  ").append(name).append(" (").append(size).append("B)");
                    }

                    if (size > MAX_ENTRY_SIZE && size > 0) {
                        Log.d(TAG, "Skip large: " + name);
                        continue;
                    }

                    if (isBinaryFile(name)) {
                        continue;
                    }

                    String content = readLimited(zis, MAX_ENTRY_SIZE);
                    if (content == null || content.length() < 20) {
                        continue;
                    }

                    processed++;

                    String brand = detectBrand(name, content);

                    BatteryInfo info = parseContent(content, brand);
                    if (info != null) {
                        info.brand = brand;
                        info.rawContent = extractRawContent(content);
                        info.dataSource = name;

                        if (bestInfo == null || info.confidence > bestInfo.confidence) {
                            bestInfo = info;
                            Log.d(TAG, "Found battery info in " + name
                                + " (conf=" + String.format("%.2f", info.confidence)
                                + ", cap=" + info.currentCapacity
                                + ", cyc=" + info.cycleCount
                                + ", temp=" + info.batteryTemp + ")");
                        }
                    }

                    if (progress != null) {
                        progress.onProgress(processed, scanned, name, bestInfo);
                    }

                    // 早退：高置信度 + 有容量 + 有循环次数
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

    private static boolean isBinaryFile(String name) {
        if (name == null || name.isEmpty()) return false;
        String low = name.toLowerCase(Locale.US);
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
        String lowName = fileName == null || fileName.isEmpty() ? "" : fileName.toLowerCase(Locale.US);
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
        if (text == null || text.isEmpty()) return false;
        return p.matcher(text).find();
    }

    // ============= 核心解析 =============

    private static BatteryInfo parseContent(String content, String brand) {
        BatteryInfo r = new BatteryInfo();
        r.confidence = 0.0;

        // ==== 第1步：提取电池段落 ====
        List<String> sections = findBatterySections(content);

        // ==== 第2步：从每个段落提取 key-value 对 ====
        Map<String, List<String>> kvMap = new LinkedHashMap<>();

        // 先解析电池段落（优先级高）
        for (String section : sections) {
            extractKeyValuePairs(section, kvMap);
        }

        // 再解析全文（兜底）
        extractKeyValuePairs(content, kvMap);

        // ==== 第3步：优先解析 healthd 格式行 ====
        // healthd 格式是最标准、最可靠的电池数据来源
        parseHealthdLines(content, r);

        // ==== 第4步：映射到 BatteryInfo ====
        mapToBatteryInfo(r, kvMap, content, brand);

        // ==== 第5步：合理性交叉验证 ====
        validateAndCorrect(r);

        // ==== 第6步：低置信度兜底 ====
        if (!r.hasCapacity() && r.cycleCount == 0 && r.batteryTemp == 0) {
            fallbackExtractFromText(r, content);
        }

        // ==== 记录 kvMap 用于调试 ====
        if (r.hasCapacity() || r.cycleCount > 0 || r.batteryTemp > 0) {
            StringBuilder kvDump = new StringBuilder();
            kvDump.append("Keys(").append(kvMap.size()).append("): ");
            int count = 0;
            for (Map.Entry<String, List<String>> e : kvMap.entrySet()) {
                if (count++ < 20) {
                    kvDump.append(e.getKey()).append("=").append(e.getValue().get(0)).append(" ");
                }
            }
            r.kvMapDump = kvDump.toString();
        }

        if (!r.hasCapacity() && r.cycleCount == 0 && r.batteryTemp == 0) {
            return null;
        }
        return r;
    }

    // ============= healthd 格式专用解析 =============

    /**
     * 解析 healthd 格式行
     *
     * 标准 healthd 格式（来自 Android 源码 BatteryMonitor.cpp）：
     *   healthd: battery l=93 v=4363 t=32.5 h=2 st=2 c=557500 fc=5204000 cc=2 chg=u
     *
     * 字段含义：
     *   l  = level (电量百分比)
     *   v  = voltage (mV)
     *   t  = temperature (°C，格式 X.Y，如 32.5)
     *   h  = health status (2=GOOD)
     *   st = status (2=CHARGING)
     *   c  = current (电流, uA) ← 不是 charge_counter！
     *   fc = full charge (满充容量, uAh) ← 这才是当前容量！
     *   cc = cycle count (循环次数)
     *   chg = charger type (a=AC, u=USB, w=Wireless)
     *   tl = temperature limit
     *   ct = charger type name
     */
    private static void parseHealthdLines(String content, BatteryInfo r) {
        if (content == null) return;

        String[] lines = content.split("\n");
        boolean foundHealthd = false;

        for (String line : lines) {
            String low = line.toLowerCase();
            // 匹配 healthd 格式行：
            // 1. "healthd: battery l=93 v=4363 t=32.5 h=2 st=2 c=557500 fc=5204000 cc=2 chg=u"
            // 2. "  battery: l=80 v=4000 t=28.5 h=2 st=2 c=-350000 fc=6300000 cc=628 chg=a"
            // 3. 任何包含 l= v= 和 fc= 或 cc= 的行
            boolean isHealthdLine = (low.contains("healthd") && low.contains("battery"))
                || (low.contains("l=") && low.contains("v=") && (low.contains("fc=") || low.contains("cc=")));
            if (!isHealthdLine) continue;

            foundHealthd = true;

            // 提取 healthd 行中的各个字段
            Map<String, String> healthdFields = new LinkedHashMap<>();
            String[] parts = line.trim().split("[,\\s]+");
            for (String part : parts) {
                int eqIdx = part.indexOf('=');
                if (eqIdx > 0 && eqIdx < part.length() - 1) {
                    String key = part.substring(0, eqIdx).trim().toLowerCase();
                    String value = part.substring(eqIdx + 1).trim();
                    // 清理 key（去掉可能的路径前缀等）
                    key = key.replaceAll("[^a-z]", "");
                    healthdFields.put(key, value);
                }
            }

            // 解析 fc (full charge, uAh) → 当前容量
            String fcStr = healthdFields.get("fc");
            if (fcStr != null) {
                try {
                    String numStr = fcStr.replaceAll("[^0-9\\-]", "");
                    if (!numStr.isEmpty()) {
                        long fcUah = Long.parseLong(numStr);
                        // 负值检查
                        if (fcUah < 0) {
                            Log.w(TAG, "Negative fc value: " + fcUah);
                        } else if (fcUah > 0) {
                            int fcMah = uahToMah(fcUah);
                            // 边界检查：容量应在合理范围内
                            if (fcMah >= 500 && fcMah <= 30000) {
                                r.currentCapacity = fcMah;
                                r.chargeCounter = (int) fcUah;
                                r.capacitySource = "healthd.fc(" + fcUah + "uAh)";
                                r.confidence = Math.max(r.confidence, 0.95);
                            } else {
                                Log.w(TAG, "fc value out of bounds: " + fcMah + "mAh");
                            }
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Invalid fc format: " + fcStr);
                }
            }

            // 解析 cc (cycle count) → 循环次数
            String ccStr = healthdFields.get("cc");
            if (ccStr != null) {
                try {
                    String numStr = ccStr.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int cc = Integer.parseInt(numStr);
                        // 边界检查：循环次数应在合理范围内
                        if (cc > 0 && cc < 10000) {
                            r.cycleCount = cc;
                            r.cycleSource = "healthd.cc";
                            r.confidence = Math.max(r.confidence, 0.9);
                        } else if (cc >= 10000) {
                            Log.w(TAG, "cc value out of bounds: " + cc);
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Invalid cc format: " + ccStr);
                }
            }

            // 解析 v (voltage, mV)
            String vStr = healthdFields.get("v");
            if (vStr != null) {
                try {
                    String numStr = vStr.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int v = Integer.parseInt(numStr);
                        // 边界检查：电压应在合理范围内 (2500-5000mV)
                        if (v >= 2500 && v <= 5000) {
                            r.voltage = v;
                            r.confidence = Math.max(r.confidence, 0.5);
                        } else {
                            Log.w(TAG, "v value out of bounds: " + v + "mV");
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Invalid v format: " + vStr);
                }
            }

            // 解析 t (temperature, °C 格式 X.Y)
            String tStr = healthdFields.get("t");
            if (tStr != null) {
                try {
                    String numStr = tStr.replaceAll("[^0-9.\\-]", "");
                    if (!numStr.isEmpty()) {
                        double t = Double.parseDouble(numStr);
                        // 边界检查：温度应在合理范围内 (-30°C ~ 80°C)
                        if (t >= -30 && t <= 80) {
                            r.batteryTemp = t;
                            r.tempSource = "healthd.t";
                            r.confidence = Math.max(r.confidence, 0.7);
                        } else {
                            Log.w(TAG, "t value out of bounds: " + t + "°C");
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Invalid t format: " + tStr);
                }
            }

            // 解析 l (level, 百分比) - 用于验证
            String lStr = healthdFields.get("l");
            if (lStr != null) {
                try {
                    String numStr = lStr.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        int level = Integer.parseInt(numStr);
                        // 边界检查：电量百分比应在 0-100 范围内
                        if (level >= 0 && level <= 100) {
                            Log.d(TAG, "healthd battery level: " + level + "%");
                        } else {
                            Log.w(TAG, "l value out of bounds: " + level + "%");
                        }
                    }
                } catch (NumberFormatException nfe) {
                    Log.w(TAG, "Invalid l format: " + lStr);
                }
            }

            // 找到一条有效的 healthd 行就够了
            if (r.currentCapacity > 0 || r.cycleCount > 0) {
                break;
            }
        }

        if (!foundHealthd) {
            Log.d(TAG, "No healthd battery line found in content");
        }
    }

    /** uAh → mAh 转换 */
    private static int uahToMah(long uah) {
        if (uah <= 0) return 0;
        // uAh → mAh：除以 1000
        long mah = uah / 1000;
        // 四舍五入
        if (uah % 1000 >= 500) mah++;
        if (mah >= 100 && mah <= 30000) return (int) mah;
        // 如果值本身就在 mAh 范围内（500-30000），可能已经是 mAh 了
        if (uah >= 500 && uah <= 30000) return (int) uah;
        return 0;
    }

    // ============= 段落查找 =============

    private static List<String> findBatterySections(String content) {
        List<String> sections = new ArrayList<>();
        String lowContent = content.toLowerCase();

        for (String startMarker : BATTERY_SECTION_STARTS) {
            int startIdx = lowContent.indexOf(startMarker.toLowerCase());
            while (startIdx >= 0) {
                int endIdx = content.length();
                for (String endMarker : BATTERY_SECTION_ENDS) {
                    int eIdx = lowContent.indexOf(endMarker.toLowerCase(), startIdx + startMarker.length());
                    if (eIdx > startIdx && eIdx < endIdx) {
                        endIdx = eIdx;
                    }
                }

                int sectionLen = Math.min(endIdx - startIdx, 8000);
                if (sectionLen > 50) {
                    sections.add(content.substring(startIdx, startIdx + sectionLen));
                }

                startIdx = lowContent.indexOf(startMarker.toLowerCase(), startIdx + 1);
            }
        }

        return sections;
    }

    // ============= key-value 提取 =============

    private static void extractKeyValuePairs(String text, Map<String, List<String>> kvMap) {
        if (text == null) return;

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            // 处理 healthd 格式的逗号/空格分隔行
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
        List<int[]> colonPositions = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ':' || c == '：') {
                colonPositions.add(new int[]{i, c == '：' ? 1 : 0});
            }
        }

        for (int i = colonPositions.size() - 1; i >= 0; i--) {
            int pos = colonPositions.get(i)[0];
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                String normKey = normalizeKey(key);
                if (BATTERY_KEY_SET.contains(normKey)) {
                    return new String[]{key, value};
                }
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
        if (!colonPositions.isEmpty()) {
            int pos = colonPositions.get(0)[0];
            String key = line.substring(0, pos).trim();
            String value = line.substring(pos + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                return new String[]{key, value};
            }
        }

        int eqIdx = line.indexOf('=');
        if (eqIdx > 0 && eqIdx < line.length() - 1) {
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                return new String[]{key, value};
            }
        }
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

    /** 标准化 key 名称 */
    private static String normalizeKey(String key) {
        if (key == null) return "";
        String k = key.trim();

        if (k.startsWith("m_")) k = k.substring(2);
        else if (k.length() > 1 && k.startsWith("m") && Character.isUpperCase(k.charAt(1))) {
            k = Character.toLowerCase(k.charAt(1)) + k.substring(2);
        }

        k = k.toLowerCase();

        if (k.startsWith("battery_")) k = k.substring(8);
        else if (k.startsWith("battery ")) k = k.substring(8);
        else if (k.startsWith("battery")) k = k.substring(7);

        int lastSlash = k.lastIndexOf('/');
        if (lastSlash >= 0) k = k.substring(lastSlash + 1);

        k = k.replace("-", "_").replace(" ", "_").replace("：", "").replace(":", "");

        while (k.startsWith("_")) k = k.substring(1);
        while (k.endsWith("_")) k = k.substring(0, k.length() - 1);

        return k;
    }

    private static boolean isKnownBatteryKey(String key) {
        String k = normalizeKey(key);
        return BATTERY_KEY_SET.contains(k);
    }

    /** 已知电池 key 集合 */
    private static final java.util.Set<String> BATTERY_KEY_SET = new java.util.HashSet<>();
    static {
        // 容量相关（注意：不包含 "c" 和 "cc"，因为它们在 healthd 中含义不同）
        String[] capKeys = {
            "charge_counter", "chargecounter",
            "full_charge_capacity", "fullchargecapacity", "fcc", "fc",
            "design_capacity", "designcapacity",
            "nominal_capacity", "nominalcapacity",
            "rated_capacity", "ratedcapacity",
            "actual_capacity", "actualcapacity",
            "current_capacity", "currentcapacity",
            "learned_battery_capacity", "learnedbatterycapacity",
            "min_learned_battery_capacity", "minlearnedbatterycapacity", "min_learned_capacity",
            "max_learned_battery_capacity", "maxlearnedbatterycapacity", "max_learned_capacity",
            "battery_capacity", "batterycapacity",
            "满充容量", "设计容量", "标称容量", "当前容量", "实际容量", "电池容量",
        };
        for (String k : capKeys) BATTERY_KEY_SET.add(k);

        // 循环次数（cc 在 healthd 中是 cycle_count，在其他上下文中也可能是循环次数）
        String[] cycleKeys = {
            "cycle_count", "cyclecount", "charge_cycle", "chargecycle",
            "battery_cycle", "batterycycle", "cycle_counter", "cyclecounter",
            "charge_cycles", "chargecycles", "cycle", "cc",
            "充电循环次数", "循环次数", "累计循环", "充电次数",
        };
        for (String k : cycleKeys) BATTERY_KEY_SET.add(k);

        // 温度
        String[] tempKeys = {
            "temperature", "temp", "battery_temp", "batterytemp",
            "battery_temperature", "batterytemperature",
            "电池温度", "温度",
        };
        for (String k : tempKeys) BATTERY_KEY_SET.add(k);

        // 电压
        String[] voltKeys = {
            "voltage", "voltage_now", "batt_voltage", "battvoltage",
            "battery_voltage", "batteryvoltage",
            "电压",
        };
        for (String k : voltKeys) BATTERY_KEY_SET.add(k);

        // 技术
        String[] techKeys = {
            "technology", "battery_type", "batterytype", "type",
            "电池类型",
        };
        for (String k : techKeys) BATTERY_KEY_SET.add(k);

        // 电量百分比
        String[] levelKeys = {
            "level", "battery_level",
        };
        for (String k : levelKeys) BATTERY_KEY_SET.add(k);
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
        }, 500, 30000);
        if (designCap > 0) {
            r.designCapacity = designCap;
            r.confidence = Math.max(r.confidence, 0.7);
        }

        // ==== 当前容量（多种来源，按优先级） ====
        // 如果 healthd 已经解析了 fc，优先使用
        if (r.currentCapacity > 0 && r.capacitySource != null && r.capacitySource.startsWith("healthd")) {
            // healthd.fc 已经是最可靠的数据源，跳过其他
        } else {
            // 1. full_charge_capacity
            int fullChargeCap = findCapacityValue(kvMap, new String[]{
                "full_charge_capacity", "fullchargecapacity", "fcc", "fc",
                "满充容量",
            }, 500, 30000);

            // 2. min_learned
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

            // 6. charge_counter（需要单位转换 uAh → mAh）
            int ccMah = 0;
            long ccRaw = findLongValue(kvMap, new String[]{
                "charge_counter", "chargecounter",
            });
            if (ccRaw > 0) {
                ccMah = uahToMah(ccRaw);
                if (ccMah > 0) {
                    r.chargeCounter = (int) ccRaw;
                }
            }

            // 7. battery_capacity（兜底）
            int batteryCap = findCapacityValue(kvMap, new String[]{
                "battery_capacity", "batterycapacity", "电池容量",
            }, 500, 30000);

            // 智能选择
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
        }

        // ==== 循环次数 ====
        // 如果 healthd 已经解析了 cc，优先使用
        if (r.cycleCount > 0 && r.cycleSource != null && r.cycleSource.startsWith("healthd")) {
            // healthd.cc 已经是最可靠的数据源
        } else {
            int cycleCount = findIntValue(kvMap, new String[]{
                "cycle_count", "cyclecount", "charge_cycle", "chargecycle",
                "battery_cycle", "batterycycle", "cycle_counter", "cyclecounter",
                "charge_cycles", "chargecycles", "cycle", "cc",
                "充电循环次数", "循环次数", "累计循环", "充电次数",
            }, 0, 10000);

            if (cycleCount > 0) {
                r.cycleCount = cycleCount;
                r.cycleSource = "cycle_count";
                r.confidence = Math.max(r.confidence, 0.8);
            }
        }

        // ==== 温度 ====
        if (r.batteryTemp == 0) {
            double temp = findDoubleValue(kvMap, new String[]{
                "temperature", "battery_temperature", "batterytemperature",
                "battery_temp", "batterytemp", "temp",
                "电池温度", "温度",
            });
            if (temp != 0) {
                int intVal = (int) Math.round(temp);
                if (intVal > 800 && intVal < 5000) {
                    r.batteryTemp = intVal / 100.0;
                } else if (intVal > 100 && intVal <= 800) {
                    r.batteryTemp = intVal / 10.0;
                } else if (temp >= -30 && temp <= 80) {
                    r.batteryTemp = temp;
                }
                if (r.batteryTemp >= -20 && r.batteryTemp <= 80) {
                    r.confidence = Math.max(r.confidence, 0.6);
                    r.tempSource = "temperature";
                } else {
                    r.batteryTemp = 0;
                }
            }
        }

        // ==== 电压 ====
        if (r.voltage == 0) {
            double volt = findDoubleValue(kvMap, new String[]{
                "voltage", "voltage_now", "batt_voltage", "battvoltage",
                "battery_voltage", "batteryvoltage", "电压",
            });
            if (volt != 0) {
                if (volt > 10000) volt = volt / 1000.0;
                else if (volt >= 3 && volt <= 5) volt = volt * 1000.0;
                if (volt >= 2500 && volt <= 5000) {
                    r.voltage = (int) volt;
                    r.confidence = Math.max(r.confidence, 0.5);
                }
            }
        }

        // ==== 电池技术 ====
        String tech = findStringValue(kvMap, new String[]{
            "technology", "battery_type", "batterytype", "type", "电池类型",
        });
        if (tech != null && !tech.isEmpty()) {
            tech = tech.replaceAll("[,;].*$", "").trim();
            if (!tech.isEmpty()) {
                r.technology = tech;
            }
        }
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

    // ============= 合理性交叉验证 =============

    /**
     * 验证解析结果的合理性，修正明显错误
     *
     * 规则：
     * 1. 当前容量不应小于设计容量的 5%（除非电池真的报废了）
     * 2. 当前容量不应大于设计容量的 120%
     * 3. 健康度 = 当前容量 / 设计容量 * 100，应在 5%-120% 范围内
     * 4. 如果当前容量明显不合理，尝试从 charge_counter 重新计算
     */
    private static void validateAndCorrect(BatteryInfo r) {
        if (r.designCapacity <= 0 || r.currentCapacity <= 0) return;

        double healthPct = (double) r.currentCapacity / r.designCapacity * 100;

        // 当前容量 < 设计容量的 5%，很可能是解析错误
        if (healthPct < 5) {
            Log.w(TAG, "Capacity sanity check failed: " + r.currentCapacity + "mAh / "
                + r.designCapacity + "mAh = " + String.format("%.1f", healthPct) + "%");
            Log.w(TAG, "Capacity source: " + r.capacitySource);
            Log.w(TAG, "This is likely a parsing error, clearing currentCapacity");

            // 记录错误值用于调试
            String oldSource = r.capacitySource;
            int oldCap = r.currentCapacity;

            // 清除不合理的容量值
            r.currentCapacity = 0;
            r.capacitySource = "INVALID(" + oldSource + "=" + oldCap + "mAh,health="
                + String.format("%.1f", healthPct) + "%)";

            // 尝试从 charge_counter 重新计算
            if (r.chargeCounter > 0) {
                int mah = uahToMah(r.chargeCounter);
                double newHealth = (double) mah / r.designCapacity * 100;
                if (mah >= 500 && newHealth >= 5 && newHealth <= 120) {
                    r.currentCapacity = mah;
                    r.capacitySource = "charge_counter_fallback(" + r.chargeCounter + "uAh)";
                    Log.d(TAG, "Recovered capacity from charge_counter: " + mah + "mAh");
                }
            }
        }

        // 当前容量 > 设计容量的 120%，也可能是解析错误
        if (r.currentCapacity > 0 && healthPct > 120) {
            Log.w(TAG, "Capacity exceeds design by >120%: " + r.currentCapacity + "mAh / "
                + r.designCapacity + "mAh = " + String.format("%.1f", healthPct) + "%");
            // 可能是单位错误（mAh 被当成 uAh），尝试转换
            int converted = uahToMah(r.currentCapacity);
            double convertedHealth = (double) converted / r.designCapacity * 100;
            if (converted >= 500 && convertedHealth >= 5 && convertedHealth <= 120) {
                r.currentCapacity = converted;
                r.capacitySource = "unit_corrected(" + r.capacitySource + ")";
                Log.d(TAG, "Corrected capacity unit: " + converted + "mAh");
            }
        }
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
                        // 排除明显是电压的值（包含 mV 或 V 单位，但不是 mAh/uAh）
                        String low = trimmed.toLowerCase(Locale.US);
                        if (low.contains("mv") && !low.contains("mah")) continue;
                        if ((low.endsWith("v") || low.contains("v ")) && !low.contains("mah") && !low.contains("uah")) continue;

                        String numStr = trimmed.replaceAll("[^0-9.]", "");
                        if (numStr.isEmpty()) continue;
                        
                        // 验证数字格式
                        double val;
                        try {
                            val = Double.parseDouble(numStr);
                        } catch (NumberFormatException nfe) {
                            Log.w(TAG, "Invalid number format: " + numStr);
                            continue;
                        }
                        
                        // 负值检查
                        if (val < 0) {
                            Log.w(TAG, "Negative capacity value: " + val);
                            continue;
                        }

                        // 智能单位判断：
                        // 如果值 >= 100000，很可能是 uAh，需要转换
                        if (val >= 100000) {
                            int mah = uahToMah((long) val);
                            if (mah >= min && mah <= max) return mah;
                            continue;
                        }
                        // 如果值在 2500-5000 范围，可能是 mV 电压值
                        // 但也可能是 3000-5000mAh 的电池容量
                        // 用 key 名称判断：如果是 full_charge_capacity 等容量 key，更可能是容量
                        // 如果是 "capacity" 这种模糊 key，可能是电压
                        if (val >= 2500 && val <= 5000) {
                            // 检查原始值的单位标记
                            if (low.contains("mah") || low.contains("ah")) {
                                return (int) val;
                            }
                            // 如果 key 明确是容量相关，接受
                            if (key.contains("charge_capacity") || key.contains("chargecapacity")
                                || key.contains("fcc") || key.contains("fc")
                                || key.contains("learned") || key.contains("actual")
                                || key.contains("满充") || key.contains("容量")) {
                                return (int) val;
                            }
                            // 模糊 key（如 "capacity"），可能是电压，跳过
                            continue;
                        }
                        if (val >= 3 && val <= 5) {
                            // 3-5 范围，可能是 V 电压值，跳过
                            continue;
                        }
                        if (val >= min && val <= max) {
                            return (int) val;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing capacity value: " + v, e);
                    }
                }
            }
        }
        return 0;
    }

    private static int findIntValue(Map<String, List<String>> kvMap, String[] keys, int min, int max) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9\\-]", "");
                        if (numStr.isEmpty()) continue;
                        int val = Integer.parseInt(numStr);
                        // 负值检查（循环次数不能为负）
                        if (val < 0) {
                            Log.w(TAG, "Negative integer value for key " + key + ": " + val);
                            continue;
                        }
                        if (val >= min && val <= max) return val;
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "Invalid integer format for key " + key + ": " + v);
                    }
                }
            }
        }
        return 0;
    }

    private static long findLongValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9]", "");
                        if (numStr.isEmpty()) continue;
                        long val = Long.parseLong(numStr);
                        // 负值检查
                        if (val < 0) {
                            Log.w(TAG, "Negative long value for key " + key + ": " + val);
                            continue;
                        }
                        return val;
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "Invalid long format for key " + key + ": " + v);
                    }
                }
            }
        }
        return 0;
    }

    private static double findDoubleValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null) {
                for (String v : values) {
                    try {
                        String numStr = v.trim().replaceAll("[^0-9.\\-]", "");
                        if (numStr.isEmpty()) continue;
                        double val = Double.parseDouble(numStr);
                        // 边界检查（温度范围）
                        return val;
                    } catch (NumberFormatException nfe) {
                        Log.w(TAG, "Invalid double format for key " + key + ": " + v);
                    }
                }
            }
        }
        return 0;
    }

    private static String findStringValue(Map<String, List<String>> kvMap, String[] keys) {
        for (String key : keys) {
            List<String> values = kvMap.get(key);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        }
        return null;
    }

    // ============= 品牌专属段补充 =============

    private static void applyBrandSpecificSection(BatteryInfo r, String content, String brand,
                                                   Map<String, List<String>> kvMap) {
        // 华为 healthd 段（已由 parseHealthdLines 处理，这里只补充缺失数据）
        if (brand == null || brand.equals("huawei") || brand.equals("generic")
                || content.toLowerCase().contains("healthd") || content.contains("EMUI") || content.contains("HarmonyOS")) {
            String lowContent = content.toLowerCase();
            int healthdIdx = lowContent.indexOf("healthd");
            if (healthdIdx >= 0) {
                int sectionEnd = Math.min(content.length(), healthdIdx + 3000);
                String section = content.substring(healthdIdx, sectionEnd);
                Map<String, List<String>> healthdKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, healthdKv);

                if (r.designCapacity == 0) {
                    int dc = findCapacityValue(healthdKv, new String[]{
                        "design_capacity", "designcapacity", "battery_design_capacity",
                        "设计容量",
                    }, 500, 30000);
                    if (dc > 0) r.designCapacity = dc;
                }
            }
        }

        // 小米 MIUI
        if (brand == null || brand.equals("xiaomi") || brand.equals("generic")
                || content.contains("MIUI") || content.contains("miui")) {
            int miIdx = content.toLowerCase().indexOf("battery stats");
            if (miIdx >= 0) {
                int sectionEnd = Math.min(content.length(), miIdx + 5000);
                String section = content.substring(miIdx, sectionEnd);
                Map<String, List<String>> miKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, miKv);

                if (r.cycleCount == 0) {
                    int cyc = findIntValue(miKv, new String[]{
                        "cycle_count", "cyclecount", "充电循环次数", "循环次数",
                    }, 0, 10000);
                    if (cyc > 0) {
                        r.cycleCount = cyc;
                        r.cycleSource = "miui.cycle_count";
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
                if (r.currentCapacity == 0) {
                    int fcc = findCapacityValue(miKv, new String[]{
                        "full_charge_capacity", "fullchargecapacity", "fcc",
                    }, 500, 30000);
                    if (fcc > 0) {
                        r.currentCapacity = fcc;
                        r.capacitySource = "miui.fcc";
                        r.confidence = Math.max(r.confidence, 0.85);
                    }
                }
                if (r.designCapacity == 0) {
                    int dc = findCapacityValue(miKv, new String[]{
                        "design_capacity", "designcapacity", "设计容量",
                    }, 500, 30000);
                    if (dc > 0) r.designCapacity = dc;
                }
            }
        }

        // OPPO ColorOS
        if (brand == null || brand.equals("oppo") || brand.equals("generic")) {
            int oppoIdx = content.toLowerCase().indexOf("battery health");
            if (oppoIdx >= 0) {
                int sectionEnd = Math.min(content.length(), oppoIdx + 2000);
                String section = content.substring(oppoIdx, sectionEnd);
                Map<String, List<String>> oppoKv = new LinkedHashMap<>();
                extractKeyValuePairs(section, oppoKv);

                if (r.cycleCount == 0) {
                    int cyc = findIntValue(oppoKv, new String[]{
                        "cycle_count", "cyclecount", "充电循环次数", "循环次数",
                    }, 0, 10000);
                    if (cyc > 0) {
                        r.cycleCount = cyc;
                        r.cycleSource = "oppo.cycle_count";
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
                int end = Math.min(content.length(), idx + 2000);
                return content.substring(idx, end);
            }
        }
        int ccIdx = content.toLowerCase().indexOf("charge_counter");
        if (ccIdx >= 0) {
            int start = Math.max(0, ccIdx - 200);
            int end = Math.min(content.length(), ccIdx + 1000);
            return content.substring(start, end);
        }
        return content.length() > 2000 ? content.substring(0, 2000) : content;
    }

    // ============= 兜底提取 =============

    private static void fallbackExtractFromText(BatteryInfo r, String content) {
        if (content == null || content.isEmpty()) return;

        String lowContent = content.toLowerCase();

        // ==== 容量兜底 ====
        if (r.currentCapacity == 0) {
            // 查找 charge_counter 数字（uAh → mAh）
            int ccIdx = lowContent.indexOf("charge_counter");
            if (ccIdx >= 0) {
                int colonIdx = content.indexOf(':', ccIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', ccIdx);
                if (colonIdx >= 0 && colonIdx < ccIdx + 50) {
                    int endIdx = Math.min(content.length(), colonIdx + 30);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        try {
                            long val = Long.parseLong(numStr);
                            int mah = uahToMah(val);
                            if (mah >= 500 && mah <= 30000) {
                                r.currentCapacity = mah;
                                r.chargeCounter = (int) val;
                                r.capacitySource = "fallback.charge_counter";
                                r.confidence = Math.max(r.confidence, 0.65);
                            }
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }

        // ==== 循环次数兜底 ====
        if (r.cycleCount == 0) {
            int cycIdx = -1;
            String[] cycKeywords = {"cycle_count", "cycle count", "充电循环次数", "循环次数"};
            for (String kw : cycKeywords) {
                cycIdx = lowContent.indexOf(kw.toLowerCase());
                if (cycIdx >= 0) break;
            }
            if (cycIdx >= 0) {
                int colonIdx = content.indexOf(':', cycIdx);
                if (colonIdx < 0) colonIdx = content.indexOf('=', cycIdx);
                if (colonIdx >= 0 && colonIdx < cycIdx + 50) {
                    int endIdx = Math.min(content.length(), colonIdx + 20);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        try {
                            int val = Integer.parseInt(numStr);
                            if (val > 0 && val < 10000) {
                                r.cycleCount = val;
                                r.cycleSource = "fallback.cycle_count";
                                r.confidence = Math.max(r.confidence, 0.65);
                            }
                        } catch (NumberFormatException ignore) {}
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
                if (colonIdx >= 0 && colonIdx < tempIdx + 50) {
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
                                r.tempSource = "fallback.temperature";
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
                if (colonIdx >= 0 && colonIdx < voltIdx + 50) {
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
                if (colonIdx >= 0 && colonIdx < dcIdx + 50) {
                    int endIdx = Math.min(content.length(), colonIdx + 20);
                    String numStr = content.substring(colonIdx + 1, endIdx).replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        try {
                            int val = Integer.parseInt(numStr);
                            if (val >= 500 && val <= 30000) {
                                r.designCapacity = val;
                            }
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }
        }
    }
}
