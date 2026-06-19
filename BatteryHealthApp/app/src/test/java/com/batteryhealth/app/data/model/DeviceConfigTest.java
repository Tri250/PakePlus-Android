package com.batteryhealth.app.data.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DeviceConfig 格式化与业务逻辑单元测试。
 */
public class DeviceConfigTest {

    @Test
    public void testGetFormattedBrand_normalizesCase() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("XIAOMI");
        assertEquals("Xiaomi", config.getFormattedBrand());

        config.setBrand("honor");
        assertEquals("Honor", config.getFormattedBrand());
    }

    @Test
    public void testGetFormattedBrand_nullReturnsUnknown() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand(null);
        assertEquals("Unknown", config.getFormattedBrand());
    }

    @Test
    public void testGetFullModelName() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("xiaomi");
        config.setModel("Xiaomi 15");
        assertEquals("Xiaomi Xiaomi 15", config.getFullModelName());
    }

    @Test
    public void testGetFormattedMemory() {
        DeviceConfig config = new DeviceConfig();
        config.setTotalMemory(8192);
        assertEquals("8.0 GB", config.getFormattedMemory());

        config.setTotalMemory(512);
        assertEquals("512 MB", config.getFormattedMemory());

        config.setTotalMemory(0);
        assertEquals("Unknown", config.getFormattedMemory());
    }

    @Test
    public void testGetFormattedStorage() {
        DeviceConfig config = new DeviceConfig();
        config.setTotalStorage(256);
        assertEquals("256 GB", config.getFormattedStorage());

        config.setTotalStorage(0);
        assertEquals("Unknown", config.getFormattedStorage());
    }

    @Test
    public void testGetScreenResolution() {
        DeviceConfig config = new DeviceConfig();
        config.setScreenWidth(1080);
        config.setScreenHeight(2400);
        assertEquals("1080 x 2400", config.getScreenResolution());
    }

    @Test
    public void testGetFormattedScreenSize() {
        DeviceConfig config = new DeviceConfig();
        config.setScreenSize(6.73f);
        assertEquals("6.7\"", config.getFormattedScreenSize());

        config.setScreenSize(0);
        assertEquals("Unknown", config.getFormattedScreenSize());
    }

    @Test
    public void testGetAndroidCodename() {
        DeviceConfig config = new DeviceConfig();
        config.setSdkVersion(android.os.Build.VERSION_CODES.BAKLAVA);
        assertEquals("Android 16", config.getAndroidCodename());

        config.setSdkVersion(android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM);
        assertEquals("Android 15", config.getAndroidCodename());
    }

    @Test
    public void testIsDomesticBrand() {
        DeviceConfig config = new DeviceConfig();
        config.setBrand("xiaomi");
        assertTrue(config.isDomesticBrand());

        config.setBrand("samsung");
        assertFalse(config.isDomesticBrand());
    }

    @Test
    public void testGpuInfoField() {
        DeviceConfig config = new DeviceConfig();
        config.setGpuInfo("Adreno 830");
        assertEquals("Adreno 830", config.getGpuInfo());
    }
}
