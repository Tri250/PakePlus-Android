package com.batteryhealth.app.utils.healthcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.test.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HealthCheckEngine 稳定性 + 并发 + 性能 + 进度回调测试。
 *
 * 对应实际 API:
 * - registerChecker(IHealthChecker)
 * - getCheckerCount()
 * - isRunning()
 * - startCheck(Context, Callback)
 * - shutdown()
 * - getOverallScore()
 * - exportCsv()
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class HealthCheckEngineStabilityTest {

    private HealthCheckEngine engine;
    private Context appContext;

    @Before
    public void setUp() {
        engine = HealthCheckEngine.getInstance();
        // 重置实例状态 (但保留默认 checkers)
        appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext();
    }

    // ==================== 注册与去重 ====================

    @Test
    public void testRegister_addsNewChecker() {
        int before = engine.getCheckerCount();
        engine.registerChecker(new MockChecker(UUID.randomUUID().toString(), "cat1", 1));
        assertEquals(before + 1, engine.getCheckerCount());
    }

    @Test
    public void testRegister_null_safe() {
        int before = engine.getCheckerCount();
        engine.registerChecker(null);
        assertEquals(before, engine.getCheckerCount());
    }

    // ==================== 执行 ====================

    @Test
    public void testStartCheck_callbackInvoked() throws InterruptedException {
        // 通过 startCheck 执行实际检测
        final CountDownLatch done = new CountDownLatch(1);
        final List<HealthCheckResult>[] captured = new List[]{null};
        final Throwable[] error = new Throwable[]{null};
        engine.startCheck(appContext, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(int percent) {}

            @Override
            public void onCompleted(List<HealthCheckResult> results) {
                captured[0] = results;
                done.countDown();
            }

            @Override
            public void onError(String message) {
                error[0] = new RuntimeException(message);
                done.countDown();
            }
        });
        assertTrue("startCheck timed out", done.await(15, TimeUnit.SECONDS));
        if (error[0] != null) {
            fail("startCheck error: " + error[0].getMessage());
        }
        assertNotNull(captured[0]);
        // 默认有 14 个 checker
        assertTrue("Expected at least 10 results, got " + captured[0].size(),
                captured[0].size() >= 10);
    }

    @Test
    public void testStartCheck_progressReaches100() throws InterruptedException {
        final AtomicInteger maxProgress = new AtomicInteger(0);
        final CountDownLatch done = new CountDownLatch(1);
        engine.startCheck(appContext, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(int percent) {
                int cur = percent;
                while (true) {
                    int prev = maxProgress.get();
                    if (cur <= prev) break;
                    if (maxProgress.compareAndSet(prev, cur)) break;
                }
            }

            @Override
            public void onCompleted(List<HealthCheckResult> results) {
                done.countDown();
            }

            @Override
            public void onError(String message) {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertTrue("Progress should reach 100, got " + maxProgress.get(),
                maxProgress.get() == 100);
    }

    // ==================== 并发执行 ====================

    @Test
    public void testStartCheck_concurrent_multipleCallbacks() throws InterruptedException {
        int callbackCount = 5;
        final CountDownLatch done = new CountDownLatch(callbackCount);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < callbackCount; i++) {
            engine.startCheck(appContext, new HealthCheckEngine.Callback() {
                @Override
                public void onProgress(int percent) {}

                @Override
                public void onCompleted(List<HealthCheckResult> results) {
                    done.countDown();
                }

                @Override
                public void onError(String message) {
                    errors.add(new RuntimeException(message));
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(20, TimeUnit.SECONDS));
        assertEquals(0, errors.size());
    }

    // ==================== 异常处理 ====================

    @Test
    public void testStartCheck_checkerThrows_handlesGracefully() throws InterruptedException {
        engine.registerChecker(new ThrowingChecker("boom"));
        final CountDownLatch done = new CountDownLatch(1);
        final List<HealthCheckResult>[] captured = new List[]{null};
        engine.startCheck(appContext, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(int percent) {}

            @Override
            public void onCompleted(List<HealthCheckResult> results) {
                captured[0] = results;
                done.countDown();
            }

            @Override
            public void onError(String message) {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertNotNull(captured[0]);
    }

    @Test
    public void testStartCheck_slowChecker_timeoutFallback() throws InterruptedException {
        engine.registerChecker(new SlowChecker("slow", 6000));
        final CountDownLatch done = new CountDownLatch(1);
        engine.startCheck(appContext, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(int percent) {}

            @Override
            public void onCompleted(List<HealthCheckResult> results) {
                done.countDown();
            }

            @Override
            public void onError(String message) {
                done.countDown();
            }
        });
        // 即使有 6 秒慢任务，整体也应在 8 秒内完成 (单 checker 5 秒超时)
        assertTrue("Slow checker should timeout quickly",
                done.await(10, TimeUnit.SECONDS));
    }

    // ==================== 综合评分 ====================

    @Test
    public void testGetOverallScore_emptyList() {
        assertEquals(0, engine.getOverallScore(new ArrayList<>()));
    }

    @Test
    public void testGetOverallScore_null() {
        assertEquals(0, engine.getOverallScore(null));
    }

    @Test
    public void testGetOverallScore_allGood() {
        List<HealthCheckResult> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new HealthCheckResult.Builder()
                    .setId("g" + i)
                    .setTitle("g" + i)
                    .setCategory("c")
                    .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                    .setStatus("good")
                    .setItemScore(90)
                    .build());
        }
        int score = engine.getOverallScore(list);
        assertTrue("All good should give high score, got " + score,
                score >= 80);
    }

    @Test
    public void testGetOverallScore_withCritical_deductsMore() {
        List<HealthCheckResult> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new HealthCheckResult.Builder()
                    .setId("g" + i)
                    .setTitle("g" + i)
                    .setCategory("c")
                    .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                    .setStatus("good")
                    .setItemScore(90)
                    .build());
        }
        list.add(new HealthCheckResult.Builder()
                .setId("c")
                .setTitle("c")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_CRITICAL)
                .setStatus("crit")
                .setItemScore(10)
                .build());
        int score = engine.getOverallScore(list);
        // 严重项应显著拉低分数
        assertTrue("Critical should lower score, got " + score, score < 80);
    }

    // ==================== CSV 导出 ====================

    @Test
    public void testExportCsv_empty() {
        assertEquals("", engine.exportCsv(new ArrayList<>()));
    }

    @Test
    public void testExportCsv_null() {
        assertEquals("", engine.exportCsv(null));
    }

    @Test
    public void testExportCsv_includesBOM() {
        List<HealthCheckResult> list = new ArrayList<>();
        list.add(new HealthCheckResult.Builder()
                .setId("x")
                .setTitle("中文测试")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .setValue("100")
                .setUnit("mAh")
                .build());
        String csv = engine.exportCsv(list);
        assertNotNull(csv);
        assertTrue("CSV should start with BOM",
                csv.startsWith("\uFEFF"));
        assertTrue("CSV should contain title",
                csv.contains("中文测试"));
    }

    @Test
    public void testExportCsv_escapesQuotes() {
        List<HealthCheckResult> list = new ArrayList<>();
        list.add(new HealthCheckResult.Builder()
                .setId("x")
                .setTitle("title with \"quote\"")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build());
        String csv = engine.exportCsv(list);
        assertNotNull(csv);
        // CSV 应该将 " 转义为 ""
        assertTrue("Quote should be escaped", csv.contains("\"\"quote\"\""));
    }

    // ==================== 性能测试 ====================

    @Test
    public void testPerformance_fullCheck() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        long start = System.nanoTime();
        engine.startCheck(appContext, new HealthCheckEngine.Callback() {
            @Override
            public void onProgress(int percent) {}
            @Override
            public void onCompleted(List<HealthCheckResult> results) {
                done.countDown();
            }
            @Override
            public void onError(String message) {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("Full check should complete < 10s, took " + elapsed + "ms",
                elapsed < 10000);
    }

    // ==================== 辅助类 ====================

    private static class MockChecker implements IHealthChecker {
        private final String name;
        private final String category;
        private final int priority;
        private final long delayMs;

        MockChecker(String name, String category, int priority) {
            this(name, category, priority, 0);
        }

        MockChecker(String name, String category, int priority, long delayMs) {
            this.name = name;
            this.category = category;
            this.priority = priority;
            this.delayMs = delayMs;
        }

        @Override public String getName() { return name; }
        @Override public String getCategory() { return category; }
        @Override public int getPriority() { return priority; }

        @Override
        public HealthCheckResult check(Context context) {
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new HealthCheckResult.Builder()
                    .setId(name)
                    .setTitle(name)
                    .setCategory(category)
                    .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                    .setStatus("good")
                    .build();
        }
    }

    private static class ThrowingChecker implements IHealthChecker {
        private final String name;
        ThrowingChecker(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getCategory() { return "test"; }
        @Override public int getPriority() { return 5; }
        @Override public HealthCheckResult check(Context context) {
            throw new RuntimeException("Boom: " + name);
        }
    }

    private static class SlowChecker implements IHealthChecker {
        private final String name;
        private final long delayMs;
        SlowChecker(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }
        @Override public String getName() { return name; }
        @Override public String getCategory() { return "test"; }
        @Override public int getPriority() { return 5; }
        @Override
        public HealthCheckResult check(Context context) {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new HealthCheckResult.Builder()
                    .setId(name)
                    .setTitle(name)
                    .setCategory("test")
                    .setSeverity(HealthCheckResult.SEVERITY_INFO)
                    .setStatus("slow")
                    .build();
        }
    }
}
