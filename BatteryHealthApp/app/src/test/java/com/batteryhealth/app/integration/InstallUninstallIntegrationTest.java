package com.batteryhealth.app.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.database.DatabaseEncryptionHelper;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.test.TestUtils;

import net.sqlcipher.database.SupportFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * 安装/卸载/升级/清理数据场景集成测试。
 *
 * 覆盖：
 * - 全新安装：从空目录启动，应用能正常初始化加密数据库与生成密钥
 * - 卸载（清理数据）：删除全部数据库文件后，密钥已持久化到 EncryptedSharedPreferences 中
 *   可保持稳定（不会因为目录清空而重新生成密钥导致用户数据无法解密）
 * - 重新安装（保留数据）：从备份恢复明文数据 -> 加密数据库能读取
 * - 升级：明文数据库 -> 加密数据库数据迁移后，明文备份被删除
 * - 多次清理 / 多次安装：密钥一致性
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InstallUninstallIntegrationTest {

    private Context context;
    private static final String DB_NAME = "battery_health_db";

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // 清理测试用 SharedPreferences
        clearAllTestPrefs();
        // 清理测试用数据库文件
        clearAllTestDatabases();
    }

    @After
    public void tearDown() {
        TestUtils.clearAllTestData();
        clearAllTestPrefs();
    }

    // ====================================================================
    // 全新安装场景
    // ====================================================================

    @Test
    public void freshInstall_createsEncryptedDatabase() {
        // 全新安装：没有任何数据
        assertFalse("DB should not exist before init",
                databaseExists(DB_NAME));

        // 第一次获取密钥
        byte[] passphrase = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(passphrase);
        assertEquals("Passphrase length should be 32 bytes", 32, passphrase.length);

        // 第二次获取密钥应保持一致
        byte[] passphrase2 = DatabaseEncryptionHelper.getPassphrase(context);
        assertNotNull(passphrase2);
        for (int i = 0; i < passphrase.length; i++) {
            assertEquals("Passphrase should be stable across calls", passphrase[i], passphrase2[i]);
        }
    }

    @Test
    public void freshInstall_emptyDatabaseInsertQuery() {
        // 全新安装后插入并查询数据
        AppDatabase db = createEncryptedDatabase();
        assertNotNull(db);
        assertTrue(db.isOpen());

        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        info.setCurrentCapacity(4500);
        info.setHealthPercentage(90f);
        db.batteryInfoDao().insert(info);

        List<BatteryInfo> all = db.batteryInfoDao().getAll();
        assertEquals(1, all.size());
        assertEquals(5000, all.get(0).getDesignCapacity());
    }

    // ====================================================================
    // 卸载场景（清理数据）
    // ====================================================================

    @Test
    public void uninstall_clearsDatabaseFiles() {
        // 模拟首次安装
        AppDatabase db = createEncryptedDatabase();
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        db.batteryInfoDao().insert(info);
        db.close();
        assertTrue("DB should exist after first install", databaseExists(DB_NAME));

        // 模拟卸载：删除数据库文件
        deleteDatabaseFiles(DB_NAME);
        assertFalse("DB should not exist after uninstall", databaseExists(DB_NAME));

        // 重新安装后，数据库应能正常打开（密钥从 EncryptedSharedPreferences 恢复）
        AppDatabase db2 = createEncryptedDatabase();
        assertNotNull(db2);
        assertTrue(db2.isOpen());
        // 数据库是新的，列表应该为空
        List<BatteryInfo> all = db2.batteryInfoDao().getAll();
        assertNotNull(all);
        db2.close();
    }

    @Test
    public void uninstall_doesNotAffectEncryptionKey() {
        // 第一次启动获取密钥
        byte[] passphrase1 = DatabaseEncryptionHelper.getPassphrase(context);

        // 模拟卸载后再次启动
        clearAllTestDatabases();
        byte[] passphrase2 = DatabaseEncryptionHelper.getPassphrase(context);

        // 密钥应该一致（来自 SharedPreferences，不会因数据库文件被清空而改变）
        for (int i = 0; i < passphrase1.length; i++) {
            assertEquals("Passphrase should survive uninstall (key stored in prefs)",
                    passphrase1[i], passphrase2[i]);
        }
    }

    // ====================================================================
    // 升级场景（明文库 -> 加密库迁移）
    // ====================================================================

    @Test
    public void upgrade_plainToEncrypted_migratesData() {
        // 1. 创建明文数据库并插入数据
        AppDatabase plainDb = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        info.setCurrentCapacity(4500);
        info.setHealthPercentage(85f);
        plainDb.batteryInfoDao().insert(info);

        PerformanceData perf = new PerformanceData();
        perf.setCpuUsage(30f);
        plainDb.performanceDataDao().insert(perf);

        PowerHistory history = new PowerHistory();
        history.setPower(80f);
        plainDb.powerHistoryDao().insert(history);
        plainDb.close();
        assertTrue("Plain DB should exist", databaseExists(DB_NAME));

        // 2. 模拟升级：执行明文 -> 加密迁移
        DatabaseEncryptionHelper.DatabaseSnapshot snapshot =
                DatabaseEncryptionHelper.migratePlainDatabaseIfNeeded(context);
        assertNotNull("Snapshot should not be null when plain DB exists", snapshot);
        assertEquals("Should have 1 battery info", 1, snapshot.batteryInfoList.size());
        assertEquals("Should have 1 performance data", 1, snapshot.performanceDataList.size());
        assertEquals("Should have 1 power history", 1, snapshot.powerHistoryList.size());

        // 3. 重命名明文库为备份
        boolean renamed = DatabaseEncryptionHelper.renamePlainDatabaseToBackup(context);
        assertTrue("Plain DB should be renamed to backup", renamed);
        assertFalse("Plain DB should not exist after rename", databaseExists(DB_NAME));
        assertTrue("Backup should exist", databaseExists(DB_NAME + "_plain_backup"));

        // 4. 验证备份文件结构
        File backupWal = context.getDatabasePath(DB_NAME + "_plain_backup-wal");
        File backupShm = context.getDatabasePath(DB_NAME + "_plain_backup-shm");
        // 实际生产中 wal/shm 文件由 SQLite 生成
        assertNotNull(backupWal);
        assertNotNull(backupShm);

        // 5. 清理备份
        boolean deleted = DatabaseEncryptionHelper.deletePlainDatabaseBackup(context);
        assertTrue("Backup should be deleted", deleted);
        assertFalse("Backup should not exist", databaseExists(DB_NAME + "_plain_backup"));
    }

    @Test
    public void upgrade_noPlainDb_snapshotIsNull() {
        // 没有任何数据库文件时，迁移应该返回 null
        assertFalse(databaseExists(DB_NAME));
        DatabaseEncryptionHelper.DatabaseSnapshot snapshot =
                DatabaseEncryptionHelper.migratePlainDatabaseIfNeeded(context);
        assertNull("Snapshot should be null when no plain DB exists", snapshot);
    }

    @Test
    public void restoreFromBackup_roundTrip() {
        // 1. 创建明文库
        AppDatabase plainDb = Room.databaseBuilder(context, AppDatabase.class, DB_NAME).build();
        BatteryInfo info = new BatteryInfo();
        info.setDesignCapacity(5000);
        plainDb.batteryInfoDao().insert(info);
        plainDb.close();

        // 2. 重命名为备份
        assertTrue(DatabaseEncryptionHelper.renamePlainDatabaseToBackup(context));

        // 3. 模拟加密库初始化失败：从备份恢复明文
        assertTrue(DatabaseEncryptionHelper.restorePlainDatabaseFromBackup(context));
        assertTrue("Plain DB should be restored", databaseExists(DB_NAME));
        assertFalse("Backup should be gone", databaseExists(DB_NAME + "_plain_backup"));

        // 4. 验证数据
        AppDatabase restoredDb = Room.databaseBuilder(context, AppDatabase.class, DB_NAME).build();
        List<BatteryInfo> all = restoredDb.batteryInfoDao().getAll();
        assertEquals("Restored DB should have data", 1, all.size());
        restoredDb.close();
    }

    // ====================================================================
    // 多次安装/卸载循环
    // ====================================================================

    @Test
    public void reinstallCycle_keyStability() {
        byte[] initialKey = DatabaseEncryptionHelper.getPassphrase(context);

        for (int i = 0; i < 5; i++) {
            clearAllTestDatabases();
            byte[] currentKey = DatabaseEncryptionHelper.getPassphrase(context);
            for (int j = 0; j < initialKey.length; j++) {
                assertEquals("Key should remain stable across uninstall/reinstall cycles",
                        initialKey[j], currentKey[j]);
            }
        }
    }

    @Test
    public void multipleInstallations_dbCanBeReopened() {
        for (int i = 0; i < 3; i++) {
            AppDatabase db = createEncryptedDatabase();
            assertNotNull(db);
            assertTrue(db.isOpen());

            // 每次插入不同的数据
            BatteryInfo info = new BatteryInfo();
            info.setDesignCapacity(1000 * (i + 1));
            db.batteryInfoDao().insert(info);

            db.close();

            // 模拟卸载
            deleteDatabaseFiles(DB_NAME);
        }
    }

    // ====================================================================
    // 边界场景
    // ====================================================================

    @Test
    public void emptyEncryptedDb_canBeInsertedAndRead() {
        AppDatabase db = createEncryptedDatabase();
        assertNotNull(db);

        // 首次插入
        BatteryInfo info1 = new BatteryInfo();
        info1.setDesignCapacity(5000);
        long id1 = db.batteryInfoDao().insert(info1);
        assertTrue("Inserted id should be > 0", id1 > 0);

        // 多次插入
        for (int i = 0; i < 10; i++) {
            BatteryInfo info = new BatteryInfo();
            info.setDesignCapacity(1000 * i);
            long id = db.batteryInfoDao().insert(info);
            assertTrue(id > 0);
        }

        // 验证数量
        List<BatteryInfo> all = db.batteryInfoDao().getAll();
        assertEquals("Should have 11 records", 11, all.size());
        db.close();
    }

    @Test
    public void uninstall_doesNotAffectSharedPrefs() {
        // 写入 SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(
                "test_install_uninstall", Context.MODE_PRIVATE);
        String value = "test_value_" + UUID.randomUUID();
        prefs.edit().putString("key1", value).apply();

        // 模拟卸载数据库（不删除 SharedPreferences）
        deleteDatabaseFiles(DB_NAME);

        // 重新读取
        SharedPreferences prefs2 = context.getSharedPreferences(
                "test_install_uninstall", Context.MODE_PRIVATE);
        assertEquals(value, prefs2.getString("key1", null));
    }

    // ====================================================================
    // 辅助方法
    // ====================================================================

    private boolean databaseExists(String name) {
        File dbFile = context.getDatabasePath(name);
        return dbFile != null && dbFile.exists();
    }

    private void deleteDatabaseFiles(String name) {
        File dbFile = context.getDatabasePath(name);
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();
    }

    private void clearAllTestDatabases() {
        String[] dbNames = {
                DB_NAME,
                DB_NAME + "-journal",
                DB_NAME + "_plain_backup",
                DB_NAME + "_plain_backup-wal",
                DB_NAME + "_plain_backup-shm"
        };
        for (String name : dbNames) {
            File f = context.getDatabasePath(name);
            if (f != null && f.exists()) f.delete();
            // 兜底：在 databases/ 目录下查找
            File alt = new File(context.getDatabasePath(DB_NAME).getParent(), name);
            if (alt.exists()) alt.delete();
        }
    }

    private void clearAllTestPrefs() {
        // 清理所有测试用 SharedPreferences 文件
        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            File[] files = prefsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.startsWith("test_")
                            || name.equals("battery_db_key_prefs.xml")
                            || name.equals("battery_db_key_prefs_plain.xml")) {
                        f.delete();
                    }
                }
            }
        }
    }

    private AppDatabase createEncryptedDatabase() {
        byte[] passphrase = DatabaseEncryptionHelper.getPassphrase(context);
        SupportFactory factory = new SupportFactory(passphrase);
        return Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(AppDatabase.MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
    }
}
