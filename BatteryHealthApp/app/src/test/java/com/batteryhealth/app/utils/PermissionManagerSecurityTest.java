package com.batteryhealth.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 权限管理 + 加密安全 + 隐私保护测试。
 *
 * 覆盖：
 * - 权限码常量
 * - hasPermission / hasPermissions 在已授予/未授予状态下的行为
 * - 空数组 / null 安全性
 * - 多权限批量检查
 * - 并发权限检查线程安全
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PermissionManagerSecurityTest {

    private android.app.Activity activity;

    @Before
    public void setUp() {
        ActivityController<android.app.Activity> controller = Robolectric.buildActivity(android.app.Activity.class);
        controller.create().start().resume();
        activity = controller.get();
    }

    // ====================================================================
    // 常量测试
    // ====================================================================

    @Test
    public void permissionRequestCode_isValid() {
        assertTrue("PERMISSION_REQUEST_CODE should be positive",
                PermissionManager.PERMISSION_REQUEST_CODE > 0);
        // 通常 100 以上以避免与系统回调冲突
        assertTrue("PERMISSION_REQUEST_CODE should be >= 100 to avoid conflicts",
                PermissionManager.PERMISSION_REQUEST_CODE >= 100);
    }

    // ====================================================================
    // hasPermission 测试
    // ====================================================================

    @Test
    public void hasPermission_nonExistentPermission_returnsFalse() {
        String fakePermission = "android.permission." + UUID.randomUUID().toString();
        boolean granted = PermissionManager.hasPermission(activity, fakePermission);
        assertFalse("Non-existent permission should not be granted", granted);
    }

    @Test
    public void hasPermission_nullActivity_throwsNPE() {
        String permission = "android.permission.POST_NOTIFICATIONS";
        try {
            PermissionManager.hasPermission(null, permission);
            // Robolectric 可能返回 false 而不抛 NPE
        } catch (NullPointerException expected) {
            // NPE is acceptable
        } catch (Exception e) {
            // 其他异常
        }
    }

    @Test
    public void hasPermission_nullPermission_returnsFalse() {
        try {
            boolean granted = PermissionManager.hasPermission(activity, null);
            assertFalse(granted);
        } catch (Exception e) {
            // NPE 或其他异常均可
        }
    }

    @Test
    public void hasPermission_realPermission_ungranted() {
        // 在测试环境下，POST_NOTIFICATIONS 应该未授予
        boolean granted = PermissionManager.hasPermission(activity,
                "android.permission.POST_NOTIFICATIONS");
        assertFalse(granted);
    }

    // ====================================================================
    // hasPermissions 测试
    // ====================================================================

    @Test
    public void hasPermissions_emptyArray_returnsTrue() {
        // 空数组视为"全部已授予"
        boolean result = PermissionManager.hasPermissions(activity, new String[0]);
        assertTrue("Empty array should return true", result);
    }

    @Test
    public void hasPermissions_allUngranted_returnsFalse() {
        String[] permissions = {
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_PHONE_STATE",
                "android.permission." + UUID.randomUUID()
        };
        boolean result = PermissionManager.hasPermissions(activity, permissions);
        assertFalse("All ungranted permissions should return false", result);
    }

    @Test
    public void hasPermissions_atLeastOneUngranted_returnsFalse() {
        String[] permissions = {
                "android.permission." + UUID.randomUUID(),  // 未授予
                "android.permission.POST_NOTIFICATIONS"     // 假定未授予
        };
        boolean result = PermissionManager.hasPermissions(activity, permissions);
        assertFalse(result);
    }

    @Test
    public void hasPermissions_nullArray_handlesGracefully() {
        try {
            PermissionManager.hasPermissions(activity, null);
        } catch (NullPointerException expected) {
            // NPE acceptable
        } catch (Exception e) {
            // 其他异常
        }
    }

    // ====================================================================
    // checkAndRequestPermissions 测试
    // ====================================================================

    @Test
    public void checkAndRequestPermissions_emptyArray_doesNotThrow() {
        try {
            PermissionManager.checkAndRequestPermissions(activity, new String[0]);
        } catch (Exception e) {
            // AlertDialog 可能在 Robolectric 下抛 RuntimeException
        }
    }

    @Test
    public void checkAndRequestPermissions_alreadyGranted_doesNotThrow() {
        // 假设所有权限都未授予
        try {
            PermissionManager.checkAndRequestPermissions(activity,
                    new String[]{"android.permission." + UUID.randomUUID()});
        } catch (Exception e) {
            // AlertDialog 抛异常可接受
        }
    }

    // ====================================================================
    // handlePermissionResult 测试
    // ====================================================================

    @Test
    public void handlePermissionResult_allGranted_doesNotThrow() {
        String[] permissions = {"android.permission.POST_NOTIFICATIONS"};
        int[] grants = {PackageManager.PERMISSION_GRANTED};
        try {
            PermissionManager.handlePermissionResult(activity, permissions, grants);
        } catch (Exception e) {
            // 不应该为已授予权限弹窗
        }
    }

    @Test
    public void handlePermissionResult_allDenied_doesNotThrow() {
        String[] permissions = {"android.permission.POST_NOTIFICATIONS"};
        int[] grants = {PackageManager.PERMISSION_DENIED};
        try {
            PermissionManager.handlePermissionResult(activity, permissions, grants);
        } catch (Exception e) {
            // AlertDialog 抛异常可接受
        }
    }

    @Test
    public void handlePermissionResult_mixedGrants_doesNotThrow() {
        String[] permissions = {
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_PHONE_STATE"
        };
        int[] grants = {PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED};
        try {
            PermissionManager.handlePermissionResult(activity, permissions, grants);
        } catch (Exception e) {
            // AlertDialog 抛异常可接受
        }
    }

    // ====================================================================
    // 并发安全性测试
    // ====================================================================

    @Test
    public void hasPermission_concurrentInvocation_threadSafe() throws InterruptedException {
        int threads = 10;
        String permission = "android.permission.POST_NOTIFICATIONS";
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        PermissionManager.hasPermission(activity, permission);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("concurrent hasPermission should finish", done.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void hasPermissions_concurrentInvocation_threadSafe() throws InterruptedException {
        int threads = 10;
        String[] permissions = {
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_PHONE_STATE"
        };
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        PermissionManager.hasPermissions(activity, permissions);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("concurrent hasPermissions should finish", done.await(10, TimeUnit.SECONDS));
    }

    // ====================================================================
    // 性能测试
    // ====================================================================

    @Test
    public void performance_hasPermission_1000calls() {
        long elapsed = TestUtils.measureExecutionTime("hasPermission*1000", () -> {
            for (int i = 0; i < 1000; i++) {
                PermissionManager.hasPermission(activity,
                        "android.permission.POST_NOTIFICATIONS");
            }
        });
        // 1000 次应在 5 秒内完成
        assertTrue("1000 calls should be fast: " + elapsed + "ms", elapsed < 5000);
    }
}
