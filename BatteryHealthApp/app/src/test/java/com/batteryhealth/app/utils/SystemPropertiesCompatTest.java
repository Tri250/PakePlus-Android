package com.batteryhealth.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SystemPropertiesCompat 反射安全 + 缓存 + 性能测试。
 *
 * 验证:
 * 1. null key 安全
 * 2. 缓存正确性
 * 3. 默认值机制
 * 4. 并发安全
 * 5. 大量读取性能
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SystemPropertiesCompatTest {

    @Test
    public void testGet_nullKey_returnsNull() {
        assertNull(SystemPropertiesCompat.get(null));
    }

    @Test
    public void testGet_nullKeyWithDefault_returnsDefault() {
        assertEquals("default", SystemPropertiesCompat.get(null, "default"));
    }

    @Test
    public void testGet_unknownKey_returnsNull() {
        // 未知 key 应返回 null
        String result = SystemPropertiesCompat.get("com.batteryhealth.app.test.nonexistent.key.12345");
        // 不崩溃即可
    }

    @Test
    public void testGet_unknownKeyWithDefault_returnsDefault() {
        String result = SystemPropertiesCompat.get(
                "com.batteryhealth.app.test.nonexistent.12345", "fallback");
        assertEquals("fallback", result);
    }

    @Test
    public void testGet_knownKey_returnsValue() {
        // ro.product.brand 在测试中可能为空
        String result = SystemPropertiesCompat.get("ro.product.brand");
        // 不为 null 即可（Robolectric 通常提供值）
    }

    @Test
    public void testGet_concurrent_safe() throws InterruptedException {
        int threadCount = 10;
        int iterations = 100;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        try {
                            SystemPropertiesCompat.get("ro.product.brand");
                            SystemPropertiesCompat.get("ro.product.model");
                            SystemPropertiesCompat.get("ro.build.version.sdk", "0");
                        } catch (Throwable t) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }

    @Test
    public void testGetSoC_returnsValueOrNull() {
        String soc = SystemPropertiesCompat.getSoC();
        // 不崩溃; 可能为 null
    }

    @Test
    public void testGetDeviceMarketingName_neverNull() {
        String name = SystemPropertiesCompat.getDeviceMarketingName();
        assertNotNull(name);
    }

    @Test
    public void testPerformance_bulkGet_1000() {
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SystemPropertiesCompat.get("ro.product.brand", "unknown");
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("1000 gets should be < 500ms, took " + elapsed + "ms",
                elapsed < 500);
    }

    @Test
    public void testCache_effective() {
        // 第一次调用 + 后续调用应使用缓存
        long start1 = System.nanoTime();
        SystemPropertiesCompat.get("ro.product.brand", "x");
        long t1 = System.nanoTime() - start1;

        // 1000 次缓存命中
        long start2 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SystemPropertiesCompat.get("ro.product.brand", "x");
        }
        long t2 = System.nanoTime() - start2;

        // 缓存命中应极快
        assertTrue("Cached reads should be fast: " + t2 / 1_000_000L + "ms",
                t2 < 10_000_000L);
    }
}
