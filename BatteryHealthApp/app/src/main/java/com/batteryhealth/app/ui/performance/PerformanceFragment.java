package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
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
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.PerformanceData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 性能分析Fragment
 */
public class PerformanceFragment extends Fragment {
    
    private static final String TAG = "PerformanceFragment";
    
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
        StringBuilder message = new StringBuilder();
        message.append("界面加载失败，请重启应用\n\n");
        message.append("错误类型: ").append(e.getClass().getSimpleName()).append("\n");
        message.append("错误信息: ").append(e.getMessage() != null ? e.getMessage() : "未知错误").append("\n\n");
        message.append("堆栈跟踪:\n");
        for (StackTraceElement element : e.getStackTrace()) {
            message.append(element.toString()).append("\n");
        }
        errorView.setText(message.toString());
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(14);
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

            updateTask = new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;

                    // 检查Fragment是否仍然附加到Activity
                    if (!isAdded() || isDetached() || getContext() == null) {
                        isRunning = false;
                        return;
                    }

                    updatePerformanceData();
                    if (handler != null && isAdded()) {
                        handler.postDelayed(this, 2000);
                    }
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void animateCardsEntry(View view) {
        try {
            if (view == null || !(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            animateViewGroupRecursive(root, 0);
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }

    private void animateViewGroupRecursive(android.view.ViewGroup parent, int depth) {
        if (parent == null) return;
        if (depth > 4) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            if (child.getId() == R.id.view_pager) continue;
            boolean shouldAnimate = (child instanceof com.google.android.material.card.MaterialCardView)
                    || depth == 1
                    || (depth == 0 && parent.getChildCount() > 1);
            if (shouldAnimate) {
                try {
                    child.setAlpha(0f);
                    child.setTranslationY(60f);
                    child.setScaleX(0.94f);
                    child.setScaleY(0.94f);
                    child.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(650)
                        .setStartDelay(i * 100L)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                        .start();
                } catch (Exception ignored) {}
            }
            if (child instanceof android.view.ViewGroup) {
                animateViewGroupRecursive((android.view.ViewGroup) child, depth + 1);
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
    }
    
    private void updatePerformanceData() {
        try {
            // 获取CPU使用率
            float cpuUsage = readCpuUsage();
            if (tvCpuUsage != null) {
                tvCpuUsage.setText(String.format("%.1f%%", cpuUsage));
            }
            if (progressCpu != null) {
                progressCpu.setProgress((int) Math.min(cpuUsage, 100));
            }
            
            // 获取内存使用率
            float memoryUsage = readMemoryUsage();
            if (tvMemoryUsage != null) {
                tvMemoryUsage.setText(String.format("%.1f%%", memoryUsage));
            }
            if (progressMemory != null) {
                progressMemory.setProgress((int) Math.min(memoryUsage, 100));
            }

            // 存储使用率
            float storageUsage = readStorageUsage();
            if (tvStorageUsage != null) {
                tvStorageUsage.setText(String.format("%.1f%%", storageUsage));
            }
            if (progressStorage != null) {
                progressStorage.setProgress((int) Math.min(storageUsage, 100));
            }

            // 性能评分
            int score = calculatePerformanceScore(cpuUsage, memoryUsage, storageUsage);
            if (tvPerformanceScore != null) {
                tvPerformanceScore.setText(score + " 分");
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

            // GPU信息
            if (tvGpuInfo != null) {
                String gpuInfo = readGpuInfo();
                tvGpuInfo.setText(gpuInfo);
            }

            // 保存性能数据到数据库
            savePerformanceData(cpuUsage, memoryUsage, storageUsage, score);
        } catch (Exception e) {
            Log.e(TAG, "Error updating performance data: " + e.getMessage());
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

    private float readCpuUsage() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();

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
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
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
        // CPU权重40%，内存权重35%，存储权重25%
        float cpuScore = Math.max(0, 100 - cpuUsage);
        float memScore = Math.max(0, 100 - memoryUsage);
        float storageScore = Math.max(0, 100 - storageUsage * 0.5f);
        float total = cpuScore * 0.4f + memScore * 0.35f + storageScore * 0.25f;
        return Math.round(total);
    }

    private String readGpuInfo() {
        java.io.BufferedReader reader = null;
        try {
            String[] renderers = {
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/class/devfreq/gpu0/governor",
                "/sys/devices/platform/soc/soc:qcom,kgsl-3d0/gpuclk"
            };
            for (String path : renderers) {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.canRead()) {
                    try {
                        reader = new java.io.BufferedReader(new java.io.FileReader(file));
                        String line = reader.readLine();
                        if (line != null && !line.isEmpty()) {
                            return line.trim();
                        }
                    } finally {
                        if (reader != null) {
                            try { reader.close(); } catch (Exception ignored) {}
                            reader = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "GPU info not available");
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return "不可用";
    }

    private void savePerformanceData(float cpuUsage, float memoryUsage, float storageUsage, int score) {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) {
                    Log.w(TAG, "App is null, cannot save performance data");
                    return;
                }
                AppDatabase db = app.getDatabase();
                if (db == null) {
                    Log.w(TAG, "Database is null, cannot save performance data");
                    return;
                }
                if (db.performanceDataDao() == null) {
                    Log.w(TAG, "PerformanceDataDao is null, cannot save performance data");
                    return;
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
                    data.setIssueDescription("综合性能评分低于60，建议关闭后台应用");
                }

                db.performanceDataDao().insert(data);
            } catch (Exception e) {
                Log.e(TAG, "Error saving performance data: " + e.getMessage());
            }
        }).start();
    }

    private long getTotalMemory() {
        try {
            if (getContext() == null) return 0;
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
            if (getContext() == null) return 0;
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
            if (getContext() == null) return 0;
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return mi.availMem / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        return 0;
    }
}