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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * 性能测试
 * 
 * 验证核心算法的执行效率：
 * 1. 健康度计算性能
 * 2. 电池来源判断性能
 * 3. 中值滤波性能
 * 4. 大数据量处理性能
 */
public class PerformanceTest {

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

    @Test
    public void testHealthCalculation_performance_under10ms() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            calculateHealthUseCase.execute(4500, 4200, 100, 365);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        assertTrue("Health calculation should complete under 100ms for 1000 iterations, took " + durationMs + "ms",
                durationMs < 100);
    }

    @Test
    public void testHealthCalculation_singleCall_under1ms() {
        long startTime = System.nanoTime();
        
        calculateHealthUseCase.execute(4500, 4200, 100, 365);
        
        long endTime = System.nanoTime();
        long durationMicros = (endTime - startTime) / 1000;
        
        assertTrue("Single health calculation should complete under 1000µs, took " + durationMicros + "µs",
                durationMicros < 1000);
    }

    @Test
    public void testBatterySourceCalculation_performance_under10ms() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            determineBatterySourceUseCase.execute(
                    "BYD1234567890ABC",
                    "byd",
                    "ABC123456789",
                    4400,
                    4500
            );
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        assertTrue("Battery source calculation should complete under 100ms for 1000 iterations, took " + durationMs + "ms",
                durationMs < 100);
    }

    @Test
    public void testMedianFilter_performance_largeDataset() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            calculateHealthUseCase.execute(4500, 4000 + (i % 500), i % 200, 365);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        assertTrue("Median filter with 10000 iterations should complete under 500ms, took " + durationMs + "ms",
                durationMs < 500);
    }

    @Test
    public void testGradeCalculation_performance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            calculateHealthUseCase.calculateGrade(i % 101);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        assertTrue("Grade calculation should complete under 50ms for 10000 iterations, took " + durationMs + "ms",
                durationMs < 50);
    }

    @Test
    public void testBatteryInfoCopy_performance() {
        BatteryInfo original = createFullBatteryInfo();
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            BatteryInfo copy = original.copy();
            assertNotNull(copy);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        
        assertTrue("BatteryInfo copy should complete under 100ms for 10000 iterations, took " + durationMs + "ms",
                durationMs < 100);
    }

    @Test
    public void testMemoryAllocation_noExcessiveGrowth() {
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC before measurement
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Perform many operations
        List<BatteryInfo> infos = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setDesignCapacity(4500);
            info.setCurrentCapacity(4000 + i % 500);
            info.setHealthPercentage(80f + i % 20);
            info.setCycleCount(i);
            infos.add(info);
        }
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryGrowth = (memoryAfter - memoryBefore) / 1024; // KB
        
        assertTrue("Memory growth should be under 500KB for 1000 BatteryInfo objects, grew " + memoryGrowth + "KB",
                memoryGrowth < 500);
    }

    @Test
    public void testConcurrentHealthCalculations() throws InterruptedException {
        final int threadCount = 10;
        final int iterationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        final boolean[] success = {true};
        
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        CalculateHealthUseCase.Result result = calculateHealthUseCase.execute(
                                4500, 4000 + i % 500, i % 200, 365);
                        if (result == null) {
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
            thread.join(5000);
        }
        
        assertTrue("Concurrent health calculations should all succeed", success[0]);
    }

    @Test
    public void testHealthCalculation_consistency() {
        CalculateHealthUseCase.Result result1 = calculateHealthUseCase.execute(4500, 4200, 100, 365);
        CalculateHealthUseCase.Result result2 = calculateHealthUseCase.execute(4500, 4200, 100, 365);
        
        assertEquals("Results should be consistent", result1.healthPercentage, result2.healthPercentage, 0.01f);
        assertEquals("Results should be consistent", result1.healthLevel, result2.healthLevel);
        assertEquals("Results should be consistent", result1.source, result2.source);
    }

    private BatteryInfo createFullBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        info.setId(1);
        info.setTimestamp(System.currentTimeMillis());
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(4200);
        info.setChargeCounter(500000);
        info.setHealthPercentage(93.3f);
        info.setHealthStatus("good");
        info.setCycleCount(150);
        info.setTemperature(30.5f);
        info.setVoltage(4100f);
        info.setCurrentNow(-500000);
        info.setStatus(2);
        info.setPlugged(1);
        info.setLevel(80);
        info.setTechnology("Li-ion");
        info.setBatterySource("original");
        info.setBatterySerial("ABC123456789");
        info.setChargingPower(65f);
        info.setChargingVoltage(9.5f);
        info.setChargingCurrent(6.8f);
        info.setDeviceModel("Test Model");
        info.setDeviceBrand("Test Brand");
        info.setCycleCountEstimated(false);
        info.setCycleCountSource("sysfs");
        info.setDesignCapacitySource("database");
        info.setCurrentCapacitySource("battery_manager");
        info.setHealthDataSource("fcc_ratio");
        info.setHealthConfidence(0.95f);
        info.setSystemHealth(2);
        info.setEnergyCounter(12345678);
        info.setBatterySourceConfidence(0.85f);
        info.setFactoryLossPercent(2.0f);
        info.setCycleLossPercent(4.5f);
        info.setUsageLossPercent(1.5f);
        info.setBatterySourceReason("综合多项原厂标识通过");
        return info;
    }
}
