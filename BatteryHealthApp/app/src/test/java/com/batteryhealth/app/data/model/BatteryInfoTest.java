package com.batteryhealth.app.data.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BatteryInfoTest {

    private BatteryInfo batteryInfo;

    @Before
    public void setUp() {
        batteryInfo = new BatteryInfo();
    }

    @Test
    public void testDefaultConstructor_setsTimestamp() {
        BatteryInfo info = new BatteryInfo();
        long now = System.currentTimeMillis();
        assertTrue("Timestamp should be recent", 
                Math.abs(info.getTimestamp() - now) < 1000);
    }

    @Test
    public void testDesignCapacity_setAndGet() {
        batteryInfo.setDesignCapacity(4500);
        assertEquals(4500, batteryInfo.getDesignCapacity());
    }

    @Test
    public void testCurrentCapacity_setAndGet() {
        batteryInfo.setCurrentCapacity(4200);
        assertEquals(4200, batteryInfo.getCurrentCapacity());
    }

    @Test
    public void testHealthPercentage_setAndGet() {
        batteryInfo.setHealthPercentage(92.5f);
        assertEquals(92.5f, batteryInfo.getHealthPercentage(), 0.01f);
    }

    @Test
    public void testHealthPercentage_negativeValue() {
        batteryInfo.setHealthPercentage(-1);
        assertEquals(-1, batteryInfo.getHealthPercentage(), 0.01f);
    }

    @Test
    public void testCycleCount_setAndGet() {
        batteryInfo.setCycleCount(150);
        assertEquals(150, batteryInfo.getCycleCount());
    }

    @Test
    public void testTemperature_setAndGet() {
        batteryInfo.setTemperature(28.5f);
        assertEquals(28.5f, batteryInfo.getTemperature(), 0.01f);
    }

    @Test
    public void testVoltage_setAndGet() {
        batteryInfo.setVoltage(4200f);
        assertEquals(4200f, batteryInfo.getVoltage(), 0.01f);
    }

    @Test
    public void testCurrentNow_setAndGet() {
        batteryInfo.setCurrentNow(-500000);
        assertEquals(-500000, batteryInfo.getCurrentNow());
    }

    @Test
    public void testLevel_setAndGet() {
        batteryInfo.setLevel(75);
        assertEquals(75, batteryInfo.getLevel());
    }

    @Test
    public void testTechnology_setAndGet() {
        batteryInfo.setTechnology("Li-ion");
        assertEquals("Li-ion", batteryInfo.getTechnology());
    }

    @Test
    public void testBatterySource_setAndGet() {
        batteryInfo.setBatterySource("original");
        assertEquals("original", batteryInfo.getBatterySource());
    }

    @Test
    public void testChargingPower_setAndGet() {
        batteryInfo.setChargingPower(67.5f);
        assertEquals(67.5f, batteryInfo.getChargingPower(), 0.01f);
    }

    @Test
    public void testChargingVoltageAndCurrent_calculatesPower() {
        batteryInfo.setChargingVoltage(10f);
        batteryInfo.setChargingCurrent(6.7f);
        batteryInfo.calculateChargingPower();
        assertEquals(67f, batteryInfo.getChargingPower(), 0.1f);
    }

    @Test
    public void testCalculateChargingPower_zeroValues() {
        batteryInfo.setChargingVoltage(0);
        batteryInfo.setChargingCurrent(0);
        batteryInfo.calculateChargingPower();
        assertEquals(0f, batteryInfo.getChargingPower(), 0.01f);
    }

    @Test
    public void testGetHealthGrade_excellent() {
        batteryInfo.setHealthPercentage(97f);
        assertEquals("A+", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthGrade_good() {
        batteryInfo.setHealthPercentage(88f);
        assertEquals("A", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthGrade_average() {
        batteryInfo.setHealthPercentage(78f);
        assertEquals("B", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthGrade_poor() {
        batteryInfo.setHealthPercentage(65f);
        assertEquals("C", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthGrade_veryPoor() {
        batteryInfo.setHealthPercentage(50f);
        assertEquals("D", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthGrade_negativeReturnsDash() {
        batteryInfo.setHealthPercentage(-1);
        assertEquals("--", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthDescription_excellent() {
        batteryInfo.setHealthPercentage(96f);
        assertEquals("电池状态极佳", batteryInfo.getHealthDescription());
    }

    @Test
    public void testGetHealthDescription_good() {
        batteryInfo.setHealthPercentage(87f);
        assertEquals("电池状态良好", batteryInfo.getHealthDescription());
    }

    @Test
    public void testGetHealthDescription_average() {
        batteryInfo.setHealthPercentage(77f);
        assertEquals("电池状态一般", batteryInfo.getHealthDescription());
    }

    @Test
    public void testGetHealthDescription_poor() {
        batteryInfo.setHealthPercentage(62f);
        assertEquals("电池损耗明显", batteryInfo.getHealthDescription());
    }

    @Test
    public void testGetHealthDescription_veryPoor() {
        batteryInfo.setHealthPercentage(55f);
        assertEquals("建议尽快更换电池", batteryInfo.getHealthDescription());
    }

    @Test
    public void testGetHealthDescription_negative() {
        batteryInfo.setHealthPercentage(-1);
        assertEquals("无法获取电池健康数据", batteryInfo.getHealthDescription());
    }

    @Test
    public void testHasValidHealthData_positive() {
        batteryInfo.setHealthPercentage(80f);
        assertTrue(batteryInfo.hasValidHealthData());
    }

    @Test
    public void testHasValidHealthData_negative() {
        batteryInfo.setHealthPercentage(-1);
        assertFalse(batteryInfo.hasValidHealthData());
    }

    @Test
    public void testHasValidCycleCount_positive() {
        batteryInfo.setCycleCount(100);
        assertTrue(batteryInfo.hasValidCycleCount());
    }

    @Test
    public void testHasValidCycleCount_negative() {
        batteryInfo.setCycleCount(-1);
        assertFalse(batteryInfo.hasValidCycleCount());
    }

    @Test
    public void testIsCharging_statusCharging() {
        batteryInfo.setStatus(2);
        assertTrue(batteryInfo.isCharging());
    }

    @Test
    public void testIsCharging_statusFull() {
        batteryInfo.setStatus(5);
        assertTrue(batteryInfo.isCharging());
    }

    @Test
    public void testIsCharging_statusDischarging() {
        batteryInfo.setStatus(3);
        assertFalse(batteryInfo.isCharging());
    }

    @Test
    public void testIsCharging_statusNotCharging() {
        batteryInfo.setStatus(4);
        assertFalse(batteryInfo.isCharging());
    }

    @Test
    public void testCopy_deepCopyAllFields() {
        batteryInfo.setId(1);
        batteryInfo.setTimestamp(1234567890L);
        batteryInfo.setDesignCapacity(4500);
        batteryInfo.setCurrentCapacity(4200);
        batteryInfo.setHealthPercentage(93.3f);
        batteryInfo.setCycleCount(150);
        batteryInfo.setTemperature(30.5f);
        batteryInfo.setVoltage(4100f);
        batteryInfo.setLevel(80);
        batteryInfo.setTechnology("Li-ion");
        batteryInfo.setBatterySource("original");
        batteryInfo.setChargingPower(65f);
        batteryInfo.setDeviceBrand("Xiaomi");
        batteryInfo.setDeviceModel("Mi 14");
        batteryInfo.setCycleCountEstimated(false);
        batteryInfo.setHealthConfidence(0.95f);
        batteryInfo.setFactoryLossPercent(2.0f);
        batteryInfo.setCycleLossPercent(4.5f);

        BatteryInfo copy = batteryInfo.copy();

        assertEquals(batteryInfo.getId(), copy.getId());
        assertEquals(batteryInfo.getTimestamp(), copy.getTimestamp());
        assertEquals(batteryInfo.getDesignCapacity(), copy.getDesignCapacity());
        assertEquals(batteryInfo.getCurrentCapacity(), copy.getCurrentCapacity());
        assertEquals(batteryInfo.getHealthPercentage(), copy.getHealthPercentage(), 0.01f);
        assertEquals(batteryInfo.getCycleCount(), copy.getCycleCount());
        assertEquals(batteryInfo.getTemperature(), copy.getTemperature(), 0.01f);
        assertEquals(batteryInfo.getVoltage(), copy.getVoltage(), 0.01f);
        assertEquals(batteryInfo.getLevel(), copy.getLevel());
        assertEquals(batteryInfo.getTechnology(), copy.getTechnology());
        assertEquals(batteryInfo.getBatterySource(), copy.getBatterySource());
        assertEquals(batteryInfo.getChargingPower(), copy.getChargingPower(), 0.01f);
        assertEquals(batteryInfo.getDeviceBrand(), copy.getDeviceBrand());
        assertEquals(batteryInfo.getDeviceModel(), copy.getDeviceModel());
        assertEquals(batteryInfo.isCycleCountEstimated(), copy.isCycleCountEstimated());
        assertEquals(batteryInfo.getHealthConfidence(), copy.getHealthConfidence(), 0.01f);
        assertEquals(batteryInfo.getFactoryLossPercent(), copy.getFactoryLossPercent(), 0.01f);
        assertEquals(batteryInfo.getCycleLossPercent(), copy.getCycleLossPercent(), 0.01f);
    }

    @Test
    public void testCopy_independentObject() {
        batteryInfo.setLevel(50);
        BatteryInfo copy = batteryInfo.copy();
        batteryInfo.setLevel(100);
        assertEquals(50, copy.getLevel());
    }

    @Test
    public void testStatus_setAndGet() {
        batteryInfo.setStatus(2);
        assertEquals(2, batteryInfo.getStatus());
    }

    @Test
    public void testPlugged_setAndGet() {
        batteryInfo.setPlugged(1);
        assertEquals(1, batteryInfo.getPlugged());
    }

    @Test
    public void testChargeCounter_setAndGet() {
        batteryInfo.setChargeCounter(500000);
        assertEquals(500000, batteryInfo.getChargeCounter());
    }

    @Test
    public void testHealthStatus_setAndGet() {
        batteryInfo.setHealthStatus("good");
        assertEquals("good", batteryInfo.getHealthStatus());
    }

    @Test
    public void testBatterySerial_setAndGet() {
        batteryInfo.setBatterySerial("ABC123456789");
        assertEquals("ABC123456789", batteryInfo.getBatterySerial());
    }

    @Test
    public void testChargingVoltage_setAndGet() {
        batteryInfo.setChargingVoltage(9.5f);
        assertEquals(9.5f, batteryInfo.getChargingVoltage(), 0.01f);
    }

    @Test
    public void testChargingCurrent_setAndGet() {
        batteryInfo.setChargingCurrent(3.0f);
        assertEquals(3.0f, batteryInfo.getChargingCurrent(), 0.01f);
    }

    @Test
    public void testDeviceModel_setAndGet() {
        batteryInfo.setDeviceModel("Test Model");
        assertEquals("Test Model", batteryInfo.getDeviceModel());
    }

    @Test
    public void testDeviceBrand_setAndGet() {
        batteryInfo.setDeviceBrand("Test Brand");
        assertEquals("Test Brand", batteryInfo.getDeviceBrand());
    }

    @Test
    public void testCycleCountEstimated_setAndGet() {
        batteryInfo.setCycleCountEstimated(true);
        assertTrue(batteryInfo.isCycleCountEstimated());
    }

    @Test
    public void testCycleCountSource_setAndGet() {
        batteryInfo.setCycleCountSource("battery_usage");
        assertEquals("battery_usage", batteryInfo.getCycleCountSource());
    }

    @Test
    public void testDesignCapacitySource_setAndGet() {
        batteryInfo.setDesignCapacitySource("sysfs");
        assertEquals("sysfs", batteryInfo.getDesignCapacitySource());
    }

    @Test
    public void testCurrentCapacitySource_setAndGet() {
        batteryInfo.setCurrentCapacitySource("charge_counter");
        assertEquals("charge_counter", batteryInfo.getCurrentCapacitySource());
    }

    @Test
    public void testHealthDataSource_setAndGet() {
        batteryInfo.setHealthDataSource("fcc_ratio");
        assertEquals("fcc_ratio", batteryInfo.getHealthDataSource());
    }

    @Test
    public void testHealthConfidence_setAndGet() {
        batteryInfo.setHealthConfidence(0.85f);
        assertEquals(0.85f, batteryInfo.getHealthConfidence(), 0.01f);
    }

    @Test
    public void testSystemHealth_setAndGet() {
        batteryInfo.setSystemHealth(2);
        assertEquals(2, batteryInfo.getSystemHealth());
    }

    @Test
    public void testEnergyCounter_setAndGet() {
        batteryInfo.setEnergyCounter(12345678);
        assertEquals(12345678, batteryInfo.getEnergyCounter());
    }

    @Test
    public void testBatterySourceConfidence_setAndGet() {
        batteryInfo.setBatterySourceConfidence(0.75f);
        assertEquals(0.75f, batteryInfo.getBatterySourceConfidence(), 0.01f);
    }

    @Test
    public void testFactoryLossPercent_setAndGet() {
        batteryInfo.setFactoryLossPercent(2.5f);
        assertEquals(2.5f, batteryInfo.getFactoryLossPercent(), 0.01f);
    }

    @Test
    public void testCycleLossPercent_setAndGet() {
        batteryInfo.setCycleLossPercent(5.2f);
        assertEquals(5.2f, batteryInfo.getCycleLossPercent(), 0.01f);
    }

    @Test
    public void testUsageLossPercent_setAndGet() {
        batteryInfo.setUsageLossPercent(3.1f);
        assertEquals(3.1f, batteryInfo.getUsageLossPercent(), 0.01f);
    }

    @Test
    public void testBatterySourceReason_setAndGet() {
        batteryInfo.setBatterySourceReason("综合多项原厂标识通过");
        assertEquals("综合多项原厂标识通过", batteryInfo.getBatterySourceReason());
    }

    @Test
    public void testGetHealthGrade_boundaryValues() {
        batteryInfo.setHealthPercentage(95f);
        assertEquals("A+", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(94.9f);
        assertEquals("A", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(90f);
        assertEquals("A", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(89.9f);
        assertEquals("A-", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(85f);
        assertEquals("A-", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(84.9f);
        assertEquals("B+", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(80f);
        assertEquals("B+", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(79.9f);
        assertEquals("B", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(75f);
        assertEquals("B", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(74.9f);
        assertEquals("B-", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(70f);
        assertEquals("B-", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(69.9f);
        assertEquals("C", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(60f);
        assertEquals("C", batteryInfo.getHealthGrade());

        batteryInfo.setHealthPercentage(59.9f);
        assertEquals("D", batteryInfo.getHealthGrade());
    }

    @Test
    public void testGetHealthDescription_boundaryValues() {
        batteryInfo.setHealthPercentage(95f);
        assertEquals("电池状态极佳", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(94.9f);
        assertEquals("电池状态良好", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(85f);
        assertEquals("电池状态良好", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(84.9f);
        assertEquals("电池状态一般", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(75f);
        assertEquals("电池状态一般", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(74.9f);
        assertEquals("电池损耗明显", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(60f);
        assertEquals("电池损耗明显", batteryInfo.getHealthDescription());

        batteryInfo.setHealthPercentage(59.9f);
        assertEquals("建议尽快更换电池", batteryInfo.getHealthDescription());
    }

    @Test
    public void testHasValidHealthData_zeroIsValid() {
        batteryInfo.setHealthPercentage(0f);
        assertTrue(batteryInfo.hasValidHealthData());
    }

    @Test
    public void testHasValidCycleCount_zeroIsValid() {
        batteryInfo.setCycleCount(0);
        assertTrue(batteryInfo.hasValidCycleCount());
    }

    @Test
    public void testIsCharging_statusZero() {
        batteryInfo.setStatus(0);
        assertFalse(batteryInfo.isCharging());
    }

    @Test
    public void testCalculateChargingPower_onlyVoltage() {
        batteryInfo.setChargingVoltage(10f);
        batteryInfo.setChargingCurrent(0f);
        batteryInfo.calculateChargingPower();
        assertEquals(0f, batteryInfo.getChargingPower(), 0.01f);
    }

    @Test
    public void testCalculateChargingPower_onlyCurrent() {
        batteryInfo.setChargingVoltage(0f);
        batteryInfo.setChargingCurrent(5f);
        batteryInfo.calculateChargingPower();
        assertEquals(0f, batteryInfo.getChargingPower(), 0.01f);
    }
}
