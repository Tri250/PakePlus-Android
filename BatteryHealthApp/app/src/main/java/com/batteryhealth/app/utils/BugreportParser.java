package com.batteryhealth.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bugreport 本地解析器。
 *
 * 支持解析 Android bugreport ZIP（通常包含 bugreport-xxx.txt 及 FS 数据文件），
 * 从中提取电池、充电、设备、性能相关字段，供后续模块使用。
 */
public class BugreportParser {

    private static final String TAG = "BugreportParser";

    private final Context context;

    public BugreportParser(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 解析用户选择的 bugreport 文件。
     *
     * @param uri 用户通过文件选择器返回的 URI
     * @return 解析后的原始数据
     * @throws IOException 读取或解析失败时抛出
     */
    public ParsedResult parse(Uri uri) throws IOException {
        ParsedResult result = new ParsedResult();
        result.fileName = getFileName(uri);

        StringBuilder fullText = new StringBuilder();
        List<BatterySnapshot> batterySnapshots = new ArrayList<>();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            if (is == null) throw new IOException("无法打开文件");

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (entry.isDirectory()) continue;

                // 只读取文本类文件
                if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
                    String text = readAllText(zis);
                    fullText.append(text).append("\n");

                    // 主 bugreport 文本
                    if (name.contains("bugreport") && name.endsWith(".txt")) {
                        result.mainBugreportText = text;
                    }

                    // 电池历史数据（batterystats 相关）
                    if (name.contains("batterystats") || name.contains("battery")) {
                        batterySnapshots.addAll(extractBatterySnapshots(text));
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            // 可能不是 ZIP，尝试按纯文本读取
            try (InputStream fallback = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fallback, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) fullText.append(line).append("\n");
                result.mainBugreportText = fullText.toString();
                batterySnapshots.addAll(extractBatterySnapshots(result.mainBugreportText));
            }
        }

        result.fullText = fullText.toString();
        result.batterySnapshots = batterySnapshots;
        extractKeyFields(result);
        return result;
    }

    private String getFileName(Uri uri) {
        String name = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = cursor.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (name == null) name = uri.getLastPathSegment();
        return name != null ? name : "unknown";
    }

    private String readAllText(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int len;
        while ((len = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, len);
        }
        return sb.toString();
    }

    private List<BatterySnapshot> extractBatterySnapshots(String text) {
        List<BatterySnapshot> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;

        // 匹配 batterystats 中类似：
        // "0 (screen off) 0 (screen on) ... health: GOOD status: 2 plug: 2 temp: 310 volt: 4210 ..."
        Pattern p = Pattern.compile(
                "status:\\s*(\\d+).*?plug:\\s*(\\d+).*?temp:\\s*(\\d+).*?volt:\\s*(\\d+).*?level:\\s*(\\d+).*?health:\\s*(\\w+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            try {
                BatterySnapshot s = new BatterySnapshot();
                s.status = Integer.parseInt(m.group(1));
                s.plugged = Integer.parseInt(m.group(2));
                s.temperatureDeciC = Integer.parseInt(m.group(3));
                s.voltageMv = Integer.parseInt(m.group(4));
                s.level = Integer.parseInt(m.group(5));
                s.health = m.group(6);
                list.add(s);
            } catch (Exception ignored) {
            }
        }

        // 备选：DUMP OF SERVICE batterystats 中的 "Battery" 段
        if (list.isEmpty()) {
            extractBatteryDump(text, list);
        }
        return list;
    }

    private void extractBatteryDump(String text, List<BatterySnapshot> list) {
        // 匹配 "Capacity: 4450" "Charge counter: ..." "status: 5" 等
        Pattern capP = Pattern.compile("Capacity:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher capM = capP.matcher(text);
        if (capM.find()) {
            BatterySnapshot s = new BatterySnapshot();
            s.level = Integer.parseInt(capM.group(1));

            s.voltageMv = findIntPattern(text, "voltage:\\s*(\\d+)");
            s.temperatureDeciC = findIntPattern(text, "temperature:\\s*(\\d+)");
            s.status = findIntPattern(text, "status:\\s*(\\d+)");
            s.plugged = findIntPattern(text, "plugged:\\s*(\\d+)");
            list.add(s);
        }
    }

    private int findIntPattern(String text, String regex) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void extractKeyFields(ParsedResult result) {
        String text = result.mainBugreportText != null ? result.mainBugreportText : result.fullText;
        if (text == null) return;

        // 设计容量 / 满充容量
        result.designCapacityMah = findIntPattern(text, "charge_full_design[=:]\\s*(\\d+)");
        if (result.designCapacityMah > 1_000_000) result.designCapacityMah /= 1000;

        result.fullCapacityMah = findIntPattern(text, "charge_full[=:]\\s*(\\d+)");
        if (result.fullCapacityMah > 1_000_000) result.fullCapacityMah /= 1000;

        // 循环次数
        result.cycleCount = findIntPattern(text, "cycle_count[=:]\\s*(\\d+)");

        // 健康度：优先读取 Android 16 BATTERY_PROPERTY_BATTERY_HEALTH 打印值
        result.batteryHealthPercent = findIntPattern(text, "battery health[=:]\\s*(\\d+)");
        if (result.batteryHealthPercent <= 0 || result.batteryHealthPercent > 100) {
            result.batteryHealthPercent = findIntPattern(text, "health[=:]\\s*(\\d+)");
        }

        // 电压 / 温度（取快照平均值）
        if (!result.batterySnapshots.isEmpty()) {
            BatterySnapshot last = result.batterySnapshots.get(result.batterySnapshots.size() - 1);
            result.voltageMv = last.voltageMv;
            result.temperatureC = last.temperatureDeciC / 10.0;
            if (result.batteryHealthPercent <= 0 && result.designCapacityMah > 0 && result.fullCapacityMah > 0) {
                result.batteryHealthPercent = (int) (result.fullCapacityMah * 100.0 / result.designCapacityMah);
            }
        }

        // 电池技术
        Matcher techM = Pattern.compile("technology[=:]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (techM.find()) result.technology = techM.group(1).trim();

        // 充电策略
        Matcher policyM = Pattern.compile("charging policy[=:]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (policyM.find()) result.chargingPolicy = policyM.group(1).trim();

        Log.d(TAG, "Bugreport parsed: design=" + result.designCapacityMah
                + " full=" + result.fullCapacityMah + " cycles=" + result.cycleCount
                + " health=" + result.batteryHealthPercent);
    }

    /**
     * 解析结果容器。
     */
    public static class ParsedResult {
        public String fileName;
        public String mainBugreportText;
        public String fullText;
        public List<BatterySnapshot> batterySnapshots = new ArrayList<>();

        public int designCapacityMah;
        public int fullCapacityMah;
        public int cycleCount = -1;
        public int batteryHealthPercent = -1;
        public int voltageMv;
        public double temperatureC;
        public String technology;
        public String chargingPolicy;
    }

    /**
     * Bugreport 中某个时间点的电池快照。
     */
    public static class BatterySnapshot {
        public int status;
        public int plugged;
        public int level;
        public int voltageMv;
        public int temperatureDeciC;
        public String health;
    }
}
