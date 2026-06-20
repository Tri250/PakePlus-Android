package com.batteryhealth.app.bugreport;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 电池健康周报/月报生成器（基于历史容量 / 循环 / 温度数据）。
 *
 * <p>当用户连续 3 天以上提供数据后，系统自动聚合为周报；连续 30 天聚合为月报。</p>
 */
public final class BatteryReportGenerator {

    private static final String PREF = "battery_history";
    private static final String KEY_HISTORY = "history_json";

    public enum Period { WEEK, MONTH, QUARTER }

    public static class Report {
        public final Period period;
        public final long startTs;
        public final long endTs;
        public final int sampleCount;
        public final float avgHealth;
        public final float minHealth;
        public final float maxHealth;
        public final float avgTemp;
        public final float maxTemp;
        public final int totalCycles;
        public final float healthChange;
        public final List<String> highlights = new ArrayList<>();
        public final List<String> advice = new ArrayList<>();

        public String getPeriodLabel(Context ctx) {
            SimpleDateFormat df = new SimpleDateFormat("MM/dd", Locale.getDefault());
            switch (period) {
                case WEEK: return "周报 " + df.format(new Date(startTs)) + " - " + df.format(new Date(endTs));
                case MONTH: return "月报 " + df.format(new Date(startTs)) + " - " + df.format(new Date(endTs));
                case QUARTER: return "季报 " + df.format(new Date(startTs)) + " - " + df.format(new Date(endTs));
                default: return "报告";
            }
        }
    }

    private BatteryReportGenerator() {}

    /** 追加一次历史采样。 */
    public static void appendSample(Context ctx, float health, int cycles, float temperature) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_HISTORY, "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject o = new JSONObject();
            o.put("ts", System.currentTimeMillis());
            o.put("h", health);
            o.put("c", cycles);
            o.put("t", temperature);
            arr.put(o);
            // 限制最多 500 条
            while (arr.length() > 500) arr.remove(0);
            sp.edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /** 生成指定周期的报告。 */
    public static Report generate(Context ctx, Period period) {
        Report r = new Report();
        r.period = period;

        long end = System.currentTimeMillis();
        long start;
        switch (period) {
            case WEEK: start = end - 7L * 24 * 3600 * 1000; break;
            case MONTH: start = end - 30L * 24 * 3600 * 1000; break;
            case QUARTER: start = end - 90L * 24 * 3600 * 1000; break;
            default: start = end - 7L * 24 * 3600 * 1000;
        }
        r.startTs = start;
        r.endTs = end;

        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_HISTORY, "[]");
            JSONArray arr = new JSONArray(json);

            float sumH = 0, sumT = 0;
            float minH = Float.MAX_VALUE, maxH = Float.MIN_VALUE;
            float maxT = Float.MIN_VALUE;
            int minCycles = Integer.MAX_VALUE, maxCycles = Integer.MIN_VALUE;
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("ts", 0);
                if (ts < start) continue;
                float h = (float) o.optDouble("h", 0);
                float t = (float) o.optDouble("t", 0);
                int c = o.optInt("c", 0);
                sumH += h;
                sumT += t;
                if (h > 0 && h < minH) minH = h;
                if (h > maxH) maxH = h;
                if (t > maxT) maxT = t;
                if (c < minCycles) minCycles = c;
                if (c > maxCycles) maxCycles = c;
                count++;
            }
            r.sampleCount = count;
            r.avgHealth = count == 0 ? 0 : sumH / count;
            r.avgTemp = count == 0 ? 0 : sumT / count;
            r.minHealth = count == 0 ? 0 : minH;
            r.maxHealth = count == 0 ? 0 : maxH;
            r.maxTemp = maxT == Float.MIN_VALUE ? 0 : maxT;
            r.totalCycles = count == 0 ? 0 : (maxCycles - minCycles);
            r.healthChange = r.maxHealth - r.minHealth;
        } catch (JSONException ignored) {
            r.avgHealth = 0; r.avgTemp = 0; r.minHealth = 0; r.maxHealth = 0;
        }

        buildHighlights(r);
        buildAdvice(r);
        return r;
    }

    private static void buildHighlights(Report r) {
        if (r.sampleCount == 0) {
            r.highlights.add("暂无历史数据，连续使用 3 天后将自动生成报告");
            return;
        }
        r.highlights.add(String.format(Locale.US, "采集 %d 个有效样本", r.sampleCount));
        r.highlights.add(String.format(Locale.US, "平均健康度 %.1f%%", r.avgHealth));
        if (r.maxTemp > 0)
            r.highlights.add(String.format(Locale.US, "周内最高温度 %.1f°C", r.maxTemp));
        if (r.totalCycles > 0)
            r.highlights.add(String.format(Locale.US, "周内累计 %d 次充电循环", r.totalCycles));
        if (r.healthChange > 0)
            r.highlights.add(String.format(Locale.US, "健康度波动 %.2f%%", r.healthChange));
    }

    private static void buildAdvice(Report r) {
        if (r.maxTemp > 42) r.advice.add("本周最高温度偏高，建议减少边玩边充");
        if (r.avgTemp > 38) r.advice.add("平均温度偏高，避免高温场景使用");
        if (r.healthChange > 1.0f) r.advice.add("健康度波动较大，建议保持稳定使用习惯");
        if (r.avgHealth > 0 && r.avgHealth < 85) r.advice.add("电池已进入老化阶段，建议关注");
        if (r.advice.isEmpty()) r.advice.add("电池使用状况良好，继续保持");
    }
}
