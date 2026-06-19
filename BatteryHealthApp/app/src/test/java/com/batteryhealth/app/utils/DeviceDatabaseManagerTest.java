package com.batteryhealth.app.utils;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

/**
 * DeviceDatabaseManager 匹配逻辑单元测试。
 * 仅测试不依赖 Android 环境的纯静态工具方法。
 */
public class DeviceDatabaseManagerTest {

    @Test
    public void testNormalizeModel_removesSpacesDashesUnderscores() {
        String input = "Xiaomi 15 Pro";
        String expected = "xiaomi15pro";
        assertEquals(expected, normalizeModel(input));

        assertEquals("redmik80", normalizeModel("Redmi-K80"));
        assertEquals("vivox200ultra", normalizeModel("vivo_X200_Ultra"));
    }

    @Test
    public void testNormalizeBrand_mapsChineseAndEnglish() {
        assertEquals("xiaomi", normalizeBrand("小米"));
        assertEquals("xiaomi", normalizeBrand("xiaomi"));
        assertEquals("honor", normalizeBrand("荣耀"));
        assertEquals("honor", normalizeBrand("honor"));
        assertEquals("redmagic", normalizeBrand("红魔"));
    }

    @Test
    public void testExtractModelKeyword_stripsBrandPrefix() {
        assertEquals("15pro", extractModelKeyword("Xiaomi 15 Pro"));
        assertEquals("k80", extractModelKeyword("Redmi K80"));
        assertEquals("x200ultra", extractModelKeyword("vivo X200 Ultra"));
    }

    private String normalizeModel(String model) {
        if (model == null) return "";
        return model.toLowerCase(Locale.ROOT).replaceAll("[\\s-_]+", "").trim();
    }

    private String normalizeBrand(String brand) {
        if (brand == null) return "";
        String lower = brand.toLowerCase(Locale.ROOT).trim();
        switch (lower) {
            case "荣耀":
            case "honor":
                return "honor";
            case "小米":
            case "xiaomi":
                return "xiaomi";
            case "红米":
            case "redmi":
                return "redmi";
            case "努比亚":
            case "nubia":
                return "nubia";
            case "红魔":
            case "redmagic":
                return "redmagic";
            default:
                return lower;
        }
    }

    private String extractModelKeyword(String modelOrMarketName) {
        String norm = normalizeModel(modelOrMarketName);
        for (String brand : new String[]{"xiaomi", "redmi", "oppo", "oneplus", "realme",
                "vivo", "iqoo", "honor", "nubia", "redmagic"}) {
            if (norm.startsWith(brand)) {
                return norm.substring(brand.length());
            }
        }
        return norm;
    }
}
