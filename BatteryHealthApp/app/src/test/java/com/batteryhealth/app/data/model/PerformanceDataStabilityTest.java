package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

/**
 * PerformanceData 稳定性 + 性能测试。
 */
public class PerformanceDataStabilityTest {

    @Test
    public void testRoundTrip_allFields() {
        PerformanceData d = new PerformanceData();
        d.setTimestamp(System.currentTimeMillis());
        d.setCpuUsage(45.5f);
        d.setCpuFreqMax(2400000);
        d.setCpuFreqCurrent(1800000);
        d.setMemoryTotal(8192L);
        d.setMemoryUsed(4096L);
        d.setMemoryFree(4096L);
        d.setAppPackage("com.batteryhealth.app");
        d.setAppName("BatteryHealth");
        d.setAppMemory(256L);
        d.setAppCpuTime(1000L);
        d.setFrameDropCount(2);
        d.setFrameTotal(60);
        d.setFps(58.0f);
        d.setPerformanceScore(85);
        d.setHasIssue(false);
        d.setIssueType(null);
        d.setIssueDescription("performance sample");

        assertEquals(45.5f, d.getCpuUsage(), 0.001f);
        assertEquals(2400000, d.getCpuFreqMax());
        assertEquals(1800000, d.getCpuFreqCurrent());
        assertEquals(8192L, d.getMemoryTotal());
        assertEquals(4096L, d.getMemoryUsed());
        assertEquals(4096L, d.getMemoryFree());
        assertEquals("com.batteryhealth.app", d.getAppPackage());
        assertEquals("BatteryHealth", d.getAppName());
        assertEquals(256L, d.getAppMemory());
        assertEquals(1000L, d.getAppCpuTime());
        assertEquals(2, d.getFrameDropCount());
        assertEquals(60, d.getFrameTotal());
        assertEquals(58.0f, d.getFps(), 0.001f);
        assertEquals(85, d.getPerformanceScore());
        assertFalse(d.isHasIssue());
    }

    @Test
    public void testGetMemoryUsagePercent_calculated() {
        PerformanceData d = new PerformanceData();
        d.setMemoryTotal(8192L);
        d.setMemoryUsed(4096L);
        assertEquals(50.0f, d.getMemoryUsagePercent(), 0.001f);

        d.setMemoryUsed(0L);
        assertEquals(0.0f, d.getMemoryUsagePercent(), 0.001f);
    }

    @Test
    public void testGetMemoryUsagePercent_zeroTotal() {
        PerformanceData d = new PerformanceData();
        d.setMemoryTotal(0L);
        d.setMemoryUsed(100L);
        assertEquals(0.0f, d.getMemoryUsagePercent(), 0.001f);
    }

    @Test
    public void testGetFrameDropRate_calculated() {
        PerformanceData d = new PerformanceData();
        d.setFrameTotal(100);
        d.setFrameDropCount(5);
        assertEquals(5.0f, d.getFrameDropRate(), 0.001f);

        d.setFrameTotal(0);
        assertEquals(0.0f, d.getFrameDropRate(), 0.001f);
    }

    @Test
    public void testGetPerformanceLevel_branches() {
        PerformanceData d = new PerformanceData();
        d.setPerformanceScore(95);
        assertEquals("优秀", d.getPerformanceLevel());
        d.setPerformanceScore(85);
        assertEquals("良好", d.getPerformanceLevel());
        d.setPerformanceScore(70);
        assertEquals("一般", d.getPerformanceLevel());
        d.setPerformanceScore(50);
        assertEquals("较差", d.getPerformanceLevel());
    }

    @Test
    public void testNegativeValues_handled() {
        PerformanceData d = new PerformanceData();
        d.setCpuUsage(-1.0f);
        d.setMemoryUsed(-100L);
        // 原始数据，不应崩溃
        assertEquals(-1.0f, d.getCpuUsage(), 0.001f);
    }

    @Test
    public void testNullStrings_handled() {
        PerformanceData d = new PerformanceData();
        d.setAppPackage(null);
        d.setAppName(null);
        d.setIssueType(null);
        d.setIssueDescription(null);
        assertNotNull(d);
    }

    @Test
    public void testPerformance_bulkCreation() {
        long elapsed = TestUtils.measureExecutionTime("PerformanceData.bulk", () -> {
            PerformanceData[] arr = new PerformanceData[TestUtils.ITERATION_STRESS];
            for (int i = 0; i < arr.length; i++) {
                PerformanceData d = new PerformanceData();
                d.setTimestamp(i);
                d.setCpuUsage(i % 100);
                d.setMemoryUsed(i * 1024L);
                d.setPerformanceScore(i % 100);
                arr[i] = d;
            }
            assertTrue(arr.length == TestUtils.ITERATION_STRESS);
        });
        assertTrue("Bulk creation too slow: " + elapsed + "ms", elapsed < 1500);
    }
}
