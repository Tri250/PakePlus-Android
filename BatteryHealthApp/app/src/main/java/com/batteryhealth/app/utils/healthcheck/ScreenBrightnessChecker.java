package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 屏幕亮度检测
 * 检测屏幕亮度设置，过高的亮度会加速耗电
 */
public class ScreenBrightnessChecker implements IHealthChecker {

    private static final String NAME = "屏幕亮度";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 60;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public HealthCheckResult check(Context context) {
        HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                .setId("screen_brightness")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            int brightness = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

            int brightnessMode = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE);

            boolean isAuto = brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;

            int brightnessPercent = (int) ((brightness / 255f) * 100);

            builder.setValue(String.valueOf(brightnessPercent));
            builder.setUnit("%");

            if (isAuto) {
                builder.setStatus("自动亮度");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(90);
                builder.setDescription("屏幕亮度已设置为自动调节，系统会根据环境光线自动调整亮度，有助于节省电量。");
                builder.setAdvice("保持自动亮度调节可有效延长续航时间。");
            } else if (brightnessPercent > 80) {
                builder.setStatus("亮度过高");
                builder.setSeverity(HealthCheckResult.SEVERITY_WARNING);
                builder.setItemScore(60);
                builder.setDescription(String.format("当前屏幕亮度为%d%%，亮度过高会显著增加屏幕耗电。", brightnessPercent));
                builder.setAdvice("建议开启自动亮度调节或适当降低屏幕亮度。");
                builder.setRepairable(true);
                builder.setFixAction(HealthCheckResult.FIX_ACTION_DISPLAY_SETTINGS);
            } else {
                builder.setStatus("手动亮度");
                builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                builder.setItemScore(75);
                builder.setDescription(String.format("当前屏幕亮度为%d%%。", brightnessPercent));
                builder.setAdvice("建议开启自动亮度调节以获得更好的续航体验。");
                builder.setRepairable(true);
                builder.setFixAction(HealthCheckResult.FIX_ACTION_DISPLAY_SETTINGS);
            }
        } catch (Exception e) {
            builder.setStatus("无法读取");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法读取屏幕亮度设置。");
            builder.setAdvice("您可以手动检查显示设置中的亮度选项。");
        }

        return builder.build();
    }
}
