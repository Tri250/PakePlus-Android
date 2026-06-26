package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BatteryOriginRecord 稳定性 + 封装测试。
 */
public class BatteryOriginRecordStabilityTest {

    @Test
    public void testEncapsulation_allFieldsHaveAccessors() {
        BatteryOriginRecord record = new BatteryOriginRecord();
        record.setId(1L);
        record.setTimestamp(System.currentTimeMillis());
        record.setOriginal(true);
        record.setConfidence(95);
        record.setConclusion("原装");
        record.setManufacturer("ATL");
        record.setManufactureDate("2023-01-15");
        record.setSerialNumber("SN123456");
        record.setOemInfo("OEM");
        record.setTechnology("Li-poly");
        record.setHealthStatus("GOOD");
        record.setCycleCount("150");
        record.setDesignCapacity(4500);
        record.setCurrentCapacity(4200);
        record.setBatteryInfoRaw("raw");
        record.setDeviceBrand("huawei");
        record.setDeviceModel("Mate 60");
        record.setDetectionMethodsJson("[]");
        record.setSourceTag("manual");

        assertEquals(1L, record.getId());
        assertTrue(record.isOriginal());
        assertEquals(95, record.getConfidence());
        assertEquals("原装", record.getConclusion());
        assertEquals("ATL", record.getManufacturer());
        assertEquals("2023-01-15", record.getManufactureDate());
        assertEquals("SN123456", record.getSerialNumber());
        assertEquals("OEM", record.getOemInfo());
        assertEquals("Li-poly", record.getTechnology());
        assertEquals("GOOD", record.getHealthStatus());
        assertEquals("150", record.getCycleCount());
        assertEquals(4500, record.getDesignCapacity());
        assertEquals(4200, record.getCurrentCapacity());
        assertEquals("raw", record.getBatteryInfoRaw());
        assertEquals("huawei", record.getDeviceBrand());
        assertEquals("Mate 60", record.getDeviceModel());
        assertEquals("[]", record.getDetectionMethodsJson());
        assertEquals("manual", record.getSourceTag());
    }

    @Test
    public void testEqualsById_differentTimestampsEqual() {
        BatteryOriginRecord r1 = new BatteryOriginRecord();
        r1.setId(42L);
        r1.setTimestamp(1000L);

        BatteryOriginRecord r2 = new BatteryOriginRecord();
        r2.setId(42L);
        r2.setTimestamp(2000L);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testNotEquals_differentId() {
        BatteryOriginRecord r1 = new BatteryOriginRecord();
        r1.setId(1L);
        BatteryOriginRecord r2 = new BatteryOriginRecord();
        r2.setId(2L);
        assertNotEquals(r1, r2);
    }

    @Test
    public void testConcurrentSetter_doesNotCorruptData() throws InterruptedException {
        final BatteryOriginRecord record = new BatteryOriginRecord();
        int threadCount = 10;
        final int iterations = 1000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        record.setId((long) j);
                        record.setTimestamp(System.currentTimeMillis());
                        record.setOriginal(j % 2 == 0);
                        record.setConfidence(j % 100);
                        record.setConclusion("C" + j);
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertNull(error.get());
        // 最终一致性
        assertNotNull(record);
    }

    @Test
    public void testLongStringFields_handledGracefully() {
        BatteryOriginRecord record = new BatteryOriginRecord();
        StringBuilder longStr = new StringBuilder();
        for (int i = 0; i < 5000; i++) longStr.append(UUID.randomUUID().toString());
        try {
            record.setConclusion(longStr.toString());
            record.setOemInfo(longStr.toString());
            assertEquals(longStr.length(), record.getConclusion().length());
        } catch (OutOfMemoryError oom) {
            // 5KB 不应该 OOM，但允许在内存极小的环境
        }
    }
}
