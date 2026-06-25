package com.batteryhealth.app.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.PowerHistory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数据导出管理器
 *
 * 功能：
 * 1. 导出全部电池历史数据为 CSV 文件
 * 2. 支持 JSON 格式导出
 * 3. 使用 SAF (Storage Access Framework) 保存到用户选择的位置
 * 4. 导出进度回调
 */
public class DataExportManager {

    private static final String TAG = "DataExportManager";

    public static final int FORMAT_CSV = 0;
    public static final int FORMAT_JSON = 1;

    private final Context context;

    public DataExportManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface ExportCallback {
        void onProgress(int progress, int total);
        void onSuccess(Uri uri, String fileName);
        void onError(String message);
    }

    public String generateFileName(int format) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String dateStr = sdf.format(new Date());
        if (format == FORMAT_JSON) {
            return "battery_history_" + dateStr + ".json";
        }
        return "battery_history_" + dateStr + ".csv";
    }

    public void exportBatteryData(final List<BatteryInfo> batteryHistory,
                                   final List<PowerHistory> powerHistory,
                                   final Uri targetUri,
                                   final int format,
                                   final ExportCallback callback) {
        if (targetUri == null) {
            if (callback != null) callback.onError("目标 URI 无效");
            return;
        }

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int totalItems = (batteryHistory != null ? batteryHistory.size() : 0)
                            + (powerHistory != null ? powerHistory.size() : 0);
                    if (totalItems == 0) totalItems = 1;

                    if (format == FORMAT_JSON) {
                        exportJson(batteryHistory, powerHistory, targetUri, totalItems, callback);
                    } else {
                        exportCsv(batteryHistory, powerHistory, targetUri, totalItems, callback);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Export failed", e);
                    if (callback != null) {
                        callback.onError("导出失败: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void exportCsv(List<BatteryInfo> batteryHistory,
                           List<PowerHistory> powerHistory,
                           Uri targetUri,
                           int totalItems,
                           ExportCallback callback) throws IOException {
        OutputStream os = context.getContentResolver().openOutputStream(targetUri);
        if (os == null) {
            if (callback != null) callback.onError("无法打开输出流");
            return;
        }

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
        try {
            writer.write("数据类型,时间戳,电量%,电压(mV),电流(mA),温度(℃),健康度%,循环次数,充电功率(W),充电状态");
            writer.newLine();

            int processed = 0;

            if (batteryHistory != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (BatteryInfo info : batteryHistory) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("电池信息").append(",");
                    sb.append(sdf.format(new Date(info.getTimestamp()))).append(",");
                    sb.append(info.getLevel()).append(",");
                    sb.append(info.getVoltage()).append(",");
                    sb.append(info.getCurrentNow() / 1000f).append(",");
                    sb.append(info.getTemperature()).append(",");
                    sb.append(info.getHealthPercentage()).append(",");
                    sb.append(info.getCycleCount()).append(",");
                    sb.append(info.getChargingPower()).append(",");
                    sb.append(getStatusText(info.getStatus()));
                    writer.write(sb.toString());
                    writer.newLine();

                    processed++;
                    if (callback != null && totalItems > 0) {
                        callback.onProgress(processed * 100 / totalItems, totalItems);
                    }
                }
            }

            if (powerHistory != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (PowerHistory ph : powerHistory) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("充电记录").append(",");
                    sb.append(sdf.format(new Date(ph.getTimestamp()))).append(",");
                    sb.append(ph.getBatteryLevel()).append(",");
                    sb.append(ph.getVoltage() * 1000).append(",");
                    sb.append(ph.getCurrent() * 1000).append(",");
                    sb.append(ph.getBatteryTemp()).append(",");
                    sb.append("--").append(",");
                    sb.append("--").append(",");
                    sb.append(ph.getPower()).append(",");
                    sb.append(ph.getChargingPhase());
                    writer.write(sb.toString());
                    writer.newLine();

                    processed++;
                    if (callback != null && totalItems > 0) {
                        callback.onProgress(processed * 100 / totalItems, totalItems);
                    }
                }
            }

            writer.flush();
            if (callback != null) {
                String fileName = getFileNameFromUri(targetUri);
                callback.onSuccess(targetUri, fileName);
            }
        } finally {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void exportJson(List<BatteryInfo> batteryHistory,
                            List<PowerHistory> powerHistory,
                            Uri targetUri,
                            int totalItems,
                            ExportCallback callback) throws IOException {
        OutputStream os = context.getContentResolver().openOutputStream(targetUri);
        if (os == null) {
            if (callback != null) callback.onError("无法打开输出流");
            return;
        }

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            ExportData exportData = new ExportData();
            exportData.exportTime = System.currentTimeMillis();
            exportData.batteryInfoList = batteryHistory;
            exportData.powerHistoryList = powerHistory;
            exportData.totalBatteryRecords = batteryHistory != null ? batteryHistory.size() : 0;
            exportData.totalPowerRecords = powerHistory != null ? powerHistory.size() : 0;

            if (callback != null) {
                callback.onProgress(50, totalItems);
            }

            String json = gson.toJson(exportData);
            OutputStreamWriter writer = new OutputStreamWriter(os);
            writer.write(json);
            writer.flush();

            if (callback != null) {
                callback.onProgress(100, totalItems);
                String fileName = getFileNameFromUri(targetUri);
                callback.onSuccess(targetUri, fileName);
            }
        } finally {
            try {
                os.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String getStatusText(int status) {
        switch (status) {
            case 2: return "充电中";
            case 3: return "放电中";
            case 5: return "已充满";
            case 4: return "未充电";
            default: return "未知";
        }
    }

    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return "";
        String path = uri.getPath();
        if (path == null) return "";
        int cut = path.lastIndexOf('/');
        if (cut >= 0) return path.substring(cut + 1);
        return path;
    }

    public static class ExportData {
        public long exportTime;
        public int totalBatteryRecords;
        public int totalPowerRecords;
        public List<BatteryInfo> batteryInfoList;
        public List<PowerHistory> powerHistoryList;
    }
}
