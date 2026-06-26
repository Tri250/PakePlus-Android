package com.batteryhealth.app.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Converters 类型转换稳定性 + 边界值测试。
 */
public class ConvertersStabilityTest {

    private final Converters converters = new Converters();

    @Test
    public void testStringList_roundTrip() {
        List<String> input = new ArrayList<>();
        input.add("a");
        input.add("b");
        input.add("c");
        String serialized = converters.fromStringList(input);
        List<String> deserialized = converters.toStringList(serialized);
        assertNotNull(deserialized);
        assertEquals(3, deserialized.size());
        assertEquals("a", deserialized.get(0));
        assertEquals("c", deserialized.get(2));
    }

    @Test
    public void testStringList_nullSafe() {
        assertNull(converters.fromStringList(null));
        assertNull(converters.toStringList(null));
    }

    @Test
    public void testStringList_emptyList() {
        String serialized = converters.fromStringList(new ArrayList<>());
        assertNotNull(serialized);
        List<String> deserialized = converters.toStringList(serialized);
        assertNotNull(deserialized);
        assertEquals(0, deserialized.size());
    }

    @Test
    public void testStringList_unicodeAndSpecialChars() {
        List<String> input = new ArrayList<>();
        input.add("中文");
        input.add("🎉");
        input.add("with \"quotes\"");
        input.add("with\nnewline");
        input.add("with\\backslash");
        String serialized = converters.fromStringList(input);
        List<String> deserialized = converters.toStringList(serialized);
        assertEquals(input, deserialized);
    }

    @Test
    public void testStringList_largeList() {
        List<String> input = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            input.add("item-" + i);
        }
        String serialized = converters.fromStringList(input);
        List<String> deserialized = converters.toStringList(serialized);
        assertEquals(1000, deserialized.size());
    }

    @Test
    public void testStringList_performance() {
        List<String> input = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            input.add("item-" + i);
        }
        long elapsed = TestUtils.measureExecutionTime("Converters.stringList.100", () -> {
            for (int i = 0; i < 100; i++) {
                String s = converters.fromStringList(input);
                List<String> r = converters.toStringList(s);
                assertEquals(100, r.size());
            }
        });
        assertTrue("Converters too slow: " + elapsed + "ms", elapsed < 2000);
    }
}
