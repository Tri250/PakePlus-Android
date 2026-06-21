package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class PerformanceFragment extends Fragment {

    private TextView tvCpuUsage, tvMemoryUsage, tvPerformanceScore, tvStorageUsage;
    private ProgressBar progressCpu, progressMemory, progressScore, progressStorage;
    private TextView tvAppCpu, tvAppMemory, tvRuntime, tvForegroundService;
    private TextView tvGpuRenderer, tvOpenglVersion, tvVulkanVersion;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance, container, false);
        initViews(view);
        animateEntry(view);
        return view;
    }

    private void initViews(View view) {
        tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
        tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
        tvPerformanceScore = view.findViewById(R.id.tv_performance_score);
        tvStorageUsage = view.findViewById(R.id.tv_storage_usage);
        progressCpu = view.findViewById(R.id.progress_cpu);
        progressMemory = view.findViewById(R.id.progress_memory);
        progressScore = view.findViewById(R.id.progress_score);
        progressStorage = view.findViewById(R.id.progress_storage);

        tvAppCpu = view.findViewById(R.id.tv_app_cpu);
        tvAppMemory = view.findViewById(R.id.tv_app_memory);
        tvRuntime = view.findViewById(R.id.tv_runtime);
        tvForegroundService = view.findViewById(R.id.tv_foreground_service);

        tvGpuRenderer = view.findViewById(R.id.tv_gpu_renderer);
        tvOpenglVersion = view.findViewById(R.id.tv_opengl_version);
        tvVulkanVersion = view.findViewById(R.id.tv_vulkan_version);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicUpdate();
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadData();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private void loadData() {
        // CPU
        int cpuUsage = readCpuUsage();
        tvCpuUsage.setText(String.format(Locale.getDefault(), "%d%%", cpuUsage));
        UiAnimationHelper.animateProgressBar(progressCpu, cpuUsage);

        // Memory
        ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            int memUsage = (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem);
            tvMemoryUsage.setText(String.format(Locale.getDefault(), "%d%%", memUsage));
            UiAnimationHelper.animateProgressBar(progressMemory, memUsage);
        }

        // Storage
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long used = total - stat.getAvailableBytes();
        int storageUsage = (int) (used * 100 / total);
        tvStorageUsage.setText(String.format(Locale.getDefault(), "%d%%", storageUsage));
        UiAnimationHelper.animateProgressBar(progressStorage, storageUsage);

        // Performance score
        int score = calculatePerformanceScore(cpuUsage, mi.totalMem == 0 ? 50 : (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem), storageUsage);
        tvPerformanceScore.setText(String.valueOf(score));
        UiAnimationHelper.animateProgressBar(progressScore, score);

        // App info
        tvAppCpu.setText(String.format(Locale.getDefault(), "%.1f%%", getAppCpuUsage()));
        tvAppMemory.setText(formatSize(getAppMemoryUsage()));
        long runtimeMs = SystemClock.elapsedRealtime();
        tvRuntime.setText(formatDuration(runtimeMs));
        tvForegroundService.setText(getString(R.string.status_running));

        // GPU
        loadGpuInfo();
    }

    private int readCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long total = user + nice + system + idle;
                return (int) ((user + nice + system) * 100 / total);
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0;
    }

    private int calculatePerformanceScore(int cpu, int memory, int storage) {
        int baseScore = 100 - (cpu + memory + storage) / 3;
        return Math.max(0, Math.min(100, baseScore));
    }

    private long lastAppCpuTotal = 0;
    private long lastAppCpuTimestamp = 0;
    private float lastAppCpuPercent = 0f;

    private float getAppCpuUsage() {
        // 真实实现：读取 /proc/<pid>/stat 的 utime+stime，结合两次采样的差值计算占用率
        long now = System.nanoTime();
        long totalTime = readProcessCpuTicks(android.os.Process.myPid());
        if (totalTime <= 0) {
            return lastAppCpuPercent; // 读取失败时保持上一次结果
        }
        long clockTicksPerSecond = sysconfClockTicksPerSecond();
        if (lastAppCpuTotal > 0 && lastAppCpuTimestamp > 0 && clockTicksPerSecond > 0) {
            long deltaTicks = totalTime - lastAppCpuTotal;
            long deltaNanos = now - lastAppCpuTimestamp;
            if (deltaNanos > 0 && deltaTicks >= 0) {
                // 进程 CPU 时间（秒）= ticks / clockTicks；占用率 = 进程时间 / 实际墙钟时间 * 100
                double cpuSeconds = deltaTicks / (double) clockTicksPerSecond;
                double wallSeconds = deltaNanos / 1_000_000_000.0;
                double cores = Math.max(1L, Runtime.getRuntime().availableProcessors());
                double usage = (cpuSeconds / wallSeconds) * 100.0 / cores;
                if (usage >= 0 && usage <= 100) {
                    lastAppCpuPercent = (float) usage;
                }
            }
        }
        lastAppCpuTotal = totalTime;
        lastAppCpuTimestamp = now;
        return lastAppCpuPercent;
    }

    /**
     * 读取 /proc/<pid>/stat，提取 utime（第 14 字段）+ stime（第 15 字段）作为进程 CPU 时钟 tick 数。
     * 由于进程名可能包含空格或括号，使用最后一个 ')' 定位字段起始。
     */
    private long readProcessCpuTicks(int pid) {
        if (pid <= 0) return -1;
        File stat = new File("/proc/" + pid + "/stat");
        if (!stat.exists() || !stat.canRead()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(stat))) {
            String line = reader.readLine();
            if (line == null) return -1;
            int rParen = line.lastIndexOf(')');
            if (rParen < 0 || rParen >= line.length() - 1) return -1;
            // 进程名字段在第 2 个字段（"(" 和 ")" 包裹），因此从 ')' 后开始拆分
            String[] tokens = line.substring(rParen + 1).trim().split("\\s+");
            // tokens[0] = 状态（字段 3），向后偏移到字段 13 = utime，字段 14 = stime
            // 字段 3 = state → tokens[0]
            // 字段 4 = ppid → tokens[1]
            // 字段 5 = pgrp
            // 字段 6 = session
            // 字段 7 = tty_nr
            // 字段 8 = tpgid
            // 字段 9 = flags
            // 字段 10 = minflt
            // 字段 11 = cminflt
            // 字段 12 = majflt
            // 字段 13 = cmajflt
            // 字段 14 = utime
            // 字段 15 = stime
            if (tokens.length < 13) return -1;
            long utime;
            long stime;
            try {
                utime = Long.parseLong(tokens[11]);
                stime = Long.parseLong(tokens[12]);
            } catch (NumberFormatException nfe) {
                return -1;
            }
            return utime + stime;
        } catch (IOException io) {
            return -1;
        }
    }

    private long sysconfClockTicksPerSecond() {
        // 优先读 /proc/self/kernel/clocktick，兜底 100（Linux 默认）
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/kernel/clocktick"))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                String[] kv = line.split(":");
                if (kv.length == 2) {
                    return Long.parseLong(kv[1].trim());
                }
            }
        } catch (Exception ignored) {
        }
        return 100L;
    }

    private long getAppMemoryUsage() {
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);
        return memoryInfo.getTotalPss() * 1024L;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }

    private void loadGpuInfo() {
        try {
            EGL10 egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            egl.eglInitialize(display, new int[2]);
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            egl.eglChooseConfig(display, new int[]{EGL10.EGL_NONE}, configs, 1, numConfigs);
            EGLContext context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, new int[]{0x3098, 2, EGL10.EGL_NONE});
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, context);

            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            String version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);

            tvGpuRenderer.setText(renderer != null ? renderer : "Unknown");
            tvOpenglVersion.setText(version != null ? version : "Unknown");

            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            egl.eglDestroyContext(display, context);
            egl.eglTerminate(display);
        } catch (Exception e) {
            tvGpuRenderer.setText("Unknown");
            tvOpenglVersion.setText("Unknown");
        }
        // 真实检测 Vulkan 版本：优先系统属性 ro.hardware.vulkan，其次通过反射调用 Vk API
        tvVulkanVersion.setText(detectVulkanVersion());
    }

    /**
     * 检测设备 Vulkan 支持版本。优先读取系统属性 ro.hardware.vulkan 与 device_api，
     * 兜底为 "N/A"。
     */
    private String detectVulkanVersion() {
        // 1) 系统属性 ro.hardware.vulkan（部分设备厂商写入，形如 "1.1.128"）
        String v = readSystemProperty("ro.hardware.vulkan");
        if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
            return v;
        }
        // 2) Android device_api 中设备可选 API 列表（仅在 Android 11+ 可用）
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.content.pm.PackageManager pm = requireContext().getPackageManager();
                boolean hasVulkan1_1 = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, "1.1");
                boolean hasVulkan1_2 = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, "1.2");
                boolean hasVulkan1_3 = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, "1.3");
                if (hasVulkan1_3) return "1.3";
                if (hasVulkan1_2) return "1.2";
                if (hasVulkan1_1) return "1.1";
                boolean hasVulkan1_0 = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, "1.0");
                if (hasVulkan1_0) return "1.0";
            }
        } catch (Throwable ignored) {
        }
        // 3) 旧 API 仅有布尔支持
        try {
            android.content.pm.PackageManager pm = requireContext().getPackageManager();
            if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
                return "1.0";
            }
        } catch (Throwable ignored) {
        }
        return "N/A";
    }

    @android.annotation.SuppressLint("PrivateApi")
    private String readSystemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value != null ? value.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
