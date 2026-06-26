package com.batteryhealth.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Constants 一致性 + 边界值 + 安全测试。
 *
 * 验证:
 * 1. 常量值合理性
 * 2. 阈值递增关系
 * 3. 通知 ID 不冲突
 * 4. 超时/重试值合理
 */
public class ConstantsTest {

    // ==================== Battery 常量 ====================

    @Test
    public void testBattery_designCapacityRange_valid() {
        assertTrue("MIN_DESIGN_CAPACITY should be > 0",
                Constants.Battery.MIN_DESIGN_CAPACITY > 0);
        assertTrue("MAX_DESIGN_CAPACITY should be reasonable",
                Constants.Battery.MAX_DESIGN_CAPACITY > 1000);
        assertTrue("MIN should be < MAX",
                Constants.Battery.MIN_DESIGN_CAPACITY < Constants.Battery.MAX_DESIGN_CAPACITY);
    }

    @Test
    public void testBattery_healthThresholds_descending() {
        // 健康度阈值应递减: excellent > good > average > poor
        assertTrue(Constants.Battery.HEALTH_EXCELLENT_THRESHOLD
                > Constants.Battery.HEALTH_GOOD_THRESHOLD);
        assertTrue(Constants.Battery.HEALTH_GOOD_THRESHOLD
                > Constants.Battery.HEALTH_AVERAGE_THRESHOLD);
        assertTrue(Constants.Battery.HEALTH_AVERAGE_THRESHOLD
                > Constants.Battery.HEALTH_POOR_THRESHOLD);
    }

    @Test
    public void testBattery_healthAlertThreshold_reasonable() {
        // 预警阈值应在 50-100 之间
        assertTrue("Alert threshold in [50, 100]",
                Constants.Battery.HEALTH_ALERT_THRESHOLD >= 50
                        && Constants.Battery.HEALTH_ALERT_THRESHOLD <= 100);
    }

    @Test
    public void testBattery_dailyLossRate_reasonable() {
        // 日均衰减率 0.026% (年化约 9.5%) - 合理范围
        assertTrue("Daily loss rate should be 0-1%",
                Constants.Battery.DAILY_LOSS_RATE >= 0
                        && Constants.Battery.DAILY_LOSS_RATE <= 0.01f);
    }

    @Test
    public void testBattery_medianFilterWindow_valid() {
        // 中值滤波窗口应为奇数 (中位数定义)
        assertTrue("Median filter window should be odd",
                Constants.Battery.MEDIAN_FILTER_WINDOW % 2 == 1);
        assertTrue("Median filter window should be > 1",
                Constants.Battery.MEDIAN_FILTER_WINDOW > 1);
    }

    // ==================== Network 常量 ====================

    @Test
    public void testNetwork_timeoutReasonable() {
        assertTrue("Timeout should be > 1s",
                Constants.Network.TIMEOUT_MS >= 1000);
        assertTrue("Timeout should be < 2min",
                Constants.Network.TIMEOUT_MS < 120000);
    }

    @Test
    public void testNetwork_retryCount_reasonable() {
        assertTrue("Retry count should be >= 1",
                Constants.Network.RETRY_COUNT >= 1);
        assertTrue("Retry count should be < 10",
                Constants.Network.RETRY_COUNT < 10);
    }

    @Test
    public void testNetwork_retryDelay_reasonable() {
        assertTrue("Retry delay should be >= 0",
                Constants.Network.RETRY_DELAY_MS >= 0);
    }

    // ==================== Database 常量 ====================

    @Test
    public void testDatabase_nameNotEmpty() {
        assertNotNull(Constants.Database.DB_NAME);
        assertFalse(Constants.Database.DB_NAME.isEmpty());
    }

    @Test
    public void testDatabase_maxWait_reasonable() {
        assertTrue(Constants.Database.MAX_WAIT_SECONDS > 0);
    }

    @Test
    public void testDatabase_retention_reasonable() {
        assertTrue("Retention should be 1-365 days",
                Constants.Database.HISTORY_RETENTION_DAYS >= 1
                        && Constants.Database.HISTORY_RETENTION_DAYS <= 365);
    }

    // ==================== Worker 常量 ====================

    @Test
    public void testWorker_dataCollectionInterval_reasonable() {
        assertTrue("Interval >= 1 min",
                Constants.Worker.DATA_COLLECTION_INTERVAL_MINUTES >= 1);
        assertTrue("Interval < 1 day",
                Constants.Worker.DATA_COLLECTION_INTERVAL_MINUTES < 24 * 60);
    }

    @Test
    public void testWorker_healthAlertInterval_reasonable() {
        assertTrue("Interval >= 1 hour",
                Constants.Worker.HEALTH_ALERT_INTERVAL_HOURS >= 1);
        assertTrue("Interval < 1 day",
                Constants.Worker.HEALTH_ALERT_INTERVAL_HOURS < 24);
    }

    // ==================== Notification 常量 ====================

    @Test
    public void testNotification_channelId_notEmpty() {
        assertNotNull(Constants.Notification.CHANNEL_ID);
        assertFalse(Constants.Notification.CHANNEL_ID.isEmpty());
    }

    @Test
    public void testNotification_alertId_doesNotConflictWithForeground() {
        // 前台服务通知 ID 通常 1
        // ALERT_NOTIFICATION_ID 1005 应不与前台服务冲突
        assertTrue("Alert ID should be > 1000 to avoid conflict",
                Constants.Notification.ALERT_NOTIFICATION_ID > 1000);
    }

    // ==================== Charging 常量 ====================

    @Test
    public void testCharging_thresholds_descending() {
        assertTrue(Constants.Charging.SUPER_FAST_THRESHOLD
                > Constants.Charging.FAST_THRESHOLD);
        assertTrue(Constants.Charging.FAST_THRESHOLD
                > Constants.Charging.QUICK_THRESHOLD);
        assertTrue(Constants.Charging.QUICK_THRESHOLD
                > Constants.Charging.NORMAL_THRESHOLD);
    }

    @Test
    public void testCharging_thresholds_positive() {
        assertTrue(Constants.Charging.SUPER_FAST_THRESHOLD > 0);
        assertTrue(Constants.Charging.FAST_THRESHOLD > 0);
        assertTrue(Constants.Charging.QUICK_THRESHOLD > 0);
        assertTrue(Constants.Charging.NORMAL_THRESHOLD > 0);
    }

    // ==================== HealthGrade 常量 ====================

    @Test
    public void testHealthGrade_descending() {
        assertEquals(95f, Constants.HealthGrade.A_PLUS, 0.01f);
        assertEquals(90f, Constants.HealthGrade.A, 0.01f);
        assertEquals(85f, Constants.HealthGrade.A_MINUS, 0.01f);
        assertEquals(80f, Constants.HealthGrade.B_PLUS, 0.01f);
        assertEquals(75f, Constants.HealthGrade.B, 0.01f);
        assertEquals(70f, Constants.HealthGrade.B_MINUS, 0.01f);
        assertEquals(60f, Constants.HealthGrade.C, 0.01f);
    }
}
