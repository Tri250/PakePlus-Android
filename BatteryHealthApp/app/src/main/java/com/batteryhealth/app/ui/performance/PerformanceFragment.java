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

    private float getAppCpuUsage() {
        return 0.5f; // Placeholder
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
        tvVulkanVersion.setText("N/A");
    }
}
