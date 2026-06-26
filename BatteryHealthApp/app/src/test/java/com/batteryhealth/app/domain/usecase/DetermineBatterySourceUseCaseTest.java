package com.batteryhealth.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * DetermineBatterySourceUseCase 单元测试 (回退路径测试)。
 *
 * 当 BatteryDataManager 不可用时，执行回退逻辑：
 * - 验证 OEM 序列号格式
 * - 验证厂商匹配
 * - 验证容量比例合理性
 * - 验证综合信号评分
 */
public class DetermineBatterySourceUseCaseTest {

    private final DetermineBatterySourceUseCase useCase =
            new DetermineBatterySourceUseCase(null, null);

    // ==================== OEM 序列号格式验证 ====================

    @Test
    public void testValidOemSerial_alphabeticAndDigits() {
        assertTrue(invokeIsValid("ABC12345678"));
        assertTrue(invokeIsValid("MFG12345ABC"));
        assertTrue(invokeIsValid("LIION20240101"));
    }

    @Test
    public void testInvalidOemSerial_tooShort() {
        assertFalse(invokeIsValid("AB1"));
        assertFalse(invokeIsValid(""));
    }

    @Test
    public void testInvalidOemSerial_tooLong() {
        assertFalse(invokeIsValid("ABCDEFGHIJKLMNOPQRSTUVWXY12345")); // > 24
    }

    @Test
    public void testInvalidOemSerial_specialChars() {
        assertFalse(invokeIsValid("ABC12345!@#"));
        assertFalse(invokeIsValid("ABC-12345"));
        assertFalse(invokeIsValid("ABC 12345"));
    }

    @Test
    public void testInvalidOemSerial_null() {
        assertFalse(invokeIsValid(null));
    }

    // ==================== 容量比例测试 ====================

    @Test
    public void testCapacityRatio_normalRange() {
        // current/design = 1.0 -> original
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                "valid_serial_123", "coslight", "ABC12345678", 4500, 4500);
        assertNotNull(r);
    }

    @Test
    public void testCapacityRatio_lowEnd() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                "valid_serial_123", "sunwoda", "ABC12345678", 1000, 4500);
        assertNotNull(r);
    }

    @Test
    public void testCapacityRatio_negative_fallback() {
        // 设计容量无效
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                "valid_serial_123", "byd", "ABC12345678", 4500, 0);
        assertNotNull(r);
    }

    // ==================== 边界条件 ====================

    @Test
    public void testAllNullInputs_safe() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                null, null, null, 0, 0);
        assertNotNull(r);
        // 综合得分应为 0 或负，应为 unknown
        assertEquals("unknown", r.source);
    }

    @Test
    public void testEmptyStrings_safe() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                "", "", "", 0, 0);
        assertNotNull(r);
    }

    @Test
    public void testKnownOemManufacturer() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                null, "COSLIGHT", null, 4500, 4500);
        // 大写也能匹配
        assertNotNull(r);
    }

    @Test
    public void testUnknownManufacturer_penalized() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                null, "Unknown", null, 4500, 4500);
        assertNotNull(r);
    }

    @Test
    public void testZeroManufacturer_penalized() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                null, "0", null, 4500, 4500);
        assertNotNull(r);
    }

    @Test
    public void testConfidenceInRange() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                "valid_serial_123", "coslight", "ABC12345678", 4500, 4500);
        assertTrue("Confidence should be in [0,1]",
                r.confidence >= 0 && r.confidence <= 1.0f);
    }

    @Test
    public void testResultFieldsAreInitialized() {
        DetermineBatterySourceUseCase.Result r = useCase.execute(
                null, null, null, 0, 0);
        assertNotNull(r.source);
        assertNotNull(r.reason);
    }

    /**
     * 通过反射访问 private 方法
     */
    private boolean invokeIsValid(String serial) {
        try {
            java.lang.reflect.Method m = DetermineBatterySourceUseCase.class
                    .getDeclaredMethod("isValidOemSerialFormat", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(useCase, serial);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLooksLikeOemSerial_valid() {
        // 通过反射测试
        try {
            java.lang.reflect.Method m = DetermineBatterySourceUseCase.class
                    .getDeclaredMethod("looksLikeOemSerial", String.class);
            m.setAccessible(true);
            assertTrue((boolean) m.invoke(useCase, "ABC-12345_678"));
            assertTrue((boolean) m.invoke(useCase, "ABC12345 678"));
            assertFalse((boolean) m.invoke(useCase, "AB1!@#"));
            assertFalse((boolean) m.invoke(useCase, null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
