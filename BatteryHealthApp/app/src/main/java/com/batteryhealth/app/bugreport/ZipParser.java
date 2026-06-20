package com.batteryhealth.app.bugreport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 解析器（纯 Java 实现，避免引入 libzip）。
 *
 * <p>Android bugreport 实际是 ZIP，里面通常包含 main entry（如
 * {@code bugreport-XXXX.zip -> bugreport-XXXX.txt}，以及 dumpstate_*.txt）。本解析器：</p>
 * <ul>
 *   <li>枚举所有条目内容</li>
 *   <li>找出最长的纯文本条目作为主 bugreport 内容</li>
 *   <li>支持递归解析 1 层嵌套 ZIP（部分 ADB 抓取会双层压缩）</li>
 * </ul>
 */
public final class ZipParser {

    public static class Result {
        public boolean success;
        public String errorMessage;
        public final List<FileEntry> files = new ArrayList<>();
        public String mainBugreportContent;
        public int totalFilesExtracted;
        public int nestedZipsProcessed;

        public static class FileEntry {
            public final String name;
            public final String content;
            public FileEntry(String name, String content) {
                this.name = name;
                this.content = content;
            }
        }
    }

    private ZipParser() {}

    public static Result parseFromFile(java.io.File file) {
        Result r = new Result();
        r.success = false;
        if (file == null || !file.exists() || !file.canRead()) {
            r.errorMessage = "ZIP 文件不存在或不可读";
            return r;
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            return parseFromStream(is);
        } catch (IOException e) {
            r.errorMessage = "读取 ZIP 失败: " + e.getMessage();
            return r;
        }
    }

    public static Result parseFromInputStream(InputStream is) {
        return parseFromStream(is);
    }

    private static Result parseFromStream(InputStream is) {
        Result r = new Result();
        r.success = false;
        try {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
                zis.closeEntry();
                byte[] bytes = baos.toByteArray();
                // 二进制过滤：bugreport 内部是文本，忽略图像/二进制
                if (looksLikeBinary(bytes)) continue;
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                r.files.add(new Result.FileEntry(name, content));
            }
            r.totalFilesExtracted = r.files.size();
            r.mainBugreportContent = findMainBugreport(r.files);
            r.success = r.mainBugreportContent != null;
            if (!r.success) r.errorMessage = "未在 ZIP 中找到可识别的 bugreport 文本";
        } catch (IOException e) {
            r.errorMessage = "ZIP 解压失败: " + e.getMessage();
        }
        return r;
    }

    /** 启发式：bugreport 主条目通常体积最大且包含 ro.product.brand 字符串。 */
    private static String findMainBugreport(List<Result.FileEntry> files) {
        Result.FileEntry best = null;
        int bestScore = -1;
        for (Result.FileEntry f : files) {
            int score = f.content.length();
            if (f.name.toLowerCase().contains("bugreport")) score += 50_000;
            if (f.content.contains("ro.product.brand")) score += 30_000;
            if (f.content.contains("DumpState")) score += 10_000;
            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best != null ? best.content : null;
    }

    private static boolean looksLikeBinary(byte[] bytes) {
        if (bytes.length == 0) return false;
        int sample = Math.min(bytes.length, 2048);
        int suspicious = 0;
        for (int i = 0; i < sample; i++) {
            int b = bytes[i] & 0xFF;
            // 控制字符 + 高位
            if (b == 0) return true;
            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) suspicious++;
        }
        return suspicious * 10 > sample;
    }
}
