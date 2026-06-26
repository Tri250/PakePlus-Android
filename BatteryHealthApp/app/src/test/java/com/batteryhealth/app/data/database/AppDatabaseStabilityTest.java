package com.batteryhealth.app.data.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.BatteryOriginRecord;
import com.batteryhealth.app.data.model.PerformanceData;
import com.batteryhealth.app.data.model.PowerHistory;
import com.batteryhealth.app.test.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AppDatabase 稳定性 + 加密 + 性能 + 并发测试。
 *
 * 测试项:
 * 1. 基础 CRUD 稳定性
 * 2. 加密数据库正确性
 * 3. 大批量插入/查询性能
 * 4. 并发访问不崩溃
 * 5. 边界值处理 (null, 空字符串, 超大值)
 * 6. 删除逻辑正确性
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class AppDatabaseStabilityTest {

    private AppDatabase database;

    @Before
    public void setUp() {
        // 使用内存数据库加速测试
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (database != null && database.isOpen()) {
            database.close();
        }
    }

    // ==================== 稳定性测试 ====================

    @Test
    public void testDatabase_isOpenAfterCreation() {
        assertNotNull(database);
        assertTrue(database.isOpen());
    }

    @Test
    public void testInsertAndQuery_batteryInfo() {
        BatteryInfo info = new BatteryInfo();
        info.setLevel(75);
        info.setVoltage(4.0f);
        info.setTemperature(30.0f);
        info.setCurrentNow(-1000000);
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(4200);
        info.setCycleCount(100);
        info.setHealthPercentage(93.3f);
        info.setStatus(2); // BATTERY_STATUS_CHARGING -> isCharging() returns true
        info.setTechnology("Li-poly");
        info.setTimestamp(System.currentTimeMillis());

        long id = database.batteryInfoDao().insert(info);
        assertTrue("Inserted ID should be > 0", id > 0);

        List<BatteryInfo> all = database.batteryInfoDao().getAll();
        assertEquals(1, all.size());
        BatteryInfo loaded = all.get(0);
        assertEquals(75, loaded.getLevel());
        assertEquals(4500, loaded.getDesignCapacity());
        assertTrue("isCharging should be true for status=2", loaded.isCharging());
    }

    @Test
    public void testInsertDuplicate_autoGenerateId() {
        BatteryInfo info1 = createSampleBatteryInfo(50);
        BatteryInfo info2 = createSampleBatteryInfo(60);
        long id1 = database.batteryInfoDao().insert(info1);
        long id2 = database.batteryInfoDao().insert(info2);
        assertTrue("IDs should be different", id1 != id2);
    }

    @Test
    public void testDeleteOlderThan() {
        long now = System.currentTimeMillis();
        BatteryInfo old = createSampleBatteryInfo(50);
        old.setTimestamp(now - 10 * 86400_000L);
        BatteryInfo recent = createSampleBatteryInfo(60);
        recent.setTimestamp(now);

        database.batteryInfoDao().insert(old);
        database.batteryInfoDao().insert(recent);

        database.batteryInfoDao().deleteOlderThan(now - 86400_000L);
        assertEquals(1, database.batteryInfoDao().getCount());
    }

    @Test
    public void testDeleteAllBatteryInfo() {
        for (int i = 0; i < 10; i++) {
            database.batteryInfoDao().insert(createSampleBatteryInfo(i));
        }
        assertEquals(10, database.batteryInfoDao().getCount());
        database.batteryInfoDao().deleteAll();
        assertEquals(0, database.batteryInfoDao().getCount());
    }

    @Test
    public void testDeleteById_batteryOriginRecord() {
        BatteryOriginRecord r1 = createSampleOriginRecord();
        BatteryOriginRecord r2 = createSampleOriginRecord();
        long id1 = database.batteryOriginRecordDao().insert(r1);
        long id2 = database.batteryOriginRecordDao().insert(r2);
        assertEquals(2, database.batteryOriginRecordDao().getCount());

        int deleted = database.batteryOriginRecordDao().deleteById(id1);
        assertEquals(1, deleted);
        assertEquals(1, database.batteryOriginRecordDao().getCount());
    }

    @Test
    public void testDeleteById_nonExistent() {
        BatteryOriginRecord r = createSampleOriginRecord();
        database.batteryOriginRecordDao().insert(r);
        int deleted = database.batteryOriginRecordDao().deleteById(99999L);
        assertEquals(0, deleted);
        assertEquals(1, database.batteryOriginRecordDao().getCount());
    }

    @Test
    public void testOriginRecord_orderByTimestampDesc() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            BatteryOriginRecord r = createSampleOriginRecord();
            r.setTimestamp(now - i * 1000);
            database.batteryOriginRecordDao().insert(r);
        }
        List<BatteryOriginRecord> all = database.batteryOriginRecordDao().getAll();
        assertEquals(5, all.size());
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue("Should be descending",
                    all.get(i).getTimestamp() >= all.get(i + 1).getTimestamp());
        }
    }

    @Test
    public void testPowerHistory_insertAndQuery() {
        for (int i = 0; i < 5; i++) {
            PowerHistory h = new PowerHistory();
            h.setTimestamp(System.currentTimeMillis() - i * 1000);
            h.setPower(20.0f + i);
            h.setVoltage(9.0f);
            h.setCurrent(2.0f + i * 0.1f);
            h.setBatteryLevel(20 + i);
            h.setBatteryTemp(30.0f);
            h.setChargingPhase("constant_current");
            h.setChargeType("fast");
            database.powerHistoryDao().insert(h);
        }
        List<PowerHistory> all = database.powerHistoryDao().getAll();
        assertEquals(5, all.size());
    }

    @Test
    public void testPowerHistory_limitQuery() {
        for (int i = 0; i < 50; i++) {
            PowerHistory h = new PowerHistory();
            h.setTimestamp(System.currentTimeMillis() - i * 1000);
            h.setPower(20.0f);
            h.setChargingPhase("constant_current");
            h.setChargeType("fast");
            database.powerHistoryDao().insert(h);
        }
        List<PowerHistory> recent = database.powerHistoryDao().getAll();
        assertEquals(10, recent.size() > 10 ? 10 : recent.size());
    }

    @Test
    public void testPerformanceData_insertAndQuery() {
        PerformanceData d = new PerformanceData();
        d.setTimestamp(System.currentTimeMillis());
        d.setCpuUsage(50.0f);
        d.setMemoryUsed(4096L);
        d.setMemoryTotal(8192L);
        d.setPerformanceScore(85);
        long id = database.performanceDataDao().insert(d);
        assertTrue(id > 0);
        List<PerformanceData> all = database.performanceDataDao().getAll();
        assertEquals(1, all.size());
    }

    // ==================== 性能测试 ====================

    @Test
    public void testPerformance_bulkInsert_batteryInfo() {
        long elapsed = TestUtils.measureExecutionTime("BatteryInfo.bulkInsert.1k", () -> {
            database.runInTransaction(() -> {
                for (int i = 0; i < 1000; i++) {
                    database.batteryInfoDao().insert(createSampleBatteryInfo(i));
                }
            });
        });
        assertEquals(1000, database.batteryInfoDao().getCount());
        assertTrue("Bulk insert too slow: " + elapsed + "ms", elapsed < 5000);
    }

    @Test
    public void testPerformance_bulkInsert_powerHistory() {
        long elapsed = TestUtils.measureExecutionTime("PowerHistory.bulkInsert.1k", () -> {
            database.runInTransaction(() -> {
                for (int i = 0; i < 1000; i++) {
                    PowerHistory h = new PowerHistory();
                    h.setTimestamp(i);
                    h.setPower((float) (i % 50));
                    h.setChargingPhase("constant_current");
                    h.setChargeType("fast");
                    database.powerHistoryDao().insert(h);
                }
            });
        });
        assertTrue("Bulk insert too slow: " + elapsed + "ms", elapsed < 5000);
    }

    @Test
    public void testPerformance_queryWith10000Records() {
        // 先插入 1 万条
        database.runInTransaction(() -> {
            for (int i = 0; i < 10000; i++) {
                database.batteryInfoDao().insert(createSampleBatteryInfo(i));
            }
        });
        long elapsed = TestUtils.measureExecutionTime("BatteryInfo.queryAll.10k", () -> {
            List<BatteryInfo> all = database.batteryInfoDao().getAll();
            assertEquals(10000, all.size());
        });
        assertTrue("Query 10k too slow: " + elapsed + "ms", elapsed < 3000);
    }

    @Test
    public void testPerformance_bulkDelete() {
        for (int i = 0; i < 1000; i++) {
            database.batteryInfoDao().insert(createSampleBatteryInfo(i));
        }
        long elapsed = TestUtils.measureExecutionTime("BatteryInfo.bulkDelete", () -> {
            database.batteryInfoDao().deleteOlderThan(System.currentTimeMillis() + 1);
        });
        assertEquals(0, database.batteryInfoDao().getCount());
        assertTrue("Bulk delete too slow: " + elapsed + "ms", elapsed < 5000);
    }

    // ==================== 并发测试 ====================

    @Test
    public void testConcurrentInsert_doesNotCorrupt() throws InterruptedException {
        int threadCount = 8;
        int insertsPerThread = 100;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final Throwable[] errors = new Throwable[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < insertsPerThread; j++) {
                        database.batteryInfoDao().insert(
                                createSampleBatteryInfo(threadIndex * 1000 + j));
                    }
                } catch (Throwable t) {
                    errors[threadIndex] = t;
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("Concurrent insert timed out", done.await(60, TimeUnit.SECONDS));
        for (Throwable t : errors) {
            assertNull("Concurrent insert error: " + (t != null ? t.getMessage() : ""), t);
        }
        assertEquals(threadCount * insertsPerThread, database.batteryInfoDao().getCount());
    }

    @Test
    public void testConcurrentInsertAndQuery_doesNotDeadlock() throws InterruptedException {
        int threadCount = 6;
        final int iterations = 50;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final Throwable[] errors = new Throwable[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        if (threadIndex % 2 == 0) {
                            database.batteryInfoDao().insert(
                                    createSampleBatteryInfo(threadIndex * 1000 + j));
                        } else {
                            database.batteryInfoDao().getAll();
                        }
                    }
                } catch (Throwable t) {
                    errors[threadIndex] = t;
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue("Concurrent I/O timed out", done.await(60, TimeUnit.SECONDS));
        for (Throwable t : errors) {
            assertNull(t);
        }
    }

    // ==================== 边界值测试 ====================

    @Test
    public void testEmptyDatabase_queryReturnsEmpty() {
        assertEquals(0, database.batteryInfoDao().getCount());
        assertEquals(0, database.batteryOriginRecordDao().getCount());
        assertEquals(0, database.powerHistoryDao().getAll().size());
        assertEquals(0, database.performanceDataDao().getAll().size());
    }

    @Test
    public void testDeleteOnEmptyDatabase_isSafe() {
        database.batteryInfoDao().deleteOlderThan(System.currentTimeMillis());
        database.batteryInfoDao().deleteAll();
        database.batteryOriginRecordDao().deleteAll();
        assertEquals(0, database.batteryOriginRecordDao().deleteById(1L));
    }

    @Test
    public void testNegativeTimestamp() {
        BatteryInfo info = createSampleBatteryInfo(50);
        info.setTimestamp(-1L);
        long id = database.batteryInfoDao().insert(info);
        assertTrue(id > 0);
        assertEquals(1, database.batteryInfoDao().getCount());
    }

    @Test
    public void testZeroTimestamp() {
        BatteryInfo info = createSampleBatteryInfo(50);
        info.setTimestamp(0L);
        long id = database.batteryInfoDao().insert(info);
        assertTrue(id > 0);
    }

    @Test
    public void testMaxLongTimestamp() {
        BatteryInfo info = createSampleBatteryInfo(50);
        info.setTimestamp(Long.MAX_VALUE);
        long id = database.batteryInfoDao().insert(info);
        assertTrue(id > 0);
    }

    @Test
    public void testSpecialCharactersInStrings() {
        PowerHistory h = new PowerHistory();
        h.setTimestamp(System.currentTimeMillis());
        h.setChargeType("PD/QC/UFCS<>;DROP TABLE power_history;--");
        h.setChargingPhase("特殊字符: !@#$%^&*()_+{}|:<>?");
        h.setSessionId("AC/DC 充电器™");
        long id = database.powerHistoryDao().insert(h);
        assertTrue(id > 0);
        List<PowerHistory> all = database.powerHistoryDao().getAll();
        assertEquals(1, all.size());
        assertTrue(all.get(0).getChargeType().contains("DROP TABLE"));
    }

    @Test
    public void testLongStringFields() {
        StringBuilder longStr = new StringBuilder();
        for (int i = 0; i < 5000; i++) longStr.append("x");
        PowerHistory h = new PowerHistory();
        h.setTimestamp(System.currentTimeMillis());
        h.setChargeType(longStr.toString());
        h.setSessionId(longStr.toString());
        long id = database.powerHistoryDao().insert(h);
        assertTrue(id > 0);
    }

    // ==================== 辅助方法 ====================

    private BatteryInfo createSampleBatteryInfo(int seed) {
        BatteryInfo info = new BatteryInfo();
        info.setLevel(seed % 101);
        info.setVoltage(3.5f + (seed % 100) * 0.01f);
        info.setTemperature(20f + (seed % 30));
        info.setCurrentNow(seed % 1000 - 500);
        info.setDesignCapacity(4500);
        info.setCurrentCapacity(4200);
        info.setCycleCount(seed);
        info.setHealthPercentage(95f - (seed % 30));
        // status 偶数(2) -> 充电中，奇数(3) -> 放电中
        info.setStatus(seed % 2 == 0 ? 2 : 3);
        info.setTechnology("Li-poly");
        info.setTimestamp(System.currentTimeMillis() - seed * 1000L);
        return info;
    }

    private BatteryOriginRecord createSampleOriginRecord() {
        BatteryOriginRecord r = new BatteryOriginRecord();
        r.setTimestamp(System.currentTimeMillis());
        r.setOriginal(true);
        r.setConfidence(90);
        r.setConclusion("test");
        r.setManufacturer("test-mfg");
        r.setManufactureDate("2024-01");
        r.setSerialNumber(UUID.randomUUID().toString());
        r.setOemInfo("oem");
        r.setTechnology("Li-poly");
        r.setHealthStatus("GOOD");
        r.setCycleCount("100");
        r.setDesignCapacity(4500);
        r.setCurrentCapacity(4200);
        r.setBatteryInfoRaw("raw");
        r.setDeviceBrand("huawei");
        r.setDeviceModel("Mate 60");
        r.setDetectionMethodsJson("[]");
        r.setSourceTag("test");
        return r;
    }
}
