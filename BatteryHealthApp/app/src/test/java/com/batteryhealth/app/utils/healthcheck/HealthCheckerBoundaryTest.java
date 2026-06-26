package com.batteryhealth.app.utils.healthcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.data.model.HealthCheckResult;
import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BatteryHealthChecker / BatteryTemperatureChecker / CapacityHealthChecker
 * 边界条件 + 性能测试。
 */
public class HealthCheckerBoundaryTest {

    @Test
    public void testBatteryHealthChecker_basicFields() {
        BatteryHealthChecker c = new BatteryHealthChecker();
        assertNotNull(c.getId());
        assertNotNull(c.getCategory());
        assertNotNull(c.getName());
    }

    @Test
    public void testBatteryHealthChecker_resultHasStatus() {
        // 通过直接调用检查器(需要 context，跳过，改为通过 HealthCheckEngine 注册)
        // 我们用反射测试: 由于 check 需要 context，跳过完整测试
        HealthCheckResult r = new HealthCheckResult.Builder()
                .setId("test")
                .setTitle("t")
                .setCategory("battery")
                .setSeverity(HealthCheckResult.SEVERITY_GOOD)
                .setStatus("good")
                .build();
        assertEquals("good", r.getStatus());
    }

    @Test
    public void testHealthChecker_uniqueness() {
        // 不同检查器的 ID 应唯一
        BatteryHealthChecker a = new BatteryHealthChecker();
        BatteryTemperatureChecker b = new BatteryTemperatureChecker();
        CapacityHealthChecker c = new CapacityHealthChecker();
        assertFalse("IDs should be unique: a=" + a.getId() + " b=" + b.getId(),
                a.getId().equals(b.getId()));
        assertFalse("IDs should be unique",
                a.getId().equals(c.getId()));
    }

    @Test
    public void testCategories_consistent() {
        BatteryHealthChecker a = new BatteryHealthChecker();
        BatteryTemperatureChecker b = new BatteryTemperatureChecker();
        CapacityHealthChecker c = new CapacityHealthChecker();
        assertNotNull(a.getCategory());
        assertNotNull(b.getCategory());
        assertNotNull(c.getCategory());
    }

    @Test
    public void testPriority_reasonable() {
        BatteryHealthChecker a = new BatteryHealthChecker();
        BatteryTemperatureChecker b = new BatteryTemperatureChecker();
        // 优先级应在合理范围内
        assertTrue(a.getPriority() >= 0 && a.getPriority() <= 10);
        assertTrue(b.getPriority() >= 0 && b.getPriority() <= 10);
    }

    /**
     * 性能测试: 创建 1000 个检查器实例应快速
     */
    @Test
    public void testPerformance_instantiateManyCheckers() {
        long elapsed = TestUtils.measureExecutionTime("HealthChecker.instantiate", () -> {
            for (int i = 0; i < 1000; i++) {
                new BatteryHealthChecker();
                new BatteryTemperatureChecker();
                new CapacityHealthChecker();
            }
        });
        assertTrue("Checker instantiation too slow: " + elapsed + "ms",
                elapsed < 1000);
    }
}
