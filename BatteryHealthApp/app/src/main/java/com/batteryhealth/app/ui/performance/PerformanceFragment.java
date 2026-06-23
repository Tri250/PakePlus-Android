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
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.R;
import com.batteryhealth.app.ui.viewmodel.PerformanceViewModel;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.PerformanceAnalyzer;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

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

    private long lastCpuTime = 0;
    private long lastAppCpuTime = 0;
    private long lastSysIdle = 0;
    private long lastSysTotal = 0;

    private PerformanceViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_performance, container, false);
        initViews(view);
        initViewModel();
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

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(PerformanceViewModel.class);
        viewModel.getCpuUsage().observe(getViewLifecycleOwner(), this::updateCpuDisplay);
        viewModel.getMemoryUsage().observe(getViewLifecycleOwner(), this::updateMemoryDisplay);
    }

    private void updateCpuDisplay(Integer cpu) {
        if (cpu >= 0) {
            tvCpuUsage.setText(String.format(Locale.getDefault(), "%d%%", cpu));
            UiAnimationHelper.animateProgressBar(progressCpu, cpu);
        }
    }

    private void updateMemoryDisplay(Integer memory) {
        if (memory >= 0) {
            tvMemoryUsage.setText(String.format(Locale.getDefault(), "%d%%", memory));
            UiAnimationHelper.animateProgressBar(progressMemory, memory);
        }
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        deviceInfoManager = new DeviceInfoManager(requireContext());
        performanceAnalyzer = new PerformanceAnalyzer(requireContext());
        startPeriodicUpdate();
        loadAnrAnalysis();
        loadGpuInfo();
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
        viewModel.refreshData();

        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long used = total - stat.getAvailableBytes();
        int storageUsage = (int) (used * 100 / total);
        tvStorageUsage.setText(String.format(Locale.getDefault(), "%d%%", storageUsage));
        UiAnimationHelper.animateProgressBar(progressStorage, storageUsage);

        ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            int memUsage = (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem);
            int cpuUsage = readCpuUsage();
            int score = calculatePerformanceScore(cpuUsage, memUsage, storageUsage);
            tvPerformanceScore.setText(String.valueOf(score));
            UiAnimationHelper.animateProgressBar(progressScore, score);
        }

        tvAppCpu.setText(String.format(Locale.getDefault(), "%.1f%%", getAppCpuUsage()));
        tvAppMemory.setText(formatSize(getAppMemoryUsage()));
        long runtimeMs = SystemClock.elapsedRealtime();
        tvRuntime.setText(formatDuration(runtimeMs));
        tvForegroundService.setText(getString(R.string.status_running));
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
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int calculatePerformanceScore(int cpu, int memory, int storage) {
        int baseScore = 100 - (cpu + memory + storage) / 3;
        return Math.max(0, Math.min(100, baseScore));
    }

    private float getAppCpuUsage() {
        try {
            long[] appTimes = readProcessCpuTimes();
            long[] sysTimes = readSystemCpuTimes();

            if (appTimes == null || sysTimes == null) return 0f;

            long appCpuTime = appTimes[0] + appTimes[1];
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
        } catch (Exception e) {
        }
        return 0f;
    }

    private long[] readProcessCpuTimes() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
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

    private void loadGpuInfo() {
        if (deviceInfoManager != null) {
            String gpuInfo = deviceInfoManager.getGpuInfo();
            tvGpuRenderer.setText(gpuInfo != null && !gpuInfo.isEmpty() ? gpuInfo : "Unknown");
        } else {
            tvGpuRenderer.setText("Unknown");
        }

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

        tvVulkanVersion.setText(detectVulkanVersion());
    }

    private String detectVulkanVersion() {
        try {
            String vulkanProp = getSystemProperty("ro.hardware.vulkan");
            if (vulkanProp != null && !vulkanProp.isEmpty()) {
                return "Vulkan " + vulkanProp;
            }

            String[] vulkanPaths = {
                    "/system/lib64/libvulkan.so",
                    "/system/lib/libvulkan.so",
                    "/vendor/lib64/libvulkan.so",
                    "/vendor/lib/libvulkan.so"
            };
            for (String path : vulkanPaths) {
                if (new File(path).exists()) {
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

            String glVersion = getSystemProperty("ro.opengles.version");
            if (glVersion != null && !glVersion.isEmpty()) {
                try {
                    int version = Integer.parseInt(glVersion.trim());
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

            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    tvAnrCount.setText(String.valueOf(anrResult.ourAppAnrs));
                    tvAnrSeverity.setText(anrResult.severity);
                    tvAnrMessage.setText(anrResult.message);

                    StringBuilder tipsBuilder = new StringBuilder();
                    for (String tip : insights.suggestions) {
                        tipsBuilder.append(tip).append("\n");
                    }
                    tvPerformanceTips.setText(tipsBuilder.toString().trim());
                });
            }
        }).start();
    }
}