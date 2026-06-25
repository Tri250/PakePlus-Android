package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.batteryhealth.app.data.model.HealthCheckResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自检引擎：注册与调度所有 {@link IHealthChecker}，并发执行检测并返回
 * 统一排序后的 {@link HealthCheckResult} 列表；同时负责执行一键修复
 * 跳转、生成 CSV 诊断报告。
 *
 * <p>使用示例：
 * <pre>{@code
 *   HealthCheckEngine engine = HealthCheckEngine.getInstance();
 *   engine.startCheck(context, new HealthCheckEngine.Callback() { ... });
 * }</pre>
 */
public class HealthCheckEngine {

    private static final HealthCheckEngine INSTANCE = new HealthCheckEngine();

    /** 内部并发线程数；默认 4 足以并行读取 /proc、BatteryManager 等不阻塞资源。 */
    private static final int POOL_SIZE = 4;

    private final List<IHealthChecker> checkers = new CopyOnWriteArrayList<>();
    private ExecutorService executor;

    // 使用 AtomicBoolean 替代 volatile boolean，通过 CAS 保证 startCheck 的原子性
    private final AtomicBoolean running = new AtomicBoolean(false);

    public interface Callback {
        /** 进度回调：0-100。在任意线程上被调用。 */
        void onProgress(int percent);

        /** 所有检测完成时回调。结果列表按 priority 升序排序。 */
        void onCompleted(List<HealthCheckResult> results);

        /** 若发生不可恢复的异常（几乎不会发生）。 */
        void onError(String message);
    }

    private HealthCheckEngine() {
        registerDefaultCheckers();
    }

    /** 懒初始化线程池，shutdown 后可重建。 */
    private synchronized ExecutorService ensureExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(POOL_SIZE, new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "HealthCheckEngine");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return executor;
    }

    /**
     * 关闭内部线程池，释放资源。可在 Application.onTerminate() 中调用。
     * 关闭后再次调用 startCheck 会自动重建线程池。
     */
    public synchronized void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static HealthCheckEngine getInstance() {
        return INSTANCE;
    }

    /** 注册内部默认的检测项集合。子类可覆盖此方法扩展。 */
    protected void registerDefaultCheckers() {
        checkers.clear();
        checkers.add(new BatteryHealthChecker());
        checkers.add(new BatteryTemperatureChecker());
        checkers.add(new CapacityHealthChecker());
        checkers.add(new EnduranceChecker());
        checkers.add(new ChargingProtocolChecker());
        checkers.add(new ChargingLimitChecker());
        checkers.add(new ChargingProtectionChecker());
        checkers.add(new PerformanceHealthChecker());
        checkers.add(new MemoryHealthChecker());
        checkers.add(new StorageHealthChecker());
        checkers.add(new NotificationPermissionChecker());
        checkers.add(new BatteryOptimizationChecker());
        checkers.add(new NetworkHealthChecker());
        checkers.add(new ScreenBrightnessChecker());
        checkers.add(new BluetoothChecker());
        checkers.add(new WifiChecker());
        checkers.add(new MobileNetworkChecker());
        checkers.add(new SyncChecker());
        checkers.add(new VibrationChecker());
    }

    /** 注册自定义检测项；引擎会在下次检测时使用。 */
    public void registerChecker(IHealthChecker checker) {
        if (checker != null) checkers.add(checker);
    }

    public int getCheckerCount() {
        return checkers.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 启动一轮检测。
     *
     * @param context 应用上下文；内部会转成 getApplicationContext()。
     * @param callback 进度与完成回调；可为 null，但强烈建议提供。
     */
    public void startCheck(final Context context, final Callback callback) {
        // CAS 保证并发调用时只有一个能进入
        if (!running.compareAndSet(false, true)) {
            if (callback != null) callback.onError("检测正在运行中，请稍候再试。");
            return;
        }

        final Context appCtx = context != null ? context.getApplicationContext() : null;
        if (appCtx == null) {
            running.set(false);
            if (callback != null) callback.onError("上下文不可用。");
            return;
        }

        final List<IHealthChecker> snapshot = new ArrayList<>(checkers);
        // 按 priority 升序执行，保证前端 UI 先显示最关心的项。
        Collections.sort(snapshot, new Comparator<IHealthChecker>() {
            @Override public int compare(IHealthChecker a, IHealthChecker b) {
                return Integer.compare(a.getPriority(), b.getPriority());
            }
        });

        final int total = snapshot.size();
        final AtomicInteger done = new AtomicInteger(0);
        final List<HealthCheckResult> collector = new CopyOnWriteArrayList<>();
        final ExecutorService ex = ensureExecutor();

        ex.execute(new Runnable() {
            @Override
            public void run() {
                List<Future<HealthCheckResult>> futures = new ArrayList<>();
                for (final IHealthChecker checker : snapshot) {
                    futures.add(ex.submit(new java.util.concurrent.Callable<HealthCheckResult>() {
                        @Override
                        public HealthCheckResult call() {
                            try {
                                HealthCheckResult result = checker.check(appCtx);
                                return result != null ? result : buildFallbackResult(checker.getName(), checker.getCategory());
                            } catch (Throwable t) {
                                return buildFallbackResult(checker.getName(), checker.getCategory());
                            }
                        }
                    }));
                }

                for (Future<HealthCheckResult> future : futures) {
                    try {
                        HealthCheckResult result = future.get();
                        if (result != null) collector.add(result);
                    } catch (Exception ignored) {
                    } finally {
                        int current = done.incrementAndGet();
                        if (callback != null) {
                            try {
                                callback.onProgress(total > 0 ? (current * 100 / total) : 100);
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                Collections.sort(collector, new Comparator<HealthCheckResult>() {
                    @Override
                    public int compare(HealthCheckResult a, HealthCheckResult b) {
                        return Integer.compare(severityRank(a), severityRank(b)) != 0
                                ? -Integer.compare(severityRank(a), severityRank(b)) // 严重的在前
                                : Integer.compare(a.getItemScore(), b.getItemScore());
                    }
                });

                running.set(false);
                if (callback != null) {
                    try {
                        callback.onCompleted(collector);
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    /** 综合评分：按各项 itemScore 的加权平均，其中严重项扣分权重更高。 */
    public int getOverallScore(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return 0;
        float sum = 0f;
        float weightSum = 0f;
        for (HealthCheckResult r : results) {
            float weight = 1f;
            switch (r.getSeverity()) {
                case HealthCheckResult.SEVERITY_CRITICAL: weight = 3f; break;
                case HealthCheckResult.SEVERITY_WARNING: weight = 2f; break;
                case HealthCheckResult.SEVERITY_INFO: weight = 1.5f; break;
                default: weight = 1f; break;
            }
            sum += r.getItemScore() * weight;
            weightSum += weight;
        }
        return weightSum > 0 ? Math.round(sum / weightSum) : 0;
    }

    /**
     * 执行修复（跳转到对应的系统设置页）。
     *
     * @return true 表示成功发起跳转；false 表示无修复动作可执行。
     */
    public boolean applyFix(Context context, HealthCheckResult result) {
        if (context == null || result == null) return false;
        if (!result.isRepairable()) return false;

        try {
            Intent intent = buildFixIntent(context, result);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Intent buildFixIntent(Context context, HealthCheckResult result) {
        String pkg = context.getPackageName();
        switch (result.getFixAction()) {
            case HealthCheckResult.FIX_ACTION_NOTIFICATION_SETTINGS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent nIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    nIntent.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                    return nIntent;
                }
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_BATTERY_OPTIMIZATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + pkg));
                }
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_POWER_USAGE_DETAILS:
                Intent pi = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return pi;
            case HealthCheckResult.FIX_ACTION_CHARGING_LIMIT:
                Intent batt = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                batt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return batt;
            case HealthCheckResult.FIX_ACTION_BATTERY_SAVER:
                return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            case HealthCheckResult.FIX_ACTION_NETWORK_SETTINGS:
                return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            case HealthCheckResult.FIX_ACTION_APPLICATION_DETAILS:
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_DISPLAY_SETTINGS:
                return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            case HealthCheckResult.FIX_ACTION_BLUETOOTH_SETTINGS:
                return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            case HealthCheckResult.FIX_ACTION_WIFI_SETTINGS:
                return new Intent(Settings.ACTION_WIFI_SETTINGS);
            case HealthCheckResult.FIX_ACTION_ACCOUNT_SYNC_SETTINGS:
                return new Intent(Settings.ACTION_SYNC_SETTINGS);
            case HealthCheckResult.FIX_ACTION_SOUND_SETTINGS:
                return new Intent(Settings.ACTION_SOUND_SETTINGS);
            default:
                return buildAppDetailsIntent(pkg);
        }
    }

    private Intent buildAppDetailsIntent(String pkg) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + pkg));
        return intent;
    }

    /**
     * 生成 CSV 诊断报告；字段分隔符为 ","，首行是表头。
     */
    public String exportCsv(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\"检测项\",\"分类\",\"状态\",\"数值\",\"单位\",\"严重度\",\"评分\",\"详情\",\"建议\",\"时间戳\"\n");
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (HealthCheckResult r : results) {
            sb.append("\"").append(escapeCsv(r.getTitle())).append("\",");
            sb.append("\"").append(escapeCsv(r.getCategory())).append("\",");
            sb.append("\"").append(escapeCsv(r.getStatus())).append("\",");
            sb.append("\"").append(escapeCsv(r.getValue())).append("\",");
            sb.append("\"").append(escapeCsv(r.getUnit())).append("\",");
            sb.append("\"").append(severityLabel(r.getSeverity())).append("\",");
            sb.append("\"").append(r.getItemScore()).append("\",");
            sb.append("\"").append(escapeCsv(r.getDescription())).append("\",");
            sb.append("\"").append(escapeCsv(r.getAdvice())).append("\",");
            sb.append("\"").append(fmt.format(new Date(r.getTimestamp()))).append("\"\n");
        }
        return sb.toString();
    }

    private static int severityRank(HealthCheckResult r) {
        switch (r.getSeverity()) {
            case HealthCheckResult.SEVERITY_CRITICAL: return 3;
            case HealthCheckResult.SEVERITY_WARNING:  return 2;
            case HealthCheckResult.SEVERITY_INFO:     return 1;
            default: return 0;
        }
    }

    private static String severityLabel(int severity) {
        switch (severity) {
            case HealthCheckResult.SEVERITY_CRITICAL: return "严重";
            case HealthCheckResult.SEVERITY_WARNING:  return "警告";
            case HealthCheckResult.SEVERITY_INFO:     return "信息";
            default: return "良好";
        }
    }

    private static HealthCheckResult buildFallbackResult(String title, String category) {
        return new HealthCheckResult.Builder()
                .setId("fallback_" + (title == null ? "unknown" : title))
                .setTitle(title == null ? "未知项" : title)
                .setCategory(category == null ? HealthCheckResult.CATEGORY_SYSTEM : category)
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("读取失败")
                .setValue("--")
                .setUnit("")
                .setDescription("检测过程中发生异常。")
                .setAdvice("请稍后重试。")
                .setItemScore(50)
                .build();
    }

    private static String escapeCsv(String raw) {
        if (raw == null) return "";
        return raw.replace("\"", "\"\"");
    }

    public int getCriticalCount(List<HealthCheckResult> results) {
        int count = 0;
        if (results == null) return 0;
        for (HealthCheckResult r : results) {
            if (r.getSeverity() == HealthCheckResult.SEVERITY_CRITICAL) count++;
        }
        return count;
    }

    public int getWarningCount(List<HealthCheckResult> results) {
        int count = 0;
        if (results == null) return 0;
        for (HealthCheckResult r : results) {
            if (r.getSeverity() == HealthCheckResult.SEVERITY_WARNING) count++;
        }
        return count;
    }

    public int getInfoCount(List<HealthCheckResult> results) {
        int count = 0;
        if (results == null) return 0;
        for (HealthCheckResult r : results) {
            if (r.getSeverity() == HealthCheckResult.SEVERITY_INFO) count++;
        }
        return count;
    }

    public int getGoodCount(List<HealthCheckResult> results) {
        int count = 0;
        if (results == null) return 0;
        for (HealthCheckResult r : results) {
            if (r.getSeverity() == HealthCheckResult.SEVERITY_GOOD) count++;
        }
        return count;
    }

    public int getRepairableCount(List<HealthCheckResult> results) {
        int count = 0;
        if (results == null) return 0;
        for (HealthCheckResult r : results) {
            if (r.isRepairable()) count++;
        }
        return count;
    }

    public String generateTextReport(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        sb.append("═══════════════════════════════════\n");
        sb.append("       电池健康自检报告\n");
        sb.append("═══════════════════════════════════\n\n");
        sb.append("生成时间：").append(fmt.format(new Date())).append("\n");
        sb.append("检测项目：").append(results.size()).append(" 项\n");
        sb.append("综合评分：").append(getOverallScore(results)).append(" 分\n\n");

        int critical = getCriticalCount(results);
        int warning = getWarningCount(results);
        int info = getInfoCount(results);
        int good = getGoodCount(results);

        sb.append("───────────────────────────────────\n");
        sb.append("问题统计\n");
        sb.append("───────────────────────────────────\n");
        sb.append("严重问题：").append(critical).append(" 项\n");
        sb.append("警告问题：").append(warning).append(" 项\n");
        sb.append("提示信息：").append(info).append(" 项\n");
        sb.append("状态良好：").append(good).append(" 项\n\n");

        sb.append("───────────────────────────────────\n");
        sb.append("详细结果\n");
        sb.append("───────────────────────────────────\n\n");

        for (int i = 0; i < results.size(); i++) {
            HealthCheckResult r = results.get(i);
            sb.append(String.format("%d. %s [%s]\n", i + 1, r.getTitle(),
                    severityLabel(r.getSeverity())));
            sb.append("   状态：").append(r.getStatus()).append("\n");
            if (r.getValue() != null && !r.getValue().isEmpty()) {
                sb.append("   数值：").append(r.getValue());
                if (r.getUnit() != null && !r.getUnit().isEmpty()) {
                    sb.append(r.getUnit());
                }
                sb.append("\n");
            }
            sb.append("   评分：").append(r.getItemScore()).append(" 分\n");
            sb.append("   说明：").append(r.getDescription()).append("\n");
            sb.append("   建议：").append(r.getAdvice()).append("\n\n");
        }

        sb.append("═══════════════════════════════════\n");
        sb.append("  报告由电池健康助手生成\n");
        sb.append("═══════════════════════════════════\n");

        return sb.toString();
    }

    public String generateSummary(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return "";
        int score = getOverallScore(results);
        int critical = getCriticalCount(results);
        int warning = getWarningCount(results);
        int total = results.size();

        if (score >= 90) {
            return String.format("电池状态非常好！综合评分 %d 分，%d 项检测全部通过。", score, total);
        } else if (score >= 75) {
            return String.format("电池状态良好。综合评分 %d 分，有 %d 项警告建议优化。", score, warning);
        } else if (score >= 60) {
            return String.format("电池状态一般。综合评分 %d 分，有 %d 项警告和 %d 项严重问题需要关注。",
                    score, warning, critical);
        } else {
            return String.format("电池状态较差！综合评分 %d 分，有 %d 项严重问题需要立即处理。", score, critical);
        }
    }
}
