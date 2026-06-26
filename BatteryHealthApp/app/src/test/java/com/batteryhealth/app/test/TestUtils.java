package com.batteryhealth.app.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试基础设施工具类。
 *
 * 提供测试所需的：
 * - 性能监控 (CPU/内存)
 * - 稳定性测试辅助
 * - 上下文获取
 * - 测试用 SharedPreferences
 * - 临时目录管理
 */
public final class TestUtils {

    public static final long DEFAULT_TIMEOUT_MS = 5000L;
    public static final long LONG_TIMEOUT_MS = 30000L;
    public static final int ITERATION_STRESS = 1000;
    public static final int ITERATION_HEAVY = 10000;

    private TestUtils() {}

    /**
     * 获取测试应用 Context (兼容 Robolectric 和仪器测试)
     */
    public static Context getContext() {
        try {
            return ApplicationProvider.getApplicationContext();
        } catch (Throwable t) {
            // 单元测试时无 ApplicationProvider，使用 Robolectric 上下文
            try {
                return InstrumentationRegistry.getInstrumentation().getTargetContext();
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * 创建一个隔离的 SharedPreferences (用于 PreferenceManager 测试)
     */
    public static SharedPreferences createTestSharedPreferences(String name) {
        Context ctx = getContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences("test_" + name + "_" + UUID.randomUUID(),
                Context.MODE_PRIVATE);
    }

    /**
     * 测量代码块执行时间 (毫秒)
     *
     * @param label 测试标签
     * @param runnable 待测试代码
     * @return 耗时 (毫秒)
     */
    public static long measureExecutionTime(String label, Runnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } catch (Throwable t) {
            android.util.Log.e("TestUtils", "Test '" + label + "' threw exception", t);
            throw t;
        }
        long end = System.nanoTime();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(end - start);
        android.util.Log.i("TestUtils", "Test '" + label + "' took " + elapsedMs + "ms");
        return elapsedMs;
    }

    /**
     * 创建可监控性能的规则 (JUnit Rule)
     */
    public static TestRule performanceRule(String testName) {
        return new TestRule() {
            @Override
            public Statement apply(Statement base, Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        long start = System.nanoTime();
                        Runtime runtime = Runtime.getRuntime();
                        long memBefore = runtime.totalMemory() - runtime.freeMemory();
                        try {
                            base.evaluate();
                        } finally {
                            long end = System.nanoTime();
                            long memAfter = runtime.totalMemory() - runtime.freeMemory();
                            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(end - start);
                            long memDeltaKb = (memAfter - memBefore) / 1024;
                            android.util.Log.i("PerfTest",
                                    testName + " elapsed=" + elapsedMs + "ms"
                                            + " memDelta=" + memDeltaKb + "KB");
                        }
                    }
                };
            }
        };
    }

    /**
     * 清理测试数据 (在测试结束后调用)
     */
    public static void clearAllTestData() {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            String dbName = "battery_health_db";
            ctx.deleteDatabase(dbName);
            ctx.deleteDatabase(dbName + "-journal");
        } catch (Throwable t) {
            android.util.Log.w("TestUtils", "clearAllTestData failed: " + t.getMessage());
        }
    }

    /**
     * 生成随机长整型时间戳 (在 [minYear, maxYear] 范围内)
     */
    public static long randomTimestamp(int minYear, int maxYear) {
        long min = (long) minYear * 365L * 24L * 60L * 60L * 1000L;
        long max = (long) maxYear * 365L * 24L * 60L * 60L * 1000L;
        return min + (long) (Math.random() * (max - min));
    }
}
