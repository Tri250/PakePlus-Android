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
import java.util.concurrent.TimeoutException;
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

    // 当前轮次的回调订阅者列表：支持多个 Fragment 同时订阅同一轮检测结果。
    // 修复场景：用户在自检进行中切走再切回，新 Fragment 的 onViewCreated 自动触发 startCheck，
    // 旧实现直接 return 导致新 Fragment 永远收不到 onCompleted；改为订阅后即可在 engine 完成时一并通知。
    private final List<Callback> activeCallbacks = new CopyOnWriteArrayList<>();

    public interface Callback {
        /** 进度回调：0-100。在任意线程上被调用。 */
        void onProgress(int percent);

        /** 进度回调：附带当前正在检测的项名称，用于前端展示更友好的扫描状态。 */
        default void onProgress(int percent, String currentItemName) {
            onProgress(percent);
        }

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
            executor = Executors.newFixedThreadPool(POOL_SIZE, r -> {
                Thread t = new Thread(r, "HealthCheckEngine");
                t.setDaemon(true);
                return t;
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
        checkers.add(new PermissionHealthChecker());
        checkers.add(new BatteryOptimizationChecker());
        checkers.add(new NetworkHealthChecker());
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
     *                 若 engine 已在运行，本次 callback 会被加入订阅列表，
     *                 在当前轮次完成后一并收到 onCompleted/onError。
     */
    public void startCheck(final Context context, final Callback callback) {
        // 已在运行：直接订阅当前轮次，不再 onError 拒绝（避免切回 Tab 的新 Fragment 永远收不到结果）
        if (running.get()) {
            if (callback != null) activeCallbacks.add(callback);
            return;
        }
        // CAS 保证并发调用时只有一个能进入新一轮
        if (!running.compareAndSet(false, true)) {
            // CAS 失败说明刚被其他线程抢入，按订阅处理
            if (callback != null) activeCallbacks.add(callback);
            return;
        }

        if (callback != null) activeCallbacks.add(callback);

        final Context appCtx = context != null ? context.getApplicationContext() : null;
        if (appCtx == null) {
            try {
                notifyError("上下文不可用。");
            } finally {
                activeCallbacks.clear();
                running.set(false);
            }
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
                try {
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

                    // futures 与 snapshot 顺序一一对应；按索引取 checker 以便超时时构建兜底结果
                    for (int idx = 0; idx < futures.size(); idx++) {
                        Future<HealthCheckResult> future = futures.get(idx);
                        IHealthChecker checker = snapshot.get(idx);
                        // 每轮开始前先通知：当前正在检测的项名称
                        notifyProgress((idx * 100) / total, checker.getName());
                        try {
                            // 限制单个 Checker 最多阻塞 5 秒，避免一项卡死导致整个自检流程不返回
                            HealthCheckResult result = future.get(5, TimeUnit.SECONDS);
                            if (result != null) collector.add(result);
                        } catch (TimeoutException te) {
                            // 超时则中断该任务并用兜底结果填充，保证自检流程可继续
                            future.cancel(true);
                            collector.add(buildFallbackResult(checker.getName(), checker.getCategory()));
                        } catch (Exception e) {
                            // 非超时异常（InterruptedException / ExecutionException 等）也用兜底结果填充，
                            // 避免该 checker 从结果列表中静默消失导致 UI 少一项
                            android.util.Log.w("HealthCheckEngine",
                                    "Checker '" + checker.getName() + "' threw: " + e.getClass().getSimpleName());
                            collector.add(buildFallbackResult(checker.getName(), checker.getCategory()));
                        } finally {
                            int current = done.incrementAndGet();
                            notifyProgress(total > 0 ? (current * 100 / total) : 100, checker.getName());
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

                    notifyCompleted(collector);
                } catch (Throwable t) {
                    // 兜底：外层 Runnable 抛异常时也必须通知订阅者并释放 running，否则自检永久瘫痪
                    notifyError("检测异常：" + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                } finally {
                    activeCallbacks.clear();
                    running.set(false);
                }
            }
        });
    }

    /** 广播进度给所有当前订阅者。 */
    private void notifyProgress(int percent) {
        notifyProgress(percent, "");
    }

    private void notifyProgress(int percent, String currentItemName) {
        for (Callback cb : activeCallbacks) {
            try {
                cb.onProgress(percent, currentItemName != null ? currentItemName : "");
            } catch (Throwable ignored) {}
        }
    }

    /** 广播完成结果给所有当前订阅者。 */
    private void notifyCompleted(List<HealthCheckResult> results) {
        for (Callback cb : activeCallbacks) {
            try { cb.onCompleted(results); } catch (Throwable ignored) {}
        }
    }

    /** 广播错误给所有当前订阅者。 */
    private void notifyError(String message) {
        for (Callback cb : activeCallbacks) {
            try { cb.onError(message); } catch (Throwable ignored) {}
        }
    }

    /**
     * 取消订阅当前轮次的回调；用于 Fragment 销毁时避免回调持有已销毁视图导致泄漏。
     * 不会中断正在进行的检测。
     */
    public void removeCallback(Callback callback) {
        if (callback != null) activeCallbacks.remove(callback);
    }

    /** 综合评分：按各项 itemScore 的加权平均，其中严重项扣分权重更高。
     *  逻辑：以 GOOD(正常) 为基准权重 1，INFO 轻微下调，WARNING 显著放大，
     *  CRITICAL 再次加倍，确保单项严重问题不会淹没在大量正常项中。 */
    public int getOverallScore(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return 0;
        float sum = 0f;
        float weightSum = 0f;
        for (HealthCheckResult r : results) {
            float weight;
            switch (r.getSeverity()) {
                case HealthCheckResult.SEVERITY_CRITICAL: weight = 5f; break;
                case HealthCheckResult.SEVERITY_WARNING:  weight = 2.5f; break;
                case HealthCheckResult.SEVERITY_INFO:     weight = 0.8f; break;
                default:                                   weight = 1f; break;
            }
            sum += r.getItemScore() * weight;
            weightSum += weight;
        }
        int score = weightSum > 0 ? Math.round(sum / weightSum) : 0;
        return Math.max(0, Math.min(100, score));
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
                    // Android 13+ 需要附加 channel 信息才能精确跳转到通知设置中的特定应用
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        nIntent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, pkg);
                    }
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
                // 系统无统一的充电限制页面；回退到电池设置页。
                Intent batt = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                batt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return batt;
            case HealthCheckResult.FIX_ACTION_BATTERY_SAVER:
                return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            case HealthCheckResult.FIX_ACTION_NETWORK_SETTINGS:
                return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            case HealthCheckResult.FIX_ACTION_APPLICATION_DETAILS:
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_PERMISSION_SETTINGS:
                // 引导到应用详情页，用户可在其中检查所有权限项
                return buildAppDetailsIntent(pkg);
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
     * 开头附加 UTF-8 BOM（\uFEFF），确保 Excel 正确识别中文编码。
     */
    public String exportCsv(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM：防止 Excel 打开时中文乱码
        sb.append('\uFEFF');
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
}
