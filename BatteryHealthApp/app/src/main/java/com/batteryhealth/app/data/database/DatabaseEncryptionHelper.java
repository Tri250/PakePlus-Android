package com.batteryhealth.app.data.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.security.SecureRandom;
import java.util.List;

/**
 * 数据库加密助手
 *
 * 使用 SQLCipher 对 Room 数据库进行透明加密，加密密钥通过
 * EncryptedSharedPreferences + Android Keystore 安全存储。
 *
 * 迁移策略：
 * 1. 若检测到已存在的明文数据库，自动导出数据。
 * 2. 将明文数据库重命名为备份，再创建加密数据库。
 * 3. 将历史数据导入加密数据库，成功后删除备份。
 * 4. 若任何步骤失败，从备份恢复明文数据库并回退到无加密模式，避免用户数据丢失。
 */
public class DatabaseEncryptionHelper {

    private static final String TAG = "DatabaseEncryptionHelper";
    private static final String PREFS_FILE = "battery_db_key_prefs";
    private static final String PREFS_FILE_PLAIN = "battery_db_key_prefs_plain";
    private static final String KEY_PASSPHRASE = "db_passphrase";
    private static final String KEY_USE_PLAIN_PREFS = "use_plain_prefs_fallback";
    private static final int PASSPHRASE_LENGTH = 32;
    private static final String DATABASE_NAME = "battery_health_db";

    /**
     * 获取数据库加密密钥。
     * <p>
     * 优先使用 EncryptedSharedPreferences + Android Keystore 存储；
     * 若 Keystore 不可用（如部分定制系统、root 设备），降级到普通 SharedPreferences，
     * 保证应用不崩溃且同一安装周期内密钥保持一致。
     */
    public static byte[] getPassphrase(Context context) {
        Context appContext = context.getApplicationContext();

        // 用于记录降级标志的独立 SharedPreferences（不存储密钥本身）
        SharedPreferences fallbackFlagPrefs = appContext.getSharedPreferences(
                PREFS_FILE_PLAIN, Context.MODE_PRIVATE);
        boolean forcePlain = fallbackFlagPrefs.getBoolean(KEY_USE_PLAIN_PREFS, false);

        SharedPreferences prefs = null;
        if (!forcePlain) {
            prefs = getEncryptedSharedPreferences(appContext);
            if (prefs == null) {
                // 一旦初始化失败，后续整个安装周期都使用明文存储，避免密钥不一致
                forcePlain = true;
                fallbackFlagPrefs.edit().putBoolean(KEY_USE_PLAIN_PREFS, true).apply();
                Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs");
            }
        }
        // 每次启动时都尝试重新初始化 EncryptedSharedPreferences，
        // 如果 Keystore 已恢复可用，则迁移密钥到加密存储并清除降级标志
        if (forcePlain) {
            SharedPreferences tryEncryptedPrefs = getEncryptedSharedPreferences(appContext);
            if (tryEncryptedPrefs != null) {
                // EncryptedSharedPreferences 现在可用，迁移密钥
                SharedPreferences plainPrefs = appContext.getSharedPreferences(PREFS_FILE_PLAIN, Context.MODE_PRIVATE);
                String encoded = plainPrefs.getString(KEY_PASSPHRASE, null);
                if (encoded != null) {
                    tryEncryptedPrefs.edit().putString(KEY_PASSPHRASE, encoded).apply();
                    fallbackFlagPrefs.edit().putBoolean(KEY_USE_PLAIN_PREFS, false).apply();
                    prefs = tryEncryptedPrefs;
                    Log.i(TAG, "Migrated encryption key from plain to encrypted storage");
                }
            }
        }
        if (prefs == null) {
            prefs = appContext.getSharedPreferences(PREFS_FILE_PLAIN, Context.MODE_PRIVATE);
        }

        String encoded = prefs.getString(KEY_PASSPHRASE, null);
        if (encoded == null) {
            byte[] passphrase = generatePassphrase();
            encoded = Base64.encodeToString(passphrase, Base64.NO_WRAP);
            prefs.edit().putString(KEY_PASSPHRASE, encoded).apply();
            return passphrase;
        }
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    /**
     * 若存在明文数据库，读取全部数据并返回快照；若不存在或读取失败返回 null。
     */
    public static DatabaseSnapshot migratePlainDatabaseIfNeeded(Context context) {
        Context appContext = context.getApplicationContext();
        File dbFile = appContext.getDatabasePath(DATABASE_NAME);
        if (dbFile == null || !dbFile.exists()) {
            return null;
        }

        AppDatabase plainDb = null;
        try {
            plainDb = androidx.room.Room.databaseBuilder(
                            appContext,
                            AppDatabase.class,
                            DATABASE_NAME)
                    .build();

            List<com.batteryhealth.app.data.model.BatteryInfo> batteryInfoList =
                    plainDb.batteryInfoDao().getAll();
            List<com.batteryhealth.app.data.model.PerformanceData> performanceDataList =
                    plainDb.performanceDataDao().getAll();
            List<com.batteryhealth.app.data.model.PowerHistory> powerHistoryList =
                    plainDb.powerHistoryDao().getAll();

            if (com.batteryhealth.app.BuildConfigHelper.isDebugMode()) {
                Log.d(TAG, "Migrating plain database: battery=" + batteryInfoList.size()
                        + ", performance=" + performanceDataList.size()
                        + ", power=" + powerHistoryList.size());
            }

            return new DatabaseSnapshot(batteryInfoList, performanceDataList, powerHistoryList);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read plain database: " + e.getMessage(), e);
            return null;
        } finally {
            if (plainDb != null) {
                plainDb.close();
            }
        }
    }

    /**
     * 将明文数据库重命名为备份文件，便于加密失败时回滚
     */
    public static boolean renamePlainDatabaseToBackup(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            File dbFile = appContext.getDatabasePath(DATABASE_NAME);
            if (dbFile == null || !dbFile.exists()) {
                return false;
            }
            File backupFile = new File(dbFile.getParent(), DATABASE_NAME + "_plain_backup");
            deleteBackupFiles(backupFile);
            boolean renamed = dbFile.renameTo(backupFile);
            if (renamed) {
                // 同时重命名 wal/shm 文件
                File walFile = new File(dbFile.getAbsolutePath() + "-wal");
                File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
                walFile.renameTo(new File(backupFile.getAbsolutePath() + "-wal"));
                shmFile.renameTo(new File(backupFile.getAbsolutePath() + "-shm"));
            }
            Log.d(TAG, "Plain database renamed to backup: " + renamed);
            return renamed;
        } catch (Exception e) {
            Log.e(TAG, "Failed to rename plain database: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从备份恢复明文数据库（加密初始化失败时回退）
     */
    public static boolean restorePlainDatabaseFromBackup(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            File dbFile = appContext.getDatabasePath(DATABASE_NAME);
            if (dbFile == null) {
                return false;
            }
            File backupFile = new File(dbFile.getParent(), DATABASE_NAME + "_plain_backup");
            if (!backupFile.exists()) {
                return false;
            }
            deleteDatabaseFiles(dbFile);
            boolean restored = backupFile.renameTo(dbFile);
            if (restored) {
                File backupWal = new File(backupFile.getAbsolutePath() + "-wal");
                File backupShm = new File(backupFile.getAbsolutePath() + "-shm");
                backupWal.renameTo(new File(dbFile.getAbsolutePath() + "-wal"));
                backupShm.renameTo(new File(dbFile.getAbsolutePath() + "-shm"));
            }
            Log.d(TAG, "Plain database restored from backup: " + restored);
            return restored;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore plain database: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除明文数据库备份文件
     */
    public static boolean deletePlainDatabaseBackup(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            File dbFile = appContext.getDatabasePath(DATABASE_NAME);
            if (dbFile == null) {
                return false;
            }
            File backupFile = new File(dbFile.getParent(), DATABASE_NAME + "_plain_backup");
            boolean deleted = deleteBackupFiles(backupFile);
            Log.d(TAG, "Plain database backup deleted: " + deleted);
            return deleted;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete plain database backup: " + e.getMessage(), e);
            return false;
        }
    }

    private static boolean deleteBackupFiles(File backupFile) {
        if (backupFile == null) return false;
        boolean deleted = backupFile.delete();
        File walFile = new File(backupFile.getAbsolutePath() + "-wal");
        File shmFile = new File(backupFile.getAbsolutePath() + "-shm");
        walFile.delete();
        shmFile.delete();
        return deleted;
    }

    private static void deleteDatabaseFiles(File dbFile) {
        if (dbFile == null) return;
        dbFile.delete();
        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
        walFile.delete();
        shmFile.delete();
    }

    private static SharedPreferences getEncryptedSharedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize encrypted preferences", e);
            return null;
        }
    }

    private static byte[] generatePassphrase() {
        byte[] bytes = new byte[PASSPHRASE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * 明文数据库数据快照
     */
    public static class DatabaseSnapshot {
        public final List<com.batteryhealth.app.data.model.BatteryInfo> batteryInfoList;
        public final List<com.batteryhealth.app.data.model.PerformanceData> performanceDataList;
        public final List<com.batteryhealth.app.data.model.PowerHistory> powerHistoryList;

        public DatabaseSnapshot(List<com.batteryhealth.app.data.model.BatteryInfo> batteryInfoList,
                                List<com.batteryhealth.app.data.model.PerformanceData> performanceDataList,
                                List<com.batteryhealth.app.data.model.PowerHistory> powerHistoryList) {
            this.batteryInfoList = batteryInfoList;
            this.performanceDataList = performanceDataList;
            this.powerHistoryList = powerHistoryList;
        }
    }
}
