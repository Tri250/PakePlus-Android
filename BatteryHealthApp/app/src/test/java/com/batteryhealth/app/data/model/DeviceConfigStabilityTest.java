package com.batteryhealth.app.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * DeviceConfig 模型稳定性 + 边界 + 安全测试。
 */
public class DeviceConfigStabilityTest {

    @Test
    public void testGetFormattedBrand_normalCase() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("huawei");
        assertEquals("Huawei", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_alreadyFormatted() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("XIAOMI");
        assertEquals("Xiaomi", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_nullSafe() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand(null);
        assertEquals("Unknown", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_emptyStringSafe() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("");
        assertEquals("Unknown", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_singleChar() {
        DeviceConfig config = new DeviceConfig();
        // Vivo / V 品牌可能在某些固件中以单字符表示
        config.setBrand("V");
        assertEquals("V", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_chineseBrand() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("huawei");
        assertNotNull(config.getFormattedBrand());
    }

    @Test
    public void testGetFullModelName_includesBrand() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("huawei");
        config.setModel("Mate 60 Pro");
        String full = config.getFullModelName();
        assertTrue(full.contains("Mate 60 Pro"));
        assertTrue(full.contains("Huawei") || full.contains("huawei"));
    }

    @Test
    public void testGetFullModelName_nullModelSafe() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("huawei");
        config.setModel(null);
        String full = config.getFullModelName();
        assertNotNull(full);
        assertTrue(full.contains("Unknown"));
    }

    /**
     * 安全性测试: 异常输入不应导致崩溃
     */
    @Test
    public void testInjectionAttempt_inBrand_doesNotCrash() {
        DeviceConfig config = new DeviceConfig();
        // SQL/路径注入尝试
        config.setBrand("huawei'; DROP TABLE devices;--");
        config.setModel("../../../etc/passwd");
        try {
            String result = config.getFormattedBrand();
            assertNotNull(result);
            String full = config.getFullModelName();
            assertNotNull(full);
        } catch (Exception e) {
            fail("Injection-like input should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testVeryLongString_doesNotCrash() {
        DeviceConfig config = new DeviceConfig();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) sb.append("a");
        config.setBrand(sb.toString());
        config.setModel(sb.toString());
        try {
            String result = config.getFormattedBrand();
            assertNotNull(result);
            assertTrue(result.length() > 0);
            String full = config.getFullModelName();
            assertNotNull(full);
        } catch (Exception e) {
            fail("Very long string should not crash: " + e.getMessage());
        }
    }

    @Test
    public void testSetterConsistency_unicodeString() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("三星");
        config.setModel("Galaxy S24");
        String brand = config.getFormattedBrand();
        assertNotNull(brand);
        String full = config.getFullModelName();
        assertNotNull(full);
    }
}
