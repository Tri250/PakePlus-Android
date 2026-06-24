package com.batteryhealth.app.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommunityFragment extends Fragment {
    private static final String TAG = "CommunityFragment";

    private BatteryDataManager batteryDataManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout containerTips;
    private LinearLayout containerTemp;
    private LinearLayout containerLifespan;
    private LinearLayout containerQa;
    private LinearLayout containerPosts;
    private Button btnShareTip;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_community, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        TextView errorView = new TextView(requireContext());
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            containerTips = view.findViewById(R.id.community_tips_container);
            containerTemp = view.findViewById(R.id.community_temp_container);
            containerLifespan = view.findViewById(R.id.community_lifespan_container);
            containerQa = view.findViewById(R.id.community_qa_container);
            containerPosts = view.findViewById(R.id.community_posts_container);
            btnShareTip = view.findViewById(R.id.btn_share_tip);

            btnShareTip.setOnClickListener(v -> shareTip());

            // 从 MainActivity 获取共享的 BatteryDataManager
            if (getActivity() instanceof MainActivity) {
                batteryDataManager = ((MainActivity) getActivity()).getBatteryDataManager();
            }
            if (batteryDataManager == null) {
                batteryDataManager = new BatteryDataManager(requireContext());
            }

            loadCommunityPosts();
            loadBatteryDataAndPopulate();
            UiAnimationHelper.animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void loadBatteryDataAndPopulate() {
        ThreadExecutor.execute(() -> {
            try {
                batteryDataManager.refreshFromStickyIntent();
                BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                if (info != null && isAdded()) {
                    handler.post(() -> populateContent(info));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading battery data: " + e.getMessage());
            }
        });
    }

    private void loadCommunityPosts() {
        if (containerPosts == null) return;
        containerPosts.removeAllViews();

        // 社区帖子基于用户真实电池数据动态生成，不使用硬编码假帖子
        List<CommunityPost> posts = buildDataDrivenPosts();
        if (posts.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("暂无社区动态，保持良好的电池使用习惯即可");
            emptyView.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            emptyView.setPadding(dpToPx(22), dpToPx(16), dpToPx(22), dpToPx(16));
            containerPosts.addView(emptyView);
            return;
        }

        for (int i = 0; i < posts.size(); i++) {
            CommunityPost post = posts.get(i);
            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                containerPosts.addView(separator);
            }

            LinearLayout postRow = new LinearLayout(requireContext());
            postRow.setOrientation(LinearLayout.VERTICAL);
            int padH = dpToPx(22);
            int padTop = dpToPx(14);
            int padBottom = dpToPx(14);
            postRow.setPadding(padH, padTop, padH, padBottom);

            TextView tvAuthor = new TextView(requireContext());
            tvAuthor.setText(post.author);
            tvAuthor.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvAuthor.setTextSize(12);

            TextView tvContent = new TextView(requireContext());
            tvContent.setText(post.content);
            tvContent.setTextAppearance(requireContext(), R.style.iOSBody);
            tvContent.setLineSpacing(dpToPx(3), 1f);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            contentParams.topMargin = dpToPx(6);
            tvContent.setLayoutParams(contentParams);

            LinearLayout footer = new LinearLayout(requireContext());
            footer.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            footerParams.topMargin = dpToPx(8);
            footer.setLayoutParams(footerParams);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(post.time);
            tvTime.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvTime.setTextSize(11);

            footer.addView(tvTime);

            postRow.addView(tvAuthor);
            postRow.addView(tvContent);
            postRow.addView(footer);
            containerPosts.addView(postRow);
        }
    }

    /**
     * 基于用户真实电池数据动态生成社区动态，不使用硬编码假帖子。
     */
    private List<CommunityPost> buildDataDrivenPosts() {
        List<CommunityPost> posts = new ArrayList<>();
        if (batteryDataManager == null) return posts;

        BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
        if (info == null) return posts;

        float health = info.getHealthPercentage();
        float temp = info.getTemperature();
        int cycle = info.getCycleCount();
        int level = info.getLevel();

        // 基于真实健康度生成建议动态
        if (health >= 0 && health < 75) {
            posts.add(new CommunityPost("电池健康助手",
                    String.format(Locale.getDefault(), "您的电池健康度为%.0f%%，建议避免深度放电和高温环境，保持电量在20%%-80%%区间可有效减缓老化。", health),
                    "刚刚", 0));
        }
        if (temp > 35) {
            posts.add(new CommunityPost("温度监测",
                    String.format(Locale.getDefault(), "当前电池温度%.1f°C偏高，充电时建议取下手机壳并关闭高耗电应用以帮助散热。", temp),
                    "刚刚", 0));
        }
        if (cycle > 0 && cycle >= 300) {
            posts.add(new CommunityPost("循环次数提醒",
                    String.format("您的充电循环已达%d次，锂离子电池设计寿命约500次循环后保持80%%容量，建议关注健康度变化。", cycle),
                    "刚刚", 0));
        }
        if (level <= 20) {
            posts.add(new CommunityPost("低电量提醒",
                    String.format("当前电量%d%%，建议尽快充电，避免深度放电损伤电池。", level),
                    "刚刚", 0));
        }

        return posts;
    }

    private void shareTip() {
        // 分享包含用户真实电池数据的报告
        StringBuilder shareText = new StringBuilder();
        shareText.append("【电池健康报告】\n\n");

        if (batteryDataManager != null) {
            BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
            if (info != null && info.hasValidHealthData()) {
                shareText.append(String.format(Locale.getDefault(), "电池健康度：%.0f%%（%s）\n",
                        info.getHealthPercentage(), info.getHealthDescription()));
                shareText.append(String.format(Locale.getDefault(), "健康等级：%s\n", info.getHealthGrade()));
                if (info.hasValidCycleCount()) {
                    shareText.append(String.format("循环次数：%d 次\n", info.getCycleCount()));
                }
                shareText.append(String.format(Locale.getDefault(), "当前电量：%d%%\n", info.getLevel()));
                shareText.append(String.format(Locale.getDefault(), "电池温度：%.1f°C\n", info.getTemperature()));
            } else {
                shareText.append("暂无电池数据\n");
            }
        }

        shareText.append("\n—— 来自「电池健康」APP");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_title)));
    }

    private void populateContent(BatteryInfo info) {
        if (!isAdded()) return;

        float healthPct = info.getHealthPercentage();
        float temperature = info.getTemperature();
        int level = info.getLevel();
        int cycleCount = info.getCycleCount();
        boolean isCharging = info.isCharging();

        // === 充电建议 ===
        if (containerTips != null) {
            containerTips.removeAllViews();
            List<TipItem> chargeTips = buildChargeTips(level, isCharging);
            populateTipContainer(containerTips, chargeTips);
        }

        // === 温度管理 ===
        if (containerTemp != null) {
            containerTemp.removeAllViews();
            List<TipItem> tempTips = buildTempTips(temperature, isCharging);
            populateTipContainer(containerTemp, tempTips);
        }

        // === 延长寿命 ===
        if (containerLifespan != null) {
            containerLifespan.removeAllViews();
            List<TipItem> lifespanTips = buildLifespanTips(healthPct, cycleCount);
            populateTipContainer(containerLifespan, lifespanTips);
        }

        // === 常见问题 ===
        if (containerQa != null) {
            containerQa.removeAllViews();
            List<QaItem> qaList = buildPersonalizedQa(healthPct, temperature, cycleCount);
            populateQaContainer(containerQa, qaList);
        }
    }

    private void populateTipContainer(LinearLayout container, List<TipItem> tips) {
        for (int i = 0; i < tips.size(); i++) {
            TipItem tip = tips.get(i);
            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                container.addView(separator);
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int padV = dpToPx(12);
            int padH = dpToPx(22);
            row.setPadding(padH, padV, padH, padV);
            row.setMinimumHeight(dpToPx(48));

            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(tip.title);
            tvTitle.setTextAppearance(requireContext(), R.style.iOSBody);
            tvTitle.setLineSpacing(dpToPx(3), 1f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvTitle.setLayoutParams(titleParams);

            TextView tvSummary = new TextView(requireContext());
            tvSummary.setText(tip.summary);
            tvSummary.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvSummary.setLineSpacing(dpToPx(2), 1f);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryParams.setMarginStart(dpToPx(12));
            tvSummary.setLayoutParams(summaryParams);

            row.addView(tvTitle);
            row.addView(tvSummary);
            container.addView(row);
        }
    }

    private void populateQaContainer(LinearLayout container, List<QaItem> qaList) {
        for (int i = 0; i < qaList.size(); i++) {
            QaItem qa = qaList.get(i);
            if (i > 0) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_separator));
                container.addView(separator);
            }

            LinearLayout qaRow = new LinearLayout(requireContext());
            qaRow.setOrientation(LinearLayout.VERTICAL);
            int padH = dpToPx(22);
            int padTop = dpToPx(14);
            int padBottom = dpToPx(14);
            qaRow.setPadding(padH, padTop, padH, padBottom);

            TextView tvQuestion = new TextView(requireContext());
            tvQuestion.setText(qa.question);
            tvQuestion.setTextAppearance(requireContext(), R.style.iOSBody);
            tvQuestion.setLineSpacing(dpToPx(3), 1f);

            TextView tvAnswer = new TextView(requireContext());
            tvAnswer.setText(qa.answer);
            tvAnswer.setTextAppearance(requireContext(), R.style.iOSBody_Secondary);
            tvAnswer.setLineSpacing(dpToPx(3), 1f);
            LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            answerParams.topMargin = dpToPx(6);
            tvAnswer.setLayoutParams(answerParams);

            qaRow.addView(tvQuestion);
            qaRow.addView(tvAnswer);
            container.addView(qaRow);
        }
    }

    // === 充电建议 ===
    private List<TipItem> buildChargeTips(int level, boolean isCharging) {
        List<TipItem> tips = new ArrayList<>();

        if (isCharging && level >= 80) {
            tips.add(new TipItem("建议停止充电", "当前已充至" + level + "%"));
        } else if (isCharging && level < 80) {
            tips.add(new TipItem("充电中保持耐心", "充至80%后可拔掉"));
        } else if (level <= 10) {
            tips.add(new TipItem("立即充电", "电量严重不足"));
        } else if (level <= 20) {
            tips.add(new TipItem("请尽快充电", "电量低于20%"));
        } else {
            tips.add(new TipItem("避免过度充电", "充至80%即可"));
        }

        if (level > 80 && !isCharging) {
            tips.add(new TipItem("电量充足无需充电", "当前" + level + "%"));
        }

        tips.add(new TipItem("避免深度放电", "低于20%请充电"));
        tips.add(new TipItem("使用原装充电器", "安全可靠"));

        if (isCharging) {
            tips.add(new TipItem("避免边充边玩", "减少发热损耗"));
        }

        return tips;
    }

    // === 温度管理 ===
    private List<TipItem> buildTempTips(float temperature, boolean isCharging) {
        List<TipItem> tips = new ArrayList<>();

        if (temperature > 45) {
            tips.add(new TipItem("电池过热警告", String.format(Locale.getDefault(), "%.1f°C", temperature)));
        } else if (temperature > 35) {
            tips.add(new TipItem("温度偏高注意散热", String.format(Locale.getDefault(), "%.1f°C", temperature)));
        } else if (temperature < 0) {
            tips.add(new TipItem("低温影响电池性能", String.format(Locale.getDefault(), "%.1f°C", temperature)));
        } else {
            tips.add(new TipItem("温度正常", String.format(Locale.getDefault(), "%.1f°C", temperature)));
        }

        tips.add(new TipItem("理想温度范围", "0-35°C"));

        if (isCharging && temperature > 35) {
            tips.add(new TipItem("充电时取下保护壳", "帮助散热"));
        }

        if (temperature > 35) {
            tips.add(new TipItem("关闭高耗电应用", "降低发热"));
        }

        if (isCharging) {
            tips.add(new TipItem("充电时注意散热", "避免高温加速老化"));
        }

        return tips;
    }

    // === 延长寿命 ===
    private List<TipItem> buildLifespanTips(float healthPct, int cycleCount) {
        List<TipItem> tips = new ArrayList<>();

        // 统一阈值与 BatteryInfo.getHealthDescription() 一致：95+极佳，85+良好，75+一般，60+较差，<60极差
        if (healthPct >= 0 && healthPct < 60) {
            tips.add(new TipItem("建议更换电池", String.format(Locale.getDefault(), "健康度%.0f%%", healthPct)));
        } else if (healthPct >= 60 && healthPct < 75) {
            tips.add(new TipItem("电池损耗明显", "注意保养"));
        } else if (healthPct >= 75 && healthPct < 85) {
            tips.add(new TipItem("电池状态一般", "注意保养"));
        } else if (healthPct >= 85 && healthPct < 95) {
            tips.add(new TipItem("电池状态良好", "继续保持"));
        } else if (healthPct >= 95) {
            tips.add(new TipItem("电池状态极佳", "保养得当"));
        }

        if (cycleCount > 0 && cycleCount >= 500) {
            tips.add(new TipItem("循环次数较多", cycleCount + "次"));
        } else if (cycleCount > 0 && cycleCount >= 300) {
            tips.add(new TipItem("关注循环次数", cycleCount + "次"));
        } else if (cycleCount > 0) {
            tips.add(new TipItem("循环次数正常", cycleCount + "次"));
        }

        tips.add(new TipItem("保持20%-80%电量区间", "减缓老化"));
        tips.add(new TipItem("避免长时间满充", "减少化学应力"));

        return tips;
    }

    // === 常见问题 ===
    private List<QaItem> buildPersonalizedQa(float healthPct, float temperature, int cycleCount) {
        List<QaItem> qaList = new ArrayList<>();

        // 统一阈值与 BatteryInfo.getHealthDescription() 一致
        if (healthPct >= 0 && healthPct < 60) {
            qaList.add(new QaItem(
                    "电池健康度低于60%怎么办？",
                    "电池容量已严重衰减，建议尽快前往官方售后更换原装电池，以确保设备正常使用和安全。"));
        } else if (healthPct >= 60 && healthPct < 75) {
            qaList.add(new QaItem(
                    "电池健康度下降明显，如何减缓？",
                    "避免将电量用到20%以下再充电，充电至80%左右即可拔掉。避免高温环境下使用和充电，这些措施能有效减缓电池老化。"));
        } else if (healthPct >= 75 && healthPct < 85) {
            qaList.add(new QaItem(
                    "电池健康度一般，如何保持？",
                    "保持20%-80%的电量区间使用，避免长时间满充或深度放电，远离高温环境，使用原装充电器即可。"));
        } else if (healthPct >= 85 && healthPct < 95) {
            qaList.add(new QaItem(
                    "如何保持电池健康度？",
                    "保持20%-80%的电量区间使用，避免长时间满充或深度放电，远离高温环境，使用原装充电器即可。"));
        } else {
            qaList.add(new QaItem(
                    "电池状态很好，需要特别注意什么吗？",
                    "继续保持良好的充电习惯即可。建议开启充电保护（充至80%自动停止），避免高温环境长期使用。"));
        }

        // 基于温度的 Q&A
        if (temperature > 35) {
            qaList.add(new QaItem(
                    "电池温度偏高会有什么影响？",
                    "高温会加速锂离子电池的化学老化，长期处于35°C以上会显著缩短电池寿命。建议关闭高耗电应用、取下保护壳散热，充电时尤其注意。"));
        } else {
            qaList.add(new QaItem(
                    "电池温度多少算正常？",
                    "锂离子电池的理想工作温度为0-35°C。在此范围内使用和充电对电池寿命影响最小。低于0°C会导致暂时性容量下降，高于35°C会加速老化。"));
        }

        // 基于循环次数的 Q&A
        if (cycleCount > 0 && cycleCount >= 500) {
            qaList.add(new QaItem(
                    "充电循环次数达到" + cycleCount + "次意味着什么？",
                    "锂离子电池的设计寿命通常为500次完整充放电循环。达到此数值后，电池容量通常会降至设计容量的80%左右，属于正常老化现象。"));
        } else if (cycleCount > 0) {
            qaList.add(new QaItem(
                    "什么是充电循环次数？",
                    "一次完整的充电循环是指电池从0%充到100%的过程。部分充电（如从50%充到100%）只算半个循环。电池的设计寿命通常为500次循环后保持80%容量。"));
        }

        // 通用 Q&A
        qaList.add(new QaItem(
                "为什么建议充到80%就拔掉？",
                "锂离子电池在20%-80%电量区间内工作最稳定。长期将电池充至100%并保持满充状态，会增加电池内部化学应力，加速容量衰减。"));

        qaList.add(new QaItem(
                "快充会损伤电池吗？",
                "正规快充不会直接损伤电池，但快充会产生更多热量，高温才是加速老化的主因。建议日常使用标准充电，需要时再使用快充，并注意充电时的散热。"));

        qaList.add(new QaItem(
                "夜间充电会伤电池吗？",
                "现代手机有充电保护机制，充满后会自动停止充电。但长时间保持100%电量仍会增加电池化学应力。建议使用充电保护功能，限制充至80%。"));

        return qaList;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private static class TipItem {
        final String title;
        final String summary;

        TipItem(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }
    }

    private static class QaItem {
        final String question;
        final String answer;

        QaItem(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    private static class CommunityPost {
        final String author;
        final String content;
        final String time;
        final int likes;

        CommunityPost(String author, String content, String time, int likes) {
            this.author = author;
            this.content = content;
            this.time = time;
            this.likes = likes;
        }
    }
}
