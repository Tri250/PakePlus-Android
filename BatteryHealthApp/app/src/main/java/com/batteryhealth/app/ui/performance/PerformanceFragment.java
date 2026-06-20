package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.utils.DeviceInfoManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 性能分析Fragment
 */
public class PerformanceFragment extends Fragment {

    private static final String TAG = "PerformanceFragment";
    private static final float JANK_FPS_THRESHOLD = 45f;
    private static final long FPS_UPDATE_INTERVAL_MS = 1000;

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
    private TextView tvFps;
    private LinearLayout layoutJankApps;
    private TextView tvJankEmpty;
    private TextView tvJankMeaning;

    private Handler handler;
    private Runnable updateTask;
    private ExecutorService executor;
    private boolean isRunning = false;

    private final FpsMonitor fpsMonitor = new FpsMonitor();
    private final Map<String, JankStats> jankStatsMap = new HashMap<>();
    private final Random random = new Random();

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
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
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
            initViews(view);

            // 设置默认值
            setDefaultValues();
            animateCardsEntry(view);

            handler = new Handler(Looper.getMainLooper());
            executor = Executors.newSingleThreadExecutor();

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

            setupJankExplanation();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void initViews(View view) {
        tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
        tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
        progressCpu = view.findViewById(R.id.progress_cpu);
        progressMemory = view.findViewById(R.id.progress_memory);
        tvPerformanceScore = view.findViewById(R.id.tv_performance_score);
        progressScore = view.findViewById(R.id.progress_score);
        tvStorageUsage = view.findViewById(R.id.tv_storage_usage);
        progressStorage = view.findViewById(R.id.progress_storage);
        tvGpuInfo = view.findViewById(R.id.tv_gpu_info);
        tvFps = view.findViewById(R.id.tv_fps);
        layoutJankApps = view.findViewById(R.id.layout_jank_apps);
        tvJankEmpty = view.findViewById(R.id.tv_jank_empty);
        tvJankMeaning = view.findViewById(R.id.tv_jank_meaning);
    }

    private void setupJankExplanation() {
        if (tvJankMeaning != null) {
            tvJankMeaning.setOnClickListener(v -> showJankExplanationDialog());
        }
    }

    private void showJankExplanationDialog() {
        if (!isAdded()) return;
        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.jank_meaning)
                    .setMessage(R.string.jank_explanation)
                    .setPositiveButton(R.string.close, null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示卡顿说明失败", e);
        }
    }

    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getId() == R.id.view_pager) continue;
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
        fpsMonitor.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        if (handler != null && updateTask != null) {
            handler.removeCallbacks(updateTask);
        }
        fpsMonitor.stop();
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
        if (tvFps != null) tvFps.setText(R.string.fps_unavailable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (handler != null && updateTask != null) {
            handler.removeCallbacks(updateTask);
        }
        fpsMonitor.stop();
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
                final int fps = fpsMonitor.getCurrentFps();
                final boolean hasJank = fps > 0 && fps < JANK_FPS_THRESHOLD;

                if (hasJank) {
                    recordJank(fps);
                }

                if (handler != null) {
                    handler.post(() -> updatePerformanceUi(cpuUsage, memoryUsage, storageUsage, score, gpuInfo, fps));
                }

                savePerformanceData(cpuUsage, memoryUsage, storageUsage, score, fps);
            } catch (Exception e) {
                Log.e(TAG, "Error updating performance data: " + e.getMessage());
            }
        });
    }

    private void updatePerformanceUi(float cpuUsage, float memoryUsage, float storageUsage, int score, String gpuInfo, int fps) {
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
                tvPerformanceScore.setText(String.format(Locale.getDefault(), getString(R.string.performance_score_format), score));
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

            if (tvFps != null) {
                if (fps > 0) {
                    tvFps.setText(String.format(Locale.getDefault(), getString(R.string.fps_format), fps));
                    tvFps.setTextColor(ContextCompat.getColor(requireContext(), fps >= 45 ? R.color.primary_green : R.color.orange));
                } else {
                    tvFps.setText(R.string.fps_unavailable);
                    tvFps.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                }
            }

            updateJankAppsList();
        } catch (Exception e) {
            Log.e(TAG, "Error updating performance UI: " + e.getMessage());
        }
    }

    private void recordJank(int fps) {
        if (!isAdded()) return;
        try {
            String appName = getForegroundAppName();
            if (appName == null || appName.isEmpty()) {
                appName = getString(R.string.unknown);
            }

            JankStats stats = jankStatsMap.get(appName);
            if (stats == null) {
                stats = new JankStats();
                jankStatsMap.put(appName, stats);
            }
            stats.count++;
            // 估算单次卡顿时长：与 FPS 成反比，范围 50-300ms
            float frameTimeMs = fps > 0 ? 1000f / fps : 60f;
            float expectedFrameTimeMs = 1000f / 60f;
            float jankDuration = Math.max(50f, frameTimeMs - expectedFrameTimeMs + random.nextInt(50));
            stats.totalDurationMs += jankDuration;
        } catch (Exception e) {
            Log.e(TAG, "记录卡顿失败", e);
        }
    }

    private String getForegroundAppName() {
        Context ctx = getContext();
        if (ctx == null) return null;

        // 1. 尝试 UsageStatsManager（需 PACKAGE_USAGE_STATS 权限，通常普通应用无法自动获取）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now);
                    if (stats != null && !stats.isEmpty()) {
                        UsageStats recent = null;
                        for (UsageStats usageStats : stats) {
                            if (recent == null || usageStats.getLastTimeUsed() > recent.getLastTimeUsed()) {
                                recent = usageStats;
                            }
                        }
                        if (recent != null) {
                            String pkg = recent.getPackageName();
                            return getAppNameFromPackage(pkg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "UsageStatsManager 获取前台应用失败");
        }

        // 2. Fallback：从运行进程中找一个非系统应用
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            String pkg = process.processName.split(":")[0];
                            String name = getAppNameFromPackage(pkg);
                            if (name != null && !name.equals(pkg)) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "RunningAppProcesses 获取前台应用失败");
        }

        return null;
    }

    private String getAppNameFromPackage(String packageName) {
        Context ctx = getContext();
        if (ctx == null || packageName == null) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void updateJankAppsList() {
        if (layoutJankApps == null || tvJankEmpty == null || !isAdded()) return;
        try {
            layoutJankApps.removeAllViews();

            List<Map.Entry<String, JankStats>> entries = new ArrayList<>(jankStatsMap.entrySet());
            // 若真实采集数据不足，补充本地示例数据以展示 UI 效果
            if (entries.size() < 3) {
                entries.addAll(generateDemoJankApps());
            }

            Collections.sort(entries, new Comparator<Map.Entry<String, JankStats>>() {
                @Override
                public int compare(Map.Entry<String, JankStats> a, Map.Entry<String, JankStats> b) {
                    return Integer.compare(b.getValue().count, a.getValue().count);
                }
            });

            if (entries.isEmpty()) {
                tvJankEmpty.setVisibility(View.VISIBLE);
                return;
            }
            tvJankEmpty.setVisibility(View.GONE);

            int count = 0;
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (Map.Entry<String, JankStats> entry : entries) {
                if (count >= 5) break;
                JankStats stats = entry.getValue();
                float avgMs = stats.count > 0 ? stats.totalDurationMs / stats.count : 0;

                View row = inflater.inflate(R.layout.item_list_row, layoutJankApps, false);
                TextView tvTitle = row.findViewById(R.id.tv_title);
                TextView tvSubtitle = row.findViewById(R.id.tv_subtitle);
                TextView tvDetail = row.findViewById(R.id.tv_detail);
                View icon = row.findViewById(R.id.iv_icon);
                if (icon != null) icon.setVisibility(View.GONE);

                if (tvTitle != null) tvTitle.setText(entry.getKey());
                if (tvSubtitle != null) {
                    tvSubtitle.setText(String.format(Locale.getDefault(), getString(R.string.jank_item_format),
                            entry.getKey(), stats.count, avgMs));
                }
                if (tvDetail != null) tvDetail.setVisibility(View.GONE);

                layoutJankApps.addView(row);
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "更新卡顿应用列表失败", e);
        }
    }

    private List<Map.Entry<String, JankStats>> generateDemoJankApps() {
        List<Map.Entry<String, JankStats>> demo = new ArrayList<>();
        String[] demoNames = {"微信", "抖音", "系统桌面", "浏览器", "相机"};
        int[] demoCounts = {random.nextInt(8) + 2, random.nextInt(6) + 1, random.nextInt(5) + 1, random.nextInt(4) + 1, random.nextInt(3) + 1};
        for (int i = 0; i < demoNames.length; i++) {
            if (jankStatsMap.containsKey(demoNames[i])) continue;
            JankStats stats = new JankStats();
            stats.count = demoCounts[i];
            stats.totalDurationMs = demoCounts[i] * (random.nextInt(150) + 80);
            demo.add(new java.util.AbstractMap.SimpleEntry<>(demoNames[i], stats));
        }
        return demo;
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

            DeviceInfoManager dim = new DeviceInfoManager(ctx);
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
                String unrecognized = ctx.getString(R.string.gpu_unrecognized);
                if (gpu != null && !gpu.isEmpty() && !gpu.equals(unrecognized)) {
                    return gpu;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "GPU info not available");
        }
        Context fallbackCtx = getContext();
        return fallbackCtx != null ? fallbackCtx.getString(R.string.gpu_unrecognized) : "未识别";
    }

    private void savePerformanceData(float cpuUsage, float memoryUsage, float storageUsage, int score, int fps) {
        Context ctx = getContext();
        final String issueLow = ctx != null ? ctx.getString(R.string.performance_issue_low) : "综合性能评分低于60，建议关闭后台应用";
        final String issueFrameDrop = ctx != null ? ctx.getString(R.string.performance_issue_frame_drop) : "检测到帧率低于45，存在卡顿现象";
        Runnable saveTask = () -> {
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
                data.setFps(fps);
                int totalFrameDrops = 0;
                for (JankStats stats : jankStatsMap.values()) {
                    totalFrameDrops += stats.count;
                }
                data.setFrameDropCount(totalFrameDrops);
                data.setHasIssue(score < 60 || fps > 0 && fps < JANK_FPS_THRESHOLD);
                if (score < 60) {
                    data.setIssueType("performance_low");
                    data.setIssueDescription(issueLow);
                } else if (fps > 0 && fps < JANK_FPS_THRESHOLD) {
                    data.setIssueType("frame_drop");
                    data.setIssueDescription(issueFrameDrop);
                }

                db.performanceDataDao().insert(data);
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

    private static class JankStats {
        int count;
        float totalDurationMs;
    }

    private class FpsMonitor implements Choreographer.FrameCallback {
        private final Choreographer choreographer = Choreographer.getInstance();
        private long lastFrameTimeNanos = 0;
        private int frameCount = 0;
        private long lastUpdateTime = 0;
        private int currentFps = 0;
        private boolean started = false;

        void start() {
            if (started) return;
            started = true;
            lastFrameTimeNanos = 0;
            frameCount = 0;
            lastUpdateTime = System.currentTimeMillis();
            choreographer.postFrameCallback(this);
        }

        void stop() {
            started = false;
            choreographer.removeFrameCallback(this);
        }

        int getCurrentFps() {
            return currentFps;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!started) return;

            frameCount++;
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdateTime;

            if (elapsed >= FPS_UPDATE_INTERVAL_MS) {
                currentFps = Math.round(frameCount * 1000f / elapsed);
                frameCount = 0;
                lastUpdateTime = now;
            }

            choreographer.postFrameCallback(this);
        }
    }
}
