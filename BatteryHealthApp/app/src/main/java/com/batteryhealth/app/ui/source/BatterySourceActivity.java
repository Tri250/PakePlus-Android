package com.batteryhealth.app.ui.source;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 电池溯源页面
 *
 * 功能：
 * 1. 多维度验证电池是原装还是更换
 * 2. 展示各维度的判断依据和置信度
 * 3. 给出最终结论和建议
 */
public class BatterySourceActivity extends AppCompatActivity {

    private TextView tvFinalResult, tvConfidence, tvSummary;
    private TextView tvPsnMatch, tvFactorySerial, tvCapacityMatch, tvCycleRange, tvHealthDrop;
    private TextView tvManufacturer, tvSerialNumber, tvDesignCapacity, tvFullCapacity;
    private View progressBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void start(Context context) {
        Intent intent = new Intent(context, BatterySourceActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_source);

        initViews();
        startAnalysis();
    }

    private void initViews() {
        tvFinalResult = findViewById(R.id.tv_final_result);
        tvConfidence = findViewById(R.id.tv_confidence);
        tvSummary = findViewById(R.id.tv_summary);
        tvPsnMatch = findViewById(R.id.tv_psn_match);
        tvFactorySerial = findViewById(R.id.tv_factory_serial);
        tvCapacityMatch = findViewById(R.id.tv_capacity_match);
        tvCycleRange = findViewById(R.id.tv_cycle_range);
        tvHealthDrop = findViewById(R.id.tv_health_drop);
        tvManufacturer = findViewById(R.id.tv_manufacturer);
        tvSerialNumber = findViewById(R.id.tv_serial_number);
        tvDesignCapacity = findViewById(R.id.tv_design_capacity);
        tvFullCapacity = findViewById(R.id.tv_full_capacity);
        progressBar = findViewById(R.id.progress_loading);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void startAnalysis() {
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                BatteryDataManager manager = BatteryDataManager.getInstance(this);
                BatteryInfo info = manager.getLatestBatteryInfo(true);
                BatteryDataManager.BatterySource source = manager.verifyBatterySource(this, info);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    bindResult(info, source);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvFinalResult.setText(R.string.status_not_recognized);
                    tvSummary.setText(R.string.source_error_message);
                });
            }
        });
    }

    private void bindResult(BatteryInfo info, BatteryDataManager.BatterySource source) {
        // 最终结果
        String resultText;
        int resultColor;
        switch (source) {
            case ORIGINAL:
                resultText = getString(R.string.source_original);
                resultColor = getColor(R.color.ios_green);
                tvSummary.setText(R.string.source_summary_original);
                break;
            case REPLACED:
                resultText = getString(R.string.source_replaced);
                resultColor = getColor(R.color.ios_orange);
                tvSummary.setText(R.string.source_summary_replaced);
                break;
            case AFTERMARKET:
                resultText = getString(R.string.source_aftermarket);
                resultColor = getColor(R.color.ios_red);
                tvSummary.setText(R.string.source_summary_aftermarket);
                break;
            case UNKNOWN:
            default:
                resultText = getString(R.string.source_unknown);
                resultColor = getColor(R.color.ios_label);
                tvSummary.setText(R.string.source_summary_unknown);
                break;
        }
        tvFinalResult.setText(resultText);
        tvFinalResult.setTextColor(resultColor);

        // 置信度需要从 BatteryDataManager 内部计算，这里用简化版
        float confidence = calculateConfidence(info, source);
        tvConfidence.setText(String.format(Locale.getDefault(), "%d%%", (int) (confidence * 100)));

        // 各维度判断
        tvPsnMatch.setText(info.isBatteryInfoMatch() ? R.string.status_match : R.string.status_mismatch);
        tvPsnMatch.setTextColor(info.isBatteryInfoMatch() ? getColor(R.color.ios_green) : getColor(R.color.ios_orange));

        tvFactorySerial.setText(isFactoryFormat(info.getBatterySerial()) ? R.string.status_normal : R.string.status_abnormal);
        tvFactorySerial.setTextColor(isFactoryFormat(info.getBatterySerial()) ? getColor(R.color.ios_green) : getColor(R.color.ios_orange));

        int design = info.getDesignCapacity();
        int full = info.getFullCapacity();
        boolean capacityMatch = design > 0 && full > 0 && Math.abs(design - full) < design * 0.15;
        tvCapacityMatch.setText(capacityMatch ? R.string.status_match : R.string.status_mismatch);
        tvCapacityMatch.setTextColor(capacityMatch ? getColor(R.color.ios_green) : getColor(R.color.ios_orange));

        int cycle = info.getCycleCount();
        if (cycle >= 0) {
            tvCycleRange.setText(getString(R.string.source_cycle_value, cycle));
        } else {
            tvCycleRange.setText(R.string.status_unknown);
        }

        float health = info.getHealthPercentage();
        tvHealthDrop.setText(health > 0 ? String.format(Locale.getDefault(), "%.1f%%", health) : "--");

        // 电池信息
        tvManufacturer.setText(info.getManufacturer() != null && !info.getManufacturer().isEmpty() ? info.getManufacturer() : getString(R.string.status_unknown));
        tvSerialNumber.setText(info.getBatterySerial() != null && !info.getBatterySerial().isEmpty() ? info.getBatterySerial() : getString(R.string.status_unknown));
        tvDesignCapacity.setText(design > 0 ? design + " mAh" : "--");
        tvFullCapacity.setText(full > 0 ? full + " mAh" : "--");

        Animation fadeUp = AnimationUtils.loadAnimation(this, R.anim.fade_up);
        findViewById(R.id.content_container).startAnimation(fadeUp);
    }

    private float calculateConfidence(BatteryInfo info, BatteryDataManager.BatterySource source) {
        float score = 0.5f;
        if (source == BatteryDataManager.BatterySource.ORIGINAL) score += 0.3f;
        if (source == BatteryDataManager.BatterySource.REPLACED) score += 0.2f;
        if (source == BatteryDataManager.BatterySource.AFTERMARKET) score += 0.15f;
        if (info.isBatteryInfoMatch()) score += 0.1f;
        if (info.getBatterySerial() != null && !info.getBatterySerial().isEmpty()) score += 0.1f;
        return Math.min(0.99f, score);
    }

    private boolean isFactoryFormat(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        // 常见原厂电池序列号格式：包含字母和数字，长度在10-20之间
        return serial.length() >= 10 && serial.length() <= 20 && serial.matches(".*[A-Za-z].*") && serial.matches(".*\\d.*");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
