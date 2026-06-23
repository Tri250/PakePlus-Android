package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
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

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.PerformanceAnalyzer;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/**
 * 性能监控 Fragment。
 * 使用 DeviceInfoManager 获取真实 GPU/CPU 信息，
 * 使用 /proc/self.stat 读取真实应用 CPU 使用率。
 */
public class PerformanceFragment extends Fragment {

    private TextView tvCpuUsage, tvMemoryUsage, tvPerformanceScore, tvStorageUsage;
    private ProgressBar progressCpu, progressMemory, progressScore, progressStorage;
    private TextView tvAppCpu, tvAppMemory, tvRuntime, tvForegroundService;
    private TextView tvGpuRenderer, tvOpenglVersion, tvVulkanVersion;
    private TextView tvAnrCount, tvAnrSeverity, tvAnrMessage, tvPerformanceTips;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private DeviceInfoManager deviceInfoManager;
    private PerformanceAnalyzer performanceAnalyzer;

    // 用于计算应用 CPU 使用率的前次采样值
    private long lastCpuTime = 0;
    private long lastAppCpuTime = 0;

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

        tvAnrCount = view.findViewById(R.id.tv_anr_count);
        tvAnrSeverity = view.findViewById(R.id.tv_anr_severity);
        tvAnrMessage = view.findViewById(R.id.tv_anr_message);
        tvPerformanceTips = view.findViewById(R.id.tv_performance_tips);
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从 MainActivity 获取共享的 DeviceInfoManager
        if (getActivity() instanceof MainActivity) {
            deviceInfoManager = ((MainActivity) getActivity()).getDeviceInfoManager();
        }
        if (deviceInfoManager == null) {
            deviceInfoManager = new DeviceInfoManager(requireContext());
        }
        performanceAnalyzer = new PerformanceAnalyzer(requireContext());
        startPeriodicUpdate();
        loadAnrAnalysis();
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

        // App info - 使用真实的 /proc/self/stat 读取应用 CPU 使用率
        tvAppCpu.setText(String.format(Locale.getDefault(), "%.1f%%", getAppCpuUsage()));
        tvAppMemory.setText(formatSize(getAppMemoryUsage()));
        long runtimeMs = SystemClock.elapsedRealtime();
        tvRuntime.setText(formatDuration(runtimeMs));
        tvForegroundService.setText(getString(R.string.status_running));

        // GPU - 使用 DeviceInfoManager 获取真实 GPU 信息
        loadGpuInfo();
    }

    // 用于计算系统 CPU 使用率的前次采样值
    private long lastSysIdle = 0;
    private long lastSysTotal = 0;

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

                if (lastSysTotal > 0) {
                    long deltaTotal = total - lastSysTotal;
                    long deltaIdle = idle - lastSysIdle;
                    lastSysIdle = idle;
                    lastSysTotal = total;
                    if (deltaTotal > 0) {
                        return (int) ((deltaTotal - deltaIdle) * 100 / deltaTotal);
                    }
                }
                lastSysIdle = idle;
                lastSysTotal = total;
                return 0;
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0;
    }

    private int calculatePerformanceScore(int cpu, int memory, int storage) {
        int baseScore = 100 - (cpu + memory + storage) / 3;
        return Math.max(0, Math.min(100, baseScore));
    }

    /**
     * 通过读取 /proc/self/stat 计算应用真实 CPU 使用率。
     * 使用两次采样之间的差值计算百分比。
     */
    private float getAppCpuUsage() {
        try {
            // 读取进程总 CPU 时间
            long[] appTimes = readProcessCpuTimes();
            // 读取系统总 CPU 时间
            long[] sysTimes = readSystemCpuTimes();

            if (appTimes == null || sysTimes == null) return 0f;

            long appCpuTime = appTimes[0] + appTimes[1]; // utime + stime
            long sysCpuTime = 0;
            for (long t : sysTimes) sysCpuTime += t;

            if (lastCpuTime > 0 && lastAppCpuTime > 0) {
                long deltaApp = appCpuTime - lastAppCpuTime;
                long deltaSys = sysCpuTime - lastCpuTime;
                if (deltaSys > 0) {
                    float usage = (deltaApp * 100f) / deltaSys;
                    lastAppCpuTime = appCpuTime;
                    lastCpuTime = sysCpuTime;
                    return Math.min(usage, 100f);
                }
            }

            lastAppCpuTime = appCpuTime;
            lastCpuTime = sysCpuTime;
            return 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    /**
     * 读取 /proc/self/stat 获取进程 utime 和 stime（单位：时钟滴答）
     */
    private long[] readProcessCpuTimes() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                // utime = parts[13], stime = parts[14] (0-indexed)
                if (parts.length > 14) {
                    long utime = Long.parseLong(parts[13]);
                    long stime = Long.parseLong(parts[14]);
                    return new long[]{utime, stime};
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 读取 /proc/stat 获取系统总 CPU 时间
     */
    private long[] readSystemCpuTimes() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long[] times = new long[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    times[i - 1] = Long.parseLong(parts[i]);
                }
                return times;
            }
        } catch (Exception ignored) {
        }
        return null;
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

    /**
     * 使用 DeviceInfoManager 获取真实 GPU 信息，并检测 Vulkan 支持。
     */
    private void loadGpuInfo() {
        // GPU 渲染器名称 - 使用 DeviceInfoManager 的多路 fallback 逻辑
        if (deviceInfoManager != null) {
            String gpuInfo = deviceInfoManager.getGpuInfo();
            tvGpuRenderer.setText(gpuInfo != null && !gpuInfo.isEmpty() ? gpuInfo : "Unknown");
        } else {
            tvGpuRenderer.setText("Unknown");
        }

        // OpenGL ES 版本 - 通过 EGL 获取
        try {
            javax.microedition.khronos.egl.EGL10 egl = (javax.microedition.khronos.egl.EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
            javax.microedition.khronos.egl.EGLDisplay display = egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY);
            egl.eglInitialize(display, new int[2]);
            String version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);
            tvOpenglVersion.setText(version != null ? version : "Unknown");
            egl.eglTerminate(display);
        } catch (Exception e) {
            tvOpenglVersion.setText("Unknown");
        }

        // Vulkan 版本 - 通过系统属性检测
        tvVulkanVersion.setText(detectVulkanVersion());
    }

    /**
     * 检测设备 Vulkan API 支持版本。
     * 通过读取 ro.hardware.vulkan 和 /sys/class/kgsl/kgsl-3d0/gpu_model 等方式判断。
     */
    private String detectVulkanVersion() {
        try {
            // 1. 通过系统属性检测 Vulkan 版本
            String vulkanProp = getSystemProperty("ro.hardware.vulkan");
            if (vulkanProp != null && !vulkanProp.isEmpty()) {
                return "Vulkan " + vulkanProp;
            }

            // 2. 检查 libvulkan.so 是否存在
            String[] vulkanPaths = {
                    "/system/lib64/libvulkan.so",
                    "/system/lib/libvulkan.so",
                    "/vendor/lib64/libvulkan.so",
                    "/vendor/lib/libvulkan.so"
            };
            for (String path : vulkanPaths) {
                if (new File(path).exists()) {
                    // Check for Vulkan 1.3+ (most modern devices)
                    String vulkan13 = getSystemProperty("ro.hardware.vulkan.version");
                    if (vulkan13 != null && !vulkan13.isEmpty()) {
                        try {
                            int version = Integer.parseInt(vulkan13.trim(), 16);
                            int major = (version >> 22) & 0x3FF;
                            int minor = (version >> 12) & 0x3FF;
                            return "Vulkan " + major + "." + minor;
                        } catch (Exception ignored) {}
                    }
                    return "Vulkan 1.0+";
                }
            }

            // 3. 通过 ro.opengles.version 推断（3.x 以上通常支持 Vulkan）
            String glVersion = getSystemProperty("ro.opengles.version");
            if (glVersion != null && !glVersion.isEmpty()) {
                try {
                    int version = Integer.parseInt(glVersion.trim());
                    // OpenGL ES 3.2 = 196610, 通常对应 Vulkan 1.1+
                    // OpenGL ES 3.1 = 196609, 通常对应 Vulkan 1.0+
                    if (version >= 196610) return "Vulkan 1.1+";
                    if (version >= 196609) return "Vulkan 1.0+";
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.status_not_supported);
    }

    private String getSystemProperty(String propertyName) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, propertyName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadAnrAnalysis() {
        new Thread(() -> {
            PerformanceAnalyzer.AnrAnalysisResult anrResult = performanceAnalyzer.analyzeAnrLogs();
            PerformanceAnalyzer.PerformanceInsights insights = performanceAnalyzer.getPerformanceInsights();

            requireActivity().runOnUiThread(() -> {
                tvAnrCount.setText(String.valueOf(anrResult.ourAppAnrs));
                tvAnrSeverity.setText(anrResult.severity);
                tvAnrMessage.setText(anrResult.message);

                StringBuilder tipsBuilder = new StringBuilder();
                for (String tip : insights.suggestions) {
                    tipsBuilder.append(tip).append("\n");
                }
                tvPerformanceTips.setText(tipsBuilder.toString().trim());
            });
        }).start();
    }
}
