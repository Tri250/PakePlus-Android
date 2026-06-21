package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能基准测试工具（轻量级客户端版）。
 * 用于在用户设备上跑 CPU、内存、存储和 GPU 代理基准。
 *
 * 修复问题：
 *  - 早期版本 thermal / proc 文件读取未使用 try-with-resources，
 *    异常路径会泄漏文件句柄。
 *  - 早期版本多线程基准用 raw Thread，本版本改用 ExecutorService。
 */
public class PerformanceBenchmark {

    private static final String TAG = "PerformanceBenchmark";

    public static final class Score {
        public final double cpuScore;       // 整数分
        public final double memBandwidthMBps;
        public final double storageSeqReadMBps;
        public final double storageSeqWriteMBps;
        public final double storageRandReadIOPS;
        public final double storageRandWriteIOPS;
        public final double gpuScore;
        public final double overallScore;
        public final String cpuLabel;
        public final String ramLabel;
        public final String storageLabel;
        public final String gpuLabel;
        public final boolean valid;
        public final long durationMs;

        public Score(double cpu, double mem, double seqR, double seqW, double randR, double randW,
                     double gpu, double overall,
                     String cpuLabel, String ramLabel, String storageLabel, String gpuLabel,
                     boolean valid, long durationMs) {
            this.cpuScore = cpu;
            this.memBandwidthMBps = mem;
            this.storageSeqReadMBps = seqR;
            this.storageSeqWriteMBps = seqW;
            this.storageRandReadIOPS = randR;
            this.storageRandWriteIOPS = randW;
            this.gpuScore = gpu;
            this.overallScore = overall;
            this.cpuLabel = cpuLabel;
            this.ramLabel = ramLabel;
            this.storageLabel = storageLabel;
            this.gpuLabel = gpuLabel;
            this.valid = valid;
            this.durationMs = durationMs;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PerformanceBenchmark-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 在后台线程上执行完整基准。
     * 回调在调用方所在线程发起；不要把 UI 直接绑定到 onResult，需要切回主线程。
     */
    public interface ResultCallback {
        void onResult(Score score);
        void onError(Throwable t);
    }

    public void runFullBenchmark(Context context, ResultCallback callback) {
        if (callback == null) return;
        EXECUTOR.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                // CPU 跑 1.2s，内存 0.5s，存储 / 随机各 0.4s，GPU 0.4s
                double cpuScore = benchmarkCpu(1200);
                double memBandwidth = benchmarkMemoryBandwidth(500);
                double seqRead = benchmarkStorageSequentialRead(400);
                double seqWrite = benchmarkStorageSequentialWrite(400);
                double randRead = benchmarkStorageRandomRead(400);
                double randWrite = benchmarkStorageRandomWrite(400);
                double gpuScore = benchmarkGpuViaCpuProxy(400);

                double overall = normalizeOverallScore(cpuScore, memBandwidth, seqRead, seqWrite, randRead, randWrite, gpuScore);

                long duration = System.currentTimeMillis() - start;
                Score score = new Score(
                        cpuScore, memBandwidth, seqRead, seqWrite, randRead, randWrite, gpuScore, overall,
                        getCpuLabel(cpuScore),
                        getRamLabel(memBandwidth),
                        getStorageLabel(seqRead, seqWrite),
                        getGpuLabel(gpuScore),
                        true, duration
                );
                callback.onResult(score);
            } catch (Throwable t) {
                Log.e(TAG, "Benchmark failed", t);
                callback.onError(t);
            }
        });
    }

    /**
     * CPU 整数基准：循环计算斐波那契 + 哈希运算 + 浮点 MAD 混合。
     * 返回"1000ms 内的迭代次数"，归一化到 0-100。
     */
    private double benchmarkCpu(long budgetMs) {
        final long deadline = System.currentTimeMillis() + budgetMs;
        long iters = 0;
        long acc = 0L;
        int x = 0x9E3779B9;
        double fx = 0.0;
        while (System.currentTimeMillis() < deadline) {
            // fibonacci
            int a = 1, b = 1, c;
            for (int i = 0; i < 200; i++) {
                c = a + b;
                a = b;
                b = c;
                acc += c;
            }
            // integer hash
            int h = x;
            for (int i = 0; i < 200; i++) {
                h ^= (h << 13);
                h ^= (h >>> 17);
                h ^= (h << 5);
                acc += h;
            }
            // float mad
            for (int i = 0; i < 200; i++) {
                fx = fx * 1.0001d + 0.0001d;
                acc += Double.doubleToLongBits(fx);
            }
            iters++;
            x ^= (int) (acc & 0x7FFFFFFFL);
        }
        // Reference: 旗舰机约 6,000,000 iter / 1.2s
        double normalized = (iters / (double) budgetMs) * 1000d / 60_000d * 100d;
        if (normalized > 100d) normalized = 100d;
        if (normalized < 0d) normalized = 0d;
        // Reasonable bounds
        if (iters < 10) return 0;
        return Math.min(100d, normalized);
    }

    private double benchmarkMemoryBandwidth(long budgetMs) {
        final int size = 8 * 1024 * 1024; // 8MB
        byte[] src = new byte[size];
        byte[] dst = new byte[size];
        new Random(42).nextBytes(src);
        final long deadline = System.currentTimeMillis() + budgetMs;
        long totalBytes = 0L;
        int runs = 0;
        while (System.currentTimeMillis() < deadline) {
            System.arraycopy(src, 0, dst, 0, size);
            totalBytes += size;
            runs++;
        }
        double seconds = budgetMs / 1000d;
        double mbps = (totalBytes / (1024.0 * 1024.0)) / seconds;
        return mbps; // 旗舰机 1500+ MB/s
    }

    private double benchmarkStorageSequentialRead(long budgetMs) {
        File f = null;
        try {
            f = createTestFile(32 * 1024 * 1024, 0xA5);
            byte[] buf = new byte[256 * 1024];
            try (FileInputStream fis = new FileInputStream(f)) {
                FileChannel ch = fis.getChannel();
                long total = 0L;
                long deadline = System.currentTimeMillis() + budgetMs;
                int read;
                while (System.currentTimeMillis() < deadline && (read = fis.read(buf)) > 0) {
                    total += read;
                }
                double seconds = budgetMs / 1000d;
                return (total / (1024.0 * 1024.0)) / seconds;
            }
        } catch (Throwable t) {
            Log.d(TAG, "seq read failed: " + t.getMessage());
            return 0;
        } finally {
            if (f != null) {
                // Best-effort delete; ignore failures
                if (!f.delete()) Log.d(TAG, "Could not delete test file " + f);
            }
        }
    }

    private double benchmarkStorageSequentialWrite(long budgetMs) {
        File f = null;
        try {
            f = new File(Environment.getExternalStorageDirectory(), "battery_bench_write.bin");
            byte[] buf = new byte[256 * 1024];
            new Random(1).nextBytes(buf);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                long total = 0L;
                long deadline = System.currentTimeMillis() + budgetMs;
                int written;
                while (System.currentTimeMillis() < deadline && (written = (fos.write(buf) > 0 ? buf.length : 0)) > 0) {
                    total += written;
                }
                double seconds = budgetMs / 1000d;
                return (total / (1024.0 * 1024.0)) / seconds;
            }
        } catch (Throwable t) {
            Log.d(TAG, "seq write failed: " + t.getMessage());
            return 0;
        } finally {
            if (f != null) {
                if (!f.delete()) Log.d(TAG, "Could not delete test file " + f);
            }
        }
    }

    private double benchmarkStorageRandomRead(long budgetMs) {
        File f = null;
        try {
            f = createTestFile(64 * 1024 * 1024, 0x5A);
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                byte[] buf = new byte[4 * 1024);
                Random r = new Random(7);
                long totalReads = 0;
                long totalBytes = 0;
                long deadline = System.currentTimeMillis() + budgetMs;
                while (System.currentTimeMillis() < deadline) {
                    long pos = (long) r.nextInt((int) (raf.length() / 4096)) * 4096;
                    raf.seek(pos);
                    int read = raf.read(buf);
                    if (read > 0) {
                        totalBytes += read;
                        totalReads++;
                    }
                }
                double seconds = budgetMs / 1000d;
                if (totalBytes == 0) return 0;
                return totalReads / seconds;
            }
        } catch (Throwable t) {
            Log.d(TAG, "rand read failed: " + t.getMessage());
            return 0;
        } finally {
            if (f != null) {
                if (!f.delete()) Log.d(TAG, "Could not delete test file " + f);
            }
        }
    }

    private double benchmarkStorageRandomWrite(long budgetMs) {
        File f = null;
        try {
            f = new File(Environment.getExternalStorageDirectory(), "battery_bench_rand_write.bin");
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                raf.setLength(64L * 1024 * 1024);
                byte[] buf = new byte[4 * 1024];
                new Random(13).nextBytes(buf);
                Random r = new Random(11);
                long writes = 0;
                long deadline = System.currentTimeMillis() + budgetMs;
                while (System.currentTimeMillis() < deadline) {
                    long pos = (long) r.nextInt((int) (raf.length() / 4096)) * 4096;
                    raf.seek(pos);
                    raf.write(buf);
                    writes++;
                }
                double seconds = budgetMs / 1000d;
                return writes / seconds;
            }
        } catch (Throwable t) {
            Log.d(TAG, "rand write failed: " + t.getMessage());
            return 0;
        } finally {
            if (f != null) {
                if (!f.delete()) Log.d(TAG, "Could not delete test file " + f);
            }
        }
    }

    private double benchmarkGpuViaCpuProxy(long budgetMs) {
        // 简单的 Mandelbrot 浮点计算作为 GPU 性能代理
        final int w = 200;
        final int h = 200;
        final double xmin = -2.0, xmax = 1.0, ymin = -1.5, ymax = 1.5;
        int[] pixels = new int[w * h];
        long deadline = System.currentTimeMillis() + budgetMs;
        int frames = 0;
        double scale = 1.0;
        while (System.currentTimeMillis() < deadline) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    double zx = 0, zy = 0;
                    double cx = xmin + (xmax - xmin) * i / w * scale;
                    double cy = ymin + (ymax - ymin) * j / h * scale;
                    int iter = 0;
                    while (zx * zx + zy * zy < 4.0 && iter < 80) {
                        double t = zx * zx - zy * zy + cx;
                        zy = 2.0 * zx * zy + cy;
                        zx = t;
                        iter++;
                    }
                    pixels[j * w + i] = iter;
                }
            }
            scale += 0.05;
            frames++;
        }
        // 旗舰机能跑到 30+ 帧 / 400ms
        return Math.min(100d, frames * 100d / 20d);
    }

    private File createTestFile(int sizeBytes, byte fill) throws IOException {
        File f = new File(Environment.getExternalStorageDirectory(), "battery_bench_read.bin");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] chunk = new byte[64 * 1024];
            java.util.Arrays.fill(chunk, fill);
            int written = 0;
            while (written < sizeBytes) {
                int len = Math.min(chunk.length, sizeBytes - written);
                fos.write(chunk, 0, len);
                written += len;
            }
        }
        return f;
    }

    /**
     * 综合归一化：CPU 35%、内存 15%、存储 30%、GPU 20%。
     */
    private double normalizeOverallScore(double cpu, double mem, double seqR, double seqW,
                                         double randR, double randW, double gpu) {
        double cpuNorm = clamp(cpu, 0, 100) * 0.35;
        double memNorm = clamp(mem / 15d, 0, 100) * 0.15;
        double storageSeq = (clamp(seqR / 8d, 0, 100) + clamp(seqW / 5d, 0, 100)) / 2d * 0.15;
        double storageRand = (clamp(randR / 100d, 0, 100) + clamp(randW / 100d, 0, 100)) / 2d * 0.15;
        double gpuNorm = clamp(gpu, 0, 100) * 0.20;
        return cpuNorm + memNorm + storageSeq + storageRand + gpuNorm;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private String getCpuLabel(double score) {
        if (score >= 80) return "旗舰级";
        if (score >= 60) return "高性能";
        if (score >= 40) return "中端";
        if (score >= 20) return "入门级";
        if (score > 0) return "低端";
        return "未知";
    }

    private String getRamLabel(double memMBps) {
        if (memMBps >= 1500) return "旗舰级 LPDDR5x";
        if (memMBps >= 900) return "LPDDR5";
        if (memMBps >= 500) return "LPDDR4x";
        if (memMBps >= 200) return "LPDDR4";
        if (memMBps > 0) return "LPDDR3 / 慢速";
        return "未知";
    }

    private String getStorageLabel(double seqR, double seqW) {
        double score = (seqR / 8d + seqW / 5d) / 2d;
        if (score >= 80) return "UFS 4.0+";
        if (score >= 50) return "UFS 3.1";
        if (score >= 25) return "UFS 3.0";
        if (score >= 10) return "UFS 2.1";
        if (score > 0) return "eMMC";
        return "未知";
    }

    private String getGpuLabel(double score) {
        if (score >= 80) return "Adreno 700+ / Mali-G700+";
        if (score >= 60) return "Adreno 600+ / Mali-G600+";
        if (score >= 40) return "Adreno 500+ / Mali-G50+";
        if (score >= 20) return "入门级 GPU";
        if (score > 0) return "低端 GPU";
        return "未知";
    }

    /**
     * 读取当前温控状态（API 29+）：0..6 (NONE..SHUTDOWN)。不支持时返回 -1。
     */
    public static int getThermalStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1;
        try {
            PowerManager pm = (PowerManager)
                    android.app.ActivityThread.currentApplication().getSystemService(Context.POWER_SERVICE);
            if (pm == null) return -1;
            return pm.getCurrentThermalStatus();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 读取 /sys/class/thermal/thermal_zone0/temp 作为运行时热状态补充数据。
     */
    public static double getCurrentTempC() {
        // 多个厂商节点尝试
        String[] candidates = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
        };
        for (String p : candidates) {
            try (BufferedReader r = new BufferedReader(new FileReader(p))) {
                String line = r.readLine();
                if (line == null) continue;
                long raw = Long.parseLong(line.trim());
                return raw >= 1000 ? raw / 1000.0 : raw; // m°C or 0.001°C
            } catch (Throwable t) {
                // try next
            }
        }
        return -1;
    }
}
