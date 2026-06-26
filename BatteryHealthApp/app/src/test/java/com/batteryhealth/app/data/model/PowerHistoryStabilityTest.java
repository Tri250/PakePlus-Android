package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

/**
 * PowerHistory 稳定性 + 性能测试。
 */
public class PowerHistoryStabilityTest {

    @Test
    public void testRoundTrip_allFields() {
        PowerHistory h = new PowerHistory();
        h.setTimestamp(System.currentTimeMillis());
        h.setType(PowerHistory.TYPE_CHARGING);
        h.setStartLevel(20);
        h.setEndLevel(85);
        h.setDurationMs(3600000L);
        h.setPowerAvg(25.5f);
        h.setPowerMax(45.2f);
        h.setTemperatureAvg(32.5f);
        h.setTemperatureMax(38.0f);
        h.setProtocol("PD");
        h.setSource("AC");
        h.setNotes("快速充电");
        h.setSessionId("session-001");

        assertEquals(PowerHistory.TYPE_CHARGING, h.getType());
        assertEquals(20, h.getStartLevel());
        assertEquals(85, h.getEndLevel());
        assertEquals(3600000L, h.getDurationMs());
        assertEquals(25.5f, h.getPowerAvg(), 0.001f);
        assertEquals(45.2f, h.getPowerMax(), 0.001f);
        assertEquals(32.5f, h.getTemperatureAvg(), 0.001f);
        assertEquals(38.0f, h.getTemperatureMax(), 0.001f);
        assertEquals("PD", h.getProtocol());
        assertEquals("AC", h.getSource());
        assertEquals("快速充电", h.getNotes());
        assertEquals("session-001", h.getSessionId());
    }

    @Test
    public void testTypeConstants() {
        assertEquals(0, PowerHistory.TYPE_CHARGING);
        assertEquals(1, PowerHistory.TYPE_DISCHARGING);
    }

    @Test
    public void testCalculateDelta_levelDifference() {
        PowerHistory h = new PowerHistory();
        h.setStartLevel(20);
        h.setEndLevel(85);
        assertEquals(65, h.getEndLevel() - h.getStartLevel());
    }

    @Test
    public void testNegativeLevel_doesNotCrash() {
        PowerHistory h = new PowerHistory();
        h.setStartLevel(-5);
        h.setEndLevel(1000);
        assertEquals(-5, h.getStartLevel());
        assertEquals(1000, h.getEndLevel());
    }

    @Test
    public void testNullStrings_handledGracefully() {
        PowerHistory h = new PowerHistory();
        h.setProtocol(null);
        h.setSource(null);
        h.setNotes(null);
        h.setSessionId(null);
        assertNotNull(h);
    }

    @Test
    public void testPerformance_bulkCreation() {
        long elapsed = TestUtils.measureExecutionTime("PowerHistory.bulk", () -> {
            PowerHistory[] arr = new PowerHistory[TestUtils.ITERATION_STRESS];
            for (int i = 0; i < arr.length; i++) {
                PowerHistory h = new PowerHistory();
                h.setTimestamp(i);
                h.setType(i % 2);
                h.setStartLevel(i % 100);
                h.setEndLevel((i + 30) % 100);
                h.setDurationMs(i * 1000L);
                h.setPowerAvg(i % 50);
                h.setProtocol("PD");
                arr[i] = h;
            }
            assertTrue(arr.length == TestUtils.ITERATION_STRESS);
        });
        assertTrue("Bulk creation too slow: " + elapsed + "ms", elapsed < 1000);
    }
}
