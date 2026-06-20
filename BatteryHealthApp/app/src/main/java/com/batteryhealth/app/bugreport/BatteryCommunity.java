package com.batteryhealth.app.bugreport;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 电池江湖（社区）本地数据层。
 *
 * <p>社区内容全部本地化（无后端依赖），发布心得、保存保养技巧。SharedPreferences
 * 存储，每条 Post 包含作者（默认"我"）、内容、点赞数、评论数。</p>
 */
public final class BatteryCommunity {

    private static final String PREF = "battery_community";
    private static final String KEY_POSTS = "posts_json";

    public static class Post {
        public long id;
        public long timestamp;
        public String author;
        public String content;
        public int likes;
        public int comments;
        public String topic;
        public boolean isMine;
    }

    private BatteryCommunity() {}

    public static List<Post> all(Context ctx) {
        List<Post> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_POSTS, "");
            if (json.isEmpty()) {
                seed(ctx);
                json = sp.getString(KEY_POSTS, "[]");
            }
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Post p = new Post();
                p.id = o.optLong("id");
                p.timestamp = o.optLong("ts");
                p.author = o.optString("author", "我");
                p.content = o.optString("content", "");
                p.likes = o.optInt("likes", 0);
                p.comments = o.optInt("comments", 0);
                p.topic = o.optString("topic", "");
                p.isMine = o.optBoolean("mine", false);
                out.add(p);
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public static void publish(Context ctx, String content, String topic) {
        if (content == null || content.trim().isEmpty()) return;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_POSTS, "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("ts", System.currentTimeMillis());
            o.put("author", "我");
            o.put("content", content);
            o.put("likes", 0);
            o.put("comments", 0);
            o.put("topic", topic != null ? topic : "");
            o.put("mine", true);
            arr.put(o);
            sp.edit().putString(KEY_POSTS, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public static void like(Context ctx, long postId) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_POSTS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("id") == postId) {
                    o.put("likes", o.optInt("likes", 0) + 1);
                    break;
                }
            }
            sp.edit().putString(KEY_POSTS, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private static void seed(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!sp.getString(KEY_POSTS, "").isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            add(arr, "三个月前电池到了 89%，我开始养成随用随充、不充满的习惯，现在还保持在 95%。", "充电习惯", 124, 18);
            add(arr, "换了官方电池后，感觉新电池的 SOC 估算更准了。强烈建议去售后别去小店。", "换电经历", 86, 11);
            add(arr, "大家试试关掉蓝牙 / NFC / 5G 的搜索。出差 1 周只带移动电源。", "省电技巧", 64, 7);
            add(arr, "打游戏温度直奔 48°C，电池一年掉了 6%。现在改用散热背夹。", "发热讨论", 51, 9);
            add(arr, "iPhone 14 Pro 用了两年，电池还有 92%，秘诀是 80% 上限 + 智能充电。", "电池老化", 39, 4);
            add(arr, "冬天室外 0°C 时掉电特别快，这是锂电池的物理特性，回到室内就会恢复。", "电池老化", 27, 2);
            sp.edit().putString(KEY_POSTS, arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    private static void add(JSONArray arr, String content, String topic, int likes, int comments) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", System.currentTimeMillis() * 1000 + arr.length());
        o.put("ts", System.currentTimeMillis() - arr.length() * 3600_000L);
        o.put("author", "电池侠" + (arr.length() + 1));
        o.put("content", content);
        o.put("likes", likes);
        o.put("comments", comments);
        o.put("topic", topic);
        o.put("mine", false);
        arr.put(o);
    }

    public static List<String> tips() {
        List<String> out = new ArrayList<>();
        out.add("保持电量在 20%~80%，可延缓电池老化");
        out.add("避免边玩大型游戏边充电");
        out.add("高温环境（>40°C）会显著加速电池损耗");
        out.add("原装充电器匹配快充协议，效率最高");
        out.add("长时间不用时保持 50% 电量存放");
        out.add("每周至少一次完整充放电，校正电量计");
        out.add("夜间充电开启「优化电池充电」可减少满电停留时间");
        out.add("拆下手机壳充电有助于散热");
        return out;
    }

    public static List<String> topics() {
        List<String> out = new ArrayList<>();
        out.add("充电习惯");
        out.add("省电技巧");
        out.add("换电经历");
        out.add("发热讨论");
        out.add("电池老化");
        out.add("快充协议");
        out.add("低温使用");
        out.add("电池校准");
        return out;
    }
}
