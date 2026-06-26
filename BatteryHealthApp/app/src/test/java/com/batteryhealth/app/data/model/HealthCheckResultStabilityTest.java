package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.data.model.HealthCheckResult.Builder;

import org.junit.Test;

/**
 * HealthCheckResult 稳定性 + 严重性 + 状态机测试。
 */
public class HealthCheckResultStabilityTest {

    @Test
    public void testBuilder_requiredFields() {
        HealthCheckResult result = new Builder()
                .setId("test-id")
                .setTitle("电池温度")
                .setCategory("battery")
                .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                .setStatus("warning")
                .build();
        assertNotNull(result);
        assertEquals("test-id", result.getId());
        assertEquals("电池温度", result.getTitle());
        assertEquals("battery", result.getCategory());
        assertEquals(HealthCheckResult.SEVERITY_WARNING, result.getSeverity());
        assertEquals("warning", result.getStatus());
    }

    @Test
    public void testBuilder_defaultTimestamp() {
        long before = System.currentTimeMillis();
        HealthCheckResult result = new Builder()
                .setId("test")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build();
        long after = System.currentTimeMillis();
        assertTrue("timestamp should be in range",
                result.getTimestamp() >= before && result.getTimestamp() <= after);
    }

    @Test
    public void testBuilder_explicitTimestamp() {
        long explicit = 123456789L;
        HealthCheckResult result = new Builder()
                .setId("test")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .setTimestamp(explicit)
                .build();
        assertEquals(explicit, result.getTimestamp());
    }

    @Test
    public void testSeverity_severityConstants() {
        assertEquals(0, HealthCheckResult.SEVERITY_GOOD);
        assertEquals(1, HealthCheckResult.SEVERITY_INFO);
        assertEquals(2, HealthCheckResult.SEVERITY_WARNING);
        assertEquals(3, HealthCheckResult.SEVERITY_CRITICAL);
    }

    @Test
    public void testFixAction_constants() {
        assertEquals(0, HealthCheckResult.FIX_ACTION_NONE);
        assertEquals(1, HealthCheckResult.FIX_ACTION_OPEN_SETTINGS);
        assertEquals(2, HealthCheckResult.FIX_ACTION_OPEN_NOTIFICATION_SETTINGS);
    }

    @Test
    public void testEqualsAndHashCode_byId() {
        HealthCheckResult r1 = new Builder()
                .setId("same-id")
                .setTitle("t1")
                .setCategory("c1")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build();
        HealthCheckResult r2 = new Builder()
                .setId("same-id")
                .setTitle("t2")
                .setCategory("c2")
                .setSeverity(HealthCheckResult.SEVERITY_WARNING)
                .setStatus("warn")
                .build();
        // equals 比较 id
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testEqualsAndHashCode_differentId() {
        HealthCheckResult r1 = new Builder()
                .setId("id-1")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build();
        HealthCheckResult r2 = new Builder()
                .setId("id-2")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build();
        assertFalse(r1.equals(r2));
    }

    @Test
    public void testItemScore_rangeCheck() {
        HealthCheckResult r = new Builder()
                .setId("t")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .setItemScore(85)
                .build();
        assertEquals(85, r.getItemScore());
    }

    @Test
    public void testRepairable_flag() {
        HealthCheckResult r1 = new Builder()
                .setId("t")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .setRepairable(true)
                .build();
        assertTrue(r1.isRepairable());

        HealthCheckResult r2 = new Builder()
                .setId("t2")
                .setTitle("t")
                .setCategory("c")
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus("info")
                .build();
        assertFalse(r2.isRepairable());
    }

    @Test
    public void testNullFields_handledGracefully() {
        HealthCheckResult r = new Builder()
                .setId("t")
                .setTitle(null)
                .setCategory(null)
                .setSeverity(HealthCheckResult.SEVERITY_INFO)
                .setStatus(null)
                .setDescription(null)
                .setAdvice(null)
                .setUnit(null)
                .setValue(null)
                .build();
        assertNotNull(r);
        assertNull(r.getTitle());
        assertNull(r.getCategory());
    }
}
