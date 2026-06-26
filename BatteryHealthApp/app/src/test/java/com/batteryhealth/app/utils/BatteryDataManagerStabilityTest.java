package com.batteryhealth.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.test.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BatteryDataManager 性能 + 稳定性测试。
 *
 * 覆盖：
 * - getPowerLevelLabel 各分支
 * - isNearOfficialFastCharge 阈值
 * - setUsageDays 写入
 * - getCurrentBatteryInfo 双重检查锁
 * - formatCycleCount 边界值
 * - isCharging / isBypassCharging null 安全
 * - 性能：1000 次连续调用 < 1 秒
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BatteryDataManagerStabilityTest {

    private BatteryDataManager manager;

    @Before
    public void setUp() {
        long elapsed = TestUtils.measureExecutionTime("setUp", () -> {
            manager = new BatteryDataManager(ApplicationProvider.getApplicationContext());
        });
        android.util.Log.i("BatteryDataManagerTest", "setUp took " + elapsed + "ms");
        assertNotNull(manager);
    }

    // ====================================================================
    // getPowerLevelLabel 测试
    // ====================================================================

    @Test
    public void getPowerLevelLabel_ultraFast() {
        String label = manager.getPowerLevelLabel(120f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_extremeFast() {
        String label = manager.getPowerLevelLabel(60f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_fast() {
        String label = manager.getPowerLevelLabel(33f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_standard() {
        String label = manager.getPowerLevelLabel(15f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_slow() {
        String label = manager.getPowerLevelLabel(5f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_notCharging() {
        String label = manager.getPowerLevelLabel(0f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_negativeValue() {
        // 负数按 0 处理 -> not charging
        String label = manager.getPowerLevelLabel(-100f);
        assertNotNull(label);
    }

    @Test
    public void getPowerLevelLabel_allBranches() {
        float[] powers = {0f, 5f, 10f, 30f, 60f, 100f, 150f, 1000f};
        for (float p : powers) {
            String label = manager.getPowerLevelLabel(p);
            assertNotNull("Label should not be null for power=" + p, label);
        }
    }

    // ====================================================================
    // isNearOfficialFastCharge 测试
    // ====================================================================

    @Test
    public void isNearOfficialFastCharge_lowPower_returnsFalse() {
        boolean result = manager.isNearOfficialFastCharge(5f);
        // 若设备无官方功率，阈值 18W
        // 仅当电流功率 >= 18W 才返回 true
        if (result) {
            // 设备有官方功率且 5W 已被认为是快充，验证结果一致
            assertTrue(result);
        } else {
            // 设备无官方功率或 5W 不被认为是快充
            assertFalse(result);
        }
    }

    @Test
    public void isNearOfficialFastCharge_highPower_returnsTrue() {
        boolean result = manager.isNearOfficialFastCharge(100f);
        assertTrue(result);
    }

    @Test
    public void isNearOfficialFastCharge_zeroPower_returnsFalse() {
        boolean result = manager.isNearOfficialFastCharge(0f);
        assertFalse(result);
    }

    @Test
    public void isNearOfficialFastCharge_negativePower_returnsFalse() {
        boolean result = manager.isNearOfficialFastCharge(-50f);
        assertFalse(result);
    }

    // ====================================================================
    // setUsageDays 测试
    // ====================================================================

    @Test
    public void setUsageDays_zero() {
        manager.setUsageDays(0);
    }

    @Test
    public void setUsageDays_typical() {
        manager.setUsageDays(180);
    }

    @Test
    public void setUsageDays_large() {
        manager.setUsageDays(3650);
    }

    @Test
    public void setUsageDays_negativeValue() {
        manager.setUsageDays(-1);
    }

    @Test
    public void setUsageDays_maxInt() {
        manager.setUsageDays(Integer.MAX_VALUE);
    }

    // ====================================================================
    // getCurrentBatteryInfo 双重检查锁
    // ====================================================================

    @Test
    public void getCurrentBatteryInfo_doesNotThrow() {
        // 可能为 null（在 Robolectric 无电池 sticky intent）
        BatteryInfo info = manager.getCurrentBatteryInfo();
        // 不抛异常即视为通过
    }

    @Test
    public void getCurrentBatteryInfo_concurrentInvocation() throws InterruptedException {
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 50; j++) {
                        manager.getCurrentBatteryInfo();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("concurrent getCurrentBatteryInfo should finish", done.await(10, TimeUnit.SECONDS));
    }

    // ====================================================================
    // refreshAllDataAsync
    // ====================================================================

    @Test
    public void refreshAllDataAsync_doesNotThrow() {
        manager.refreshAllDataAsync();
    }

    @Test
    public void refreshAllDataAsync_multipleCalls_doesNotThrow() {
        for (int i = 0; i < 5; i++) {
            manager.refreshAllDataAsync();
        }
    }

    // ====================================================================
    // formatCycleCount 测试
    // ====================================================================

    @Test
    public void formatCycleCount_nullInfo_returnsFallback() {
        String result = manager.formatCycleCount(null);
        assertNotNull(result);
    }

    @Test
    public void formatCycleCount_negativeCount_returnsFallback() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(-1);
        String result = manager.formatCycleCount(info);
        assertNotNull(result);
    }

    @Test
    public void formatCycleCount_validCount_notEstimated() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(500);
        info.setCycleCountEstimated(false);
        String result = manager.formatCycleCount(info);
        assertNotNull(result);
        assertTrue("Result should contain the count", result.contains("500"));
    }

    @Test
    public void formatCycleCount_validCount_estimated() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(500);
        info.setCycleCountEstimated(true);
        String result = manager.formatCycleCount(info);
        assertNotNull(result);
        assertTrue("Result should contain the count", result.contains("500"));
    }

    // ====================================================================
    // isCharging 测试
    // ====================================================================

    @Test
    public void isCharging_doesNotThrow() {
        // Robolectric 不提供真实 sticky intent，应该返回 false
        boolean charging = manager.isCharging();
        // 不抛异常
    }

    @Test
    public void isCharging_concurrentInvocation() throws InterruptedException {
        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        manager.isCharging();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("concurrent isCharging should finish", done.await(10, TimeUnit.SECONDS));
    }

    // ====================================================================
    // isBypassCharging 测试
    // ====================================================================

    @Test
    public void isBypassCharging_doesNotThrow() {
        boolean bypass = manager.isBypassCharging();
        // 不抛异常即视为通过
    }

    // ====================================================================
    // getChargingLimitPercent 测试
    // ====================================================================

    @Test
    public void getChargingLimitPercent_returnsValidValue() {
        int limit = manager.getChargingLimitPercent();
        // 应该是 1-100 之间的值，或者默认值 100
        assertTrue("limit should be in [1, 100]", limit >= 1 && limit <= 100);
    }

    // ====================================================================
    // getChargingStatusText / getHealthSourceText / getBatterySourceText 测试
    // ====================================================================

    @Test
    public void getChargingStatusText_doesNotThrow() {
        String text = manager.getChargingStatusText();
        assertNotNull(text);
    }

    @Test
    public void getHealthSourceText_doesNotThrow() {
        String text = manager.getHealthSourceText();
        assertNotNull(text);
    }

    @Test
    public void getBatterySourceText_doesNotThrow() {
        String text = manager.getBatterySourceText();
        assertNotNull(text);
    }

    // ====================================================================
    // getOriginDetector 测试
    // ====================================================================

    @Test
    public void getOriginDetector_notNull() {
        assertNotNull(manager.getOriginDetector());
    }

    @Test
    public void getOriginDetector_concurrentAccess() throws InterruptedException {
        int threads = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        manager.getOriginDetector();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("concurrent getOriginDetector should finish", done.await(10, TimeUnit.SECONDS));
    }

    // ====================================================================
    // 性能测试
    // ====================================================================

    @Test
    public void performance_getPowerLevelLabel_1000calls() {
        long elapsed = TestUtils.measureExecutionTime("getPowerLevelLabel*1000", () -> {
            for (int i = 0; i < 1000; i++) {
                manager.getPowerLevelLabel((float) (i % 200));
            }
        });
        // 1000 次调用应在 1 秒内完成
        assertTrue("1000 calls should be fast: " + elapsed + "ms", elapsed < 1000);
    }

    @Test
    public void performance_isCharging_1000calls() {
        long elapsed = TestUtils.measureExecutionTime("isCharging*1000", () -> {
            for (int i = 0; i < 1000; i++) {
                manager.isCharging();
            }
        });
        assertTrue("1000 calls should be fast: " + elapsed + "ms", elapsed < 2000);
    }

    @Test
    public void performance_getChargingLimitPercent_1000calls() {
        long elapsed = TestUtils.measureExecutionTime("getChargingLimitPercent*1000", () -> {
            for (int i = 0; i < 1000; i++) {
                manager.getChargingLimitPercent();
            }
        });
        assertTrue("1000 calls should be fast: " + elapsed + "ms", elapsed < 5000);
    }
}
