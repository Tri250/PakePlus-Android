package com.batteryhealth.app;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 电池分析器 - JNI 接口封装类
 * 
 * ZIP 解压使用 Java ZipInputStream（可靠）
 * 文本解析使用 Native C++ 正则引擎（快速）
 */
public class BatteryAnalyzer {
    private static final String TAG = "BatteryAnalyzer";
    private static boolean libraryLoaded = false;

    // 加载 native 库
    static {
        try {
            System.loadLibrary("batteryhealth");
            libraryLoaded = true;
            Log.i(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            libraryLoaded = false;
        }
    }

    public static void init() {
        if (libraryLoaded) {
            nativeInit();
        }
    }

    public static boolean isNativeAvailable() {
        return libraryLoaded;
    }

    /**
     * 从文件路径解析电池数据
     * 使用 Java ZipInputStream 解压 ZIP，然后传给 Native 解析文本
     */
    public static BatteryParseResult parseFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "File not found: " + filePath);
            return new BatteryParseResult();
        }

        String fileName = file.getName().toLowerCase();

        try {
            if (fileName.endsWith(".zip")) {
                // ZIP 文件：Java 解压 + Native 解析
                return parseZipFileWithJava(filePath);
            } else {
                // 非 ZIP 文件：直接读取内容传给 Native
                byte[] content = readFileBytes(file);
                if (content != null && content.length > 0) {
                    return parseTextContent(content);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing file: " + e.getMessage(), e);
        }

        return new BatteryParseResult();
    }

    /**
     * 使用 Java ZipInputStream 解压 ZIP 文件
     * 然后将解压后的文本内容传给 Native C++ 解析
     */
    private static BatteryParseResult parseZipFileWithJava(String zipPath) {
        Log.d(TAG, "Parsing ZIP with Java ZipInputStream: " + zipPath);

        List<ZipFileEntry> entries = extractZipEntries(zipPath);

        if (entries.isEmpty()) {
            Log.w(TAG, "No entries found in ZIP file");
            return new BatteryParseResult();
        }

        Log.d(TAG, "ZIP extracted " + entries.size() + " entries");

        // 查找主 bugreport 文本文件
        String mainContent = null;
        String allTextContent = "";

        // 优先级1：查找 bugreport*.txt
        for (ZipFileEntry entry : entries) {
            String name = entry.name.toLowerCase();
            if (name.contains("bugreport") && name.endsWith(".txt") && entry.isText) {
                mainContent = entry.textContent;
                Log.d(TAG, "Found main bugreport: " + entry.name + " length=" + mainContent.length());
                break;
            }
        }

        // 优先级2：查找包含电池信息的文件
        if (mainContent == null) {
            for (ZipFileEntry entry : entries) {
                if (entry.isText && containsBatteryInfo(entry.textContent)) {
                    mainContent = entry.textContent;
                    Log.d(TAG, "Found battery info in: " + entry.name);
                    break;
                }
            }
        }

        // 优先级3：合并所有文本文件
        if (mainContent == null) {
            StringBuilder sb = new StringBuilder();
            for (ZipFileEntry entry : entries) {
                if (entry.isText) {
                    sb.append("=== ").append(entry.name).append(" ===\n");
                    sb.append(entry.textContent).append("\n\n");
                }
            }
            allTextContent = sb.toString();
            if (allTextContent.length() > 0) {
                mainContent = allTextContent;
                Log.d(TAG, "Using combined text content, length=" + mainContent.length());
            }
        }

        if (mainContent == null || mainContent.isEmpty()) {
            Log.w(TAG, "No text content found in ZIP");
            BatteryParseResult result = new BatteryParseResult();
            result.parseLog = "ZIP文件中未找到文本内容，共" + entries.size() + "个文件";
            return result;
        }

        // 传给 Native C++ 解析文本
        BatteryParseResult result = parseTextContent(mainContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // 如果 Native 解析也失败，尝试 Java 正则兜底
        if (!result.hasData) {
            Log.w(TAG, "Native parse returned no data, trying Java fallback");
            result = parseWithJavaFallback(mainContent);
        }

        // 添加解析日志
        if (result.parseLog == null || result.parseLog.isEmpty()) {
            result.parseLog = "ZIP文件:" + entries.size() + "个 | 文本长度:" + mainContent.length();
        }

        return result;
    }

    /**
     * 使用 Java ZipInputStream 提取 ZIP 文件中的所有条目
     */
    private static List<ZipFileEntry> extractZipEntries(String zipPath) {
        List<ZipFileEntry> entries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();

                // 跳过目录
                if (zipEntry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                // 读取条目内容
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] content = baos.toByteArray();
                zis.closeEntry();

                ZipFileEntry entry = new ZipFileEntry();
                entry.name = entryName;
                entry.size = content.length;

                // 判断是否是文本文件
                String lowerName = entryName.toLowerCase();
                boolean isText = lowerName.endsWith(".txt") ||
                                 lowerName.endsWith(".log") ||
                                 lowerName.endsWith(".xml") ||
                                 lowerName.endsWith(".prop") ||
                                 lowerName.contains("bugreport") ||
                                 lowerName.contains("dumpstate");

                // 对于非明确文本文件，检查内容是否包含可读文本
                if (!isText && content.length > 0 && content.length < 50 * 1024 * 1024) {
                    // 检查前1KB是否是可读文本
                    int checkLen = Math.min(content.length, 1024);
                    String preview = new String(content, 0, checkLen, "UTF-8");
                    if (preview.contains("healthd:") || preview.contains("fc=") ||
                        preview.contains("ro.product") || preview.contains("battery")) {
                        isText = true;
                    }
                }

                if (isText && content.length > 0) {
                    try {
                        entry.textContent = new String(content, "UTF-8");
                        entry.isText = true;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to decode text for: " + entryName);
                    }
                }

                // 处理嵌套 ZIP
                if (lowerName.endsWith(".zip")) {
                    Log.d(TAG, "Found nested ZIP: " + entryName);
                    try {
                        List<ZipFileEntry> nestedEntries = extractZipEntriesFromBytes(content);
                        entries.addAll(nestedEntries);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract nested ZIP: " + entryName);
                    }
                }

                entries.add(entry);
                Log.d(TAG, "ZIP entry: " + entryName + " size=" + content.length + " isText=" + isText);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading ZIP file: " + e.getMessage(), e);
        }

        return entries;
    }

    /**
     * 从字节数组提取嵌套 ZIP
     */
    private static List<ZipFileEntry> extractZipEntriesFromBytes(byte[] zipData) {
        List<ZipFileEntry> entries = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(zipData);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();

                if (zipEntry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] content = baos.toByteArray();
                zis.closeEntry();

                ZipFileEntry entry = new ZipFileEntry();
                entry.name = entryName;
                entry.size = content.length;

                String lowerName = entryName.toLowerCase();
                if (lowerName.endsWith(".txt") || lowerName.endsWith(".log") ||
                    lowerName.contains("bugreport") || lowerName.contains("dumpstate")) {
                    try {
                        entry.textContent = new String(content, "UTF-8");
                        entry.isText = true;
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to decode text for nested: " + entryName);
                    }
                }

                entries.add(entry);
            }
        } catch (IOException e) {
            Log.w(TAG, "Error reading nested ZIP: " + e.getMessage());
        }

        return entries;
    }

    /**
     * 检查文本是否包含电池信息
     */
    private static boolean containsBatteryInfo(String text) {
        if (text == null || text.isEmpty()) return false;
        return text.contains("healthd:") ||
               text.contains("fc=") ||
               text.contains("cc=") ||
               text.contains("MF_05") ||
               text.contains("MF_06") ||
               text.contains("QG_01") ||
               text.contains("QG_03") ||
               text.contains("Min learned battery capacity") ||
               text.contains("battery cycle count") ||
               text.contains("charge cycles") ||
               text.contains("DesignCapacity") ||
               text.contains("full charge capacity") ||
               text.contains("ro.product.brand");
    }

    /**
     * Java 正则兜底解析（当 Native 解析失败时使用）
     */
    private static BatteryParseResult parseWithJavaFallback(String text) {
        BatteryParseResult result = new BatteryParseResult();
        if (text == null || text.isEmpty()) return result;

        try {
            // 提取品牌
            java.util.regex.Matcher brandMatcher = java.util.regex.Pattern.compile(
                "ro\\.product\\.brand=\\s*([A-Za-z0-9_\\- ]+)").matcher(text);
            if (brandMatcher.find()) {
                result.brand = brandMatcher.group(1).trim();
            }

            // 提取型号
            java.util.regex.Matcher modelMatcher = java.util.regex.Pattern.compile(
                "ro\\.product\\.model=\\s*([A-Za-z0-9_\\- ]+)").matcher(text);
            if (modelMatcher.find()) {
                result.model = modelMatcher.group(1).trim();
            }

            // 提取当前容量 - healthd 格式 (fc= 必须在 healthd 行或独立行)
            java.util.regex.Matcher fcMatcher = java.util.regex.Pattern.compile(
                "(?:healthd:.*?fc[=:\\s]+(\\d+)|\\bfc[=:\\s]+(\\d+))").matcher(text);
            if (fcMatcher.find()) {
                String fcVal = fcMatcher.group(1) != null ? fcMatcher.group(1) : fcMatcher.group(2);
                result.currentCapacityMah = Integer.parseInt(fcVal);
            }

            // 提取循环次数 - healthd 格式 (cc= 必须在 healthd 行或独立行)
            java.util.regex.Matcher ccMatcher = java.util.regex.Pattern.compile(
                "(?:healthd:.*?cc[=:\\s]+(\\d+)|\\bcc[=:\\s]+(\\d+))").matcher(text);
            if (ccMatcher.find()) {
                String ccVal = ccMatcher.group(1) != null ? ccMatcher.group(1) : ccMatcher.group(2);
                result.cycleCount = Integer.parseInt(ccVal);
            }

            // 小米 MF 格式
            if (result.currentCapacityMah == 0) {
                java.util.regex.Matcher mf05Matcher = java.util.regex.Pattern.compile(
                    "MF_05[=:\\s]+(\\d+)").matcher(text);
                if (mf05Matcher.find()) {
                    result.currentCapacityMah = Integer.parseInt(mf05Matcher.group(1));
                }
            }
            if (result.cycleCount == 0) {
                java.util.regex.Matcher mf06Matcher = java.util.regex.Pattern.compile(
                    "MF_06[=:\\s]+(\\d+)").matcher(text);
                if (mf06Matcher.find()) {
                    result.cycleCount = Integer.parseInt(mf06Matcher.group(1));
                }
            }

            // OPPO QG 格式
            if (result.currentCapacityMah == 0) {
                java.util.regex.Matcher qg01Matcher = java.util.regex.Pattern.compile(
                    "QG_01[=:\\s]+(\\d+)").matcher(text);
                if (qg01Matcher.find()) {
                    result.currentCapacityMah = Integer.parseInt(qg01Matcher.group(1));
                }
            }
            if (result.cycleCount == 0) {
                java.util.regex.Matcher qg03Matcher = java.util.regex.Pattern.compile(
                    "QG_03[=:\\s]+(\\d+)").matcher(text);
                if (qg03Matcher.find()) {
                    result.cycleCount = Integer.parseInt(qg03Matcher.group(1));
                }
            }

            // 通用格式
            if (result.currentCapacityMah == 0) {
                java.util.regex.Matcher capMatcher = java.util.regex.Pattern.compile(
                    "Min learned battery capacity:\\s*(\\d+)\\s*mAh").matcher(text);
                if (capMatcher.find()) {
                    result.currentCapacityMah = Integer.parseInt(capMatcher.group(1));
                }
            }
            if (result.currentCapacityMah == 0) {
                java.util.regex.Matcher capMatcher = java.util.regex.Pattern.compile(
                    "full charge capacity:\\s*(\\d+)\\s*mAh").matcher(text);
                if (capMatcher.find()) {
                    result.currentCapacityMah = Integer.parseInt(capMatcher.group(1));
                }
            }

            // 设计容量
            java.util.regex.Matcher designCapMatcher = java.util.regex.Pattern.compile(
                "DesignCapacity:\\s*(\\d+)").matcher(text);
            if (designCapMatcher.find()) {
                result.designCapacityMah = Integer.parseInt(designCapMatcher.group(1));
            }
            if (result.designCapacityMah == 0) {
                java.util.regex.Matcher mf08Matcher = java.util.regex.Pattern.compile(
                    "MF_08[=:\\s]+(\\d+)").matcher(text);
                if (mf08Matcher.find()) {
                    result.designCapacityMah = Integer.parseInt(mf08Matcher.group(1));
                }
            }

            // 温度 - healthd 格式中的 t= (必须在 healthd 行内)
            java.util.regex.Matcher tempMatcher = java.util.regex.Pattern.compile(
                "healthd:\\s*battery\\s+.*?t[=:\\s]+(\\d+\\.?\\d*)").matcher(text);
            if (tempMatcher.find()) {
                result.temperatureCelsius = Float.parseFloat(tempMatcher.group(1));
            }
            // 温度 - 通用格式
            if (result.temperatureCelsius == 0) {
                java.util.regex.Matcher tempMatcher2 = java.util.regex.Pattern.compile(
                    "battery[_ ]?temperature[:\\s]+(\\d+\\.?\\d*)\\s*°?C").matcher(text);
                if (tempMatcher2.find()) {
                    result.temperatureCelsius = Float.parseFloat(tempMatcher2.group(1));
                }
            }

            // 更新 hasData
            result.hasData = result.currentCapacityMah > 0 || result.cycleCount > 0 || result.designCapacityMah > 0;

            Log.d(TAG, "Java fallback parse: hasData=" + result.hasData +
                  " brand=" + result.brand + " model=" + result.model +
                  " currentCap=" + result.currentCapacityMah +
                  " cycleCount=" + result.cycleCount +
                  " designCap=" + result.designCapacityMah);

        } catch (Exception e) {
            Log.e(TAG, "Java fallback parse error: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 使用 Native C++ 解析文本内容
     */
    private static BatteryParseResult parseTextContent(byte[] content) {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not available");
            return new BatteryParseResult();
        }
        try {
            return nativeParseBugreport(content, content.length);
        } catch (Exception e) {
            Log.e(TAG, "Native parse error: " + e.getMessage());
            return new BatteryParseResult();
        }
    }

    /**
     * 计算电池健康度
     */
    public static BatteryHealthResult calculateHealth(BatteryParseResult parseResult) {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not available");
            return new BatteryHealthResult();
        }
        if (parseResult == null || !parseResult.hasData) {
            return new BatteryHealthResult();
        }
        try {
            return nativeCalculateHealth(parseResult);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating health: " + e.getMessage());
            return new BatteryHealthResult();
        }
    }

    /**
     * 获取解析摘要
     */
    public static String getParseSummary(BatteryParseResult parseResult) {
        if (!libraryLoaded || parseResult == null) {
            return "Native library not available";
        }
        try {
            return nativeGetParseSummary(parseResult);
        } catch (Exception e) {
            return "解析摘要获取失败";
        }
    }

    /**
     * 完整分析流程
     */
    public static BatteryHealthResult analyzeFile(String filePath) {
        BatteryParseResult parseResult = parseFile(filePath);
        if (parseResult.hasData) {
            return calculateHealth(parseResult);
        }
        return new BatteryHealthResult();
    }

    /**
     * 读取文件字节数组
     */
    private static byte[] readFileBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] content = new byte[(int) file.length()];
        int read = fis.read(content);
        fis.close();
        return read > 0 ? content : null;
    }

    // ZIP 文件条目
    private static class ZipFileEntry {
        String name;
        long size;
        boolean isText;
        String textContent;
    }

    // Native 方法声明
    private static native void nativeInit();
    private static native BatteryParseResult nativeParseBugreport(byte[] content, int length);
    private static native BatteryHealthResult nativeCalculateHealth(BatteryParseResult parseResult);
    private static native String nativeGetParseSummary(BatteryParseResult parseResult);
}
