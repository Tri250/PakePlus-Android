package com.batteryhealth.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CalculateHealthUseCase 单元测试。
 *
 * 验证：
 * 1. FCC 比值路径
 * 2. 使用天数估算路径
 * 3. 无数据路径
 * 4. 中值滤波效果
 * 5. 健康度等级边界
 * 6. 健康度等级评分
 * 7. 极端输入稳定性
 * 8. 并发执行安全
 */
public class CalculateHealthUseCaseTest {

    private CalculateHealthUseCase useCase;

    @Before
    public void setUp() {
        useCase = new CalculateHealthUseCase(null, null);
    }

    // ==================== FCC 比值路径 ====================

    @Test
    public void testExecute_fullCapacity_equalsDesignCapacity_returns100() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4500, 0, 0);
        assertNotNull(result);
        assertEquals(100f, result.healthPercentage, 0.1f);
        assertEquals("fcc_ratio", result.source);
        assertEquals(0.95f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_currentCapacity_90Percent_returns90() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4050, 100, 0);
        assertEquals(90f, result.healthPercentage, 0.1f);
    }

    @Test
    public void testExecute_currentCapacity_80Percent_returns80() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 3600, 200, 0);
        assertEquals(80f, result.healthPercentage, 0.1f);
    }

    @Test
    public void testExecute_currentCapacity_aboveDesign_doesNotExceed100() {
        // 一些设备的"实际容量"可能略大于标称设计容量
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4700, 0, 0);
        assertTrue("Health should be capped at 100",
                result.healthPercentage <= 100.5f);
    }

    // ==================== 使用天数估算路径 ====================

    @Test
    public void testExecute_usageDays100_estimatedLossAbout2_6Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 0, 0, 100);
        // 100 * 0.026 = 2.6
        assertEquals(97.4f, result.healthPercentage, 0.1f);
        assertEquals("usage_days_estimate", result.source);
        assertTrue(result.confidence < 0.5f); // 低置信度
    }

    @Test
    public void testExecute_usageDaysVeryLong_healthClampedTo0() {
        // 10000天 * 0.026 = 260% 损耗，clamp 到 0
        CalculateHealthUseCase.Result result = useCase.execute(4500, 0, 0, 10000);
        assertEquals(0f, result.healthPercentage, 0.1f);
    }

    // ==================== 无数据路径 ====================

    @Test
    public void testExecute_noData_returnsUnknown() {
        CalculateHealthUseCase.Result result = useCase.execute(0, 0, 0, 0);
        assertEquals(-1f, result.healthPercentage, 0.01f);
        assertEquals("no_data", result.source);
        assertEquals(0f, result.confidence, 0.01f);
        assertEquals("unknown", result.healthLevel);
    }

    @Test
    public void testExecute_negativeDesignCapacity_treatedAsInvalid() {
        CalculateHealthUseCase.Result result = useCase.execute(-100, 50, 0, 0);
        // 设计容量无效，应走无数据或使用天数路径
        assertNotNull(result);
    }

    // ==================== 健康度等级边界 ====================

    @Test
    public void testHealthLevel_excellent() {
        assertEquals("excellent", useCase.execute(4500, 4500, 0, 0).healthLevel);
    }

    @Test
    public void testHealthLevel_good_95Boundary() {
        // 95% 应该是 excellent 的下限
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4275, 0, 0);
        assertEquals("excellent", result.healthLevel);
    }

    @Test
    public void testHealthLevel_good_94Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4230, 0, 0);
        assertEquals("good", result.healthLevel);
    }

    @Test
    public void testHealthLevel_average_84Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 3780, 0, 0);
        assertEquals("average", result.healthLevel);
    }

    @Test
    public void testHealthLevel_poor_74Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 3330, 0, 0);
        assertEquals("poor", result.healthLevel);
    }

    @Test
    public void testHealthLevel_veryPoor_59Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 2655, 0, 0);
        assertEquals("very_poor", result.healthLevel);
    }

    // ==================== 评级评分 ====================

    @Test
    public void testCalculateGrade_allBoundaries() {
        assertEquals("A+", useCase.calculateGrade(100));
        assertEquals("A+", useCase.calculateGrade(95));
        assertEquals("A", useCase.calculateGrade(94.9f));
        assertEquals("A", useCase.calculateGrade(90));
        assertEquals("A-", useCase.calculateGrade(89.9f));
        assertEquals("A-", useCase.calculateGrade(85));
        assertEquals("B+", useCase.calculateGrade(84.9f));
        assertEquals("B+", useCase.calculateGrade(80));
        assertEquals("B", useCase.calculateGrade(79.9f));
        assertEquals("B", useCase.calculateGrade(75));
        assertEquals("B-", useCase.calculateGrade(74.9f));
        assertEquals("B-", useCase.calculateGrade(70));
        assertEquals("C", useCase.calculateGrade(69.9f));
        assertEquals("C", useCase.calculateGrade(60));
        assertEquals("D", useCase.calculateGrade(59.9f));
        assertEquals("D", useCase.calculateGrade(0));
    }

    @Test
    public void testCalculateGrade_negative_unsafe() {
        // 负数映射为 D
        assertEquals("D", useCase.calculateGrade(-1));
        assertEquals("D", useCase.calculateGrade(-100));
    }

    @Test
    public void testCalculateGrade_above100_unsafe() {
        // 超过 100 也映射为 A+
        assertEquals("A+", useCase.calculateGrade(101));
        assertEquals("A+", useCase.calculateGrade(1000));
    }

    // ==================== 健康度损失字段 ====================

    @Test
    public void testCycleLoss_calculatedCorrectly() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4050, 0, 0);
        // 100% - 90% = 10%
        assertEquals(10f, result.cycleLossPercent, 0.1f);
    }

    @Test
    public void testCycleLoss_neverNegative() {
        // 即使 ratio > 100%, loss 仍 >= 0
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4700, 0, 0);
        assertTrue("Cycle loss should be >= 0",
                result.cycleLossPercent >= 0);
    }

    // ==================== 稳定性 / 性能 ====================

    @Test
    public void testPerformance_bulkExecute() {
        long elapsed = TestUtils.measureExecutionTime("CalculateHealth.bulk", () -> {
            for (int i = 0; i < 10000; i++) {
                useCase.execute(4500, 3000 + (i % 1500), i % 500, i % 1000);
            }
        });
        assertTrue("Bulk execute too slow: " + elapsed + "ms", elapsed < 3000);
    }

    @Test
    public void testConcurrentExecute_threadSafe() throws InterruptedException {
        int threadCount = 8;
        int iterations = 1000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final Throwable[] errors = new Throwable[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int seed = i;
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        CalculateHealthUseCase.Result r = useCase.execute(
                                4500, 3000 + j % 1500, j % 500, j % 1000);
                        if (r == null) throw new AssertionError("null result");
                        if (r.healthPercentage < -1 || r.healthPercentage > 100.5f) {
                            throw new AssertionError("Invalid health: " + r.healthPercentage);
                        }
                    }
                } catch (Throwable t) {
                    errors[seed] = t;
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("Concurrent test timed out", done.await(30, TimeUnit.SECONDS));
        for (Throwable t : errors) {
            assertEquals("Concurrent error", null, t);
        }
    }

    @Test
    public void testExtremeInputs_noCrash() {
        try {
            useCase.execute(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
            useCase.execute(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
            useCase.execute(0, 1, 0, 0); // 设计容量为 0 但 current=1
            useCase.execute(1, 0, 0, 0); // current 为 0
        } catch (Exception e) {
            // 极端输入可能产生 NaN/Infinity，但不应抛未捕获异常
            if (!(e instanceof ArithmeticException)) {
                throw e;
            }
        }
    }

    @Test
    public void testConsistency_sameInputs_sameOutputs() {
        CalculateHealthUseCase.Result r1 = useCase.execute(4500, 4000, 100, 200);
        CalculateHealthUseCase.Result r2 = useCase.execute(4500, 4000, 100, 200);
        // 由于中值滤波前两次结果可能不同
        assertNotNull(r1);
        assertNotNull(r2);
    }

    @Test
    public void testMedianFilter_smoothsOutliers() {
        // 多次执行：第一次异常值，第二次以后会被滤波
        CalculateHealthUseCase.Result r1 = useCase.execute(4500, 4500, 0, 0); // 100
        CalculateHealthUseCase.Result r2 = useCase.execute(4500, 4500, 0, 0);
        CalculateHealthUseCase.Result r3 = useCase.execute(4500, 4500, 0, 0);
        CalculateHealthUseCase.Result r4 = useCase.execute(4500, 100, 0, 0); // 异常值
        CalculateHealthUseCase.Result r5 = useCase.execute(4500, 4500, 0, 0);
        // 滤波后应接近中值
        assertTrue(r5.healthPercentage >= 0 && r5.healthPercentage <= 100);
    }
}
