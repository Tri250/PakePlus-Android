package com.batteryhealth.app.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局统一线程池管理器。
 *
 * 替换项目中散乱的 {@code new Thread()} 调用，统一管理 IO 线程资源：
 * - IO 线程池：4 核心线程，用于磁盘/数据库/sysfs 读取
 * - 主线程 Handler：用于 UI 回调
 *
 * 优势：
 * 1. 避免无限制创建线程导致 OOM
 * 2. 统一线程命名，便于调试和性能分析
 * 3. 线程池复用，减少线程创建/销毁开销
 */
public final class ThreadExecutor {

    private static final ThreadExecutor INSTANCE = new ThreadExecutor();

    private ThreadExecutor() {}

    /** 获取单例 */
    public static ThreadExecutor getInstance() {
        return INSTANCE;
    }

    /** IO 线程池（磁盘读取、数据库操作、sysfs 采集） */
    private final ExecutorService ioExecutor =
            Executors.newFixedThreadPool(4, new NamedThreadFactory("App-IO"));

    /** 主线程 Handler */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 在 IO 线程执行任务。
     *
     * @param task 后台任务
     */
    public void execute(Runnable task) {
        ioExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                android.util.Log.e("ThreadExecutor", "IO task failed", e);
            }
        });
    }

    /**
     * 在 IO 线程执行任务，完成后回调主线程。
     *
     * @param task    后台任务
     * @param callback 主线程回调（可能为 null）
     * @param <T>     结果类型
     */
    public <T> void executeWithCallback(Task<T> task, MainCallback<T> callback) {
        ioExecutor.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = task.run();
            } catch (Exception e) {
                error = e;
            }
            if (callback != null) {
                final T finalResult = result;
                final Exception finalError = error;
                mainHandler.post(() -> {
                    if (finalError != null) {
                        callback.onError(finalError);
                    } else {
                        callback.onSuccess(finalResult);
                    }
                });
            }
        });
    }

    /**
     * 在主线程执行。
     */
    public void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    /**
     * 在主线程延迟执行。
     */
    public void runOnMainDelayed(Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(runnable, delayMillis);
    }

    /**
     * 关闭线程池，释放资源。应用退出时调用。
     */
    public void shutdown() {
        ioExecutor.shutdown();
    }

    // ========== 静态便捷方法（向后兼容） ==========

    /** 静态便捷方法：在 IO 线程执行任务 */
    public static void execute(Runnable task) {
        INSTANCE.__execute(task);
    }

    /** 静态便捷方法：在 IO 线程执行任务，完成后回调主线程 */
    public static <T> void executeWithCallback(Task<T> task, MainCallback<T> callback) {
        INSTANCE.__executeWithCallback(task, callback);
    }

    /** 静态便捷方法：在主线程执行 */
    public static void runOnMain(Runnable runnable) {
        INSTANCE.__runOnMain(runnable);
    }

    /** 静态便捷方法：在主线程延迟执行 */
    public static void runOnMainDelayed(Runnable runnable, long delayMillis) {
        INSTANCE.__runOnMainDelayed(runnable, delayMillis);
    }

    // ========== 实例方法（内部实现） ==========

    private void __execute(Runnable task) {
        execute(task);
    }

    private <T> void __executeWithCallback(Task<T> task, MainCallback<T> callback) {
        executeWithCallback(task, callback);
    }

    private void __runOnMain(Runnable runnable) {
        runOnMain(runnable);
    }

    private void __runOnMainDelayed(Runnable runnable, long delayMillis) {
        runOnMainDelayed(runnable, delayMillis);
    }

    /** 后台任务接口 */
    public interface Task<T> {
        T run();
    }

    /** 主线程回调接口 */
    public interface MainCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /**
     * 命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    android.util.Log.e("ThreadExecutor", "Uncaught in " + thread.getName(), ex));
            return t;
        }
    }
}
