package com.batteryhealth.app.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Converters 类型转换稳定性 + 边界值测试。
 */
public class ConvertersStabilityTest {

    private final Converters converters = new Converters();

    // ==================== Date 转换测试 ====================

    @Test
    public void testDateToTimestamp_normalDate() {
        Date date = new Date(1700000000000L);
        Long timestamp = Converters.dateToTimestamp(date);
        assertNotNull(timestamp);
        assertEquals(1700000000000L, (long) timestamp);
    }

    @Test
    public void testDateToTimestamp_nullDate() {
        assertNull(Converters.dateToTimestamp(null));
    }

    @Test
    public void testDateToTimestamp_epochZero() {
        Date date = new Date(0L);
        Long timestamp = Converters.dateToTimestamp(date);
        assertNotNull(timestamp);
        assertEquals(0L, (long) timestamp);
    }

    @Test
    public void testDateToTimestamp_maxLong() {
        Date date = new Date(Long.MAX_VALUE);
        Long timestamp = Converters.dateToTimestamp(date);
        assertNotNull(timestamp);
        assertEquals(Long.MAX_VALUE, (long) timestamp);
    }

    @Test
    public void testFromTimestamp_normalTimestamp() {
        Date date = Converters.fromTimestamp(1700000000000L);
        assertNotNull(date);
        assertEquals(1700000000000L, date.getTime());
    }

    @Test
    public void testFromTimestamp_nullTimestamp() {
        assertNull(Converters.fromTimestamp(null));
    }

    @Test
    public void testFromTimestamp_zeroTimestamp() {
        Date date = Converters.fromTimestamp(0L);
        assertNotNull(date);
        // TimeZone 可能会调整到 1970-01-01 本地时区
        assertEquals(0L, date.getTime());
    }

    @Test
    public void testFromTimestamp_negativeTimestamp() {
        // 1969 年之前
        Date date = Converters.fromTimestamp(-86400000L);
        assertNotNull(date);
        assertEquals(-86400000L, date.getTime());
    }

    @Test
    public void testRoundTrip_consistency() {
        long[] timestamps = {
                0L, 1L, 1000L, 1700000000000L,
                System.currentTimeMillis(),
                -1000L, -86400000L
        };
        for (long ts : timestamps) {
            Date date = Converters.fromTimestamp(ts);
            assertNotNull("date should not be null for ts=" + ts, date);
            Long back = Converters.dateToTimestamp(date);
            assertNotNull(back);
            assertEquals("round trip should be consistent for ts=" + ts, ts, (long) back);
        }
    }

    @Test
    public void testRoundTrip_currentTime() {
        long now = System.currentTimeMillis();
        Date date = Converters.fromTimestamp(now);
        assertNotNull(date);
        assertEquals(now, date.getTime());
        Long ts = Converters.dateToTimestamp(date);
        assertNotNull(ts);
        assertEquals(now, (long) ts);
    }

    @Test
    public void testTimeZone_independence() {
        // Converters 内部不依赖默认时区
        long ts = 1700000000000L;
        Date date = Converters.fromTimestamp(ts);
        assertEquals(ts, date.getTime());
    }

    @Test
    public void testCalendarIntegration() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2024, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date date = cal.getTime();
        Long ts = Converters.dateToTimestamp(date);
        assertNotNull(ts);
        // 重新解析
        Date back = Converters.fromTimestamp(ts);
        assertNotNull(back);
        assertEquals(date.getTime(), back.getTime());
    }

    @Test
    public void testStability_manyConversions() {
        for (int i = 0; i < 10000; i++) {
            Date date = new Date(1700000000000L + i);
            Long ts = Converters.dateToTimestamp(date);
            assertNotNull(ts);
            Date back = Converters.fromTimestamp(ts);
            assertNotNull(back);
            assertEquals(date.getTime(), back.getTime());
        }
    }
}
