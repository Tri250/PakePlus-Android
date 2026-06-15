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
 * 
 * v2.1.2 关键修复：内存优化 - 不再一次性加载所有ZIP条目文本到内存
 */
public class BatteryAnalyzer {
    private static final String TAG = "BatteryAnalyzer";
    private static boolean libraryLoaded = false;
    
    // 文本大小限制：超过此限制只取前10MB传给Native，防止OOM/SIGSEGV
    private static final int MAX_TEXT_SIZE = 10 * 1024 * 1024; // 10MB
    // 预过滤后传给Native的最大文本大小
    private static final int MAX_FILTERED_SIZE = 2 * 1024 * 1024; // 2MB
    // 单条目最大大小
    private static final int MAX_ENTRY_SIZE = 50 * 1024 * 1024; // 50MB

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
                return parseZipFileMemoryEfficient(filePath);
            } else {
                byte[] content = readFileBytes(file);
                if (content != null && content.length > 0) {
                    return parseTextSafe(content);
                }
            }
        } catch (Throwable e) {
            // 捕获所有异常包括OOM，防止闪退
            Log.e(TAG, "Error parsing file: " + e.getMessage(), e);
            BatteryParseResult result = new BatteryParseResult();
            result.parseLog = "解析异常: " + e.getClass().getSimpleName();
            return result;
        }

        return new BatteryParseResult();
    }

    /**
     * 内存高效版 ZIP 解析 - v2.1.2 核心修复
     * 
     * 问题：旧版 extractZipEntries 将所有条目文本加载到内存
     *   对于50MB的bugreport ZIP，内存占用可达200MB+ → OOM闪退
     * 
     * 修复：两遍扫描策略
     *   第一遍：只收集条目名称和大小，不读文本
     *   第二遍：只提取目标文件的文本内容
     */
    private static BatteryParseResult parseZipFileMemoryEfficient(String zipPath) {
        Log.d(TAG, "Memory-efficient ZIP parsing: " + zipPath);
        
        try {
            // 第一遍：扫描所有条目，找到最佳候选
            String bestEntryName = null;
            long bestEntrySize = 0;
            List<String> textEntryNames = new ArrayList<>();
            
            try (FileInputStream fis = new FileInputStream(zipPath);
                 ZipInputStream zis = new ZipInputStream(fis)) {
                
                ZipEntry zipEntry;
                while ((zipEntry = zis.getNextEntry()) != null) {
                    String name = zipEntry.getName();
                    if (zipEntry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    
                    long size = zipEntry.getSize();
                    String lowerName = name.toLowerCase();
                    boolean isText = lowerName.endsWith(".txt") ||
                                     lowerName.endsWith(".log") ||
                                     lowerName.endsWith(".xml") ||
                                     lowerName.endsWith(".prop") ||
                                     lowerName.contains("bugreport") ||
                                     lowerName.contains("dumpstate");
                    
                    if (isText) {
                        textEntryNames.add(name);
                        Log.d(TAG, "Found text entry: " + name + " size=" + size);
                        
                        // 优先级：bugreport*.txt > dumpstate*.txt > 其他
                        if (lowerName.contains("bugreport") && lowerName.endsWith(".txt")) {
                            if (bestEntryName == null || !bestEntryName.toLowerCase().contains("bugreport")) {
                                bestEntryName = name;
                                bestEntrySize = size;
                            }
                        } else if (lowerName.contains("dumpstate") && bestEntryName == null) {
                            bestEntryName = name;
                            bestEntrySize = size;
                        } else if (bestEntryName == null) {
                            bestEntryName = name;
                            bestEntrySize = size;
                        }
                    }
                    
                    zis.closeEntry();
                }
            }
            
            if (bestEntryName == null) {
                Log.w(TAG, "No text entries found in ZIP");
                BatteryParseResult result = new BatteryParseResult();
                result.parseLog = "ZIP文件中未找到文本内容";
                return result;
            }
            
            Log.d(TAG, "Best candidate: " + bestEntryName + " size=" + bestEntrySize);
            
            // 第二遍：只提取目标文件的文本
            String mainContent = extractSingleEntryText(zipPath, bestEntryName);
            
            if (mainContent == null || mainContent.isEmpty()) {
                Log.w(TAG, "Failed to extract text from: " + bestEntryName);
                BatteryParseResult result = new BatteryParseResult();
                result.parseLog = "无法读取文件内容: " + bestEntryName;
                return result;
            }
            
            Log.d(TAG, "Extracted text length: " + mainContent.length());
            
            // 传给 Native 解析（限制文本大小）
            return parseTextSafe(mainContent);
            
        } catch (Throwable e) {
            Log.e(TAG, "ZIP parsing failed: " + e.getMessage(), e);
            BatteryParseResult result = new BatteryParseResult();
            result.parseLog = "ZIP解析失败: " + e.getClass().getSimpleName();
            return result;
        }
    }

    /**
     * 从 ZIP 中提取单个条目的文本内容
     * 限制大小不超过 MAX_ENTRY_SIZE
     */
    private static String extractSingleEntryText(String zipPath, String targetEntryName) {
        try (FileInputStream fis = new FileInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(targetEntryName)) {
                    // 读取内容，限制大小
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int len;
                    long totalRead = 0;
                    
                    while ((len = zis.read(buffer)) > 0) {
                        totalRead += len;
                        if (totalRead > MAX_ENTRY_SIZE) {
                            Log.w(TAG, "Entry too large, truncating at " + MAX_ENTRY_SIZE);
                            baos.write(buffer, 0, len - (int)(totalRead - MAX_ENTRY_SIZE));
                            break;
                        }
                        baos.write(buffer, 0, len);
                    }
                    
                    byte[] content = baos.toByteArray();
                    zis.closeEntry();
                    
                    try {
                        return new String(content, "UTF-8");
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to decode text: " + e.getMessage());
                        return null;
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extracting entry: " + e.getMessage());
        }
        return null;
    }

    /**
     * 安全解析文本 - v2.1.3 核心修复：预过滤 + 先Java后Native
     * 
     * 问题：std::regex_search 在10MB文本上运行20+次 → 卡死
     * 修复：Java预过滤只保留电池相关行 → ~100KB → 传给Native秒级完成
     */
    private static BatteryParseResult parseTextSafe(String text) {
        if (text == null || text.isEmpty()) {
            return new BatteryParseResult();
        }
        
        // 限制原始文本大小
        String safeText = text;
        if (text.length() > MAX_TEXT_SIZE) {
            Log.w(TAG, "Text too large (" + text.length() + "), truncating to " + MAX_TEXT_SIZE);
            safeText = text.substring(0, MAX_TEXT_SIZE);
        }
        
        // v2.1.3: 先用Java正则快速解析（对全文本，Java引擎更快）
        BatteryParseResult result = parseWithJavaFallback(safeText);
        
        // 如果Java解析成功，直接返回
        if (result.hasData) {
            Log.d(TAG, "Java parse succeeded, hasData=true");
            result.parseLog = "Java解析 | 文本:" + safeText.length();
            return result;
        }
        
        // v2.1.3: Java失败时才用Native，但先预过滤文本
        Log.d(TAG, "Java parse found no data, trying pre-filtered native");
        String filteredText = preFilterBatteryLines(safeText);
        Log.d(TAG, "Filtered text: " + safeText.length() + " → " + filteredText.length());
        
        if (filteredText.length() > MAX_FILTERED_SIZE) {
            filteredText = filteredText.substring(0, MAX_FILTERED_SIZE);
        }
        
        byte[] textBytes = filteredText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BatteryParseResult nativeResult = parseTextContent(textBytes);
        
        if (nativeResult.hasData) {
            result = nativeResult;
            result.parseLog = "Native解析 | 过滤后:" + filteredText.length() + " | 原始:" + safeText.length();
        } else {
            result.parseLog = "未找到电池信息 | 文本:" + safeText.length();
        }
        
        return result;
    }

    /**
     * 安全解析文本（从字节数组）- 带完整异常保护 + v2.1.3预过滤
     */
    private static BatteryParseResult parseTextSafe(byte[] content) {
        if (content == null || content.length == 0) {
            return new BatteryParseResult();
        }
        
        // 限制大小
        if (content.length > MAX_TEXT_SIZE) {
            Log.w(TAG, "Byte content too large (" + content.length + "), truncating");
            byte[] truncated = new byte[MAX_TEXT_SIZE];
            System.arraycopy(content, 0, truncated, 0, MAX_TEXT_SIZE);
            content = truncated;
        }
        
        // v2.1.3: 先转String然后用预过滤逻辑
        try {
            String text = new String(content, "UTF-8");
            return parseTextSafe(text);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode bytes: " + e.getMessage());
            return new BatteryParseResult();
        }
    }

    /**
     * 预过滤文本，只保留电池相关行 - v2.1.3 核心优化
     * 
     * 将10MB文本缩减到~100KB，Native regex不再卡死
     * 使用 StringBuilder + indexOf 高效扫描，避免 split 创建大量临时对象
     */
    private static String preFilterBatteryLines(String text) {
        if (text == null || text.isEmpty()) return "";
        
        StringBuilder filtered = new StringBuilder(Math.min(text.length(), 512 * 1024));
        int len = text.length();
        int lineStart = 0;
        
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || i == len - 1) {
                // 提取行
                int lineEnd = (c == '\n' || c == '\r') ? i : i + 1;
                String line = text.substring(lineStart, lineEnd);
                
                // 快速检查是否包含电池关键词
                if (containsBatteryKeyword(line)) {
                    filtered.append(line);
                    if (c == '\n') filtered.append('\n');
                }
                
                // 跳过 \r\n
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lineStart = i + 1;
            }
        }
        
        return filtered.toString();
    }
    
    /**
     * 快速检查行是否包含电池关键词
     */
    private static boolean containsBatteryKeyword(String line) {
        // 使用 indexOf 比正则快得多
        if (line.contains("healthd:")) return true;
        if (line.contains("fc=")) return true;
        if (line.contains("cc=")) return true;
        if (line.contains("MF_05")) return true;
        if (line.contains("MF_06")) return true;
        if (line.contains("MF_08")) return true;
        if (line.contains("QG_01")) return true;
        if (line.contains("QG_02")) return true;
        if (line.contains("QG_03")) return true;
        if (line.contains("ro.product.brand")) return true;
        if (line.contains("ro.product.model")) return true;
        if (line.contains("ro.product.manufacturer")) return true;
        if (line.contains("DesignCapacity")) return true;
        if (line.contains("full charge")) return true;
        if (line.contains("Min learned battery capacity")) return true;
        if (line.contains("battery cycle")) return true;
        if (line.contains("charge cycle")) return true;
        if (line.contains("battery temperature")) return true;
        return false;
    }

    /**
     * Java 正则兜底解析（当 Native 解析失败时使用）
     * v2.1.2: 只对已截断的文本进行解析，防止OOM
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

            // 提取当前容量 - healthd 格式 (fc= 单位是 uAh，需要除以 1000 转为 mAh)
            java.util.regex.Matcher fcMatcher = java.util.regex.Pattern.compile(
                "(?:healthd:.*?fc[=:\\s]+(\\d+)|\\bfc[=:\\s]+(\\d+))").matcher(text);
            if (fcMatcher.find()) {
                String fcVal = fcMatcher.group(1) != null ? fcMatcher.group(1) : fcMatcher.group(2);
                int fcInt = Integer.parseInt(fcVal);
                if (fcInt > 0) {
                    result.currentCapacityMah = fcInt / 1000; // uAh 转 mAh
                }
            }

            // 提取循环次数
            java.util.regex.Matcher ccMatcher = java.util.regex.Pattern.compile(
                "(?:healthd:.*?cc[=:\\s]+(\\d+)|\\bcc[=:\\s]+(\\d+))").matcher(text);
            if (ccMatcher.find()) {
                String ccVal = ccMatcher.group(1) != null ? ccMatcher.group(1) : ccMatcher.group(2);
                int ccInt = Integer.parseInt(ccVal);
                if (ccInt >= 0) {
                    result.cycleCount = ccInt;
                }
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

            // 温度
            java.util.regex.Matcher tempMatcher = java.util.regex.Pattern.compile(
                "healthd:\\s*battery\\s+.*?t[=:\\s]+(\\d+\\.?\\d*)").matcher(text);
            if (tempMatcher.find()) {
                result.temperatureCelsius = Float.parseFloat(tempMatcher.group(1));
            }
            if (result.temperatureCelsius == 0) {
                java.util.regex.Matcher tempMatcher2 = java.util.regex.Pattern.compile(
                    "battery[_ ]?temperature[:\\s]+(\\d+\\.?\\d*)\\s*°?C").matcher(text);
                if (tempMatcher2.find()) {
                    result.temperatureCelsius = Float.parseFloat(tempMatcher2.group(1));
                }
            }

            result.hasData = result.currentCapacityMah > 0 || result.cycleCount > 0 || result.designCapacityMah > 0;

            Log.d(TAG, "Java fallback parse: hasData=" + result.hasData +
                  " brand=" + result.brand + " model=" + result.model +
                  " currentCap=" + result.currentCapacityMah +
                  " cycleCount=" + result.cycleCount +
                  " designCap=" + result.designCapacityMah);

        } catch (Throwable e) {
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
            BatteryParseResult result = nativeParseBugreport(content, content.length);
            return result != null ? result : new BatteryParseResult();
        } catch (Throwable e) {
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
            BatteryHealthResult result = nativeCalculateHealth(parseResult);
            return result != null ? result : new BatteryHealthResult();
        } catch (Throwable e) {
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
        } catch (Throwable e) {
            return "解析摘要获取失败";
        }
    }

    /**
     * 完整分析流程
     */
    public static BatteryHealthResult analyzeFile(String filePath) {
        BatteryParseResult parseResult = parseFile(filePath);
        if (parseResult != null && parseResult.hasData) {
            BatteryHealthResult healthResult = calculateHealth(parseResult);
            return healthResult != null ? healthResult : new BatteryHealthResult();
        }
        return new BatteryHealthResult();
    }

    /**
     * 读取文件字节数组
     */
    private static byte[] readFileBytes(File file) throws IOException {
        long fileLen = file.length();
        if (fileLen > MAX_ENTRY_SIZE) {
            Log.w(TAG, "File too large: " + fileLen + ", truncating");
            fileLen = MAX_ENTRY_SIZE;
        }
        FileInputStream fis = new FileInputStream(file);
        byte[] content = new byte[(int) fileLen];
        int read = fis.read(content);
        fis.close();
        return read > 0 ? content : null;
    }

    // Native 方法声明
    private static native void nativeInit();
    private static native BatteryParseResult nativeParseBugreport(byte[] content, int length);
    private static native BatteryHealthResult nativeCalculateHealth(BatteryParseResult parseResult);
    private static native String nativeGetParseSummary(BatteryParseResult parseResult);
}