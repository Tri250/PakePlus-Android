package com.batteryhealth.app.ui.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.test.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ViewModel 层稳定性 + 格式化 + 生命周期测试。
 *
 * 覆盖：
 * - 格式化方法（容量/循环次数/温度/电压/电流）
 * - 健康等级与描述分支
 * - LiveData 状态更新
 * - onCleared 生命周期
 * - 异常路径不崩溃
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ViewModelStabilityTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private BatteryHealthViewModel batteryHealthViewModel;
    private DeviceConfigViewModel deviceConfigViewModel;

    @Before
    public void setUp() {
        // 重置 Locale，保证格式化字符串一致
        Locale.setDefault(Locale.US);
        BatteryHealthApplication app = (BatteryHealthApplication) ApplicationProvider.getApplicationContext();
        batteryHealthViewModel = new BatteryHealthViewModel();
        deviceConfigViewModel = new DeviceConfigViewModel();
    }

    @After
    public void tearDown() {
        TestUtils.clearAllTestData();
    }

    // ====================================================================
    // BatteryHealthViewModel 格式化方法测试
    // ====================================================================

    @Test
    public void formatCapacity_withNull_returnsPlaceholder() {
        assertEquals("--", batteryHealthViewModel.formatCapacity(null));
    }

    @Test
    public void formatCapacity_withValidData_returnsFormatted() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        info.setCurrentCapacity(4500);
        String formatted = batteryHealthViewModel.formatCapacity(info);
        assertEquals("4500 / 5000 mAh", formatted);
    }

    @Test
    public void formatCapacity_withZeroDesignCurrent_returnsDesign() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        info.setCurrentCapacity(0);
        assertEquals("5000 mAh", batteryHealthViewModel.formatCapacity(info));
    }

    @Test
    public void formatCapacity_withZeroCurrentOnly_returnsCurrent() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(0);
        info.setCurrentCapacity(4500);
        assertEquals("4500 mAh", batteryHealthViewModel.formatCapacity(info));
    }

    @Test
    public void formatCapacity_withBothZero_returnsPlaceholder() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(0);
        info.setCurrentCapacity(0);
        assertEquals("--", batteryHealthViewModel.formatCapacity(info));
    }

    @Test
    public void formatCapacity_withNegativeValues_treatedAsZero() {
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(-1);
        info.setCurrentCapacity(-1);
        assertEquals("--", batteryHealthViewModel.formatCapacity(info));
    }

    @Test
    public void formatCycleCount_withNull_returnsPlaceholder() {
        assertEquals("--", batteryHealthViewModel.formatCycleCount(null));
    }

    @Test
    public void formatCycleCount_withValid_returnsFormatted() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(500);
        String formatted = batteryHealthViewModel.formatCycleCount(info);
        assertEquals("500 次", formatted);
    }

    @Test
    public void formatCycleCount_withNegative_returnsPlaceholder() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(-1);
        assertEquals("--", batteryHealthViewModel.formatCycleCount(info));
    }

    @Test
    public void formatCycleCount_withZero_treatedAsValid() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(0);
        assertEquals("0 次", batteryHealthViewModel.formatCycleCount(info));
    }

    @Test
    public void formatTemperature_typicalValue() {
        String formatted = batteryHealthViewModel.formatTemperature(36.5f);
        assertEquals("36.5°C", formatted);
    }

    @Test
    public void formatTemperature_zeroValue() {
        String formatted = batteryHealthViewModel.formatTemperature(0f);
        assertEquals("0.0°C", formatted);
    }

    @Test
    public void formatTemperature_negativeValue() {
        String formatted = batteryHealthViewModel.formatTemperature(-10.5f);
        assertEquals("-10.5°C", formatted);
    }

    @Test
    public void formatVoltage_mVToVConversion() {
        // 4000mV -> 4.00 V
        String formatted = batteryHealthViewModel.formatVoltage(4000f);
        assertEquals("4.00 V", formatted);
    }

    @Test
    public void formatVoltage_zeroValue() {
        assertEquals("0.00 V", batteryHealthViewModel.formatVoltage(0f));
    }

    @Test
    public void formatVoltage_highValue() {
        // 25000mV -> 25.00 V
        assertEquals("25.00 V", batteryHealthViewModel.formatVoltage(25000f));
    }

    @Test
    public void formatCurrent_uAToMAConversion() {
        // -1500000 uA -> 1500 mA
        String formatted = batteryHealthViewModel.formatCurrent(-1500000);
        assertEquals("1500 mA", formatted);
    }

    @Test
    public void formatCurrent_positiveValue_returnsAbsolute() {
        // 500000 uA -> 500 mA
        String formatted = batteryHealthViewModel.formatCurrent(500000);
        assertEquals("500 mA", formatted);
    }

    @Test
    public void formatCurrent_zeroValue() {
        assertEquals("0 mA", batteryHealthViewModel.formatCurrent(0));
    }

    @Test
    public void formatMethods_concurrentInvocation() throws InterruptedException {
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        batteryHealthViewModel.formatTemperature(36.5f);
                        batteryHealthViewModel.formatVoltage(4000f);
                        batteryHealthViewModel.formatCurrent(-1000000);
                        batteryHealthViewModel.formatCapacity(null);
                        batteryHealthViewModel.formatCycleCount(null);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("format methods should finish in time", done.await(10, TimeUnit.SECONDS));
    }

    // ====================================================================
    // BatteryHealthViewModel 生命周期 / LiveData 测试
    // ====================================================================

    @Test
    public void getBatteryInfo_isNotNull() {
        assertNotNull(batteryHealthViewModel.getBatteryInfo());
    }

    @Test
    public void getIsLoading_isNotNull() {
        assertNotNull(batteryHealthViewModel.getIsLoading());
    }

    @Test
    public void getHealthGrade_isNotNull() {
        assertNotNull(batteryHealthViewModel.getHealthGrade());
    }

    @Test
    public void getHealthStatus_isNotNull() {
        assertNotNull(batteryHealthViewModel.getHealthStatus());
    }

    @Test
    public void getBatterySource_isNotNull() {
        assertNotNull(batteryHealthViewModel.getBatterySource());
    }

    @Test
    public void onCleared_doesNotThrow() {
        // 模拟 ViewModel 销毁
        try {
            batteryHealthViewModel.onCleared();
        } catch (Exception e) {
            fail("onCleared should not throw: " + e.getMessage());
        }
    }

    @Test
    public void onCleared_calledTwice_doesNotThrow() {
        batteryHealthViewModel.onCleared();
        batteryHealthViewModel.onCleared(); // 幂等性
    }

    @Test
    public void refreshData_calledMultipleTimes_doesNotThrow() {
        for (int i = 0; i < 5; i++) {
            batteryHealthViewModel.refreshData();
        }
    }

    @Test
    public void refreshData_thenOnCleared_doesNotThrow() {
        batteryHealthViewModel.refreshData();
        batteryHealthViewModel.onCleared();
    }

    @Test
    public void refreshData_updatesIsLoadingAndNotifiesObservers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> lastIsLoading = new AtomicReference<>();
        Observer<Boolean> observer = isLoading -> {
            if (isLoading != null) {
                lastIsLoading.set(isLoading);
                if (!isLoading && lastIsLoading.get() != null) {
                    latch.countDown();
                }
            }
        };
        batteryHealthViewModel.getIsLoading().observeForever(observer);
        try {
            batteryHealthViewModel.refreshData();
            assertTrue("isLoading should toggle to false", latch.await(5, TimeUnit.SECONDS));
            assertFalse("lastIsLoading should be false", lastIsLoading.get());
        } finally {
            batteryHealthViewModel.getIsLoading().removeObserver(observer);
        }
    }

    // ====================================================================
    // DeviceConfigViewModel 生命周期 / LiveData 测试
    // ====================================================================

    @Test
    public void deviceConfigViewModel_getDeviceConfig_notNull() {
        assertNotNull(deviceConfigViewModel.getDeviceConfig());
    }

    @Test
    public void deviceConfigViewModel_getIsLoading_notNull() {
        assertNotNull(deviceConfigViewModel.getIsLoading());
    }

    @Test
    public void deviceConfigViewModel_getUsageDays_notNull() {
        assertNotNull(deviceConfigViewModel.getUsageDays());
    }

    @Test
    public void deviceConfigViewModel_getErrorMessage_notNull() {
        assertNotNull(deviceConfigViewModel.getErrorMessage());
    }

    @Test
    public void deviceConfigViewModel_onCleared_doesNotThrow() {
        try {
            deviceConfigViewModel.onCleared();
        } catch (Exception e) {
            fail("onCleared should not throw: " + e.getMessage());
        }
    }

    @Test
    public void deviceConfigViewModel_loadDeviceConfig_doesNotThrow() {
        try {
            deviceConfigViewModel.loadDeviceConfig();
        } catch (Exception e) {
            fail("loadDeviceConfig should not throw: " + e.getMessage());
        }
    }

    @Test
    public void deviceConfigViewModel_consecutiveLoad_doesNotThrow() {
        for (int i = 0; i < 5; i++) {
            deviceConfigViewModel.loadDeviceConfig();
        }
    }

    @Test
    public void deviceConfigViewModel_loadThenClear_doesNotThrow() {
        deviceConfigViewModel.loadDeviceConfig();
        deviceConfigViewModel.onCleared();
    }

    // ====================================================================
    // BatteryInfo 健康度分支测试（间接验证 ViewModel 内部逻辑）
    // ====================================================================

    @Test
    public void healthGrade_A_plus_branches() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(95f);
        assertEquals("A+", info.getHealthGrade());
        info.setHealthPercentage(100f);
        assertEquals("A+", info.getHealthGrade());
    }

    @Test
    public void healthGrade_A_branches() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(85f);
        assertEquals("A", info.getHealthGrade());
        info.setHealthPercentage(94.9f);
        assertEquals("A", info.getHealthGrade());
    }

    @Test
    public void healthGrade_B_branches() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(75f);
        assertEquals("B", info.getHealthGrade());
        info.setHealthPercentage(84.9f);
        assertEquals("B", info.getHealthGrade());
    }

    @Test
    public void healthGrade_C_branches() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(60f);
        assertEquals("C", info.getHealthGrade());
        info.setHealthPercentage(74.9f);
        assertEquals("C", info.getHealthGrade());
    }

    @Test
    public void healthGrade_D_branches() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(0f);
        assertEquals("D", info.getHealthGrade());
        info.setHealthPercentage(59.9f);
        assertEquals("D", info.getHealthGrade());
    }

    @Test
    public void healthGrade_negative_returnsPlaceholder() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(-1f);
        assertEquals("--", info.getHealthGrade());
    }

    @Test
    public void hasValidHealthData_negative_returnsFalse() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(-1f);
        assertFalse(info.hasValidHealthData());
    }

    @Test
    public void hasValidHealthData_zero_returnsTrue() {
        BatteryInfo info = new BatteryInfo();
        info.setHealthPercentage(0f);
        assertTrue(info.hasValidHealthData());
    }

    @Test
    public void hasValidCycleCount_negative_returnsFalse() {
        BatteryInfo info = new BatteryInfo();
        info.setCycleCount(-1);
        assertFalse(info.hasValidCycleCount());
    }

    @Test
    public void isCharging_statusTwo() {
        BatteryInfo info = new BatteryInfo();
        info.setStatus(2);
        assertTrue(info.isCharging());
    }

    @Test
    public void isCharging_statusFive() {
        BatteryInfo info = new BatteryInfo();
        info.setStatus(5);
        assertTrue(info.isCharging());
    }

    @Test
    public void isCharging_statusOther() {
        BatteryInfo info = new BatteryInfo();
        info.setStatus(3);
        assertFalse(info.isCharging());
    }

    // ====================================================================
    // BatteryInfo.copy 深拷贝测试（用于 LiveData 快照）
    // ====================================================================

    @Test
    public void copy_createsDeepCopy() {
        BatteryInfo original = new BatteryInfo();
        original.setDesignCapacity(5000);
        original.setCurrentCapacity(4500);
        original.setHealthPercentage(90f);

        BatteryInfo snapshot = original.copy();
        assertNotNull(snapshot);
        assertEquals(original.getDesignCapacity(), snapshot.getDesignCapacity());
        assertEquals(original.getCurrentCapacity(), snapshot.getCurrentCapacity());
        assertEquals(original.getHealthPercentage(), snapshot.getHealthPercentage(), 0.01f);

        // 修改副本不应影响原对象
        snapshot.setDesignCapacity(1000);
        assertEquals(5000, original.getDesignCapacity());
        assertEquals(1000, snapshot.getDesignCapacity());
    }

    @Test
    public void copy_handlesNullStrings() {
        BatteryInfo original = new BatteryInfo();
        original.setDeviceBrand(null);
        original.setDeviceModel(null);

        BatteryInfo snapshot = original.copy();
        assertNull(snapshot.getDeviceBrand());
        assertNull(snapshot.getDeviceModel());
    }
}
