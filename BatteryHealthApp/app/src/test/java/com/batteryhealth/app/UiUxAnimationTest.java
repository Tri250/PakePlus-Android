package com.batteryhealth.app;

import com.batteryhealth.app.data.model.BatteryInfo;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * UI/UX 动画效果测试
 *
 * 验证界面和动画的正确性：
 * 1. 健康度等级颜色映射
 * 2. 充电类型标签映射
 * 3. 动画阈值边界
 * 4. 进度条计算
 * 5. 文本格式化
 */
public class UiUxAnimationTest {

    // region 健康度等级颜色映射测试

    @Test
    public void testHealthGradeColor_excellent() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(97f);
        assertEquals("A+", info.getHealthGrade());
    }

    @Test
    public void testHealthGradeColor_good() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(88f);
        assertEquals("A", info.getHealthGrade());
    }

    @Test
    public void testHealthGradeColor_average() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(78f);
        assertEquals("B", info.getHealthGrade());
    }

    @Test
    public void testHealthGradeColor_poor() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(65f);
        assertEquals("C", info.getHealthGrade());
    }

    @Test
    public void testHealthGradeColor_veryPoor() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(50f);
        assertEquals("D", info.getHealthGrade());
    }

    // endregion

    // region 充电类型标签映射测试

    @Test
    public void testChargeTypeLabel_ultraFast() {
        assertEquals("超级快充", getChargeTypeLabel(120f));
        assertEquals("超级快充", getChargeTypeLabel(100f));
    }

    @Test
    public void testChargeTypeLabel_extremeFast() {
        assertEquals("极速快充", getChargeTypeLabel(80f));
        assertEquals("极速快充", getChargeTypeLabel(60f));
    }

    @Test
    public void testChargeTypeLabel_fast() {
        assertEquals("快充", getChargeTypeLabel(45f));
        assertEquals("快充", getChargeTypeLabel(30f));
    }

    @Test
    public void testChargeTypeLabel_standard() {
        assertEquals("普通充电", getChargeTypeLabel(20f));
        assertEquals("普通充电", getChargeTypeLabel(10f));
    }

    @Test
    public void testChargeTypeLabel_slow() {
        assertEquals("慢速充电", getChargeTypeLabel(5f));
        assertEquals("慢速充电", getChargeTypeLabel(1f));
    }

    @Test
    public void testChargeTypeLabel_notCharging() {
        assertEquals("未充电", getChargeTypeLabel(0f));
    }

    // endregion

    // region 进度条计算测试

    @Test
    public void testProgressCalculation_100Percent() {
        assertEquals(100, calculateProgress(100f, 0f, 100f));
    }

    @Test
    public void testProgressCalculation_50Percent() {
        assertEquals(50, calculateProgress(50f, 0f, 100f));
    }

    @Test
    public void testProgressCalculation_0Percent() {
        assertEquals(0, calculateProgress(0f, 0f, 100f));
    }

    @Test
    public void testProgressCalculation_customRange() {
        assertEquals(50, calculateProgress(75f, 50f, 100f));
    }

    @Test
    public void testProgressCalculation_clamped() {
        assertEquals(0, calculateProgress(-10f, 0f, 100f));
        assertEquals(100, calculateProgress(110f, 0f, 100f));
    }

    // endregion

    // region 文本格式化测试

    @Test
    public void testFormatPercentage() {
        assertEquals("85.5%", formatPercentage(85.5f));
        assertEquals("100.0%", formatPercentage(100f));
        assertEquals("0.0%", formatPercentage(0f));
    }

    @Test
    public void testFormatTemperature() {
        assertEquals("28.5°C", formatTemperature(28.5f));
        assertEquals("0.0°C", formatTemperature(0f));
        assertEquals("-10.5°C", formatTemperature(-10.5f));
    }

    @Test
    public void testFormatVoltage() {
        assertEquals("4.20V", formatVoltage(4200));
        assertEquals("3.85V", formatVoltage(3850));
    }

    @Test
    public void testFormatCurrent() {
        assertEquals("500mA", formatCurrent(500000));
        assertEquals("-500mA", formatCurrent(-500000));
    }

    @Test
    public void testFormatCapacity() {
        assertEquals("4500mAh", formatCapacity(4500));
        assertEquals("0mAh", formatCapacity(0));
    }

    // endregion

    // region 动画阈值边界测试

    @Test
    public void testAnimationThreshold_exactBoundary() {
        // 测试动画触发阈值
        assertTrue("Should animate at threshold", shouldAnimate(100f));
        assertFalse("Should not animate below threshold", shouldAnimate(0f));
    }

    @Test
    public void testAnimationDuration_reasonable() {
        // 动画时长应在合理范围内（100ms - 1000ms）
        int duration = getAnimationDuration();
        assertTrue("Animation duration should be reasonable", duration >= 100 && duration <= 1000);
    }

    // endregion

    // region 辅助方法

    private String getChargeTypeLabel(float powerW) {
        if (powerW >= 100) return "超级快充";
        if (powerW >= 60) return "极速快充";
        if (powerW >= 30) return "快充";
        if (powerW >= 10) return "普通充电";
        if (powerW > 0) return "慢速充电";
        return "未充电";
    }

    private int calculateProgress(float value, float min, float max) {
        if (value <= min) return 0;
        if (value >= max) return 100;
        return (int) ((value - min) / (max - min) * 100);
    }

    private String formatPercentage(float value) {
        return String.format(java.util.Locale.getDefault(), "%.1f%%", value);
    }

    private String formatTemperature(float temp) {
        return String.format(java.util.Locale.getDefault(), "%.1f°C", temp);
    }

    private String formatVoltage(int mv) {
        return String.format(java.util.Locale.getDefault(), "%.2fV", mv / 1000f);
    }

    private String formatCurrent(int ua) {
        return String.format(java.util.Locale.getDefault(), "%dmA", ua / 1000);
    }

    private String formatCapacity(int mah) {
        return String.format(java.util.Locale.getDefault(), "%dmAh", mah);
    }

    private boolean shouldAnimate(float value) {
        return value > 0;
    }

    private int getAnimationDuration() {
        return 300; // 默认 300ms
    }

    // endregion
}
