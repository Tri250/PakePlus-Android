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
        } catch (Throwable t) {
            Log.e(TAG, "Error inflating layout: " + t.getMessage(), t);
            return createErrorView(t);
        }
    }

    /**
     * 创建友好的错误页：标题 + 提示文案 + "重试" 按钮。
     */
    private View createErrorView(Throwable t) {
        final Context[] ctxHolder = new Context[1];
        try { ctxHolder[0] = getContext(); } catch (Throwable ignored) {}
        if (ctxHolder[0] == null) {
            try { ctxHolder[0] = requireActivity().getApplicationContext(); } catch (Throwable ignored2) {}
        }
        if (ctxHolder[0] == null) {
            try { ctxHolder[0] = getActivity() != null ? getActivity().getApplicationContext() : null; } catch (Throwable ignored3) {}
        }
        if (ctxHolder[0] == null) {
            android.widget.LinearLayout fallback = new android.widget.LinearLayout(android.app.Activity.class.cast(getActivity()) != null ? getActivity() : getContext());
            fallback.setOrientation(android.widget.LinearLayout.VERTICAL);
            fallback.setGravity(android.view.Gravity.CENTER);
            android.widget.TextView tv = new android.widget.TextView(fallback.getContext());
            tv.setText("界面加载失败，请重启应用");
            tv.setTextSize(16);
            fallback.addView(tv);
            return fallback;
        }
        final Context ctx = ctxHolder[0];

        android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (40 * ctx.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad * 2, pad, pad);
        try {
            root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_background));
        } catch (Throwable ignored) {
            root.setBackgroundColor(0xFFEFEFF4);
        }

        android.widget.TextView tvTitle = new android.widget.TextView(ctx);
        tvTitle.setText("界面加载失败");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        try {
            tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_label));
        } catch (Throwable ignored) {
            tvTitle.setTextColor(0xFF1C1C1E);
        }
        tvTitle.setGravity(android.view.Gravity.CENTER);
        root.addView(tvTitle);

        android.widget.TextView tvMsg = new android.widget.TextView(ctx);
        String detail = "";
        if (t != null) {
            detail = t.getClass().getSimpleName() + ": " + t.getMessage();
            Throwable cause = t.getCause();
            while (cause != null) {
                detail += "\nCaused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                cause = cause.getCause();
            }
        }
        tvMsg.setText("数据尚未就绪，请点击下方按钮重试。\n\n调试信息:\n" + detail);
        tvMsg.setTextSize(12);
        try {
            tvMsg.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_secondary_label));
        } catch (Throwable ignored) {
            tvMsg.setTextColor(0xFF3C3C43);
        }
        tvMsg.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams msgLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = (int) (12 * ctx.getResources().getDisplayMetrics().density);
        msgLp.leftMargin = pad / 2;
        msgLp.rightMargin = pad / 2;
        root.addView(tvMsg, msgLp);

        android.widget.Button btnRetry = new android.widget.Button(ctx);
        btnRetry.setText("重 试");
        btnRetry.setAllCaps(false);
        btnRetry.setTextSize(15);
        try {
            btnRetry.setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ios_blue));
        } catch (Throwable ignored) {
            btnRetry.setBackgroundColor(0xFF0A84FF);
        }
        btnRetry.setTextColor(0xFFFFFFFF);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        int btnH = (int) (44 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setMinHeight(btnH);
        int btnPad = (int) (28 * ctx.getResources().getDisplayMetrics().density);
        btnRetry.setPadding(btnPad, 0, btnPad, 0);
        root.addView(btnRetry, btnLp);

        btnRetry.setOnClickListener(v -> {
            try {
                View newView = onCreateView(LayoutInflater.from(ctx), (ViewGroup) v.getParent(), null);
                if (newView != null && v.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) v.getParent();
                    int idx = parent.indexOfChild(root);
                    parent.removeView(root);
                    parent.addView(newView, idx);
                    try { onViewCreated(newView, null); } catch (Throwable ignored) {}
                    try { animateCardsEntry(newView); } catch (Throwable ignored) {}
                }
            } catch (Throwable ex) {
                Log.e(TAG, "Retry failed: " + ex.getMessage(), ex);
            }
        });
        return root;
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
                    updatePerformanceData();
                    if (handler != null) {
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
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
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
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
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
        // CPU权重40%，内存权重35%，存储权重25%
        float cpuScore = Math.max(0, 100 - cpuUsage);
        float memScore = Math.max(0, 100 - memoryUsage);
        float storageScore = Math.max(0, 100 - storageUsage * 0.5f);
        float total = cpuScore * 0.4f + memScore * 0.35f + storageScore * 0.25f;
        return Math.round(total);
    }

    private String readGpuInfo() {
        try {
            String[] renderers = {
                "/sys/class/kgsl/kgsl-3d0/gpu_model",
                "/sys/class/devfreq/gpu0/governor",
                "/sys/devices/platform/soc/soc:qcom,kgsl-3d0/gpuclk"
            };
            for (String path : renderers) {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.canRead()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "GPU info not available");
        }
        return "不可用";
    }

    private void savePerformanceData(float cpuUsage, float memoryUsage, float storageUsage, int score) {
        new Thread(() -> {
            try {
                BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                if (app == null) return;
                AppDatabase db = app.getDatabase();
                if (db == null) return;

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
}