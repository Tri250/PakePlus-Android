package com.batteryhealth.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 电池分析器 - JNI 接口封装类
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

    /**
     * 初始化 native 库
     */
    public static void init() {
        if (libraryLoaded) {
            nativeInit();
        }
    }

    /**
     * 检查 native 库是否可用
     */
    public static boolean isNativeAvailable() {
        return libraryLoaded;
    }

    /**
     * 从文件内容解析电池数据
     * @param content 文件内容字节
     * @return 解析结果
     */
    public static BatteryParseResult parseBugreport(byte[] content) {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not available, returning empty result");
            return new BatteryParseResult();
        }
        try {
            return nativeParseBugreport(content, content.length);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing bugreport: " + e.getMessage());
            return new BatteryParseResult();
        }
    }

    /**
     * 从文件路径解析电池数据
     * @param filePath 文件路径
     * @return 解析结果
     */
    public static BatteryParseResult parseFile(String filePath) {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not available, returning empty result");
            return new BatteryParseResult();
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "File not found: " + filePath);
            return new BatteryParseResult();
        }

        String fileName = file.getName().toLowerCase();

        try {
            // ZIP 文件使用专门的解析方法
            if (fileName.endsWith(".zip")) {
                return nativeParseZipFile(filePath);
            }

            // 其他文件读取内容解析
            FileInputStream fis = new FileInputStream(file);
            byte[] content = new byte[(int) file.length()];
            int read = fis.read(content);
            fis.close();

            if (read > 0) {
                return nativeParseBugreport(content, read);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
        }

        return new BatteryParseResult();
    }

    /**
     * 计算电池健康度
     * @param parseResult 解析结果
     * @return 健康度结果
     */
    public static BatteryHealthResult calculateHealth(BatteryParseResult parseResult) {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not available, returning empty result");
            return new BatteryHealthResult();
        }

        if (parseResult == null || !parseResult.hasData) {
            Log.w(TAG, "No valid parse result, returning empty health result");
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
     * @param parseResult 解析结果
     * @return 摘要文本
     */
    public static String getParseSummary(BatteryParseResult parseResult) {
        if (!libraryLoaded || parseResult == null) {
            return "Native library not available";
        }
        try {
            return nativeGetParseSummary(parseResult);
        } catch (Exception e) {
            Log.e(TAG, "Error getting summary: " + e.getMessage());
            return "解析摘要获取失败";
        }
    }

    /**
     * 完整分析流程：解析 + 健康度计算
     * @param filePath 文件路径
     * @return 健康度结果
     */
    public static BatteryHealthResult analyzeFile(String filePath) {
        BatteryParseResult parseResult = parseFile(filePath);
        if (parseResult.hasData) {
            return calculateHealth(parseResult);
        }
        return new BatteryHealthResult();
    }

    /**
     * 完整分析流程：解析 + 健康度计算
     * @param content 文件内容
     * @return 健康度结果
     */
    public static BatteryHealthResult analyzeContent(byte[] content) {
        BatteryParseResult parseResult = parseBugreport(content);
        if (parseResult.hasData) {
            return calculateHealth(parseResult);
        }
        return new BatteryHealthResult();
    }

    // Native 方法声明
    private static native void nativeInit();
    private static native BatteryParseResult nativeParseBugreport(byte[] content, int length);
    private static native BatteryParseResult nativeParseZipFile(String filePath);
    private static native BatteryHealthResult nativeCalculateHealth(BatteryParseResult parseResult);
    private static native String nativeGetParseSummary(BatteryParseResult parseResult);
}