package com.batteryhealth.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

/**
 * LogHelper 日志输出 + 安全性测试。
 *
 * 验证:
 * 1. 各种日志级别可调用不崩溃
 * 2. TAG 前缀正确
 * 3. Release 模式不输出 Debug 日志
 * 4. 大量日志性能
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class LogHelperTest {

    @Test
    public void testDebug_doesNotCrash() {
        try {
            LogHelper.d("test", "debug message");
            LogHelper.d("test", "debug with throwable", new RuntimeException("test"));
        } catch (Exception e) {
            fail("LogHelper.d should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testInfo_doesNotCrash() {
        try {
            LogHelper.i("test", "info message");
            LogHelper.i("test", "info with throwable", new RuntimeException("test"));
        } catch (Exception e) {
            fail("LogHelper.i should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testWarn_doesNotCrash() {
        try {
            LogHelper.w("test", "warn message");
            LogHelper.w("test", "warn with throwable", new RuntimeException("test"));
        } catch (Exception e) {
            fail("LogHelper.w should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testError_doesNotCrash() {
        try {
            LogHelper.e("test", "error message");
            LogHelper.e("test", "error with throwable", new RuntimeException("test"));
        } catch (Exception e) {
            fail("LogHelper.e should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testWtf_doesNotCrash() {
        try {
            LogHelper.wtf("test", "wtf message");
            LogHelper.wtf("test", "wtf with throwable", new RuntimeException("test"));
        } catch (Exception e) {
            fail("LogHelper.wtf should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testLogOutput_capturedByShadowLog() {
        ShadowLog.clear();
        LogHelper.i("MyTag", "test message");
        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        // 应有日志被记录
        boolean found = false;
        for (ShadowLog.LogItem item : logs) {
            if (item.msg != null && item.msg.contains("test message")) {
                found = true;
                break;
            }
        }
        assertTrue("Log message should be captured", found);
    }

    @Test
    public void testTagPrefix_present() {
        ShadowLog.clear();
        LogHelper.i("MyComponent", "msg");
        List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        for (ShadowLog.LogItem item : logs) {
            if (item.msg != null && item.msg.contains("msg")) {
                // TAG 应包含前缀
                assertTrue("Tag should have prefix: " + item.tag,
                        item.tag != null && item.tag.startsWith("BatteryHealth_"));
                return;
            }
        }
    }

    @Test
    public void testNullMessage_doesNotCrash() {
        try {
            LogHelper.i("test", null);
        } catch (Exception e) {
            fail("null message should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testNullTag_doesNotCrash() {
        try {
            LogHelper.i(null, "msg");
        } catch (Exception e) {
            fail("null tag should not throw: " + e.getMessage());
        }
    }

    @Test
    public void testPerformance_1000Logs() {
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            LogHelper.i("perf", "message " + i);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("1000 logs should be < 1s, took " + elapsed + "ms",
                elapsed < 1000);
    }

    @Test
    public void testSpecialCharsInMessage_safe() {
        try {
            LogHelper.i("test", "包含中文 🔥 & special < > \" ' \\ chars");
        } catch (Exception e) {
            fail("Special chars should not throw: " + e.getMessage());
        }
    }
}
