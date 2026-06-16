package com.batteryhealth.app;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * BatteryParser 单元测试套件
 * 测试覆盖率目标：核心业务逻辑 100%
 *
 * @version 2.1.17
 */
public class BatteryParserTest {

    private BatteryParser.BatteryInfo info;
    private Map<String, List<String>> kvMap;

    @Before
    public void setUp() {
        info = new BatteryParser.BatteryInfo();
        kvMap = new HashMap<>();
    }

    @After
    public void tearDown() {
        info = null;
        kvMap = null;
    }

    // ==================== healthd 格式解析测试 ====================

    @Test
    public void parseHealthdLines_validInput_returnsCorrectCapacity() {
        String input = "healthd: l=85 v=4200 t=35.0 c=500000 fc=4000000 cc=150";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertNotNull("Result should not be null", result);
        assertEquals("Current capacity should be 4000mAh", 4000, result.currentCapacity);
        assertEquals("Cycle count should be 150", 150, result.cycleCount);
        assertEquals("Voltage should be 4200mV", 4200, result.voltage);
        assertEquals("Temperature should be 35.0", 35.0, result.batteryTemp, 0.1);
        // getLevel() depends on designCapacity which is not set in this test
    }

    @Test
    public void parseHealthdLines_negativeCurrent_calculatesCorrectly() {
        String input = "healthd: l=50 v=3800 t=30.5 c=-200000 fc=3500000 cc=200";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertNotNull("Result should not be null", result);
        assertEquals("Current capacity should be 3500mAh", 3500, result.currentCapacity);
    }

    @Test
    public void parseHealthdLines_zeroFullCharge_returnsZero() {
        String input = "healthd: l=100 v=4200 t=25.0 c=0 fc=0 cc=0";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertNotNull("Result should not be null", result);
        assertEquals("Current capacity should be 0 when fc=0", 0, result.currentCapacity);
    }

    @Test
    public void parseHealthdLines_invalidFormat_returnsEmptyInfo() {
        String input = "invalid data without battery info";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertNotNull("Result should not be null even for invalid input", result);
        assertEquals("Current capacity should be 0", 0, result.currentCapacity);
        assertEquals("Cycle count should be 0", 0, result.cycleCount);
    }

    @Test
    public void parseHealthdLines_nullInput_returnsEmptyInfo() {
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(null);

        assertNotNull("Result should not be null for null input", result);
        assertEquals("Current capacity should be 0", 0, result.currentCapacity);
    }

    @Test
    public void parseHealthdLines_emptyInput_returnsEmptyInfo() {
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines("");

        assertNotNull("Result should not be null for empty input", result);
        assertEquals("Current capacity should be 0", 0, result.currentCapacity);
    }

    // ==================== 设备信息提取测试 ====================

    @Test
    public void extractDeviceInfo_validIMEI_extractsCorrectly() {
        String content = "imei=867123456789012\nserial_number=ABC123";
        addToKvMap("imei", "867123456789012");
        addToKvMap("serial_number", "ABC123");

        BatteryParser.extractDeviceInfo(info, content, kvMap);

        assertEquals("IMEI should be extracted", "867123456789012", info.imei1);
        assertEquals("Serial number should be extracted", "ABC123", info.serialNumber);
        assertTrue("Should have device info", info.hasDeviceInfo());
    }

    @Test
    public void extractDeviceInfo_imeiWithSpaces_extractsCorrectly() {
        addToKvMap("imei", " 867123456789012 ");

        BatteryParser.extractDeviceInfo(info, "", kvMap);

        assertEquals("IMEI should be trimmed", "867123456789012", info.imei1);
    }

    @Test
    public void extractDeviceInfo_invalidIMEILength_rejects() {
        addToKvMap("imei", "123456"); // Too short

        BatteryParser.extractDeviceInfo(info, "", kvMap);

        assertNull("Invalid IMEI should be rejected", info.imei1);
    }

    @Test
    public void extractDeviceInfo_imeiWithNonDigits_extractsDigitsOnly() {
        addToKvMap("imei", "8671-2345-6789-012");

        BatteryParser.extractDeviceInfo(info, "", kvMap);

        assertEquals("IMEI should contain only digits", "867123456789012", info.imei1);
    }

    @Test
    public void extractDeviceInfo_dualIMEI_extractsBoth() {
        addToKvMap("imei1", "867123456789012");
        addToKvMap("imei2", "867123456789013");

        BatteryParser.extractDeviceInfo(info, "", kvMap);

        assertEquals("IMEI1 should be extracted", "867123456789012", info.imei1);
        assertEquals("IMEI2 should be extracted", "867123456789013", info.imei2);
    }

    @Test
    public void extractDeviceInfo_fromTelephonySection_extractsCorrectly() {
        String content = "DUMP OF SERVICE telephony.registry\n" +
                        "imei: 867123456789012\n" +
                        "other data";

        BatteryParser.extractDeviceInfo(info, content, kvMap);

        assertEquals("IMEI should be extracted from telephony section", "867123456789012", info.imei1);
        assertEquals("Source should be telephony.registry", "telephony.registry", info.deviceSource);
    }

    @Test
    public void extractDeviceInfo_noDeviceInfo_hasDeviceInfoReturnsFalse() {
        BatteryParser.extractDeviceInfo(info, "", kvMap);

        assertFalse("Should not have device info", info.hasDeviceInfo());
        assertNull("IMEI should be null", info.imei1);
        assertNull("Serial number should be null", info.serialNumber);
    }

    // ==================== 品牌识别测试 ====================

    @Test
    public void detectBrand_xiaomiKeywords_returnsXiaomi() {
        String content = "ro.product.brand=Xiaomi\nro.product.manufacturer=Xiaomi";

        String brand = BatteryParser.detectBrand(content);

        assertEquals("Should detect Xiaomi", "xiaomi", brand);
    }

    @Test
    public void detectBrand_huaweiKeywords_returnsHuawei() {
        String content = "ro.product.brand=HUAWEI\nro.build.version.emui=12.0";

        String brand = BatteryParser.detectBrand(content);

        assertEquals("Should detect Huawei", "huawei", brand);
    }

    @Test
    public void detectBrand_noMatch_returnsGeneric() {
        String content = "ro.product.brand=UnknownBrand";

        String brand = BatteryParser.detectBrand(content);

        assertEquals("Should return generic", "generic", brand);
    }

    @Test
    public void detectBrand_nullContent_returnsGeneric() {
        String brand = BatteryParser.detectBrand(null);

        assertEquals("Should return generic for null", "generic", brand);
    }

    // ==================== 容量计算测试 ====================

    @Test
    public void calculateHealthPercentage_normalValues_returnsCorrect() {
        int current = 4000;
        int design = 4500;

        double health = BatteryParser.calculateHealthPercentage(current, design);

        assertEquals("Health should be 88.9%", 88.9, health, 0.1);
    }

    @Test
    public void calculateHealthPercentage_zeroDesign_returnsZero() {
        int current = 4000;
        int design = 0;

        double health = BatteryParser.calculateHealthPercentage(current, design);

        assertEquals("Health should be 0 when design is 0", 0.0, health, 0.1);
    }

    @Test
    public void calculateHealthPercentage_currentExceedsDesign_cappedAt100() {
        int current = 5000;
        int design = 4500;

        double health = BatteryParser.calculateHealthPercentage(current, design);

        assertEquals("Health should be capped at 100%", 100.0, health, 0.1);
    }

    // ==================== 边界条件测试 ====================

    @Test
    public void parseHealthdLines_extremeTemperature_handlesCorrectly() {
        String input = "healthd: l=50 v=3800 t=99.9 c=0 fc=3000000 cc=100";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertEquals("Temperature should be 99.9", 99.9, result.batteryTemp, 0.1);
    }

    @Test
    public void parseHealthdLines_negativeTemperature_handlesCorrectly() {
        String input = "healthd: l=50 v=3800 t=-10.5 c=0 fc=3000000 cc=100";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertEquals("Temperature should be -10.5", -10.5, result.batteryTemp, 0.1);
    }

    @Test
    public void parseHealthdLines_veryHighCycleCount_handlesCorrectly() {
        String input = "healthd: l=50 v=3800 t=30.0 c=0 fc=3000000 cc=9999";
        BatteryParser.BatteryInfo result = BatteryParser.parseHealthdLines(input);

        assertEquals("Cycle count should be 9999", 9999, result.cycleCount);
    }

    // ==================== 辅助方法 ====================

    private void addToKvMap(String key, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        kvMap.put(key, values);
    }
}
