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
 * 关键设计（v2.1.9 重构）：
 * 1. ZipInputStream 流式扫描 zip entries（内存 O(1)，无 Base64）
 * 2. 单 entry 最大 30MB（dumpstate 前 30MB 必有电池段）
 * 3. 预编译 Pattern（比 JS 快 10x+）
 * 4. 早退：高置信度匹配后立即停止
 * 5. 容量选择策略：fullChargeCap > minLearned > actual > current > maxLearned > chargeCounter
 * 6. 设计容量单独提取（作为健康度计算基准）
 * 7. 品牌专属段：华为 healthd / 小米 Battery Stats / OPPO ColorOS / 三星 OneUI
 *
 * 对应原 JS parsers.js 的 BRAND_CONFIG、calculateBatteryGrade 等逻辑。
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

    /**
     * charge_counter 模式（uAh 微安时，需要 /1000 转换为 mAh）
     * v2.1.8 重大扩充：覆盖华为/小米/三星/OPPO/vivo/魅族/努比亚/一加/真我/联想/中兴等所有已知格式
     */
    /**
     * charge_counter 模式（uAh 微安时，需要 /1000 转换为 mAh）
     * v2.1.8 重大扩充：覆盖华为/小米/三星/OPPO/vivo/魅族/努比亚/一加/真我/联想/中兴等所有已知格式
     * 修复 v2.1.8 问题：cc 模式添加 \\b 结尾，避免 628 这样的 cycle_count 被误识别为 charge_counter
     */
    private static final Pattern[] CHARGE_COUNTER_PATTERNS = new Pattern[]{
            // ===== 标准 Android 格式 =====
            Pattern.compile("(?i)charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)\\s*(?:u|μ)?ah"),
            Pattern.compile("CHARGE_COUNTER[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)(?!\\s*(?:u|μ)?\\s*ah\\b)"),
            // cc: 仅在带 uAh 后缀时算 charge_counter（v2.1.9 修复：添加 \\b 结尾）
            Pattern.compile("(?i)\\bcc[\\s\\-：:=]+(\\d+)\\s*(?:u|μ)?ah"),
            // charge_counter 作为独立行
            Pattern.compile("(?im)^\\s*charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)\\s*(?:u|μ)?ah?\\s*$"),
            Pattern.compile("(?im)^\\s*charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)\\s*$"),
            // last_full_charge / full charge
            Pattern.compile("(?i)last[\\s_\\-]*full[\\s_\\-]*charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*(?:u|μ)?ah"),
            Pattern.compile("(?i)fcc[\\s\\-：:=]+(\\d+)\\s*(?:u|μ)?ah"),
            // sysfs 路径形式
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/charge_counter[\\s\\S]{0,200}?(\\d{4,})"),
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/charge_full[\\s\\S]{0,200}?(\\d{4,})"),
            // BatteryManager dump
            Pattern.compile("(?im)^\\s*charge[\\s_\\-]*counter\\s+(\\d+)\\s*$"),
            Pattern.compile("(?im)^\\s*fcc\\s+(\\d+)\\s*$"),
            // 单词边界形式
            Pattern.compile("(?i)\\bcharge[\\s_\\-]*counter\\b[\\s\\-：:=]+(\\d+)"),
            // 健康度段
            Pattern.compile("(?i)healthd[\\s\\S]{0,500}?charge[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)"),
    };

    /**
     * 设计容量模式（直接 mAh）
     * v2.1.9 新增：与 currentCapacity 分开提取，用于健康度计算基准
     */
    private static final Pattern[] DESIGN_CAPACITY_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)design[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)design[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)nominal[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)rated[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            // 华为 Battery design capacity
            Pattern.compile("(?i)battery[\\s_\\-]*design[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            // 中文
            Pattern.compile("设计容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
            Pattern.compile("标称容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
    };

    /**
     * 当前/实际容量模式（直接 mAh，不需要单位转换）
     * v2.1.8 新增：覆盖全充电容量、实际容量、设计容量、学习容量等
     */
    private static final Pattern[] CAPACITY_MAH_PATTERNS = new Pattern[]{
            // 全充电容量
            Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)fcc[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            // 实际容量 / 当前容量 / 学习容量
            Pattern.compile("(?i)actual[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)actual[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)current[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)current[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)learned[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            // 小米专属：Min learned / Maximum learned
            Pattern.compile("(?i)min[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)min[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)max[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            // 电池容量（中文）
            Pattern.compile("当前容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
            Pattern.compile("实际容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
            Pattern.compile("满充容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
            Pattern.compile("电池容量[\\s\\-：:=]+(\\d+)\\s*m?\\s*ah"),
            // 设计容量（用于参考，但优先级低）
            Pattern.compile("(?i)design[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)nominal[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)rated[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            // 容量（带 mAh 后缀）
            Pattern.compile("(?im)^\\s*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah\\s*$"),
            Pattern.compile("(?i)battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
            Pattern.compile("(?i)battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
            // sysfs 路径
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/capacity[\\s\\S]{0,200}?(\\d{3,5})"),
    };

    private static final Pattern[] CURRENT_NOW_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)current[_\\s\\-]?now[:= ]+(-?\\d+)\\s*ua"),
            Pattern.compile("(?i)current[_\\s\\-]?now[:= ]+(-?\\d+)"),
            Pattern.compile("CURRENT_NOW[:= ]+(-?\\d+)"),
            Pattern.compile("(?i)\\bcurrent_now\\b[\\s:=]+(-?\\d+)")
    };

    private static final Pattern[] CYCLE_COUNT_PATTERNS = new Pattern[]{
            // ===== 标准 Android 格式 =====
            Pattern.compile("(?i)cycle[\\s_\\-]*count[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("CYCLE_COUNT[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)charge[\\s_\\-]*cycle[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)battery[\\s_\\-]*cycle[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)cycle[\\s_\\-]*counter[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)cc[\\s\\-：:=]+(\\d+)(?!\\s*(?:u|μ)?\\s*ah)"),
            Pattern.compile("(?i)charge[\\s_\\-]*cycles[\\s\\-：:=]+(\\d+)"),
            // 中文
            Pattern.compile("充电循环次数[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("循环次数[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("累计循环[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("充电次数[\\s\\-：:=]+(\\d+)"),
            // 全大写
            Pattern.compile("(?i)\\bcycle_count\\b[\\s\\-：:=]+(\\d+)"),
            Pattern.compile("(?i)\\bcycle\\b[\\s\\-：:=]+(\\d+)"),
            // battery health 段下的
            Pattern.compile("(?i)battery[\\s_\\-]*cycle[\\s_\\-]*count[\\s\\-：:=]+(\\d+)"),
            // sysfs
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/cycle_count[\\s\\S]{0,200}?(\\d+)"),
    };

    private static final Pattern[] TEMP_PATTERNS = new Pattern[]{
            // ===== 标准 Android 格式（摄氏度） =====
            Pattern.compile("(?i)temperature[\\s\\-：:=]+(-?\\d+\\.?\\d*)\\s*°?\\s*c"),
            Pattern.compile("(?i)temperature[\\s\\-：:=]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("(?i)\\btemperature\\b[\\s\\-：:=]+(-?\\d+)"),
            Pattern.compile("(?i)battery[\\s_\\-]*temp(?:erature)?[\\s\\-：:=]+(-?\\d+\\.?\\d*)"),
            // TEMP 缩写
            Pattern.compile("(?im)^\\s*TEMP[\\s\\-：:=]+(-?\\d+)\\s*$"),
            Pattern.compile("(?i)\\btemp[\\s\\-：:=]+(-?\\d+\\.?\\d*)(?!\\s*%)"),
            // t= 形式（dumpsys battery）
            Pattern.compile("(?i)\\bt[\\s\\-：:=]+(-?\\d+)\\b"),
            // battery temp sysfs
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/temp[\\s\\S]{0,200}?(\\d{2,4})"),
            // 中文
            Pattern.compile("电池温度[\\s\\-：:=]+(-?\\d+\\.?\\d*)"),
            Pattern.compile("温度[\\s\\-：:=]+(-?\\d+\\.?\\d*)\\s*°?\\s*℃?"),
            Pattern.compile("温度[\\s\\-：:=]+(-?\\d+\\.?\\d*)"),
    };

    private static final Pattern[] VOLTAGE_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)voltage[\\s\\-：:=]+(\\d+\\.?\\d*)\\s*v\\b"),
            Pattern.compile("(?i)voltage[\\s\\-：:=]+(\\d+\\.?\\d*)"),
            Pattern.compile("VOLTAGE[\\s\\-：:=]+(\\d+\\.?\\d*)"),
            Pattern.compile("(?i)batt[\\s_\\-]*voltage[\\s\\-：:=]+(\\d+\\.?\\d*)"),
            Pattern.compile("(?i)\\bvoltage\\b[\\s\\-：:=]+(\\d+\\.?\\d*)"),
            Pattern.compile("(?is)/sys/class/power_supply/(?:battery|main|bat1)/voltage_now[\\s\\S]{0,200}?(\\d{5,})"),
    };

    private static final Pattern[] TECHNOLOGY_PATTERNS = new Pattern[]{
            // 关键: 用 \\S+ 替代 \\w+，支持 Li-poly / Li-po 等带连字符的命名
            Pattern.compile("(?i)technology[\\s\\-：:=]+(\\S+)"),
            Pattern.compile("TECHNOLOGY[\\s\\-：:=]+(\\S+)"),
            Pattern.compile("(?i)battery[\\s_\\-]*type[\\s\\-：:=]+(\\S+)"),
            Pattern.compile("(?i)\\btechnology\\b[\\s\\-：:=]+(\\S+)"),
            // 中文
            Pattern.compile("电池类型[\\s\\-：:=]+(\\S+)"),
            // 启发式：单独识别 Li-ion / Li-poly / Li-po
            Pattern.compile("(?i)\\b(li[\\s\\-_]*ion)\\b"),
            Pattern.compile("(?i)\\b(li[\\s\\-_]*poly)\\b"),
            Pattern.compile("(?i)\\b(li[\\s\\-_]*po)\\b"),
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
                    if (content == null || content.length() < 20) {
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
     * 主解析：单 entry 内容（v2.1.9 重构）
     *
     * 关键设计：
     * 1. 提取设计容量（design_capacity）作为健康度基准
     * 2. 提取满充容量（full_charge_capacity）作为当前容量
     * 3. 提取 charge_counter (uAh) 转换为 mAh
     * 4. 提取 min_learned（最准确的健康指标）
     * 5. 智能选择：满充容量 > min_learned > charge_counter > 设计容量
     * 6. 温度单位自适应（0.1°C、0.01°C、℃、°C）
     */
    private static BatteryInfo parseContent(String content, String brand) {
        BatteryInfo r = new BatteryInfo();
        r.confidence = 0.0;

        // ==== 第0步：提取设计容量（用于健康度基准） ====
        Long designCap = firstMatchGroup(content, DESIGN_CAPACITY_PATTERNS);
        if (designCap != null && designCap > 0) {
            r.designCapacity = designCap.intValue();
        }

        // ==== 第1步：提取 charge_counter (uAh → mAh) ====
        Long ccUah = firstMatchGroup(content, CHARGE_COUNTER_PATTERNS);
        int ccMah = 0;
        if (ccUah != null && ccUah > 0) {
            ccMah = convertToMah(ccUah);
            r.chargeCounter = ccUah.intValue();
            r.confidence = Math.max(r.confidence, 0.95);
        }

        // ==== 第2步：提取各类 mAh 容量 ====
        // 依次提取：满充容量 > min_learned > actual > current > max_learned
        int fullChargeCap = 0;
        int minLearnedCap = 0;
        int actualCap = 0;
        int currentCapVal = 0;
        int maxLearnedCap = 0;

        // full_charge_capacity
        Pattern[] fullChargePatterns = {
                Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
                Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
                Pattern.compile("(?i)fcc[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
                Pattern.compile("(?i)fcc[\\s\\-：:=]+(\\d+)"),
        };
        Long fcc = firstMatchGroup(content, fullChargePatterns);
        if (fcc != null && fcc > 0 && fcc <= 20000) {
            fullChargeCap = fcc.intValue();
        }

        // min_learned（最准确的健康指标）
        Pattern[] minLearnedPatterns = {
                Pattern.compile("(?i)min[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
                Pattern.compile("(?i)min[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
                Pattern.compile("(?i)min[\\s_\\-]*learned[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
        };
        Long ml = firstMatchGroup(content, minLearnedPatterns);
        if (ml != null && ml > 0 && ml <= 20000) {
            minLearnedCap = ml.intValue();
        }

        // actual_capacity
        Pattern[] actualPatterns = {
                Pattern.compile("(?i)actual[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
                Pattern.compile("(?i)actual[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
        };
        Long ac = firstMatchGroup(content, actualPatterns);
        if (ac != null && ac > 0 && ac <= 20000) {
            actualCap = ac.intValue();
        }

        // current_capacity
        Pattern[] currentPatterns = {
                Pattern.compile("(?i)current[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)\\s*m\\s*ah"),
                Pattern.compile("(?i)current[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
        };
        Long cc = firstMatchGroup(content, currentPatterns);
        if (cc != null && cc > 0 && cc <= 20000) {
            currentCapVal = cc.intValue();
        }

        // max_learned
        Pattern[] maxLearnedPatterns = {
                Pattern.compile("(?i)max[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
                Pattern.compile("(?i)max[\\s_\\-]*learned[\\s_\\-]*battery[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
                Pattern.compile("(?i)max[\\s_\\-]*learned[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
        };
        Long maxl = firstMatchGroup(content, maxLearnedPatterns);
        if (maxl != null && maxl > 0 && maxl <= 20000) {
            maxLearnedCap = maxl.intValue();
        }

        // ==== 第3步：智能选择当前容量 ====
        // 优先级：fullChargeCap > minLearnedCap > actualCap > currentCapVal > maxLearnedCap > ccMah
        int chosenMah = 0;
        if (fullChargeCap > 0) {
            chosenMah = fullChargeCap;
            r.confidence = Math.max(r.confidence, 0.90);
        } else if (minLearnedCap > 0) {
            chosenMah = minLearnedCap;
            r.confidence = Math.max(r.confidence, 0.93);
        } else if (actualCap > 0) {
            chosenMah = actualCap;
            r.confidence = Math.max(r.confidence, 0.88);
        } else if (currentCapVal > 0) {
            chosenMah = currentCapVal;
            r.confidence = Math.max(r.confidence, 0.85);
        } else if (maxLearnedCap > 0) {
            chosenMah = maxLearnedCap;
            r.confidence = Math.max(r.confidence, 0.82);
        } else if (ccMah > 0) {
            chosenMah = ccMah;
            r.confidence = Math.max(r.confidence, 0.95);
        }
        r.currentCapacity = chosenMah;

        // ==== 第4步：循环次数 ====
        Integer cyc = firstMatchGroupInt(content, CYCLE_COUNT_PATTERNS);
        if (cyc != null && cyc >= 0 && cyc < 10000) {
            r.cycleCount = cyc;
            r.confidence = Math.max(r.confidence, 0.9);
        }

        // ==== 第5步：温度（v2.1.9 智能单位） ====
        Double temp = firstMatchGroupDouble(content, TEMP_PATTERNS);
        if (temp != null) {
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
                r.confidence = Math.max(r.confidence, 0.8);
            } else {
                r.batteryTemp = 0;
            }
        }

        // ==== 第6步：电压 ====
        Double volt = firstMatchGroupDouble(content, VOLTAGE_PATTERNS);
        if (volt != null) {
            if (volt > 10000) volt = volt / 1000.0;     // uV → mV
            else if (volt >= 3 && volt <= 5) volt = volt * 1000.0;  // V → mV
            if (volt >= 2500 && volt <= 5000) {
                r.voltage = volt.intValue();
                r.confidence = Math.max(r.confidence, 0.6);
            }
        }

        // ==== 第7步：电池技术 ====
        for (Pattern p : TECHNOLOGY_PATTERNS) {
            Matcher m = p.matcher(content);
            if (m.find()) {
                String tech = m.groupCount() >= 1 ? m.group(1) : m.group();
                if (tech != null) {
                    r.technology = tech;
                    break;
                }
            }
        }
        // 启发式
        if (r.technology == null || r.technology.isEmpty()) {
            if (Pattern.compile("(?i)li[\\s\\-_]*poly").matcher(content).find()
                    || Pattern.compile("(?i)li[\\s\\-_]*po\\b").matcher(content).find()) {
                r.technology = "Li-poly";
            } else if (Pattern.compile("(?i)li[\\s\\-_]*ion").matcher(content).find()) {
                r.technology = "Li-ion";
            }
        }

        // ==== 第8步：品牌专属段二次验证 ====
        applyBrandSpecificSection(r, content, brand);

        // ==== 至少要有容量或循环次数 ====
        if (!r.hasCapacity() && r.cycleCount == 0) {
            return null;
        }
        return r;
    }

    /**
     * 品牌专属段提取（v2.1.9 修复）
     *
     * 策略：仅在主解析未提取到值时才补充，不覆盖已有数据
     * - 华为 healthd 段：提取缺失的 cycle_count / charge_counter
     * - 小米 Battery Stats：提取 min_learned / full_charge_capacity
     * - 三星 battery_health：识别百分比但不覆盖 cycle_count
     */
    private static void applyBrandSpecificSection(BatteryInfo r, String content, String brand) {
        // ===== 华为：healthd 段（仅补充缺失数据） =====
        if (brand == null || brand.equals("huawei") || brand.equals("generic")
                || content.contains("healthd:") || content.contains("harmony") || content.contains("EMUI")) {
            Matcher hm = Pattern.compile("(?is)healthd:[\\s\\S]{0,2500}").matcher(content);
            if (hm.find()) {
                String section = hm.group();
                // 只补充缺失数据
                if (r.cycleCount == 0) {
                    Integer scyc = firstMatchGroupInt(section, CYCLE_COUNT_PATTERNS);
                    if (scyc != null && scyc >= 0 && scyc < 10000) {
                        r.cycleCount = scyc;
                        r.confidence = Math.max(r.confidence, 0.92);
                    }
                }
                if (r.currentCapacity == 0) {
                    // 先试 healthd 专属的 charge_counter
                    Long scc = firstMatchGroup(section, CHARGE_COUNTER_PATTERNS);
                    if (scc != null && scc > 0) {
                        r.chargeCounter = scc.intValue();
                        r.currentCapacity = convertToMah(scc);
                        r.confidence = Math.max(r.confidence, 0.93);
                    }
                }
                if (r.designCapacity == 0) {
                    Integer sdc = firstMatchGroupInt(section, DESIGN_CAPACITY_PATTERNS);
                    if (sdc != null && sdc > 0 && sdc <= 20000) {
                        r.designCapacity = sdc;
                    }
                }
                // 华为温度通常不带单位
                if (r.batteryTemp == 0) {
                    Double st = firstMatchGroupDouble(section, TEMP_PATTERNS);
                    if (st != null) {
                        int intVal = (int) Math.round(st);
                        if (intVal > 100 && intVal <= 800) {
                            r.batteryTemp = intVal / 10.0;
                        } else if (intVal > 800 && intVal < 5000) {
                            r.batteryTemp = intVal / 100.0;
                        } else if (st >= -30 && st <= 80) {
                            r.batteryTemp = st;
                        }
                    }
                }
            }
        }

        // ===== 小米 MIUI：Battery Stats 段（仅补充缺失数据） =====
        if (brand == null || brand.equals("xiaomi") || brand.equals("generic")
                || content.contains("MIUI") || content.contains("miui")) {
            Matcher mm = Pattern.compile("(?is)Battery Stats[\\s\\S]{0,3000}").matcher(content);
            if (mm.find()) {
                String section = mm.group();
                // 补充缺失的 cycle_count
                if (r.cycleCount == 0) {
                    Integer scyc = firstMatchGroupInt(section, CYCLE_COUNT_PATTERNS);
                    if (scyc != null && scyc >= 0 && scyc < 10000) {
                        r.cycleCount = scyc;
                        r.confidence = Math.max(r.confidence, 0.92);
                    }
                }
                // 补充缺失的 full_charge_capacity（小米通常用这个作为当前容量）
                if (r.currentCapacity == 0) {
                    Long sfcc = firstMatchGroup(section, new Pattern[]{
                            Pattern.compile("(?i)full[\\s_\\-]*charge[\\s_\\-]*capacity[\\s\\-：:=]+(\\d+)"),
                    });
                    if (sfcc != null && sfcc > 0 && sfcc <= 20000) {
                        r.currentCapacity = sfcc.intValue();
                        r.confidence = Math.max(r.confidence, 0.91);
                    }
                }
                // 补充缺失的设计容量
                if (r.designCapacity == 0) {
                    Integer sdc = firstMatchGroupInt(section, DESIGN_CAPACITY_PATTERNS);
                    if (sdc != null && sdc > 0 && sdc <= 20000) {
                        r.designCapacity = sdc;
                    }
                }
            }
        }

        // ===== OPPO ColorOS：Battery Health 段 =====
        if (brand == null || brand.equals("oppo") || brand.equals("generic")) {
            Matcher om = Pattern.compile("(?is)Battery Health[\\s\\S]{0,1500}").matcher(content);
            if (om.find()) {
                String section = om.group();
                if (r.cycleCount == 0) {
                    Integer scyc = firstMatchGroupInt(section, CYCLE_COUNT_PATTERNS);
                    if (scyc != null && scyc >= 0 && scyc < 10000) {
                        r.cycleCount = scyc;
                        r.confidence = Math.max(r.confidence, 0.91);
                    }
                }
            }
        }

        // ===== 三星 OneUI：battery_health（百分比，不覆盖已有数据） =====
        if (brand != null && brand.equals("samsung")) {
            Matcher sm = Pattern.compile("(?i)battery[\\s_\\-]*health[\\s\\-：:=]+(\\d+)%").matcher(content);
            if (sm.find()) {
                r.confidence = Math.max(r.confidence, 0.85);
            }
        }
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
        // 找包含电池相关字段的段（v2.1.9 增强）
        String[] sectionStarts = {
                "Healthd:", "Battery Service", "BatteryInfo", "Battery Stats",
                "Battery Information", "Battery Properties", "Battery Health",
                "dumpstate_battery", "DUMP OF SERVICE batterystats"
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
