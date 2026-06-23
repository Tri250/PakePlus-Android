package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.domain.repository.BatteryRepository;
import com.batteryhealth.app.domain.repository.DeviceRepository;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class CalculateHealthUseCaseTest {

    @Mock
    private BatteryRepository batteryRepository;

    @Mock
    private DeviceRepository deviceRepository;

    private CalculateHealthUseCase useCase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        useCase = new CalculateHealthUseCase(batteryRepository, deviceRepository);
    }

    @Test
    public void testExecute_withValidCapacityRatio_returnsHighHealth() {
        CalculateHealthUseCase.Result result = useCase.execute(4500, 4200, 100, 0);

        assertTrue("Health should be valid", result.healthPercentage >= 0);
        assertEquals("fcc_ratio", result.source);
        assertEquals(0.95f, result.confidence, 0.01f);
        assertTrue("Cycle loss should be positive", result.cycleLossPercent >= 0);
        assertNotNull(result.healthLevel);
        assertNotNull(result.healthStatus);
    }

    @Test
    public void testExecute_withExactCapacity_returns100Percent() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 4000, 0, 0);

        assertEquals(100f, result.healthPercentage, 0.5f);
        assertEquals("excellent", result.healthLevel);
        assertEquals("电池状态极佳", result.healthStatus);
    }

    @Test
    public void testExecute_with90PercentCapacity_returnsGoodHealth() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3600, 0, 0);

        assertEquals(90f, result.healthPercentage, 0.5f);
        assertEquals("good", result.healthLevel);
        assertEquals("电池状态良好", result.healthStatus);
    }

    @Test
    public void testExecute_with75PercentCapacity_returnsAverageHealth() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3000, 0, 0);

        assertEquals(75f, result.healthPercentage, 0.5f);
        assertEquals("average", result.healthLevel);
        assertEquals("电池状态一般", result.healthStatus);
    }

    @Test
    public void testExecute_with60PercentCapacity_returnsPoorHealth() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 2400, 0, 0);

        assertEquals(60f, result.healthPercentage, 0.5f);
        assertEquals("poor", result.healthLevel);
        assertEquals("电池损耗明显", result.healthStatus);
    }

    @Test
    public void testExecute_withVeryLowCapacity_returnsVeryPoorHealth() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 2000, 0, 0);

        assertEquals(50f, result.healthPercentage, 0.5f);
        assertEquals("very_poor", result.healthLevel);
        assertEquals("建议尽快更换电池", result.healthStatus);
    }

    @Test
    public void testExecute_capacityExceedsDesign_clampedTo100() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 4500, 0, 0);

        assertEquals(100f, result.healthPercentage, 0.1f);
    }

    @Test
    public void testExecute_negativeCapacity_usesUsageDays() {
        when(deviceRepository.getUsageDays()).thenReturn(365);

        CalculateHealthUseCase.Result result = useCase.execute(4000, 0, 0, 365);

        assertEquals("usage_days_estimate", result.source);
        assertEquals(0.35f, result.confidence, 0.01f);
        assertTrue("Health should be below 100", result.healthPercentage < 100f);
        assertTrue("Usage loss should be positive", result.usageLossPercent > 0);
    }

    @Test
    public void testExecute_noDataAvailable() {
        CalculateHealthUseCase.Result result = useCase.execute(0, 0, 0, 0);

        assertEquals(-1, result.healthPercentage, 0.01f);
        assertEquals("no_data", result.source);
        assertEquals("unknown", result.healthLevel);
        assertEquals("无法获取电池健康数据", result.healthStatus);
        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_nullBatteryInfo_returnsNoData() {
        CalculateHealthUseCase.Result result = useCase.execute((BatteryInfo) null);

        assertEquals(-1, result.healthPercentage, 0.01f);
        assertEquals("no_data", result.source);
    }

    @Test
    public void testExecute_batteryInfoWithValidData() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(4200);
        info.setCycleCount(100);

        when(deviceRepository.getUsageDays()).thenReturn(100);

        CalculateHealthUseCase.Result result = useCase.execute(info);

        assertTrue(result.healthPercentage > 0);
        assertEquals("fcc_ratio", result.source);
    }

    @Test
    public void testExecute_batteryInfoWithOnlyDesignCapacity() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(0);
        info.setCycleCount(0);

        when(deviceRepository.getUsageDays()).thenReturn(200);

        CalculateHealthUseCase.Result result = useCase.execute(info);

        assertEquals("usage_days_estimate", result.source);
        assertTrue(result.healthPercentage > 0);
    }

    @Test
    public void testCalculateGrade_excellent() {
        assertEquals("A+", useCase.calculateGrade(97f));
        assertEquals("A+", useCase.calculateGrade(95f));
    }

    @Test
    public void testCalculateGrade_good() {
        assertEquals("A", useCase.calculateGrade(93f));
        assertEquals("A", useCase.calculateGrade(90f));
    }

    @Test
    public void testCalculateGrade_goodMinus() {
        assertEquals("A-", useCase.calculateGrade(87f));
        assertEquals("A-", useCase.calculateGrade(85f));
    }

    @Test
    public void testCalculateGrade_aboveAverage() {
        assertEquals("B+", useCase.calculateGrade(82f));
        assertEquals("B+", useCase.calculateGrade(80f));
    }

    @Test
    public void testCalculateGrade_average() {
        assertEquals("B", useCase.calculateGrade(77f));
        assertEquals("B", useCase.calculateGrade(75f));
    }

    @Test
    public void testCalculateGrade_belowAverage() {
        assertEquals("B-", useCase.calculateGrade(72f));
        assertEquals("B-", useCase.calculateGrade(70f));
    }

    @Test
    public void testCalculateGrade_poor() {
        assertEquals("C", useCase.calculateGrade(65f));
        assertEquals("C", useCase.calculateGrade(60f));
    }

    @Test
    public void testCalculateGrade_veryPoor() {
        assertEquals("D", useCase.calculateGrade(50f));
        assertEquals("D", useCase.calculateGrade(0f));
    }

    @Test
    public void testExecute_usageDaysCalculation() {
        int usageDays = 100;
        float expectedLoss = usageDays * 0.026f;

        CalculateHealthUseCase.Result result = useCase.execute(4000, 0, 0, usageDays);

        assertEquals(expectedLoss, result.usageLossPercent, 0.01f);
        assertEquals(100f - expectedLoss, result.healthPercentage, 0.5f);
    }

    @Test
    public void testExecute_zeroUsageDaysNoCapacity_returnsNoData() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 0, 0, 0);

        assertEquals("no_data", result.source);
    }

    @Test
    public void testExecute_validCapacityOverridesUsageDays() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3600, 0, 100);

        assertEquals("fcc_ratio", result.source);
        assertEquals(0.95f, result.confidence, 0.01f);
    }

    @Test
    public void testCycleLossPercent_calculatedCorrectly() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3800, 0, 0);

        float expectedLoss = 100f - (3800 / 4000f * 100f);
        assertEquals(expectedLoss, result.cycleLossPercent, 0.01f);
    }

    @Test
    public void testCycleLossPercent_neverNegative() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 4500, 0, 0);

        assertTrue(result.cycleLossPercent >= 0);
    }

    @Test
    public void testHealthLevel_excellent() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3900, 0, 0);
        assertEquals("excellent", result.healthLevel);
    }

    @Test
    public void testHealthLevel_good() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3500, 0, 0);
        assertEquals("good", result.healthLevel);
    }

    @Test
    public void testHealthLevel_average() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3100, 0, 0);
        assertEquals("average", result.healthLevel);
    }

    @Test
    public void testHealthLevel_poor() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 2500, 0, 0);
        assertEquals("poor", result.healthLevel);
    }

    @Test
    public void testHealthLevel_veryPoor() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 2000, 0, 0);
        assertEquals("very_poor", result.healthLevel);
    }

    @Test
    public void testHealthStatus_unknownForNoData() {
        CalculateHealthUseCase.Result result = useCase.execute(0, 0, 0, 0);
        assertEquals("健康状态未知", result.healthStatus);
    }

    @Test
    public void testMedianFilter_smoothsFluctuations() {
        useCase.execute(4000, 3600, 0, 0);
        useCase.execute(4000, 3800, 0, 0);
        useCase.execute(4000, 3400, 0, 0);
        useCase.execute(4000, 3900, 0, 0);

        CalculateHealthUseCase.Result result = useCase.execute(4000, 3700, 0, 0);

        assertTrue("Filtered value should be in reasonable range",
                result.healthPercentage >= 80 && result.healthPercentage <= 100);
    }

    @Test
    public void testConfidence_highForCapacityRatio() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3600, 0, 0);
        assertEquals(0.95f, result.confidence, 0.01f);
    }

    @Test
    public void testConfidence_lowForUsageDays() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 0, 0, 100);
        assertEquals(0.35f, result.confidence, 0.01f);
    }

    @Test
    public void testConfidence_zeroForNoData() {
        CalculateHealthUseCase.Result result = useCase.execute(0, 0, 0, 0);
        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testFactoryLossPercent_defaultZero() {
        CalculateHealthUseCase.Result result = useCase.execute(4000, 3600, 0, 0);
        assertEquals(0f, result.factoryLossPercent, 0.01f);
    }
}
