package com.batteryhealth.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 性能基准测试工具：CPU、内存带宽、存储 IO、GPU 渲染基准。
 *
 * 设计目标：
 *  1. 真实可执行：在主线程外运行，结果可重复。
 *  2. 跨设备可比：分数采用与安兔兔、鲁大师类似的对数或分段映射。
 *  3. 耗时可控：单测 ≤ 200ms，不阻塞 UI。
 */
public class PerformanceBenchmark {

    private static final String TAG = "PerformanceBenchmark";

    public static final class Result {
        public final long cpuSingleCoreScore;
        public final long cpuMultiCoreScore;
        public final long memoryBandwidthMBps;
        public final long storageReadMBps;
        public final long storageWriteMBps;
        public final long storageRandomReadIOPS;
        public final long storageRandomWriteIOPS;
        public final long gpuRenderFps;
        public final long gpuScore;
        public final long overallScore;

        public Result(long cpuSingleCoreScore, long cpuMultiCoreScore, long memoryBandwidthMBps,
                      long storageReadMBps, long storageWriteMBps, long storageRandomReadIOPS,
                      long storageRandomWriteIOPS, long gpuRenderFps, long gpuScore, long overallScore) {
            this.cpuSingleCoreScore = cpuSingleCoreScore;
            this.cpuMultiCoreScore = cpuMultiCoreScore;
            this.memoryBandwidthMBps = memoryBandwidthMBps;
            this.storageReadMBps = storageReadMBps;
            this.storageWriteMBps = storageWriteMBps;
            this.storageRandomReadIOPS = storageRandomReadIOPS;
            this.storageRandomWriteIOPS = storageRandomWriteIOPS;
            this.gpuRenderFps = gpuRenderFps;
            this.gpuScore = gpuScore;
            this.overallScore = overallScore;
        }
    }

    /**
     * 执行全部基准测试。在工作线程调用。
     */
    public static Result runFullBenchmark(Context context) {
        long start = System.currentTimeMillis();

        // CPU 性能：双精度浮点运算多轮
        int cores = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        long cpuScore = benchmarkCpu(cores);
        long cpuSingle = benchmarkCpu(1);
        long cpuMulti = benchmarkCpu(cores);

        // 内存带宽
        long memMBps = benchmarkMemoryBandwidth();

        // 存储 IO
        long readMBps = benchmarkStorageSequentialRead();
        long writeMBps = benchmarkStorageSequentialWrite();
        long randomReadIops = benchmarkStorageRandomRead();
        long randomWriteIops = benchmarkStorageRandomWrite();

        // GPU 渲染基准：基于 GLES 2.0 离屏渲染
        GpuBenchmark.Result gpuResult = GpuBenchmark.run();
        long gpuFps = gpuResult.fps;
        long gpuScore = gpuResult.score;

        // 综合分数：CPU 35% + 内存 20% + 存储 20% + GPU 25%（2026年校准，GPU权重提升）
        long overall = (long) (cpuMulti * 0.35
                + (memMBps * 0.6) * 0.2   // 10000 MBps ≈ 6000
                + (readMBps * 0.4 + writeMBps * 0.3 + (randomReadIops / 100) * 0.15) * 0.2
                + gpuScore * 0.25);
        overall = Math.max(1, Math.min(250000, overall));

        Log.d(TAG, "Benchmark finished in " + (System.currentTimeMillis() - start) + "ms: "
                + "cpu=" + cpuMulti + ", mem=" + memMBps + "MB/s, "
                + "r=" + readMBps + "MB/s w=" + writeMBps + "MB/s, "
                + "rr=" + randomReadIops + " iops, fps=" + gpuFps + ", overall=" + overall);

        return new Result(cpuSingle, cpuMulti, memMBps, readMBps, writeMBps,
                randomReadIops, randomWriteIops, gpuFps, gpuScore, overall);
    }

    /**
     * CPU 浮点基准：单/多核场景下的双精度浮点运算速度。
     * @return 分数（数值越大越好，类比 Geekbench 单核）
     */
    private static long benchmarkCpu(int threads) {
        int operations = 1_500_000; // 单轮运算量
        // Android 16 热节流检测：读取热区温度，高温时减少运算量以避免进一步降频
        try {
            java.io.BufferedReader thermalReader = new java.io.BufferedReader(
                    new java.io.FileReader("/sys/class/thermal/thermal_zone0/temp"));
            String tempStr = thermalReader.readLine();
            thermalReader.close();
            if (tempStr != null) {
                int tempMilliC = Integer.parseInt(tempStr.trim());
                float tempC = tempMilliC / 1000f;
                if (tempC > 45f) {
                    Log.d(TAG, "Thermal throttling detected: " + tempC + "°C, reducing CPU iterations");
                    operations = 800_000;
                }
            }
        } catch (Exception e) {
            // 无法读取热区温度，使用默认运算量
        }
        int rounds = 3;
        long best = 0;
        for (int r = 0; r < rounds; r++) {
            long start = System.nanoTime();
            Thread[] ts = new Thread[threads];
            final int opsPerThread = operations / threads;
            for (int t = 0; t < threads; t++) {
                ts[t] = new Thread(() -> {
                    double acc = 1.0;
                    for (int i = 0; i < opsPerThread; i++) {
                        acc = Math.sin(acc) * Math.cos(acc) + Math.sqrt(Math.abs(acc) + 1);
                    }
                    if (acc == 0) Log.d(TAG, "noop");
                }, "BenchCpu-" + t);
                ts[t].setDaemon(true);
                ts[t].start();
            }
            for (Thread t : ts) {
                try { t.join(2000); } catch (InterruptedException ignored) {}
            }
            long elapsed = System.nanoTime() - start;
            // 100ms 跑完 = 10000 分；线性归一化
            long score = (long) ((double) operations / (elapsed / 1_000_000.0) * 10.0);
            if (score > best) best = score;
        }
        return best;
    }

    /**
     * 内存带宽基准：分配大块数组，顺序写入与读取。
     * 实际数值受 GC 影响，仅供参考。
     */
    private static long benchmarkMemoryBandwidth() {
        int sizeMb = 16;
        int size = sizeMb * 1024 * 1024 / 4; // int 数组
        int[] a = new int[size];
        int[] b = new int[size];
        int rounds = 3;
        long best = 0;
        for (int r = 0; r < rounds; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < size; i++) a[i] = i;
            for (int i = 0; i < size; i++) b[i] = a[i] ^ 0x5A5A5A5A;
            long sum = 0;
            for (int i = 0; i < size; i++) sum += b[i];
            long elapsed = System.nanoTime() - start;
            if (sum == Long.MAX_VALUE) Log.d(TAG, "noop");
            // 16MB 写入 + 16MB 读取 ≈ 32MB
            long mbps = (long) (32.0 * 1_000_000_000.0 / elapsed);
            if (mbps > best) best = mbps;
        }
        return best;
    }

    /**
     * 顺序读带宽：基于内部存储的 8MB 文件。
     */
    private static long benchmarkStorageSequentialRead() {
        File cacheDir = context().getCacheDir();
        // Android 16 兼容：检查缓存目录是否存在且可写
        if (cacheDir == null || !cacheDir.exists() || !cacheDir.canWrite()) {
            return 0;
        }
        File f = new File(cacheDir, "bench_seq_read.bin");
        // 写入 8MB 数据
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            for (int i = 0; i < 8 * 1024 * 1024 / buf.length; i++) {
                fos.write(buf);
            }
            // Android 16：flush/sync 确保数据真正写入存储后再测量读取速度
            fos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            return 0;
        }
        long best = 0;
        for (int r = 0; r < 3; r++) {
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                FileChannel ch = raf.getChannel();
                ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
                long start = System.nanoTime();
                long total = 0;
                while (ch.read(buf) > 0) {
                    buf.clear();
                    total += 64 * 1024;
                    if (total >= 8L * 1024 * 1024) break;
                }
                long elapsed = System.nanoTime() - start;
                long mbps = (long) (total * 1_000_000_000L / elapsed / 1024 / 1024);
                if (mbps > best) best = mbps;
            } catch (IOException e) {
                return 0;
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return best;
    }

    /**
     * 顺序写带宽。
     */
    private static long benchmarkStorageSequentialWrite() {
        File cacheDir = context().getCacheDir();
        // Android 16 兼容：检查缓存目录是否存在且可写
        if (cacheDir == null || !cacheDir.exists() || !cacheDir.canWrite()) {
            return 0;
        }
        File f = new File(cacheDir, "bench_seq_write.bin");
        byte[] buf = new byte[64 * 1024];
        long best = 0;
        for (int r = 0; r < 3; r++) {
            long start = System.nanoTime();
            try (FileOutputStream fos = new FileOutputStream(f)) {
                for (int i = 0; i < 8 * 1024 * 1024 / buf.length; i++) {
                    fos.write(buf);
                }
                // Android 16：flush/sync 确保数据真正写入存储
                fos.flush();
                fos.getFD().sync();
            } catch (IOException e) {
                return 0;
            }
            long elapsed = System.nanoTime() - start;
            long mbps = (long) (8L * 1024 * 1024 * 1_000_000_000L / elapsed / 1024 / 1024);
            if (mbps > best) best = mbps;
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return best;
    }

    /**
     * 随机读 IOPS：4KB 块随机定位读。
     */
    private static long benchmarkStorageRandomRead() {
        File f = new File(context().getCacheDir(), "bench_rnd_read.bin");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            byte[] buf = new byte[4 * 1024];
            for (int i = 0; i < 16 * 1024; i++) fos.write(buf);
        } catch (IOException e) {
            return 0;
        }
        long total = 0;
        int operations = 4000;
        long start = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            for (int i = 0; i < operations; i++) {
                long pos = (Math.abs((i * 2654435761L) % 0x3FFF)) * 4L * 1024L;
                raf.seek(pos);
                byte[] b = new byte[4 * 1024];
                if (raf.read(b) > 0) total += b.length;
            }
        } catch (IOException e) {
            return 0;
        }
        long elapsedNs = System.nanoTime() - start;
        long iops = (long) (operations * 1_000_000_000L / elapsedNs);
        if (total == 0) return 0;
        return iops;
    }

    /**
     * 随机写 IOPS：4KB 块随机写。
     */
    private static long benchmarkStorageRandomWrite() {
        File f = new File(context().getCacheDir(), "bench_rnd_write.bin");
        int operations = 2000;
        long start = System.nanoTime();
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.setLength(64L * 1024 * 1024);
            byte[] b = new byte[4 * 1024];
            for (int i = 0; i < b.length; i++) b[i] = (byte) i;
            for (int i = 0; i < operations; i++) {
                long pos = (Math.abs((i * 1597334677L) % 0x3FFF)) * 4L * 1024L;
                raf.seek(pos);
                raf.write(b);
            }
            // Android 16：sync 确保数据刷入磁盘，获得更准确的 IOPS 测量
            raf.getFD().sync();
        } catch (IOException e) {
            return 0;
        }
        long elapsedNs = System.nanoTime() - start;
        return (long) (operations * 1_000_000_000L / elapsedNs);
    }

    private static long mapGpuScore(long fps) {
        // 60fps ≈ 1000，30fps ≈ 500，120fps ≈ 2000
        return fps * 17;
    }

    private static Context context() {
        try {
            Class<?> clazz = Class.forName("android.app.AppGlobals");
            return (Context) clazz.getMethod("getInitialApplication").invoke(null);
        } catch (Throwable t) {
            // AppGlobals 失败，尝试 ActivityThread.currentApplication() 回退
            try {
                Class<?> clazz = Class.forName("android.app.ActivityThread");
                return (Context) clazz.getMethod("currentApplication").invoke(null);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * 评分归一化：让不同档位的手机/平板得到合理的"综合性能分数"。
     * 2026年校准：旗舰 180k-250k，中端 80k-150k。
     * 输入为原始浮点，返回 0-250000 区间的整数。
     */
    public static int normalizeOverallScore(long raw) {
        if (raw <= 0) return 0;
        // 2026年档位映射：低 <30k，中端 80k-150k，高端 150k-180k，旗舰 180k-250k
        if (raw < 5000) return (int) (raw * 4);
        if (raw < 30000) return (int) (20000 + (raw - 5000) * 2.5);
        if (raw < 80000) return (int) (70000 + (raw - 30000) * 0.8);
        if (raw < 150000) return (int) (110000 + (raw - 80000) * 0.5);
        return (int) Math.min(250000, 150000 + (raw - 150000) * 0.3);
    }

    /**
     * 获取设备热状态，用于 UI 展示基准测试期间的热状态。
     * 读取 /sys/class/thermal/thermal_zone0/temp 获取温度。
     * @param context 上下文（保留参数以备未来使用 PowerManager API）
     * @return 热状态字符串："正常"(<35°C), "温暖"(35-40°C), "较热"(40-45°C), "过热"(>45°C)
     */
    public static String getThermalStatus(Context context) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/sys/class/thermal/thermal_zone0/temp"));
            String tempStr = reader.readLine();
            reader.close();
            if (tempStr != null) {
                int tempMilliC = Integer.parseInt(tempStr.trim());
                float tempC = tempMilliC / 1000f;
                if (tempC < 35f) return "正常";
                if (tempC < 40f) return "温暖";
                if (tempC < 45f) return "较热";
                return "过热";
            }
        } catch (Exception e) {
            // 无法读取热区温度
        }
        return "正常";
    }
}
