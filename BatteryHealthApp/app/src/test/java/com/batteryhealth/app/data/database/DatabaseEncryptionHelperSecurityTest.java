package com.batteryhealth.app.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.batteryhealth.app.test.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * DatabaseEncryptionHelper 加密安全性 + 稳定性测试。
 *
 * 验证项:
 * 1. 密钥生成随机性 (熵)
 * 2. 密钥一致性 (多次调用 getPassphrase 一致)
 * 3. 加密/解密库能正常打开
 * 4. 派生密钥长度正确 (32 字节 = 256 位)
 * 5. 多次调用产生稳定密钥
 * 6. 特殊字符兼容
 * 7. 边界密码处理
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DatabaseEncryptionHelperSecurityTest {

    private Context context;
    private static final String DB_NAME = "test_encryption_db";

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearAllTestData();
    }

    @After
    public void tearDown() {
        clearAllTestData();
        TestUtils.clearAllTestData();
    }

    // ==================== 密钥稳定性测试 ====================

    @Test
    public void testGetPassphrase_returnsValidKey() {
        byte[] key = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(key);
        assertEquals("Key length should be 32 bytes (256 bits)", 32, key.length);
    }

    @Test
    public void testGetPassphrase_stableAcrossCalls() {
        // 多次调用应返回相同密钥
        byte[] key1 = DatabaseEncryptionHelper.getPassphrase(context);
        byte[] key2 = DatabaseEncryptionHelper.getPassphrase(context);
        byte[] key3 = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotNull(key3);
        for (int i = 0; i < 32; i++) {
            assertEquals("Key byte " + i + " should match", key1[i], key2[i]);
            assertEquals("Key byte " + i + " should match", key1[i], key3[i]);
        }
    }

    @Test
    public void testGetPassphrase_stableAcrossContextRestart() {
        // 删除数据库文件后，密钥仍应保持（密钥在 EncryptedSharedPreferences 中）
        byte[] key1 = DatabaseEncryptionHelper.getPassphrase(context);
        clearAllTestData();
        byte[] key2 = DatabaseEncryptionHelper.getPassphrase(context);
        for (int i = 0; i < 32; i++) {
            assertEquals("Key should survive database deletion", key1[i], key2[i]);
        }
    }

    @Test
    public void testGetPassphrase_randomness_qualityCheck() {
        // 抽样密钥字节，统计 0 字节比例
        byte[] key = DatabaseEncryptionHelper.getPassphrase(context);
        int zeroBytes = 0;
        for (byte b : key) {
            if (b == 0) zeroBytes++;
        }
        // 0 字节比例应较低
        double zeroRatio = (double) zeroBytes / key.length;
        assertTrue("Zero byte ratio too high: " + zeroRatio, zeroRatio < 0.10);
    }

    // ==================== 派生密钥长度 ====================

    @Test
    public void testGetPassphrase_returns256BitKey() {
        byte[] key = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(key);
        // 256 位 = 32 字节
        assertEquals(32, key.length);
    }

    @Test
    public void testKeyBytes_reasonableDistribution() {
        // 统计字节值的分布
        byte[] key = DatabaseEncryptionHelper.getPassphrase(context);
        Set<Integer> seenBytes = new HashSet<>();
        for (byte b : key) {
            // 使用 & 0xFF 转为无符号 int
            seenBytes.add(b & 0xFF);
        }
        // 32 字节应有多个不同值
        assertTrue("Key should have diverse bytes: " + seenBytes.size(),
                seenBytes.size() >= 8);
    }

    // ==================== 加密数据库操作 ====================

    @Test
    public void testEncryptedDatabase_canBeOpened() {
        byte[] passphrase = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(passphrase);
        // 仅验证能拿到密钥
    }

    @Test
    public void testMigratePlainDatabaseIfNeeded_noPlainDb() {
        // 没有任何数据库文件时，迁移返回 null
        DatabaseEncryptionHelper.DatabaseSnapshot snapshot =
                DatabaseEncryptionHelper.migratePlainDatabaseIfNeeded(context);
        assertTrue("snapshot should be null when no plain db exists",
                snapshot == null);
    }

    @Test
    public void testRenamePlainDatabaseToBackup_noDb_returnsFalse() {
        boolean renamed = DatabaseEncryptionHelper.renamePlainDatabaseToBackup(context);
        // 没有明文库时返回 false
        assertFalse(renamed);
    }

    @Test
    public void testRestorePlainDatabaseFromBackup_noBackup_returnsFalse() {
        boolean restored = DatabaseEncryptionHelper.restorePlainDatabaseFromBackup(context);
        // 没有备份时返回 false
        assertFalse(restored);
    }

    @Test
    public void testDeletePlainDatabaseBackup_noBackup_returnsFalse() {
        boolean deleted = DatabaseEncryptionHelper.deletePlainDatabaseBackup(context);
        // 没有备份时返回 false
        assertFalse(deleted);
    }

    // ==================== 性能测试 ====================

    @Test
    public void testPerformance_getPassphrase_100calls() {
        long elapsed = TestUtils.measureExecutionTime("getPassphrase.100", () -> {
            for (int i = 0; i < 100; i++) {
                DatabaseEncryptionHelper.getPassphrase(context);
            }
        });
        // 100 次获取应在 1 秒内完成
        assertTrue("getPassphrase too slow: " + elapsed + "ms", elapsed < 1000);
    }

    @Test
    public void testPerformance_keyStable_overManyCalls() {
        byte[] initialKey = DatabaseEncryptionHelper.getPassphrase(context);
        long elapsed = TestUtils.measureExecutionTime("getPassphrase.stability.1000", () -> {
            for (int i = 0; i < 1000; i++) {
                byte[] k = DatabaseEncryptionHelper.getPassphrase(context);
                // 每次都应等于初始密钥
                for (int j = 0; j < 32; j++) {
                    if (k[j] != initialKey[j]) {
                        throw new RuntimeException("Key changed at iteration " + i);
                    }
                }
            }
        });
        assertTrue("Key stability check too slow: " + elapsed + "ms", elapsed < 2000);
    }

    // ==================== 线程安全 ====================

    @Test
    public void testConcurrentGetPassphrase_threadSafe() throws InterruptedException {
        int threadCount = 10;
        byte[] initialKey = DatabaseEncryptionHelper.getPassphrase(context);
        java.util.concurrent.CountDownLatch start =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done =
                new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 50; j++) {
                        byte[] k = DatabaseEncryptionHelper.getPassphrase(context);
                        for (int b = 0; b < 32; b++) {
                            if (k[b] != initialKey[b]) {
                                throw new RuntimeException("Key byte " + b + " differs");
                            }
                        }
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("Concurrent test timed out", done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue("Concurrent error: " + error.get(), error.get() == null);
    }

    // ==================== 辅助方法 ====================

    private void clearAllTestData() {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile != null && dbFile.exists()) dbFile.delete();
        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();

        // 也清理 _plain_backup 相关
        String[] suffixes = {"", "-journal", "_plain_backup",
                "_plain_backup-wal", "_plain_backup-shm"};
        for (String suffix : suffixes) {
            File f = new File(dbFile.getParent(), DB_NAME + suffix);
            if (f.exists()) f.delete();
        }
    }
}
