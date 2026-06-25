package com.batteryhealth.app.utils.healthcheck;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Vibrator;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 振动检测
 * 检测振动反馈设置，过多振动会增加耗电
 */
public class VibrationChecker implements IHealthChecker {

    private static final String NAME = "振动反馈";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 85;

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
                .setId("vibration_feedback")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            if (vibrator == null || !vibrator.hasVibrator()) {
                builder.setStatus("不支持");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(100);
                builder.setDescription("设备不支持振动功能。");
                builder.setAdvice("无需处理。");
                return builder.build();
            }

            int ringerMode = audioManager != null ? audioManager.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
            boolean vibrateWhenRinging = false;
            boolean hapticFeedbackEnabled = false;

            try {
                if (audioManager != null) {
                    vibrateWhenRinging = audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
                            || (audioManager.getStreamVolume(AudioManager.STREAM_RING) > 0
                            && audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER));
                }
            } catch (Exception ignored) {}

            try {
                hapticFeedbackEnabled = android.provider.Settings.System.getInt(
                        context.getContentResolver(),
                        android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1;
            } catch (Exception ignored) {}

            StringBuilder statusDesc = new StringBuilder();
            int severity;
            int score;
            String advice;

            if (!vibrateWhenRinging && !hapticFeedbackEnabled) {
                statusDesc.append("振动已关闭");
                severity = HealthCheckResult.SEVERITY_GOOD;
                score = 95;
                advice = "关闭振动反馈可延长电池续航时间。";
            } else if (vibrateWhenRinging && hapticFeedbackEnabled) {
                statusDesc.append("全部开启");
                severity = HealthCheckResult.SEVERITY_INFO;
                score = 70;
                advice = "建议关闭触感反馈以节省电量，来电振动可根据个人喜好保留。";
            } else if (vibrateWhenRinging) {
                statusDesc.append("仅来电振动");
                severity = HealthCheckResult.SEVERITY_GOOD;
                score = 85;
                advice = "仅保留来电振动，平衡了使用体验和电量消耗。";
            } else {
                statusDesc.append("仅触感反馈");
                severity = HealthCheckResult.SEVERITY_INFO;
                score = 75;
                advice = "建议关闭触感反馈以节省电量。";
            }

            builder.setStatus(statusDesc.toString());
            builder.setSeverity(severity);
            builder.setItemScore(score);
            builder.setDescription(statusDesc + "。振动马达工作时会消耗额外电量，关闭不必要的振动可以延长续航。");
            builder.setAdvice(advice);
            builder.setValue(vibrateWhenRinging ? "振动开" : "振动关");
            builder.setRepairable(true);
            builder.setFixAction(HealthCheckResult.FIX_ACTION_SOUND_SETTINGS);

        } catch (Exception e) {
            builder.setStatus("无法检测");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法检测振动设置状态。");
            builder.setAdvice("您可以在声音设置中手动调整振动选项。");
        }

        return builder.build();
    }
}
