package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.utils.DeviceInfoManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能分析Fragment
 */
public class PerformanceFragment extends Fragment {
    
    private static final String TAG = "PerformanceFragment";

    // 性能评分权重：资源余量 60% + 硬件规格 40%
    private static final float CPU_USAGE_WEIGHT = 0.25f;
    private static final float MEMORY_USAGE_WEIGHT = 0.20f;
    private static final float STORAGE_USAGE_WEIGHT = 0.15f;
    private static final float HARDWARE_SCORE_MAX = 40f;
    
    private TextView tvCpuUsage;
    private TextView tvMemoryUsage;
    private ProgressBar progressCpu;
    private ProgressBar progressMemory;
    private TextView tvPerformanceScore;
    private ProgressBar progressScore;
    private TextView tvStorageUsage;
    private ProgressBar progressStorage;
    private TextView tvGpuInfo;
    
    private Handler handler;
    private Runnable updateTask;
    private ExecutorService executor;
    private boolean isRunning = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_performance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        android.widget.TextView errorView = new android.widget.TextView(requireContext());
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
            tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
            progressCpu = view.findViewById(R.id.progress_cpu);
            progressMemory = view.findViewById(R.id.progress_memory);
            tvPerformanceScore = view.findViewById(R.id.tv_performance_score);
            progressScore = view.findViewById(R.id.progress_score);
            tvStorageUsage = view.findViewById(R.id.tv_storage_usage);
            progressStorage = view.findViewById(R.id.progress_storage);
            tvGpuInfo = view.findViewById(R.id.tv_gpu_info);
            
            // 设置默认值
            setDefaultValues();
            animateCardsEntry(view);

            handler = new Handler(Looper.getMainLooper());
            executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("performance-io"));

            updateTask = new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;
                    updatePerformanceData();
                    if (handler != null) {
                        handler.postDelayed(this, 30000);
                    }
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private static final String PREFS_GLOBAL = "app_global_prefs";
    private static final String PREF_DISABLE_ANIMATIONS = "disable_animations";

    private boolean shouldSkipAnimations() {
        try {
            Context ctx = requireContext();
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_DISABLE_ANIMATIONS, false)) {
                return true;
            }
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long totalMemGb = mi.totalMem / (1024L * 1024L * 1024L);
                if (totalMemGb < 4) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Animation check skipped: " + e.getMessage());
        }
        return false;
    }

    private void animateCardsEntry(View view) {
        try {
            if (shouldSkipAnimations()) return;
            java.util.List<View> cards = new java.util.ArrayList<>();
            collectCards(view, cards);
            for (int i = 0; i < cards.size(); i++) {
                View child = cards.get(i);
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setStartDelay(i * 60L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                    .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Card entry animation skipped: " + e.getMessage());
        }
    }

    private void collectCards(View view, java.util.List<View> cards) {
        if (view instanceof com.google.android.material.card.MaterialCardView) {
            cards.add(view);
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectCards(group.getChildAt(i), cards);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        if (handler != null && updateTask != null) {
            handler.post(updateTask);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        if (handler != null && updateTask != null) {
            handler.removeCallbacks(updateTask);
        }
    }

    private void setDefaultValues() {
        if (tvCpuUsage != null) tvCpuUsage.setText("0%");
        if (tvMemoryUsage != null) tvMemoryUsage.setText("0%");
        if (progressCpu != null) progressCpu.setProgress(0);
        if (progressMemory != null) progressMemory.setProgress(0);
        if (tvPerformanceScore != null) tvPerformanceScore.setText("--");
        if (progressScore != null) progressScore.setProgress(0);
        if (tvStorageUsage != null) tvStorageUsage.setText("--");
        if (progressStorage != null) progressStorage.setProgress(0);
        if (tvGpuInfo != null) tvGpuInfo.setText("--");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (handler != null && updateTask != null) {
            handler.removeCallbacks(updateTask);
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }
    
    private void updatePerformanceData() {
        if (executor == null || executor.isShutdown()) return;

        executor.submit(() -> {
            try {
                final float cpuUsage = readCpuUsage();
                final float memoryUsage = readMemoryUsage();
                final float storageUsage = readStorageUsage();
                final int score = calculatePerformanceScore(cpuUsage, memoryUsage, storageUsage);
                final String gpuInfo = readGpuInfo();

                if (handler != null) {
                    handler.post(() -> updatePerformanceUi(cpuUsage, memoryUsage, storageUsage, score, gpuInfo));
                }

                savePerformanceData(cpuUsage, memoryUsage, storageUsage, score);
            } catch (Exception e) {
                Log.e(TAG, "Error updating performance data: " + e.getMessage());
            }
        });
    }

    private void updatePerformanceUi(float cpuUsage, float memoryUsage, float storageUsage, int score, String gpuInfo) {
        if (!isAdded()) return;
        try {
            if (tvCpuUsage != null) {
                tvCpuUsage.setText(String.format(Locale.getDefault(), "%.1f%%", cpuUsage));
            }
            if (progressCpu != null) {
                progressCpu.setProgress((int) Math.min(cpuUsage, 100));
            }

            if (tvMemoryUsage != null) {
                tvMemoryUsage.setText(String.format(Locale.getDefault(), "%.1f%%", memoryUsage));
            }
            if (progressMemory != null) {
                progressMemory.setProgress((int) Math.min(memoryUsage, 100));
            }

            if (tvStorageUsage != null) {
                tvStorageUsage.setText(String.format(Locale.getDefault(), "%.1f%%", storageUsage));
            }
            if (progressStorage != null) {
                progressStorage.setProgress((int) Math.min(storageUsage, 100));
            }

            if (tvPerformanceScore != null) {
                tvPerformanceScore.setText(getString(R.string.performance_score_format, score));
                if (score >= 80) {
                    tvPerformanceScore.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_green));
                } else if (score >= 60) {
                    tvPerformanceScore.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_yellow));
                } else {
                    tvPerformanceScore.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_red));
                }
            }
            if (progressScore != null) {
                progressScore.setProgress(score);
            }

            if (tvGpuInfo != null) {
                tvGpuInfo.setText(gpuInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating performance UI: " + e.getMessage());
        }
    }
    
    // 用于存储上一次CPU统计信息
    private long lastUser = 0;
    private long lastNice = 0;
    private long lastSystem = 0;
    private long lastIdle = 0;
    private long lastIowait = 0;
    private long lastIrq = 0;
    private long lastSoftirq = 0;
    private long lastSteal = 0;
    private boolean hasLastStat = false;
    private PerformanceData lastSavedPerformanceData;

    private float readCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();

            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                if (parts.length < 5) return 0;

                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

                if (!hasLastStat) {
                    // 第一次读取，只保存数据
                    lastUser = user;
                    lastNice = nice;
                    lastSystem = system;
                    lastIdle = idle;
                    lastIowait = iowait;
                    lastIrq = irq;
                    lastSoftirq = softirq;
                    lastSteal = steal;
                    hasLastStat = true;
                    return 0;
                }

                // 计算差值
                long userDiff = user - lastUser;
                long niceDiff = nice - lastNice;
                long systemDiff = system - lastSystem;
                long idleDiff = idle - lastIdle;
                long iowaitDiff = iowait - lastIowait;
                long irqDiff = irq - lastIrq;
                long softirqDiff = softirq - lastSoftirq;
                long stealDiff = steal - lastSteal;

                // 保存当前值
                lastUser = user;
                lastNice = nice;
                lastSystem = system;
                lastIdle = idle;
                lastIowait = iowait;
                lastIrq = irq;
                lastSoftirq = softirq;
                lastSteal = steal;

                // 计算使用率
                long totalDiff = userDiff + niceDiff + systemDiff + idleDiff + iowaitDiff + irqDiff + softirqDiff + stealDiff;
                long usedDiff = userDiff + niceDiff + systemDiff + irqDiff + softirqDiff + stealDiff;

                if (totalDiff > 0) {
                    return (usedDiff * 100.0f) / totalDiff;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading CPU usage: " + e.getMessage());
        }
        return 0;
    }
    
    private float readMemoryUsage() {
        try {
            if (getContext() == null) return 0;
            
            ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return 0;
            
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            long totalMemory = memoryInfo.totalMem;
            long availableMemory = memoryInfo.availMem;
            long usedMemory = totalMemory - availableMemory;
            
            if (totalMemory <= 0) return 0;
            
            return (usedMemory * 100.0f) / totalMemory;
        } catch (Exception e) {
            Log.e(TAG, "Error reading memory usage: " + e.getMessage());
        }
        return 0;
    }

    private float readStorageUsage() {
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long totalBlocks = statFs.getBlockCountLong();
            long availableBlocks = statFs.getAvailableBlocksLong();
            long usedBlocks = totalBlocks - availableBlocks;
            if (totalBlocks <= 0) return 0;
            return (usedBlocks * 100.0f) / totalBlocks;
        } catch (Exception e) {
            Log.e(TAG, "Error reading storage usage: " + e.getMessage());
        }
        return 0;
    }

    private int calculatePerformanceScore(float cpuUsage, float memoryUsage, float storageUsage) {
        // 资源余量：使用率越低越好
        float cpuScore = Math.max(0, 100 - cpuUsage);
        float memScore = Math.max(0, 100 - memoryUsage);
        float storageScore = Math.max(0, 100 - storageUsage * 0.5f);
        float resourceScore = cpuScore * CPU_USAGE_WEIGHT
                + memScore * MEMORY_USAGE_WEIGHT
                + storageScore * STORAGE_USAGE_WEIGHT;

        // 硬件规格分：基于总内存与 CPU 最大频率
        float hardwareScore = calculateHardwareScore();

        float total = resourceScore + hardwareScore;
        total = Math.max(0, Math.min(100, total));
        return Math.round(total);
    }

    private float calculateHardwareScore() {
        try {
            Context ctx = getContext();
            if (ctx == null) return 20;

            DeviceInfoManager dim = null;
            if (getActivity() instanceof MainActivity) {
                dim = ((MainActivity) getActivity()).getDeviceInfoManager();
            }
            if (dim == null) {
                dim = new DeviceInfoManager(ctx);
            }
            DeviceConfig config = dim.getDeviceConfig();

            // 内存分：0-20
            long totalMemMb = config.getTotalMemory();
            float memScore;
            if (totalMemMb >= 16384) memScore = 20;           // 16GB+
            else if (totalMemMb >= 12288) memScore = 18;      // 12GB
            else if (totalMemMb >= 8192) memScore = 15;       // 8GB
            else if (totalMemMb >= 6144) memScore = 12;       // 6GB
            else if (totalMemMb >= 4096) memScore = 10;       // 4GB
            else memScore = Math.max(5, totalMemMb / 1024f * 2.5f);

            // CPU 分：0-20
            int cpuFreqMax = config.getCpuFreqMax();
            float cpuScore;
            if (cpuFreqMax >= 3200) cpuScore = 20;
            else if (cpuFreqMax >= 2800) cpuScore = 17;
            else if (cpuFreqMax >= 2400) cpuScore = 14;
            else if (cpuFreqMax >= 2000) cpuScore = 11;
            else if (cpuFreqMax >= 1800) cpuScore = 8;
            else cpuScore = Math.max(3, cpuFreqMax / 400f);

            return memScore + cpuScore;
        } catch (Exception e) {
            return 20;
        }
    }

    private String readGpuInfo() {
        try {
            Context ctx = getContext();
            if (ctx != null) {
                DeviceInfoManager dim = new DeviceInfoManager(ctx);
                String gpu = dim.getGpuInfo();
                if (gpu != null && !gpu.isEmpty() && !gpu.equals(getString(R.string.status_not_recognized))) {
                    return gpu;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "GPU info not available");
        }
        return getString(R.string.status_not_recognized);
    }

    private void savePerformanceData(float cpuUsage, float memoryUsage, float storageUsage, int score) {
        Runnable saveTask = () -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

                // 采样去重：如果 CPU/内存/存储与上次记录差异 <1%，跳过写入
                if (lastSavedPerformanceData != null) {
                    float cpuDiff = Math.abs(cpuUsage - lastSavedPerformanceData.getCpuUsage());
                    float memDiff = Math.abs(memoryUsage - lastSavedPerformanceData.getMemoryUsed() / (float) lastSavedPerformanceData.getMemoryTotal() * 100f);
                    float storageDiff = Math.abs(storageUsage - (lastSavedPerformanceData.getMemoryTotal() > 0
                            ? lastSavedPerformanceData.getMemoryUsed() / (float) lastSavedPerformanceData.getMemoryTotal() * 100f : 0));
                    if (cpuDiff < 1f && memDiff < 1f && storageDiff < 1f) {
                        if (com.batteryhealth.app.BuildConfig.DEBUG) {
                            Log.d(TAG, "Performance data skipped (no significant change)");
                        }
                        return;
                    }
                }

                PerformanceData data = new PerformanceData();
                data.setCpuUsage(cpuUsage);
                data.setMemoryTotal(getTotalMemory());
                data.setMemoryUsed(getUsedMemory());
                data.setMemoryFree(getFreeMemory());
                data.setPerformanceScore(score);
                data.setHasIssue(score < 60);
                if (score < 60) {
                    data.setIssueType("performance_low");
                    data.setIssueDescription(getString(R.string.performance_low_issue));
                }

                db.performanceDataDao().insert(data);
                lastSavedPerformanceData = data;
            } catch (Exception e) {
                Log.e(TAG, "Error saving performance data: " + e.getMessage());
            }
        };
        if (executor != null && !executor.isShutdown()) {
            executor.submit(saveTask);
        } else {
            saveTask.run();
        }
    }

    private long getTotalMemory() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.totalMem / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long getUsedMemory() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return (mi.totalMem - mi.availMem) / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long getFreeMemory() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 命名线程工厂，用于为线程池中的线程设置可读名称与未捕获异常处理器。
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, ex) -> {
                Log.e("NamedThreadFactory", "Uncaught exception in thread " + thread.getName(), ex);
            });
            return t;
        }
    }
}