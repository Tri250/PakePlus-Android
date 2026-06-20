package com.batteryhealth.app.bugreport;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * bugreport 数据总线（单例）。
 *
 * <p>解析结果可被电池健康、配置查询、性能分析、续航等所有模块订阅。
 * 持久化最后一次解析结果，确保 App 重启后其他模块仍能拿到一致的数据。</p>
 */
public final class BugReportDataBus {

    private static final String TAG = "BugReportDataBus";
    private static final String PREF = "bugreport_data_bus";
    private static final String KEY_JSON = "last_result_json";
    private static final String KEY_TIMESTAMP = "last_result_timestamp";
    private static final String KEY_BRAND = "last_brand";

    private static volatile BugReportDataBus INSTANCE;

    public static BugReportDataBus get() {
        if (INSTANCE == null) {
            synchronized (BugReportDataBus.class) {
                if (INSTANCE == null) INSTANCE = new BugReportDataBus();
            }
        }
        return INSTANCE;
    }

    private BatteryRawData current;
    private BatteryHealthCalculator.Result currentHealth;
    private ParseDetail currentDetail;
    private long lastUpdateTs;
    private String sourceBrand;

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public interface Listener {
        void onBugReportUpdated(BatteryRawData data, BatteryHealthCalculator.Result health);
    }

    private BugReportDataBus() {}

    /** 写入并广播。 */
    public void publish(BatteryRawData data, BatteryHealthCalculator.Result health,
                        ParseDetail detail, String brand) {
        this.current = data;
        this.currentHealth = health;
        this.currentDetail = detail;
        this.sourceBrand = brand;
        this.lastUpdateTs = System.currentTimeMillis();
        for (Listener l : listeners) {
            try { l.onBugReportUpdated(data, health); } catch (Exception e) {
                Log.e(TAG, "listener error", e);
            }
        }
    }

    public void addListener(Listener l) { if (l != null) listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public BatteryRawData getCurrent() { return current; }
    public BatteryHealthCalculator.Result getCurrentHealth() { return currentHealth; }
    public ParseDetail getCurrentDetail() { return currentDetail; }
    public long getLastUpdateTs() { return lastUpdateTs; }
    public String getSourceBrand() { return sourceBrand; }

    public boolean hasData() {
        return current != null && current.getAvailableDataCount() > 0;
    }

    /** 用于其他模块：融合实时 (BatteryInfo) + bugreport (BatteryRawData) 数据。 */
    public Integer resolveDesignCapacity(Integer fromRealtime) {
        if (fromRealtime != null && fromRealtime > 0) return fromRealtime;
        if (current != null && current.getDesignCapacityMah() != null && current.getDesignCapacityMah() > 0) {
            return current.getDesignCapacityMah();
        }
        return null;
    }

    public Integer resolveCurrentCapacity(Integer fromRealtime) {
        if (fromRealtime != null && fromRealtime > 0) return fromRealtime;
        if (current != null && current.getCurrentCapacityMah() != null && current.getCurrentCapacityMah() > 0) {
            return current.getCurrentCapacityMah();
        }
        return null;
    }

    public Integer resolveCycleCount(Integer fromRealtime) {
        if (fromRealtime != null && fromRealtime >= 0) return fromRealtime;
        if (current != null && current.getCycleCount() != null) return current.getCycleCount();
        return null;
    }

    /** 持久化恢复（App 启动时调用一次）。 */
    public void restoreFromPrefs(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            long ts = sp.getLong(KEY_TIMESTAMP, 0);
            if (ts == 0) return;
            // 7 天后失效，强制重新上传
            if (System.currentTimeMillis() - ts > 7L * 24 * 3600 * 1000) return;
            String brand = sp.getString(KEY_BRAND, null);
            // 简化：仅恢复时间戳与品牌，详细数据由用户重新上传以保证准确性
            this.lastUpdateTs = ts;
            this.sourceBrand = brand;
        } catch (Exception ignored) {}
    }

    public void persistToPrefs(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            sp.edit()
                    .putLong(KEY_TIMESTAMP, lastUpdateTs)
                    .putString(KEY_BRAND, sourceBrand)
                    .apply();
        } catch (Exception ignored) {}
    }
}
