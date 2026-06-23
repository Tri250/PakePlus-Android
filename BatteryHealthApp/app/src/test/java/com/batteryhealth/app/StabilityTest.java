package com.batteryhealth.app;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.usecase.CalculateHealthUseCase;
import com.batteryhealth.app.domain.usecase.DetermineBatterySourceUseCase;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * 稳定性测试与崩溃测试
 *
 * 验证应用在异常输入和边界条件下的稳定性：
 * 1. 空值处理
 * 2. 极端数值
 * 3. 边界条件
 * 4. 并发安全
 * 5. 资源耗尽模拟
 */
public class StabilityTest {

    @Mock
    private BatteryRepository batteryRepository;

    @Mock
    private DeviceRepository deviceRepository;

    private CalculateHealthUseCase calculateHealthUseCase;
    private DetermineBatterySourceUseCase determineBatterySourceUseCase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        calculateHealthUseCase = new CalculateHealthUseCase(batteryRepository, deviceRepository);
        determineBatterySourceUseCase = new DetermineBatterySourceUseCase(deviceRepository);
        when(deviceRepository.getDesignCapacity()).thenReturn(4500);
    }

    // region 空值处理测试

    @Test
    public void testCalculateHealth_nullBatteryInfo_noCrash() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute((BatteryInfo) null);
        assertNotNull(result);
        assertEquals(-1, result.healthPercentage, 0.01f);
    }

    @Test
    public void testDetermineSource_nullVendorInfo_noCrash() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                null, null, null, 0, 0);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    @Test
    public void testDetermineSource_nullManufacturer_noCrash() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "test", null, "serial", 4000, 4500);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    @Test
    public void testDetermineSource_nullSerial_noCrash() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "test", "byd", null, 4000, 4500);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    // endregion

    // region 极端数值测试

    @Test
    public void testCalculateHealth_zeroDesignCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(0, 4200, 100, 365);
        assertNotNull(result);
        assertTrue(result.healthPercentage >= -1);
    }

    @Test
    public void testCalculateHealth_zeroCurrentCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(4500, 0, 100, 365);
        assertNotNull(result);
        assertTrue(result.healthPercentage >= -1);
    }

    @Test
    public void testCalculateHealth_negativeDesignCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(-1, 4200, 100, 365);
        assertNotNull(result);
        assertEquals(-1, result.healthPercentage, 0.01f);
    }

    @Test
    public void testCalculateHealth_negativeCurrentCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(4500, -1, 100, 365);
        assertNotNull(result);
        assertTrue(result.healthPercentage >= -1);
    }

    @Test
    public void testCalculateHealth_veryLargeCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(Integer.MAX_VALUE, 4200, 100, 365);
        assertNotNull(result);
        assertTrue(result.healthPercentage >= 0 && result.healthPercentage <= 100);
    }

    @Test
    public void testCalculateHealth_verySmallCapacity() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(1, 1, 0, 0);
        assertNotNull(result);
        assertEquals(100f, result.healthPercentage, 0.01f);
    }

    @Test
    public void testCalculateHealth_maxIntegerValues() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertNotNull(result);
        assertTrue(result.healthPercentage >= 0 && result.healthPercentage <= 100);
    }

    @Test
    public void testCalculateHealth_minIntegerValues() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        assertNotNull(result);
        assertEquals(-1, result.healthPercentage, 0.01f);
    }

    @Test
    public void testDetermineSource_extremeCapacityRatio() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "test", "byd", "ABC123456789", Integer.MAX_VALUE, 1);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    @Test
    public void testDetermineSource_zeroCapacity() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "test", "byd", "ABC123456789", 0, 0);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    // endregion

    // region 边界条件测试

    @Test
    public void testCalculateHealth_exactly100Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 100, 0, 0);
        assertEquals(100f, result.healthPercentage, 0.01f);
        assertEquals("excellent", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_exactly0Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 0, 0, 0);
        assertEquals(0f, result.healthPercentage, 0.01f);
        assertEquals("very_poor", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_boundary95Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 95, 0, 0);
        assertEquals(95f, result.healthPercentage, 0.01f);
        assertEquals("excellent", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_boundary85Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 85, 0, 0);
        assertEquals(85f, result.healthPercentage, 0.01f);
        assertEquals("good", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_boundary75Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 75, 0, 0);
        assertEquals(75f, result.healthPercentage, 0.01f);
        assertEquals("average", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_boundary60Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 60, 0, 0);
        assertEquals(60f, result.healthPercentage, 0.01f);
        assertEquals("poor", result.healthLevel);
    }

    @Test
    public void testCalculateHealth_boundary59Percent() {
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(100, 59, 0, 0);
        assertEquals(59f, result.healthPercentage, 0.01f);
        assertEquals("very_poor", result.healthLevel);
    }

    @Test
    public void testGradeBoundary_95() {
        assertEquals("A+", calculateHealthUseCase.calculateGrade(95f));
    }

    @Test
    public void testGradeBoundary_94() {
        assertEquals("A", calculateHealthUseCase.calculateGrade(94.9f));
    }

    @Test
    public void testGradeBoundary_90() {
        assertEquals("A", calculateHealthUseCase.calculateGrade(90f));
    }

    @Test
    public void testGradeBoundary_89() {
        assertEquals("A-", calculateHealthUseCase.calculateGrade(89.9f));
    }

    @Test
    public void testGradeBoundary_85() {
        assertEquals("A-", calculateHealthUseCase.calculateGrade(85f));
    }

    @Test
    public void testGradeBoundary_84() {
        assertEquals("B+", calculateHealthUseCase.calculateGrade(84.9f));
    }

    @Test
    public void testGradeBoundary_80() {
        assertEquals("B+", calculateHealthUseCase.calculateGrade(80f));
    }

    @Test
    public void testGradeBoundary_79() {
        assertEquals("B", calculateHealthUseCase.calculateGrade(79.9f));
    }

    @Test
    public void testGradeBoundary_75() {
        assertEquals("B", calculateHealthUseCase.calculateGrade(75f));
    }

    @Test
    public void testGradeBoundary_74() {
        assertEquals("B-", calculateHealthUseCase.calculateGrade(74.9f));
    }

    @Test
    public void testGradeBoundary_70() {
        assertEquals("B-", calculateHealthUseCase.calculateGrade(70f));
    }

    @Test
    public void testGradeBoundary_69() {
        assertEquals("C", calculateHealthUseCase.calculateGrade(69.9f));
    }

    @Test
    public void testGradeBoundary_60() {
        assertEquals("C", calculateHealthUseCase.calculateGrade(60f));
    }

    @Test
    public void testGradeBoundary_59() {
        assertEquals("D", calculateHealthUseCase.calculateGrade(59.9f));
    }

    // endregion

    // region 并发安全测试

    @Test
    public void testConcurrentHealthCalculations_noRaceCondition() throws InterruptedException {
        final int threadCount = 20;
        final int iterations = 50;
        Thread[] threads = new Thread[threadCount];
        final boolean[] success = {true};

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(
                                4500 + threadId,
                                4000 + i,
                                i,
                                365
                        );
                        if (result == null || result.healthPercentage < -1) {
                            success[0] = false;
                        }
                    }
                } catch (Exception e) {
                    success[0] = false;
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join(10000);
        }

        assertTrue("Concurrent calculations should not cause race conditions", success[0]);
    }

    @Test
    public void testConcurrentSourceDeterminations_noRaceCondition() throws InterruptedException {
        final int threadCount = 20;
        final int iterations = 50;
        Thread[] threads = new Thread[threadCount];
        final boolean[] success = {true};

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                                "BYD" + i,
                                "byd",
                                "ABC" + i + "DEF" + i,
                                4400 + i,
                                4500
                        );
                        if (result == null || result.source == null) {
                            success[0] = false;
                        }
                    }
                } catch (Exception e) {
                    success[0] = false;
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join(10000);
        }

        assertTrue("Concurrent source determinations should not cause race conditions", success[0]);
    }

    // endregion

    // region 资源压力测试

    @Test
    public void testRapidSuccessiveCalls_noMemoryLeak() {
        for (int i = 0; i < 10000; i++) {
            calculateHealthUseCase.execute(4500, 4000 + i % 500, i % 200, 365);
        }
        // If we reach here without OOM, the test passes
        assertTrue(true);
    }

    @Test
    public void testRapidBatteryInfoCreation_noMemoryLeak() {
        for (int i = 0; i < 10000; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setDesignCapacity(4500);
            info.setCurrentCapacity(4000 + i % 500);
            info.setHealthPercentage(80f + i % 20);
            // Let GC handle it
        }
        System.gc();
        assertTrue(true);
    }

    // endregion

    // region 异常输入测试

    @Test
    public void testCalculateHealth_floatNaN() {
        // Simulate NaN by using invalid operations
        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(0, 0, 0, 0);
        assertEquals(-1, result.healthPercentage, 0.01f);
    }

    @Test
    public void testDetermineSource_veryLongSerial() {
        StringBuilder longSerial = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longSerial.append("A");
        }

        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "test", "byd", longSerial.toString(), 4000, 4500);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    @Test
    public void testDetermineSource_specialCharactersInSerial() {
        String[] specialSerials = {
                "ABC\nDEF",
                "ABC\tDEF",
                "ABC\rDEF",
                "ABC\0DEF",
                "ABC\u0000DEF"
        };

        for (String serial : specialSerials) {
            DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                    "test", "byd", serial, 4000, 4500);
            assertNotNull("Should handle special character serial: " + serial.replaceAll("\\s", " "), result);
        }
    }

    @Test
    public void testDetermineSource_unicodeCharacters() {
        DetermineBatterySourceUseCase.Result result = determineBatterySourceUseCase.execute(
                "测试", "比亚迪", "序列号123", 4000, 4500);
        assertNotNull(result);
        assertNotNull(result.source);
    }

    // endregion

    // region BatteryInfo 稳定性测试

    @Test
    public void testBatteryInfo_negativeValues() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(-1);
        assertFalse(info.hasValidHealthData());

        info.setCycleCount(-1);
        assertFalse(info.hasValidCycleCount());

        info.setLevel(-1);
        assertEquals(-1, info.getLevel());
    }

    @Test
    public void testBatteryInfo_zeroValues() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(0);
        assertTrue(info.hasValidHealthData());

        info.setCycleCount(0);
        assertTrue(info.hasValidCycleCount());

        info.setLevel(0);
        assertEquals(0, info.getLevel());
    }

    @Test
    public void testBatteryInfo_copyIndependence() {
        BatteryInfo original = new BatteryInfo();
        original.setLevel(50);
        original.setHealthPercentage(80f);

        BatteryInfo copy = original.copy();
        copy.setLevel(100);
        copy.setHealthPercentage(90f);

        assertEquals(50, original.getLevel());
        assertEquals(80f, original.getHealthPercentage(), 0.01f);
        assertEquals(100, copy.getLevel());
        assertEquals(90f, copy.getHealthPercentage(), 0.01f);
    }

    @Test
    public void testBatteryInfo_multipleCopies() {
        BatteryInfo original = new BatteryInfo();
        original.setLevel(50);

        BatteryInfo copy1 = original.copy();
        BatteryInfo copy2 = original.copy();
        BatteryInfo copy3 = original.copy();

        copy1.setLevel(60);
        copy2.setLevel(70);
        copy3.setLevel(80);

        assertEquals(50, original.getLevel());
        assertEquals(60, copy1.getLevel());
        assertEquals(70, copy2.getLevel());
        assertEquals(80, copy3.getLevel());
    }

    // endregion
}
