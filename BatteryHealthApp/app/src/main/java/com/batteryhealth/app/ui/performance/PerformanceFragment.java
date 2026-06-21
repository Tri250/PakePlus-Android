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
import android.util.Log;
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
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class PerformanceFragment extends Fragment {

    private static final String TAG = "PerformanceFragment";

    // EGL constant for context client version (OpenGL ES major version)
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private TextView tvCpuUsage, tvMemoryUsage, tvPerformanceScore, tvStorageUsage;
    private ProgressBar progressCpu, progressMemory, progressScore, progressStorage;
    private TextView tvAppCpu, tvAppMemory, tvRuntime, tvForegroundService;
    private TextView tvGpuRenderer, tvOpenglVersion, tvVulkanVersion;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    // CPU usage tracking: need two readings to compute delta
    private long prevCpuIdle = -1;
    private long prevCpuTotal = -1;

    private View rootView;

    // Cached GPU info (read once, not every 2 seconds)
    private volatile String gpuRenderer;
    private volatile String gpuOpenglVersion;
    private volatile boolean gpuInfoLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_performance, container, false);
        try {
            initViews(rootView);
            animateEntry(rootView);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView failed: " + e.getMessage(), e);
        }
        return rootView;
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
        try {
            Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
            view.startAnimation(fadeUp);
        } catch (Exception e) {
            Log.e(TAG, "animateEntry failed: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reset CPU baseline so first reading after resume doesn't use stale data
        prevCpuIdle = -1;
        prevCpuTotal = -1;
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicUpdate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicUpdate();
        rootView = null;
        tvCpuUsage = null;
        tvMemoryUsage = null;
        tvPerformanceScore = null;
        tvStorageUsage = null;
        progressCpu = null;
        progressMemory = null;
        progressScore = null;
        progressStorage = null;
        tvAppCpu = null;
        tvAppMemory = null;
        tvRuntime = null;
        tvForegroundService = null;
        tvGpuRenderer = null;
        tvOpenglVersion = null;
        tvVulkanVersion = null;
    }

    private boolean isViewAvailable() {
        return rootView != null && isAdded() && getActivity() != null;
    }

    private void startPeriodicUpdate() {
        stopPeriodicUpdate();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isViewAvailable()) return;
                loadData();
                if (isViewAvailable()) {
                    handler.postDelayed(this, 2000);
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    private void loadData() {
        if (!isViewAvailable()) return;

        try {
            // CPU
            int cpuUsage = readCpuUsage();
            if (cpuUsage < 0) cpuUsage = 0;
            if (cpuUsage > 100) cpuUsage = 100;
            if (tvCpuUsage != null) {
                tvCpuUsage.setText(String.format(Locale.getDefault(), "%d%%", cpuUsage));
                if (progressCpu != null) {
                    UiAnimationHelper.animateProgressBar(progressCpu, cpuUsage);
                }
            }

            // Memory - use ActivityManager for real memory info (no hardcoded defaults)
            int memUsage = 0;
            try {
                ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    if (mi.totalMem > 0) {
                        long used = mi.totalMem - mi.availMem;
                        if (used < 0) used = 0;
                        memUsage = (int) (used * 100L / mi.totalMem);
                        if (memUsage < 0) memUsage = 0;
                        if (memUsage > 100) memUsage = 100;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read memory info: " + e.getMessage());
            }
            if (tvMemoryUsage != null) {
                tvMemoryUsage.setText(String.format(Locale.getDefault(), "%d%%", memUsage));
                if (progressMemory != null) {
                    UiAnimationHelper.animateProgressBar(progressMemory, memUsage);
                }
            }

            // Storage
            int storageUsage = 0;
            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                long total = stat.getTotalBytes();
                long avail = stat.getAvailableBytes();
                if (total > 0) {
                    long used = total - avail;
                    if (used < 0) used = 0;
                    storageUsage = (int) (used * 100L / total);
                    if (storageUsage < 0) storageUsage = 0;
                    if (storageUsage > 100) storageUsage = 100;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read storage info: " + e.getMessage());
            }
            if (tvStorageUsage != null) {
                tvStorageUsage.setText(String.format(Locale.getDefault(), "%d%%", storageUsage));
                if (progressStorage != null) {
                    UiAnimationHelper.animateProgressBar(progressStorage, storageUsage);
                }
            }

            // Performance score
            int score = calculatePerformanceScore(cpuUsage, memUsage, storageUsage);
            if (tvPerformanceScore != null) {
                tvPerformanceScore.setText(String.valueOf(score));
                if (progressScore != null) {
                    UiAnimationHelper.animateProgressBar(progressScore, score);
                }
            }

            // App info
            if (tvAppCpu != null) {
                tvAppCpu.setText(String.format(Locale.getDefault(), "%.1f%%", getAppCpuUsage()));
            }
            if (tvAppMemory != null) {
                tvAppMemory.setText(formatSize(getAppMemoryUsage()));
            }
            if (tvRuntime != null) {
                long runtimeMs = SystemClock.elapsedRealtime();
                tvRuntime.setText(formatDuration(runtimeMs));
            }
            if (tvForegroundService != null) {
                tvForegroundService.setText(getString(R.string.status_running));
            }

            // GPU - only load once, then use cached values
            if (!gpuInfoLoaded) {
                loadGpuInfo();
                gpuInfoLoaded = true;
            } else {
                applyCachedGpuInfo();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadData failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates CPU usage from /proc/stat using two readings over a time interval.
     * CPU usage must be computed from delta: (delta_total - delta_idle) / delta_total.
     * On the first call, stores the baseline and returns 0.
     */
    private int readCpuUsage() {
        try {
            long[] current = readCpuStatLine();
            if (current == null) return 0;

            long currIdle = current[0];
            long currTotal = current[1];

            if (prevCpuIdle < 0 || prevCpuTotal < 0) {
                // First reading: store baseline, return 0
                prevCpuIdle = currIdle;
                prevCpuTotal = currTotal;
                return 0;
            }

            long deltaIdle = currIdle - prevCpuIdle;
            long deltaTotal = currTotal - prevCpuTotal;

            prevCpuIdle = currIdle;
            prevCpuTotal = currTotal;

            if (deltaTotal <= 0) return 0;
            long usagePct = ((deltaTotal - deltaIdle) * 100L) / deltaTotal;
            return (int) Math.max(0L, Math.min(100L, usagePct));
        } catch (Exception e) {
            Log.e(TAG, "readCpuUsage failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Reads the aggregate "cpu " line from /proc/stat.
     * Returns [idle, total] where idle = idle + iowait,
     * total = user + nice + system + idle + iowait + irq + softirq + steal.
     * Uses try-with-resources to prevent resource leaks.
     */
    private long[] readCpuStatLine() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                if (parts.length < 5) return null;
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

                long totalIdle = idle + iowait;
                long total = user + nice + system + idle + iowait + irq + softirq + steal;
                return new long[]{totalIdle, total};
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return null;
    }

    private int calculatePerformanceScore(int cpu, int memory, int storage) {
        int baseScore = 100 - (cpu + memory + storage) / 3;
        return Math.max(0, Math.min(100, baseScore));
    }

    private float getAppCpuUsage() {
        // Placeholder: getting real per-app CPU requires sampling /proc/self/stat across intervals
        return 0.5f;
    }

    private long getAppMemoryUsage() {
        try {
            Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memoryInfo);
            long pss = memoryInfo.getTotalPss();
            return Math.max(0L, pss) * 1024L;
        } catch (Exception e) {
            Log.e(TAG, "getAppMemoryUsage failed: " + e.getMessage());
            return 0L;
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long hours = ms / (1000L * 60 * 60);
        long minutes = (ms % (1000L * 60 * 60)) / (1000L * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }

    private void loadGpuInfo() {
        if (tvGpuRenderer == null || tvOpenglVersion == null) return;

        EGL10 egl = null;
        EGLDisplay display = EGL10.EGL_NO_DISPLAY;
        EGLContext context = EGL10.EGL_NO_CONTEXT;
        boolean contextCreated = false;
        boolean displayInitialized = false;

        try {
            egl = (EGL10) EGLContext.getEGL();
            display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY) {
                gpuRenderer = "Unknown";
                gpuOpenglVersion = "Unknown";
                return;
            }

            int[] version = new int[2];
            if (!egl.eglInitialize(display, version)) {
                gpuRenderer = "Unknown";
                gpuOpenglVersion = "Unknown";
                return;
            }
            displayInitialized = true;

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(display, new int[]{EGL10.EGL_NONE}, configs, 1, numConfigs)
                    || configs[0] == null) {
                gpuRenderer = "Unknown";
                gpuOpenglVersion = "Unknown";
                return;
            }

            context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE});
            if (context == null || context == EGL10.EGL_NO_CONTEXT) {
                gpuRenderer = "Unknown";
                gpuOpenglVersion = "Unknown";
                return;
            }
            contextCreated = true;

            if (!egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, context)) {
                gpuRenderer = "Unknown";
                gpuOpenglVersion = "Unknown";
                return;
            }

            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            String versionStr = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);

            gpuRenderer = renderer != null ? renderer : "Unknown";
            gpuOpenglVersion = versionStr != null ? versionStr : "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "loadGpuInfo failed: " + e.getMessage());
            if (gpuRenderer == null) gpuRenderer = "Unknown";
            if (gpuOpenglVersion == null) gpuOpenglVersion = "Unknown";
        } finally {
            // Ensure EGL resources are always released, even on exception
            if (egl != null && display != EGL10.EGL_NO_DISPLAY) {
                try {
                    if (contextCreated) {
                        egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE,
                                EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                        egl.eglDestroyContext(display, context);
                    }
                    if (displayInitialized) {
                        egl.eglTerminate(display);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "EGL cleanup failed: " + e.getMessage());
                }
            }
        }
        applyCachedGpuInfo();
    }

    private void applyCachedGpuInfo() {
        if (tvGpuRenderer != null && gpuRenderer != null) {
            tvGpuRenderer.setText(gpuRenderer);
        }
        if (tvOpenglVersion != null && gpuOpenglVersion != null) {
            tvOpenglVersion.setText(gpuOpenglVersion);
        }
        if (tvVulkanVersion != null) {
            tvVulkanVersion.setText("N/A");
        }
    }
}
