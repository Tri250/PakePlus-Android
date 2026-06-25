package com.batteryhealth.app.ui.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.batteryhealth.app.BatteryHealthApplication;
import com.batteryhealth.app.data.database.AppDatabase;
import com.batteryhealth.app.data.model.BatteryOriginRecord;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.BatteryOriginDetector;
import com.batteryhealth.app.utils.ThreadExecutor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatteryOriginViewModel extends ViewModel {

    private final MutableLiveData<BatteryOriginDetector.OriginResult> originResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDetecting = new MutableLiveData<>(false);
    private final MutableLiveData<List<BatteryOriginRecord>> historyRecords = new MutableLiveData<>();
    private final MutableLiveData<String> reportText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> detectionError = new MutableLiveData<>(false);

    private BatteryOriginDetector originDetector;
    private BatteryDataManager batteryDataManager;
    private Context appContext;

    /** 标记 ViewModel 是否已销毁，用于取消后台任务回调 */
    private final AtomicBoolean isCleared = new AtomicBoolean(false);

    public BatteryOriginViewModel() {
    }

    @Override
    protected void onCleared() {
        isCleared.set(true);
    }

    /**
     * 初始化检测器和数据管理器。必须在 Fragment onAttach 之后调用。
     */
    public void initialize(Context context, BatteryDataManager dataManager) {
        if (context == null) {
            android.util.Log.e("BatteryOriginViewModel", "Context is null in initialize");
            return;
        }
        this.appContext = context.getApplicationContext();
        this.batteryDataManager = dataManager;
        this.originDetector = new BatteryOriginDetector(appContext);
        if (batteryDataManager != null) {
            originDetector.setBatteryDataManager(batteryDataManager);
        }
    }

    public LiveData<BatteryOriginDetector.OriginResult> getOriginResult() {
        return originResult;
    }

    public LiveData<Boolean> getIsDetecting() {
        return isDetecting;
    }

    public LiveData<List<BatteryOriginRecord>> getHistoryRecords() {
        return historyRecords;
    }

    public LiveData<String> getReportText() {
        return reportText;
    }

    public LiveData<Boolean> getDetectionError() {
        return detectionError;
    }

    /**
     * 自动检测：在页面首次进入时调用。
     */
    public void autoDetect() {
        if (Boolean.TRUE.equals(isDetecting.getValue())) return;
        performDetection(false);
    }

    /**
     * 手动检测：用户点击检测按钮时调用。
     */
    public void manualDetect() {
        if (Boolean.TRUE.equals(isDetecting.getValue())) return;
        performDetection(true);
    }

    private void performDetection(boolean forceRefresh) {
        if (originDetector == null || appContext == null) {
            android.util.Log.e("BatteryOriginViewModel", "originDetector or appContext is null, cannot detect");
            detectionError.postValue(true);
            isDetecting.postValue(false);
            return;
        }
        isDetecting.postValue(true);
        detectionError.postValue(false);

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    // 刷新 BatteryDataManager 数据
                    if (batteryDataManager != null) {
                        if (forceRefresh) {
                            batteryDataManager.refreshFromStickyIntent();
                        }
                        originDetector.setBatteryDataManager(batteryDataManager);
                    }

                    BatteryOriginDetector.OriginResult result = originDetector.detect();
                    originResult.postValue(result);

                    // 持久化检测结果
                    saveResult(result);

                    // 刷新历史记录
                    loadHistoryInternal();

                } catch (Exception e) {
                    android.util.Log.e("BatteryOriginViewModel", "Detection error: " + e.getMessage(), e);
                    detectionError.postValue(true);
                } finally {
                    isDetecting.postValue(false);
                }
            }
        });
    }

    /**
     * 加载历史检测记录
     */
    public void loadHistory() {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                loadHistoryInternal();
            }
        });
    }

    private void loadHistoryInternal() {
        if (isCleared.get()) return;
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return;
            AppDatabase db = app.getDatabase();
            if (db == null) return;

            List<BatteryOriginRecord> records = db.batteryOriginRecordDao().getRecent(20);
            historyRecords.postValue(records != null ? records : Collections.emptyList());
        } catch (Exception e) {
            historyRecords.postValue(Collections.emptyList());
        }
    }

    /**
     * 保存检测结果到数据库
     */
    private void saveResult(BatteryOriginDetector.OriginResult result) {
        if (result == null) return;
        try {
            BatteryHealthApplication app = BatteryHealthApplication.getInstance();
            if (app == null) return;
            AppDatabase db = app.getDatabase();
            if (db == null) return;

            BatteryOriginRecord record = result.toRecord();
            if (record != null) {
                db.batteryOriginRecordDao().insert(record);
            }
        } catch (Exception e) {
            // 持久化失败不影响 UI 展示
        }
    }

    /**
     * 生成溯源报告文本
     */
    public void generateReport() {
        BatteryOriginDetector.OriginResult result = originResult.getValue();
        if (result == null) {
            reportText.postValue("");
            return;
        }

        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                String report = buildReportText(result);
                reportText.postValue(report);
            }
        });
    }

    private String buildReportText(BatteryOriginDetector.OriginResult result) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        sb.append("═══════════════════════════════════\n");
        sb.append("       电池溯源检测报告\n");
        sb.append("═══════════════════════════════════\n\n");

        sb.append("检测时间：").append(sdf.format(new Date())).append("\n");
        sb.append("设备型号：").append(result.brand != null ? result.brand : "--")
          .append(" ").append(result.model != null ? result.model : "--").append("\n\n");

        // 判定结果
        sb.append("【判定结果】\n");
        sb.append(result.isOriginal ? "✓ 原装电池" : "✗ 非原装电池").append("\n");
        sb.append("置信度：").append(result.confidence).append("%\n");
        sb.append("结论：").append(result.conclusion != null ? result.conclusion : "--").append("\n\n");

        // 检测信息
        sb.append("【检测信息】\n");
        sb.append("生产日期：").append(result.manufactureDate != null ? result.manufactureDate : "--").append("\n");
        sb.append("序列号：").append(result.serialNumber != null ? result.serialNumber : "--").append("\n");
        sb.append("电池厂商：").append(result.manufacturer != null ? result.manufacturer : "--").append("\n");
        sb.append("出厂标识：").append(result.oemInfo != null ? result.oemInfo : "--").append("\n");
        sb.append("电池技术：").append(result.technology != null ? result.technology : "--").append("\n");
        sb.append("健康状态：").append(result.healthStatus != null ? result.healthStatus : "--").append("\n");
        sb.append("循环次数：").append(result.cycleCount != null ? result.cycleCount : "--").append("\n");

        if (result.designCapacity > 0 || result.currentCapacity > 0) {
            sb.append("设计容量：").append(result.designCapacity > 0 ? result.designCapacity + " mAh" : "--").append("\n");
            sb.append("当前容量：").append(result.currentCapacity > 0 ? result.currentCapacity + " mAh" : "--").append("\n");
            if (result.designCapacity > 0 && result.currentCapacity > 0) {
                float ratio = (result.currentCapacity * 100f) / result.designCapacity;
                sb.append("容量比：").append(String.format(Locale.getDefault(), "%.1f%%", ratio)).append("\n");
            }
        }

        // 检测方法
        if (result.detectionMethods != null && !result.detectionMethods.isEmpty()) {
            sb.append("\n【检测方法】\n");
            for (BatteryOriginDetector.DetectionMethod method : result.detectionMethods) {
                sb.append("• ").append(method.name).append("：").append(method.value).append("\n");
            }
        }

        sb.append("\n═══════════════════════════════════\n");
        sb.append("数据来源：").append(result.sourceTag != null ? result.sourceTag : "未知").append("\n");
        sb.append("由「电池健康」App 生成\n");

        return sb.toString();
    }

    /**
     * 获取分享 Intent
     */
    public Intent createShareIntent(String reportContent) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, reportContent);
        return shareIntent;
    }

    /**
     * 获取置信度对应的颜色资源 ID
     */
    public int getConfidenceColorRes(int confidence) {
        if (confidence >= 75) {
            return com.batteryhealth.app.R.color.confidence_high;
        } else if (confidence >= 45) {
            return com.batteryhealth.app.R.color.confidence_medium;
        } else {
            return com.batteryhealth.app.R.color.confidence_low;
        }
    }

    /**
     * 获取置信度对应的描述文本
     */
    public String getConfidenceDescription(int confidence) {
        if (confidence >= 80) {
            return "高可信度";
        } else if (confidence >= 60) {
            return "中等可信度";
        } else if (confidence >= 40) {
            return "低可信度";
        } else {
            return "可信度不足";
        }
    }

    /**
     * 格式化历史记录时间
     */
    public String formatRecordTime(long timestamp) {
        if (timestamp <= 0) return "--";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * 删除历史记录
     */
    public void deleteHistoryRecord(final long id) {
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (isCleared.get()) return;
                try {
                    BatteryHealthApplication app = BatteryHealthApplication.getInstance();
                    if (app == null) return;
                    AppDatabase db = app.getDatabase();
                    if (db == null) return;
                    db.batteryOriginRecordDao().deleteOlderThan(id + 1);
                    // Reload
                    loadHistoryInternal();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
