package com.batteryhealth.app.ui.community;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.bugreport.BatteryCommunity;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 电池江湖 Fragment：心得分享 + 保养技巧 + 热门话题。
 *
 * <p>3 个 tab 切换，发布的心得持久化到 SharedPreferences，
 * 顶部 banner + 中部分类 chip + 下方动态列表。</p>
 */
public class CommunityFragment extends Fragment {

    private static final String TAG = "CommunityFragment";
    private View tabPosts, tabTips, tabTopics;
    private TextView tabPostsLabel, tabTipsLabel, tabTopicsLabel;
    private LinearLayout contentContainer;
    private View fabPost;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupTabs();
        renderPosts();
        try {
            UiAnimationHelper.animateCardsEntry(view);
        } catch (Exception ignored) {}
    }

    private void initViews(View v) {
        tabPosts = v.findViewById(R.id.tab_posts);
        tabTips = v.findViewById(R.id.tab_tips);
        tabTopics = v.findViewById(R.id.tab_topics);
        tabPostsLabel = v.findViewById(R.id.tab_posts_label);
        tabTipsLabel = v.findViewById(R.id.tab_tips_label);
        tabTopicsLabel = v.findViewById(R.id.tab_topics_label);
        contentContainer = v.findViewById(R.id.community_content);
        fabPost = v.findViewById(R.id.community_fab);
    }

    private void setupTabs() {
        tabPosts.setOnClickListener(v -> { highlightTab(0); renderPosts(); });
        tabTips.setOnClickListener(v -> { highlightTab(1); renderTips(); });
        tabTopics.setOnClickListener(v -> { highlightTab(2); renderTopics(); });
        fabPost.setOnClickListener(v -> showPublishDialog());
    }

    private void highlightTab(int active) {
        int activeColor = requireContext().getColor(R.color.green_primary_dark);
        int inactiveColor = requireContext().getColor(R.color.label_3);
        tabPostsLabel.setTextColor(active == 0 ? activeColor : inactiveColor);
        tabTipsLabel.setTextColor(active == 1 ? activeColor : inactiveColor);
        tabTopicsLabel.setTextColor(active == 2 ? activeColor : inactiveColor);
    }

    private void renderPosts() {
        contentContainer.removeAllViews();
        List<BatteryCommunity.Post> posts = BatteryCommunity.all(requireContext());
        if (posts.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.journey_no_posts);
            empty.setTextColor(requireContext().getColor(R.color.label_2));
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            contentContainer.addView(empty);
            return;
        }
        SimpleDateFormat df = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (BatteryCommunity.Post p : posts) {
            View card = inflater.inflate(R.layout.item_community_post, contentContainer, false);
            TextView tvAuthor = card.findViewById(R.id.post_author);
            TextView tvTime = card.findViewById(R.id.post_time);
            TextView tvContent = card.findViewById(R.id.post_content);
            TextView tvTopic = card.findViewById(R.id.post_topic);
            TextView tvLikes = card.findViewById(R.id.post_likes);
            TextView tvComments = card.findViewById(R.id.post_comments);

            tvAuthor.setText(p.author);
            tvTime.setText(df.format(new Date(p.timestamp)));
            tvContent.setText(p.content);
            tvTopic.setText("#" + p.topic);
            tvLikes.setText(getString(R.string.community_like_count, p.likes));
            tvComments.setText(getString(R.string.community_comment_count, p.comments));

            card.findViewById(R.id.post_btn_like).setOnClickListener(v -> {
                BatteryCommunity.like(requireContext(), p.id);
                p.likes++;
                tvLikes.setText(getString(R.string.community_like_count, p.likes));
            });
            contentContainer.addView(card);
        }
    }

    private void renderTips() {
        contentContainer.removeAllViews();
        List<String> tips = BatteryCommunity.tips();
        int idx = 1;
        for (String tip : tips) {
            View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_community_tip, contentContainer, false);
            TextView tvIndex = card.findViewById(R.id.tip_index);
            TextView tvText = card.findViewById(R.id.tip_text);
            tvIndex.setText(String.format(Locale.getDefault(), "%02d", idx++));
            tvText.setText(tip);
            contentContainer.addView(card);
        }
    }

    private void renderTopics() {
        contentContainer.removeAllViews();
        List<String> topics = BatteryCommunity.topics();
        LinearLayout row = null;
        for (int i = 0; i < topics.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dp(10);
                contentContainer.addView(row, rlp);
            }
            if (row == null) continue;
            TextView chip = new TextView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = (i % 2 == 0) ? dp(5) : 0;
            lp.leftMargin = (i % 2 == 1) ? dp(5) : 0;
            chip.setLayoutParams(lp);
            chip.setBackgroundResource(R.drawable.bg_card_health);
            chip.setText("# " + topics.get(i));
            chip.setTextColor(requireContext().getColor(R.color.label));
            chip.setTextSize(14);
            chip.setGravity(android.view.Gravity.CENTER);
            int pad = dp(14);
            chip.setPadding(pad, pad, pad, pad);
            row.addView(chip);
        }
    }

    private void showPublishDialog() {
        Context ctx = requireContext();
        EditText input = new EditText(ctx);
        input.setHint(R.string.journey_post_hint);
        input.setMinLines(3);
        int pad = dp(20);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.community_post_new)
                .setView(input)
                .setPositiveButton(R.string.community_post_publish, (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (TextUtils.isEmpty(s)) {
                        Toast.makeText(ctx, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    BatteryCommunity.publish(ctx, s, "心得分享");
                    renderPosts();
                })
                .setNegativeButton(R.string.community_post_cancel, null)
                .show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
