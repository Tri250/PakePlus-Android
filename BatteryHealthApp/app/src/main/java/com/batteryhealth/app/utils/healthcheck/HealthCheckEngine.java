package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

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

    private static final String TAG = "HealthCheckEngine";
    private static final HealthCheckEngine INSTANCE = new HealthCheckEngine();

    /** 内部并发线程数；默认 4 足以并行读取 /proc、BatteryManager 等不阻塞资源。 */
    private static final int POOL_SIZE = 4;

    private final List<IHealthChecker> checkers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

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
        this.executor = Executors.newFixedThreadPool(POOL_SIZE, r -> {
            Thread t = new Thread(r, "HealthCheckEngine");
            t.setDaemon(true);
            return t;
        });
        registerDefaultCheckers();
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
        checkers.add(new PerformanceHealthChecker());
        checkers.add(new MemoryHealthChecker());
        checkers.add(new StorageHealthChecker());
        checkers.add(new NotificationPermissionChecker());
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
     */
    public void startCheck(final Context context, final Callback callback) {
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

        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<Future<HealthCheckResult>> futures = new ArrayList<>();
                for (final IHealthChecker checker : snapshot) {
                    futures.add(executor.submit(new java.util.concurrent.Callable<HealthCheckResult>() {
                        @Override
                        public HealthCheckResult call() {
                            try {
                                HealthCheckResult result = checker.check(appCtx);
                                return result != null ? result : buildFallbackResult(checker.getName(), checker.getCategory());
                            } catch (Throwable t) {
                                Log.e(TAG, "Checker '" + checker.getName() + "' threw exception", t);
                                return buildFallbackResult(checker.getName(), checker.getCategory());
                            }
                        }
                    }));
                }

                for (Future<HealthCheckResult> future : futures) {
                    try {
                        HealthCheckResult result = future.get();
                        if (result != null) collector.add(result);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to get result from future", e);
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
     * @return true 表示成功发起跳转；false 表示无修复动作可执行或启动失败。
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
            Log.e(TAG, "Failed to apply fix for: " + result.getId(), e);
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
                return new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
            case HealthCheckResult.FIX_ACTION_CHARGING_LIMIT:
                // 系统无统一的充电限制页面，尝试跳转到电池 saver 设置页（部分厂商支持）。
                // 若不可用则回退到应用详情页。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                }
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_BATTERY_SAVER:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                }
                return buildAppDetailsIntent(pkg);
            case HealthCheckResult.FIX_ACTION_NETWORK_SETTINGS:
                return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            case HealthCheckResult.FIX_ACTION_APPLICATION_DETAILS:
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
}
