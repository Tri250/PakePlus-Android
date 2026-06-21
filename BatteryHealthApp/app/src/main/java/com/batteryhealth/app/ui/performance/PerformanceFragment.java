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
import com.batteryhealth.app.utils.PerformanceBenchmark;
import com.batteryhealth.app.utils.StateLayoutHelper;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 性能分析页面
 *
 * 功能：
 * 1. 实时展示 CPU、内存、存储使用率
 * 2. 运行性能基准测试，给出综合评分
 * 3. 展示 GPU 信息和热状态
 * 4. 检测应用卡顿隐患（基于系统负载）
 */
public class PerformanceFragment extends Fragment {

    private TextView tvCpuUsage, tvMemoryUsage, tvPerformanceScore, tvStorageUsage;
    private ProgressBar progressCpu, progressMemory, progressScore, progressStorage;
    private TextView tvAppCpu, tvAppMemory, tvRuntime, tvForegroundService;
    private TextView tvGpuRenderer, tvOpenglVersion, tvVulkanVersion;
    private TextView tvThermalStatus, tvBenchmarkScore, tvBenchmarkDetail;
    private View benchmarkSection;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private StateLayoutHelper stateLayoutHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance, container, false);
        initViews(view);
        // 初始化 StateLayoutHelper
        if (view instanceof ViewGroup) {
            ViewGroup scrollChild = (ViewGroup) view;
            if (scrollChild.getChildCount() > 0 && scrollChild.getChildAt(0) instanceof ViewGroup) {
                stateLayoutHelper = new StateLayoutHelper((ViewGroup) scrollChild.getChildAt(0));
                stateLayoutHelper.showLoading(null);
            }
        }
        animateEntry(view);
        runBenchmarkIfNeeded();
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

        tvThermalStatus = view.findViewById(R.id.tv_thermal_status);
        tvBenchmarkScore = view.findViewById(R.id.tv_benchmark_score);
        tvBenchmarkDetail = view.findViewById(R.id.tv_benchmark_detail);
        benchmarkSection = view.findViewById(R.id.benchmark_section);

        // 基准测试按钮
        View btnRunBenchmark = view.findViewById(R.id.btn_run_benchmark);
        if (btnRunBenchmark != null) {
            btnRunBenchmark.setOnClickListener(v -> runBenchmark());
        }
    }

    private void animateEntry(View view) {
        Context ctx = getContext();
        if (ctx == null) return;
        Animation fadeUp = AnimationUtils.loadAnimation(ctx, R.anim.fade_up);
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
        if (!isAdded() || getContext() == null) return;

        // 首次数据到达，显示内容
        if (stateLayoutHelper != null && stateLayoutHelper.getCurrentState() != StateLayoutHelper.State.CONTENT) {
            stateLayoutHelper.showContent();
        }

        // CPU
        int cpuUsage = readCpuUsage();
        safeSetText(tvCpuUsage, String.format(Locale.getDefault(), "%d%%", cpuUsage));
        if (progressCpu != null) UiAnimationHelper.animateProgressBar(progressCpu, cpuUsage);

        // Memory
        Context ctx = getContext();
        if (ctx == null) return;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        int memUsage = 0;
        if (am != null) {
            am.getMemoryInfo(mi);
            memUsage = mi.totalMem > 0 ? (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem) : 0;
            safeSetText(tvMemoryUsage, String.format(Locale.getDefault(), "%d%%", memUsage));
            if (progressMemory != null) UiAnimationHelper.animateProgressBar(progressMemory, memUsage);
        }

        // Storage
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long used = total - stat.getAvailableBytes();
        int storageUsage = total > 0 ? (int) (used * 100 / total) : 0;
        safeSetText(tvStorageUsage, String.format(Locale.getDefault(), "%d%%", storageUsage));
        if (progressStorage != null) UiAnimationHelper.animateProgressBar(progressStorage, storageUsage);

        // Performance score (real-time system score)
        int score = calculatePerformanceScore(cpuUsage, memUsage, storageUsage);
        safeSetText(tvPerformanceScore, String.valueOf(score));
        if (progressScore != null) UiAnimationHelper.animateProgressBar(progressScore, score);

        // App info
        safeSetText(tvAppCpu, String.format(Locale.getDefault(), "%.1f%%", getAppCpuUsage()));
        safeSetText(tvAppMemory, formatSize(getAppMemoryUsage()));
        long runtimeMs = SystemClock.elapsedRealtime();
        safeSetText(tvRuntime, formatDuration(runtimeMs));
        safeSetText(tvForegroundService, getString(R.string.status_running));

        // GPU
        loadGpuInfo();

        // Thermal status
        Context ctx2 = getContext();
        if (ctx2 != null) {
            safeSetText(tvThermalStatus, PerformanceBenchmark.getThermalStatus(ctx2));
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private void runBenchmarkIfNeeded() {
        // 可以在这里添加逻辑：如果超过7天未跑分，自动跑一次
    }

    private void runBenchmark() {
        if (benchmarkSection != null) {
            benchmarkSection.setVisibility(View.VISIBLE);
        }
        if (tvBenchmarkScore != null) {
            tvBenchmarkScore.setText("测试中…");
        }

        executor.execute(() -> {
            try {
                Context ctx = getContext();
                if (ctx == null) return;
                PerformanceBenchmark.Result result = PerformanceBenchmark.runFullBenchmark(ctx);
                int normalized = PerformanceBenchmark.normalizeOverallScore(result.overallScore);

                handler.post(() -> {
                    if (!isAdded()) return;
                    if (tvBenchmarkScore != null) {
                        tvBenchmarkScore.setText(String.valueOf(normalized));
                    }
                    if (tvBenchmarkDetail != null) {
                        tvBenchmarkDetail.setText(String.format(Locale.getDefault(),
                                "CPU单核 %d ｜ CPU多核 %d ｜ 内存 %d MB/s ｜ 存储读 %d MB/s ｜ 存储写 %d MB/s ｜ GPU %d fps",
                                result.cpuSingleCoreScore, result.cpuMultiCoreScore,
                                result.memoryBandwidthMBps, result.storageReadMBps,
                                result.storageWriteMBps, result.gpuRenderFps));
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isAdded() && tvBenchmarkScore != null) {
                        tvBenchmarkScore.setText("--");
                    }
                });
            }
        });
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
                return total > 0 ? (int) ((user + nice + system) * 100 / total) : 0;
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return 0;
    }

    private int calculatePerformanceScore(int cpu, int memory, int storage) {
        int baseScore = 100 - (cpu + memory + storage) / 3;
        return Math.max(0, Math.min(100, baseScore));
    }

    private float getAppCpuUsage() {
        // 通过读取 /proc/self/stat 获取应用 CPU 时间估算
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length > 16) {
                    long utime = Long.parseLong(parts[13]);
                    long stime = Long.parseLong(parts[14]);
                    // 简单估算，实际应该结合系统运行时间计算百分比
                    return (float) ((utime + stime) * 100.0 / SystemClock.elapsedRealtime());
                }
            }
        } catch (Exception ignored) {
        }
        return 0.5f;
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
        String renderer = "Unknown";
        String version = "Unknown";
        try {
            javax.microedition.khronos.egl.EGL10 egl = (javax.microedition.khronos.egl.EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
            javax.microedition.khronos.egl.EGLDisplay display = egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY);
            egl.eglInitialize(display, new int[2]);
            javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[1];
            int[] numConfigs = new int[1];
            egl.eglChooseConfig(display, new int[]{javax.microedition.khronos.egl.EGL10.EGL_NONE}, configs, 1, numConfigs);
            javax.microedition.khronos.egl.EGLContext context = egl.eglCreateContext(display, configs[0], javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT, new int[]{0x3098, 2, javax.microedition.khronos.egl.EGL10.EGL_NONE});
            egl.eglMakeCurrent(display, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, context);

            renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            version = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);

            egl.eglMakeCurrent(display, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE, javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT);
            egl.eglDestroyContext(display, context);
            egl.eglTerminate(display);
        } catch (Exception e) {
            // ignore
        }
        safeSetText(tvGpuRenderer, renderer != null ? renderer : "Unknown");
        safeSetText(tvOpenglVersion, version != null ? version : "Unknown");

        // Vulkan 版本检测：尝试加载 libvulkan.so
        String vulkanVersion = detectVulkanVersion();
        safeSetText(tvVulkanVersion, vulkanVersion);
    }

    private String detectVulkanVersion() {
        try {
            System.loadLibrary("vulkan");
            // 加载成功说明设备支持 Vulkan，尝试获取版本信息
            // 通过读取 /system/lib*/libvulkan.so 或检查 API 可用性
            // Android 7.0+ 支持 Vulkan，通过 android.os.Build.VERSION 判断版本等级
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                // 尝试通过反射获取 VkPhysicalDeviceProperties 版本
                try {
                    Class<?> vkInstanceClass = Class.forName("org.lwjgl.vulkan.VkInstance");
                    // LWJUK 不在 Android 上，回退到系统属性
                } catch (ClassNotFoundException ignored) {
                }

                // 通过系统属性获取 Vulkan 版本
                String vulkanApiVersion = getSystemProperty("ro.vulkan.api");
                String vulkanDriverVersion = getSystemProperty("ro.vulkan.version");

                if (vulkanApiVersion != null && !vulkanApiVersion.isEmpty()) {
                    return "Vulkan " + vulkanApiVersion;
                }
                if (vulkanDriverVersion != null && !vulkanDriverVersion.isEmpty()) {
                    return "Vulkan " + vulkanDriverVersion;
                }

                // 尝试解析 /sys 类路径或 gfxinfo
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader("/sys/class/kgsl/kgsl-3d0/gpu_model"));
                    String gpuModel = reader.readLine();
                    reader.close();
                    if (gpuModel != null && !gpuModel.isEmpty()) {
                        return "Vulkan 1.x (" + gpuModel.trim() + ")";
                    }
                } catch (Exception ignored) {
                }

                return "Vulkan 支持 (Android 7.0+)";
            } else {
                return "不支持 (Android < 7.0)";
            }
        } catch (UnsatisfiedLinkError e) {
            return "不支持 (libvulkan.so 未找到)";
        } catch (Exception e) {
            return "检测失败";
        }
    }

    private String getSystemProperty(String key) {
        try {
            Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = systemPropertiesClass.getMethod("get", String.class);
            return (String) getMethod.invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicUpdate();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
