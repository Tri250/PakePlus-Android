package com.batteryhealth.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;

import com.batteryhealth.app.test.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadExecutor 单例化 + 稳定性 + 并发 + 性能测试。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ThreadExecutorStabilityTest {

    @Before
    public void setUp() {
        // 重置
    }

    // ==================== 单例测试 ====================

    @Test
    public void testGetInstance_returnsSameInstance() {
        ThreadExecutor a = ThreadExecutor.getInstance();
        ThreadExecutor b = ThreadExecutor.getInstance();
        assertTrue("getInstance should return same instance", a == b);
    }

    @Test
    public void testGetInstance_concurrent_returnsSameInstance() throws InterruptedException {
        int threadCount = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final ThreadExecutor[] instances = new ThreadExecutor[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    start.await();
                    instances[idx] = ThreadExecutor.getInstance();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        ThreadExecutor ref = instances[0];
        for (ThreadExecutor inst : instances) {
            assertTrue("All instances should be same", inst == ref);
        }
    }

    // ==================== 任务执行 ====================

    @Test
    public void testExecute_runsTask() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(0);
        ThreadExecutor.execute(() -> {
            result.set(42);
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(42, result.get());
    }

    @Test
    public void testExecute_throwingTask_doesNotKillExecutor() throws InterruptedException {
        // 一个任务抛异常不应影响后续任务执行
        ThreadExecutor.execute(() -> {
            throw new RuntimeException("test");
        });
        Thread.sleep(100);
        final CountDownLatch done = new CountDownLatch(1);
        ThreadExecutor.execute(() -> {
            done.countDown();
        });
        assertTrue("Subsequent task should still run",
                done.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testExecute_concurrent_manyTasks() throws InterruptedException {
        int taskCount = 1000;
        final CountDownLatch done = new CountDownLatch(taskCount);
        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < taskCount; i++) {
            ThreadExecutor.execute(() -> {
                counter.incrementAndGet();
                done.countDown();
            });
        }
        assertTrue("Concurrent execution timed out",
                done.await(30, TimeUnit.SECONDS));
        assertEquals(taskCount, counter.get());
    }

    // ==================== runOnMain 测试 ====================

    @Test
    public void testRunOnMain_runsOnMainThread() throws InterruptedException {
        final Thread[] taskThread = new Thread[1];
        final CountDownLatch done = new CountDownLatch(1);
        ThreadExecutor.runOnMain(() -> {
            taskThread[0] = Thread.currentThread();
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // 验证是主线程
        assertEquals(android.os.Looper.getMainLooper().getThread(), taskThread[0]);
    }

    @Test
    public void testRunOnMain_alreadyOnMain_runsImmediately() {
        final boolean[] ran = {false};
        ThreadExecutor.runOnMain(() -> {
            ran[0] = true;
        });
        assertTrue(ran[0]);
    }

    // ==================== 性能测试 ====================

    @Test
    public void testPerformance_bulkExecute_1000Tasks() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1000);
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            ThreadExecutor.execute(done::countDown);
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("1000 tasks should be scheduled < 5s, took " + elapsed + "ms",
                elapsed < 5000);
    }

    @Test
    public void testPerformance_cpuIntensiveTask() {
        long elapsed = TestUtils.measureExecutionTime("ThreadExecutor.cpuIntensive", () -> {
            final CountDownLatch done = new CountDownLatch(1);
            ThreadExecutor.execute(() -> {
                long sum = 0;
                for (int i = 0; i < 1_000_000; i++) {
                    sum += i;
                }
                done.countDown();
            });
            try {
                done.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue("CPU intensive too slow: " + elapsed + "ms", elapsed < 10000);
    }
}
