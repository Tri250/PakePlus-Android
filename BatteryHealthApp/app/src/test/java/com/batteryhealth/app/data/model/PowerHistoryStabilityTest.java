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
        h.setPower(25.5f);
        h.setVoltage(9.0f);
        h.setCurrent(2.83f);
        h.setBatteryLevel(85);
        h.setBatteryTemp(32.5f);
        h.setChargingPhase("constant_current");
        h.setChargeType("fast");
        h.setSessionId("session-001");

        assertEquals(25.5f, h.getPower(), 0.001f);
        assertEquals(9.0f, h.getVoltage(), 0.001f);
        assertEquals(2.83f, h.getCurrent(), 0.001f);
        assertEquals(85, h.getBatteryLevel());
        assertEquals(32.5f, h.getBatteryTemp(), 0.001f);
        assertEquals("constant_current", h.getChargingPhase());
        assertEquals("fast", h.getChargeType());
        assertEquals("session-001", h.getSessionId());
    }

    @Test
    public void testIsFastCharge_threshold() {
        PowerHistory h = new PowerHistory();
        h.setPower(17.9f);
        assertEquals(false, h.isFastCharge());
        h.setPower(18.0f);
        assertEquals(true, h.isFastCharge());
        h.setPower(100f);
        assertEquals(true, h.isFastCharge());
    }

    @Test
    public void testIsSuperCharge_threshold() {
        PowerHistory h = new PowerHistory();
        h.setPower(39.9f);
        assertEquals(false, h.isSuperCharge());
        h.setPower(40.0f);
        assertEquals(true, h.isSuperCharge());
    }

    @Test
    public void testGetChargeTypeDescription_branches() {
        PowerHistory h = new PowerHistory();
        h.setPower(0f);
        assertEquals("慢速充电", h.getChargeTypeDescription());
        h.setPower(10f);
        assertEquals("标准充电", h.getChargeTypeDescription());
        h.setPower(18f);
        assertEquals("普通快充", h.getChargeTypeDescription());
        h.setPower(40f);
        assertEquals("快速充电", h.getChargeTypeDescription());
        h.setPower(60f);
        assertEquals("超级快充", h.getChargeTypeDescription());
        h.setPower(100f);
        assertEquals("超快闪充", h.getChargeTypeDescription());
    }

    @Test
    public void testGetChargingPhaseDescription_branches() {
        PowerHistory h = new PowerHistory();
        h.setChargingPhase(null);
        assertEquals("未知", h.getChargingPhaseDescription());

        h.setChargingPhase("trickle");
        assertEquals("涓流充电", h.getChargingPhaseDescription());

        h.setChargingPhase("constant_current");
        assertEquals("恒流充电", h.getChargingPhaseDescription());

        h.setChargingPhase("constant_voltage");
        assertEquals("恒压充电", h.getChargingPhaseDescription());

        h.setChargingPhase("full");
        assertEquals("充电完成", h.getChargingPhaseDescription());

        h.setChargingPhase("other");
        assertEquals("充电中", h.getChargingPhaseDescription());
    }

    @Test
    public void testCalculatePower_voltageTimesCurrent() {
        PowerHistory h = new PowerHistory();
        h.setVoltage(10.0f);
        h.setCurrent(2.5f);
        h.calculatePower();
        assertEquals(25.0f, h.getPower(), 0.001f);
    }

    @Test
    public void testCalculatePower_zeroVoltage_noChange() {
        PowerHistory h = new PowerHistory();
        h.setPower(99.0f);
        h.setVoltage(0f);
        h.setCurrent(2.5f);
        h.calculatePower();
        // voltage=0, 不更新 power
        assertEquals(99.0f, h.getPower(), 0.001f);
    }

    @Test
    public void testNullStrings_handledGracefully() {
        PowerHistory h = new PowerHistory();
        h.setChargingPhase(null);
        h.setChargeType(null);
        h.setSessionId(null);
        assertNotNull(h);
    }

    @Test
    public void testDefaultConstructor_setsTimestamp() {
        PowerHistory h = new PowerHistory();
        long ts = h.getTimestamp();
        assertTrue("Default constructor should set timestamp", ts > 0L);
        long now = System.currentTimeMillis();
        assertTrue("Timestamp should be near now", Math.abs(now - ts) < 1000L);
    }

    @Test
    public void testPerformance_bulkCreation() {
        long elapsed = TestUtils.measureExecutionTime("PowerHistory.bulk", () -> {
            PowerHistory[] arr = new PowerHistory[TestUtils.ITERATION_STRESS];
            for (int i = 0; i < arr.length; i++) {
                PowerHistory h = new PowerHistory();
                h.setTimestamp(i);
                h.setPower(i % 50);
                h.setVoltage(5.0f);
                h.setCurrent(2.0f);
                h.setBatteryLevel(i % 100);
                h.setBatteryTemp(30.0f + (i % 20));
                h.setChargingPhase("constant_current");
                h.setChargeType("fast");
                arr[i] = h;
            }
            assertTrue(arr.length == TestUtils.ITERATION_STRESS);
        });
        assertTrue("Bulk creation too slow: " + elapsed + "ms", elapsed < 1000);
    }
}
