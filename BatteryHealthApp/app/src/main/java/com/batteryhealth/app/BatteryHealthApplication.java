package com.batteryhealth.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.room.Room;

import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.DeviceDatabaseManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application 类。
 *
 * 修复点：
 *  - 早期版本的 dbInitLatch 与 database 字段在多线程下访问未正确同步，
 *    可能返回未完全初始化的数据库或空指针异常；
 *    现统一通过 synchronized + DCL 控制并发访问。
 *  - 早期版本使用 raw Thread 启动数据库迁移，现统一走 ExecutorService。
 *  - 关闭数据库异常被静默吞掉；现以日志记录但不抛出。
 */
public class BatteryHealthApplication extends Application {

    private static final String TAG = "BatteryHealthApp";
    private static final String DB_NAME = "battery_health.db";
    private static final long DB_INIT_TIMEOUT_MS = 10_000;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BatteryHealthApp-DB");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BatteryHealthApp-Migration");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<AppDatabase> databaseRef = new AtomicReference<>();
    private final Object initLock = new Object();
    private final CountDownLatch dbInitLatch = new CountDownLatch(1);
    private volatile boolean databaseInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "BatteryHealthApplication onCreate (SDK=" + Build.VERSION.SDK_INT + ")");

        registerUncaughtExceptionHandler();
        // Preload device database (synchronous kick-off, but actual loading
        // is performed on a daemon thread)
        DeviceDatabaseManager.getInstance(this);

        // Kick off database init asynchronously to avoid blocking Application.onCreate
        startDatabaseInitAsync();
    }

    private void registerUncaughtExceptionHandler() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception on thread " + thread.getName(), throwable);
            // Persist a minimal snapshot of recent data for crash diagnostics
            try {
                persistCrashSnapshot(throwable);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to persist crash snapshot", t);
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    /**
     * Start the database initialisation asynchronously. Idempotent: subsequent
     * calls while the DB is being created are no-ops.
     */
    public void startDatabaseInitAsync() {
        synchronized (initLock) {
            if (databaseInitialized) return;
            databaseExecutor.submit(this::initDatabase);
        }
    }

    /**
     * Initialise the Room database. Runs on the databaseExecutor.
     */
    private void initDatabase() {
        synchronized (initLock) {
            if (databaseInitialized) return;
        }
        try {
            AppDatabase db = Room.databaseBuilder(
                            getApplicationContext(), AppDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
            databaseRef.set(db);
            synchronized (initLock) {
                databaseInitialized = true;
            }
            dbInitLatch.countDown();
            Log.i(TAG, "Database initialised");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialise database", t);
            dbInitLatch.countDown();
        }
    }

    /**
     * Returns the Room database, blocking (with a bounded timeout) until it is
     * available. Throws IllegalStateException on timeout.
     */
    public AppDatabase getDatabase() {
        if (databaseInitialized) {
            AppDatabase db = databaseRef.get();
            if (db != null) return db;
        }
        try {
            if (!dbInitLatch.await(DB_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Database init timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database init interrupted", e);
        }
        AppDatabase db = databaseRef.get();
        if (db == null) {
            throw new IllegalStateException("Database not initialised");
        }
        return db;
    }

    /**
     * Snapshot a few recent rows to disk for post-mortem analysis.
     */
    private void persistCrashSnapshot(Throwable throwable) {
        try {
            File snapshotDir = new File(getFilesDir(), "crash_snapshots");
            //noinspection ResultOfMethodCallIgnored
            snapshotDir.mkdirs();
            File snapshotFile = new File(snapshotDir, "latest.bin");
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(snapshotFile))) {
                out.writeUTF(throwable != null ? throwable.toString() : "unknown");
                out.writeLong(System.currentTimeMillis());
                // Snapshot the most recent 32 records
                try {
                    AppDatabase db = databaseRef.get();
                    if (db != null) {
                        long since = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
                        java.util.List<BatteryInfo> recent = db.batteryInfoDao().getSince(since);
                        int limit = Math.min(recent != null ? recent.size() : 0, 32);
                        out.writeInt(limit);
                        for (int i = 0; i < limit; i++) {
                            BatteryInfo info = recent.get(i);
                            if (info != null) {
                                out.writeObject(info);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // best-effort: don't propagate during crash handling
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write crash snapshot", e);
        }
    }

    /**
     * Restore the most recent crash snapshot. Returns null if no snapshot exists.
     */
    public CrashSnapshot readCrashSnapshot() {
        File f = new File(new File(getFilesDir(), "crash_snapshots"), "latest.bin");
        if (!f.exists() || !f.canRead()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            CrashSnapshot snap = new CrashSnapshot();
            snap.throwable = in.readUTF();
            snap.timestamp = in.readLong();
            int count = in.readInt();
            snap.recentRecords = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Object o = in.readObject();
                if (o instanceof BatteryInfo) snap.recentRecords.add((BatteryInfo) o);
            }
            return snap;
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "Failed to read crash snapshot", e);
            return null;
        }
    }

    public static final class CrashSnapshot {
        public String throwable;
        public long timestamp;
        public java.util.List<BatteryInfo> recentRecords;
    }

    /**
     * Public helper for callers that need to schedule work onto the
     * background database executor.
     */
    public ExecutorService getDatabaseExecutor() {
        return databaseExecutor;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // Graceful shutdown: ignore errors during shutdown
        try {
            AppDatabase db = databaseRef.get();
            if (db != null && db.isOpen()) {
                db.close();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error closing database", t);
        }
        try {
            databaseExecutor.shutdownNow();
            migrationExecutor.shutdownNow();
        } catch (Throwable t) {
            Log.e(TAG, "Error shutting down executors", t);
        }
    }
}
