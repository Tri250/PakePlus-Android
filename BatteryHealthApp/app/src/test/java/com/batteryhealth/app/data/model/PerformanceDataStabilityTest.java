package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
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
        d.setCpuFrequency(2400000);
        d.setCpuCores(8);
        d.setCpuTemp(55.0f);
        d.setMemoryUsed(4096L);
        d.setMemoryTotal(8192L);
        d.setMemoryUsagePercent(50.0f);
        d.setStorageUsed(64000000L);
        d.setStorageTotal(128000000L);
        d.setStorageUsagePercent(50.0f);
        d.setGpuUsage(20.0f);
        d.setGpuFrequency(700000);
        d.setGpuTemp(60.0f);
        d.setFrameRate(60);
        d.setJankCount(2);
        d.setPerformanceScore(85);
        d.setNotes("performance sample");

        assertEquals(45.5f, d.getCpuUsage(), 0.001f);
        assertEquals(2400000, d.getCpuFrequency());
        assertEquals(8, d.getCpuCores());
        assertEquals(55.0f, d.getCpuTemp(), 0.001f);
        assertEquals(4096L, d.getMemoryUsed());
        assertEquals(8192L, d.getMemoryTotal());
        assertEquals(50.0f, d.getMemoryUsagePercent(), 0.001f);
        assertEquals(64000000L, d.getStorageUsed());
        assertEquals(128000000L, d.getStorageTotal());
        assertEquals(50.0f, d.getStorageUsagePercent(), 0.001f);
        assertEquals(20.0f, d.getGpuUsage(), 0.001f);
        assertEquals(700000, d.getGpuFrequency());
        assertEquals(60.0f, d.getGpuTemp(), 0.001f);
        assertEquals(60, d.getFrameRate());
        assertEquals(2, d.getJankCount());
        assertEquals(85, d.getPerformanceScore());
        assertEquals("performance sample", d.getNotes());
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
    public void testNullNotes_handled() {
        PerformanceData d = new PerformanceData();
        d.setNotes(null);
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
