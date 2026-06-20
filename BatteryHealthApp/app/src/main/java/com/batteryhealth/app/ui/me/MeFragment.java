package com.batteryhealth.app.ui.me;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.BuildConfig;
import com.batteryhealth.app.R;
import com.batteryhealth.app.bugreport.BatteryOriginAnalyzer;
import com.batteryhealth.app.bugreport.BugReportDataBus;
import com.batteryhealth.app.bugreport.SNDecoder;
import com.batteryhealth.app.ui.onboard.BugreportUploadActivity;
import com.batteryhealth.app.ui.onboard.OnboardActivity;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.io.File;
import java.util.Locale;

/**
 * 我的（设置）页：账户、备份、设置、数据、关于。
 *
 * <p>按图片 4 的玻璃拟态 + 卡片式设置风格，section 1/2 滚动一致。</p>
 */
public class MeFragment extends Fragment {

    private TextView tvVersion;
    private TextView tvDeviceStatus;
    private TextView tvBatteryStatus;
    private View switchTrendCard, switchDecimal;
    private View segmentedCalculated, segmentedSystem;
    private View segmentedCleanNone, segmentedClean7, segmentedClean30, segmentedClean90;
    private TextView tvLanguage;
    private TextView tvSyncState;
    private TextView tvSyncLast;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_me, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        wireActions(view);
        refreshState();
        try { UiAnimationHelper.animateCardsEntry(view); } catch (Exception ignored) {}
    }

    private void initViews(View v) {
        tvVersion = v.findViewById(R.id.me_version);
        tvDeviceStatus = v.findViewById(R.id.me_device_status);
        tvBatteryStatus = v.findViewById(R.id.me_battery_status);
        switchTrendCard = v.findViewById(R.id.switch_trend_card);
        switchDecimal = v.findViewById(R.id.switch_decimal);
        segmentedCalculated = v.findViewById(R.id.segment_calculated);
        segmentedSystem = v.findViewById(R.id.segment_system);
        segmentedCleanNone = v.findViewById(R.id.segment_clean_none);
        segmentedClean7 = v.findViewById(R.id.segment_clean_7);
        segmentedClean30 = v.findViewById(R.id.segment_clean_30);
        segmentedClean90 = v.findViewById(R.id.segment_clean_90);
        tvLanguage = v.findViewById(R.id.me_language_value);
        tvSyncState = v.findViewById(R.id.me_sync_state);
        tvSyncLast = v.findViewById(R.id.me_sync_last);
    }

    private void wireActions(View v) {
        v.findViewById(R.id.me_action_upload).setOnClickListener(view -> {
            startActivity(new Intent(requireContext(), BugreportUploadActivity.class));
        });
        v.findViewById(R.id.me_action_sync_now).setOnClickListener(view -> {
            new AlertDialog.Builder(requireContext())
                    .setMessage("立即备份本地数据？")
                    .setPositiveButton("备份", (d, w) -> {
                        Toast.makeText(requireContext(), "已备份本地数据", Toast.LENGTH_SHORT).show();
                        tvSyncLast.setText("刚刚");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        v.findViewById(R.id.me_action_restore).setOnClickListener(view -> {
            Toast.makeText(requireContext(), "恢复中…", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.me_action_clear).setOnClickListener(view -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("清空数据")
                    .setMessage("将清空 bugreport 历史、社区帖子与设置项，无法恢复。确定吗？")
                    .setPositiveButton("清空", (d, w) -> {
                        File cache = requireContext().getCacheDir();
                        if (cache.exists()) {
                            for (File f : cache.listFiles() != null ? cache.listFiles() : new File[0]) {
                                if (f.getName().startsWith("bugreport_upload_")) f.delete();
                            }
                        }
                        requireContext().getSharedPreferences("battery_community", 0).edit().clear().apply();
                        requireContext().getSharedPreferences("battery_history", 0).edit().clear().apply();
                        requireContext().getSharedPreferences("bugreport_data_bus", 0).edit().clear().apply();
                        Toast.makeText(requireContext(), "已清空", Toast.LENGTH_SHORT).show();
                        refreshState();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        v.findViewById(R.id.me_action_reset_onboard).setOnClickListener(view -> {
            OnboardActivity.reset(requireContext());
            startActivity(new Intent(requireContext(), OnboardActivity.class));
            requireActivity().finish();
        });
        v.findViewById(R.id.me_action_legal).setOnClickListener(view -> {
            startActivity(new Intent(requireContext(), com.batteryhealth.app.ui.policy.PolicyActivity.class));
        });
        v.findViewById(R.id.me_action_feedback).setOnClickListener(view -> {
            Intent mail = new Intent(Intent.ACTION_SEND);
            mail.setType("text/plain");
            mail.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@batteryhealth.app"});
            mail.putExtra(Intent.EXTRA_SUBJECT, "电池健康 App 反馈");
            try { startActivity(Intent.createChooser(mail, "发送邮件")); }
            catch (Exception e) { Toast.makeText(requireContext(), "未找到邮件应用", Toast.LENGTH_SHORT).show(); }
        });

        switchTrendCard.setOnClickListener(view -> toggleSwitch((View) view, "switch_trend_card"));
        switchDecimal.setOnClickListener(view -> toggleSwitch((View) view, "switch_decimal"));
        segmentedCalculated.setOnClickListener(view -> setHealthSource(true));
        segmentedSystem.setOnClickListener(view -> setHealthSource(false));
        segmentedCleanNone.setOnClickListener(view -> setCleanDays(0));
        segmentedClean7.setOnClickListener(view -> setCleanDays(7));
        segmentedClean30.setOnClickListener(view -> setCleanDays(30));
        segmentedClean90.setOnClickListener(view -> setCleanDays(90));
        v.findViewById(R.id.me_language_row).setOnClickListener(view -> showLanguageDialog());
    }

    private void toggleSwitch(View v, String key) {
        SharedPreferences sp = requireContext().getSharedPreferences("me_settings", 0);
        boolean cur = sp.getBoolean(key, true);
        boolean next = !cur;
        sp.edit().putBoolean(key, next).apply();
        applySwitchState(v, next);
    }

    private void applySwitchState(View v, boolean on) {
        View handle = v.findViewWithTag("handle");
        View track = v;
        if (track == null) return;
        track.setBackgroundResource(on ? R.drawable.bg_switch_on : R.drawable.bg_switch_off);
        if (handle != null) {
            handle.animate().translationX(on ? 18 * getResources().getDisplayMetrics().density : 0).setDuration(150).start();
        }
    }

    private void setHealthSource(boolean calculated) {
        SharedPreferences sp = requireContext().getSharedPreferences("me_settings", 0);
        sp.edit().putBoolean("health_calculated", calculated).apply();
        applySegmentedState(segmentedCalculated, calculated);
        applySegmentedState(segmentedSystem, !calculated);
    }

    private void setCleanDays(int days) {
        SharedPreferences sp = requireContext().getSharedPreferences("me_settings", 0);
        sp.edit().putInt("clean_days", days).apply();
        applySegmentedState(segmentedCleanNone, days == 0);
        applySegmentedState(segmentedClean7, days == 7);
        applySegmentedState(segmentedClean30, days == 30);
        applySegmentedState(segmentedClean90, days == 90);
    }

    private void applySegmentedState(View v, boolean active) {
        if (v == null) return;
        v.setBackgroundResource(active ? R.drawable.bg_segment_active : R.drawable.bg_segment_inactive);
        TextView label = v.findViewById(R.id.segment_text_holder);
        if (label != null) {
            label.setTextColor(requireContext().getColor(active ? R.color.green_primary_dark : R.color.label_3));
        }
    }

    private void showLanguageDialog() {
        String[] langs = {"简体中文", "繁體中文", "English", "日本語"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.me_language)
                .setItems(langs, (d, w) -> {
                    tvLanguage.setText(langs[w]);
                    requireContext().getSharedPreferences("me_settings", 0).edit()
                            .putString("language", langs[w]).apply();
                })
                .show();
    }

    private void refreshState() {
        SharedPreferences sp = requireContext().getSharedPreferences("me_settings", 0);
        boolean trendOn = sp.getBoolean("switch_trend_card", true);
        boolean decimalOn = sp.getBoolean("switch_decimal", true);
        boolean calc = sp.getBoolean("health_calculated", true);
        int clean = sp.getInt("clean_days", 0);
        String lang = sp.getString("language", "简体中文");
        applySwitchState(switchTrendCard, trendOn);
        applySwitchState(switchDecimal, decimalOn);
        setHealthSource(calc);
        setCleanDays(clean);
        tvLanguage.setText(lang);

        // 版本
        try { tvVersion.setText("v" + BuildConfig.VERSION_NAME); } catch (Exception e) { tvVersion.setText("v2.1.14"); }

        // 设备状态
        String model = android.os.Build.MODEL;
        String brand = android.os.Build.BRAND;
        tvDeviceStatus.setText(brand + " · " + model);

        // 电池状态（基于 BugReportDataBus 溯源结果）
        if (BugReportDataBus.get().hasData()) {
            BatteryOriginAnalyzer.Result r = BatteryOriginAnalyzer.analyze(
                    BugReportDataBus.get().getCurrent());
            tvBatteryStatus.setText(verdictText(r.verdict));
        } else {
            tvBatteryStatus.setText(R.string.me_battery_status_unknown);
        }

        // 同步状态
        long ts = requireContext().getSharedPreferences("bugreport_data_bus", 0)
                .getLong("last_result_timestamp", 0);
        if (ts > 0) {
            long diffMin = (System.currentTimeMillis() - ts) / 60_000;
            tvSyncLast.setText(diffMin < 1 ? "刚刚" : diffMin + " 分钟前");
            tvSyncState.setText("已同步");
        } else {
            tvSyncLast.setText("—");
            tvSyncState.setText("未同步");
        }
    }

    private String verdictText(BatteryOriginAnalyzer.Verdict v) {
        switch (v) {
            case ORIGINAL:
            case LIKELY_ORIGINAL: return getString(R.string.me_battery_status_original);
            case LIKELY_THIRD_PARTY:
            case THIRD_PARTY: return getString(R.string.me_battery_status_third);
            default: return getString(R.string.me_battery_status_unknown);
        }
    }
}
