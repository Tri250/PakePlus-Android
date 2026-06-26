package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BatteryInfo 模型稳定性 + 边界条件测试。
 */
public class BatteryInfoStabilityTest {

    @Test
    public void testDefaultConstructor_createsValidObject() {
        BatteryInfo info = new BatteryInfo();
        assertNotNull(info);
        assertEquals(0, info.getId());
        assertEquals(0, info.getLevel());
    }

    @Test
    public void testSetterGetter_roundTripConsistent() {
        BatteryInfo info = new BatteryInfo();
        info.setLevel(85);
        info.setVoltage(4.2f);
        info.setTemperature(35.5f);
        info.setCurrentNow(-1500000);
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(4200);
        info.setCycleCount(150);
        info.setHealthPercentage(93.3f);
        info.setCharging(true);
        info.setChargingState(true);
        info.setTechnology("Li-poly");
        info.setTimestamp(System.currentTimeMillis());

        assertEquals(85, info.getLevel());
        assertEquals(4.2f, info.getVoltage(), 0.001f);
        assertEquals(35.5f, info.getTemperature(), 0.001f);
        assertEquals(-1500000, info.getCurrentNow());
        assertEquals(4500, info.getDesignCapacity());
        assertEquals(4200, info.getCurrentCapacity());
        assertEquals(150, info.getCycleCount());
        assertEquals(93.3f, info.getHealthPercentage(), 0.001f);
        assertTrue(info.isCharging());
        assertTrue(info.isChargingState());
        assertEquals("Li-poly", info.getTechnology());
    }

    @Test
    public void testLevel_boundaryValues() {
        BatteryInfo info = new BatteryInfo();
        info.setLevel(0);
        assertEquals(0, info.getLevel());
        info.setLevel(100);
        assertEquals(100, info.getLevel());
        // 负数和超过100也应被接受，因为这是原始数据，由调用方验证
        info.setLevel(-1);
        assertEquals(-1, info.getLevel());
        info.setLevel(120);
        assertEquals(120, info.getLevel());
    }

    @Test
    public void testNegativeCurrent_duringCharging() {
        BatteryInfo info = new BatteryInfo();
        info.setCurrentNow(-2000000);
        assertEquals(-2000000, info.getCurrentNow());
        // 充电时电流为负
        assertTrue(info.getCurrentNow() < 0);
    }

    @Test
    public void testNullTechnology_doesNotThrowNPE() {
        BatteryInfo info = new BatteryInfo();
        try {
            info.setTechnology(null);
            assertNull(info.getTechnology());
        } catch (NullPointerException e) {
            fail("Setting null technology should not throw NPE: " + e.getMessage());
        }
    }

    /**
     * 稳定性测试: 并发读写 BatteryInfo 不应崩溃
     */
    @Test
    public void testConcurrentReadWrite_doesNotCrash() throws InterruptedException {
        final BatteryInfo info = new BatteryInfo();
        int threadCount = 20;
        final int iterationsPerThread = 1000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int seed = i;
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        if (seed % 2 == 0) {
                            info.setLevel(j % 101);
                            info.setVoltage(3.5f + (j % 100) * 0.01f);
                            info.setTemperature(20f + (j % 30));
                        } else {
                            int level = info.getLevel();
                            float voltage = info.getVoltage();
                            // 触发实际读取，确保可见性
                            if (level < 0 || voltage < 0) {
                                throw new AssertionError("Invalid read");
                            }
                        }
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }, "BatteryInfo-Worker-" + i).start();
        }
        start.countDown();
        assertTrue("Concurrent test timed out", done.await(30, TimeUnit.SECONDS));
        assertNull("Concurrent test failed: " + error.get(), error.get());
    }

    /**
     * 性能测试: 大量对象创建和属性访问应在合理时间内完成
     */
    @Test
    public void testPerformance_createAndAccess_manyObjects() {
        long elapsed = TestUtils.measureExecutionTime("BatteryInfo.createAndAccess", () -> {
            BatteryInfo[] infos = new BatteryInfo[TestUtils.ITERATION_STRESS];
            for (int i = 0; i < infos.length; i++) {
                BatteryInfo info = new BatteryInfo();
                info.setLevel(i % 101);
                info.setVoltage(3.5f + (i % 100) * 0.01f);
                info.setTemperature(20f + (i % 30));
                info.setCurrentNow(i % 1000 - 500);
                info.setDesignCapacity(4500);
                info.setCurrentCapacity(4200);
                info.setCycleCount(i);
                info.setHealthPercentage(95f - (i % 30));
                info.setCharging(i % 2 == 0);
                info.setTechnology("Li-poly");
                info.setTimestamp(System.currentTimeMillis() + i);
                infos[i] = info;
            }
            // 强制读取
            long sum = 0;
            for (BatteryInfo info : infos) {
                sum += info.getLevel() + info.getCurrentNow();
            }
            // 防止 JIT 优化
            assertTrue(sum >= Long.MIN_VALUE);
        });
        assertTrue("Performance too slow: " + elapsed + "ms", elapsed < 2000);
    }
}
