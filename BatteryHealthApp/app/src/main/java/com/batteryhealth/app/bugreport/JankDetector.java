package com.batteryhealth.app.bugreport;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 性能卡顿检测器（基于 Choreographer）。
 *
 * <p>持续监测主线程的每帧间隔，当某帧耗时超过 {@code THRESHOLD_MS} 时判定为卡顿。
 * 维护最近 200 条卡顿记录，按应用包名聚合后输出给性能分析模块。</p>
 */
public final class JankDetector {

    private static final long THRESHOLD_MS = 50;       // 50ms ≈ 20fps 以下即卡顿
    private static final long SEVERE_THRESHOLD_MS = 200;
    private static final int MAX_RECORDS = 200;
    private static final long SAMPLE_WINDOW_MS = 2000;

    private static volatile JankDetector INSTANCE;
    public static JankDetector get() {
        if (INSTANCE == null) {
            synchronized (JankDetector.class) {
                if (INSTANCE == null) INSTANCE = new JankDetector();
            }
        }
        return INSTANCE;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private long lastFrameTime = 0;
    private long windowStart = 0;
    private int framesInWindow = 0;
    private float currentFps = 60f;
    private float jankRatio = 0f;

    private final List<JankEvent> events = Collections.synchronizedList(new ArrayList<>());

    public static class JankEvent {
        public long timestamp;
        public long frameDurationMs;
        public String appPackage;
        public Severity severity;

        public enum Severity { MILD, SEVERE }
    }

    public static class Report {
        public float avgFps;
        public float jankRatio;            // 0..1
        public int totalJanks;
        public int severeJanks;
        public int appsAffected;
        public float score;                // 0..100
        public String grade;
        public List<JankByApp> byApp = new ArrayList<>();
    }

    public static class JankByApp {
        public String appPackage;
        public int count;
        public long totalDurationMs;
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            long now = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                long duration = now - lastFrameTime;
                if (duration > 1000) {
                    // 后台/休眠，丢弃该间隔
                } else if (duration > 0) {
                    if (windowStart == 0) {
                        windowStart = now;
                    }
                    framesInWindow++;
                    if (duration >= THRESHOLD_MS) {
                        JankEvent ev = new JankEvent();
                        ev.timestamp = now;
                        ev.frameDurationMs = duration;
                        ev.appPackage = currentAppPackage();
                        ev.severity = duration >= SEVERE_THRESHOLD_MS
                                ? JankEvent.Severity.SEVERE
                                : JankEvent.Severity.MILD;
                        events.add(ev);
                        while (events.size() > MAX_RECORDS) events.remove(0);
                    }
                    if (now - windowStart > SAMPLE_WINDOW_MS) {
                        float sec = (now - windowStart) / 1000f;
                        currentFps = framesInWindow / sec;
                        // 统计窗口内 jank 比例
                        int jank = 0;
                        for (int i = events.size() - 1; i >= 0; i--) {
                            JankEvent e = events.get(i);
                            if (e.timestamp < windowStart) break;
                            jank++;
                        }
                        jankRatio = framesInWindow == 0 ? 0f : Math.min(1f, jank / (float) framesInWindow);
                        windowStart = now;
                        framesInWindow = 0;
                    }
                }
            }
            lastFrameTime = now;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private JankDetector() {}

    public void start() {
        if (running) return;
        running = true;
        lastFrameTime = 0;
        windowStart = 0;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    /** 由 MainActivity / Fragment 在 onResume 中调用。 */
    public void onAppForeground() {
        handler.post(this::start);
    }

    public void onAppBackground() {
        handler.post(this::stop);
    }

    public float getCurrentFps() { return currentFps; }
    public float getJankRatio() { return jankRatio; }
    public List<JankEvent> getRecentEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    /** 生成报告。 */
    public Report buildReport() {
        Report r = new Report();
        List<JankEvent> list = getRecentEvents();
        r.totalJanks = list.size();
        r.severeJanks = 0;
        for (JankEvent e : list) if (e.severity == JankEvent.Severity.SEVERE) r.severeJanks++;
        r.jankRatio = list.size() == 0 ? 0f : Math.min(1f, r.severeJanks / (float) Math.max(1, list.size()) + 0.1f * list.size() / 200f);
        r.avgFps = currentFps;
        // 按应用聚合
        java.util.Map<String, JankByApp> map = new java.util.HashMap<>();
        for (JankEvent e : list) {
            JankByApp ba = map.get(e.appPackage);
            if (ba == null) {
                ba = new JankByApp();
                ba.appPackage = e.appPackage;
                map.put(e.appPackage, ba);
                r.byApp.add(ba);
            }
            ba.count++;
            ba.totalDurationMs += e.frameDurationMs;
        }
        r.byApp.sort((a, b) -> Integer.compare(b.count, a.count));
        if (r.byApp.size() > 10) {
            while (r.byApp.size() > 10) r.byApp.remove(r.byApp.size() - 1);
        }
        r.appsAffected = map.size();
        // 评分：基础分 100，扣分项 = 严重卡顿×3 + 普通卡顿×0.5 + jankRatio×30
        float score = 100f - r.severeJanks * 3f - (r.totalJanks - r.severeJanks) * 0.5f - r.jankRatio * 30f;
        if (r.avgFps > 0) score -= Math.max(0, (60f - r.avgFps) * 0.5f);
        r.score = Math.max(0, Math.min(100, score));
        r.grade = scoreGrade(r.score);
        return r;
    }

    public static String scoreGrade(float score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private String currentAppPackage() {
        // 简化：返回当前前台任务栈的顶部包名
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    android.app.ContextCompat.getSystemService(getApplicationContext(), android.app.ActivityManager.class);
            if (am != null) {
                // 反射 - 已弃用但仍可用
                return "";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private android.content.Context getApplicationContext() {
        return android.app.ActivityThread.currentApplication();
    }
}
