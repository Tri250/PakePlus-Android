package com.batteryhealth.app.ui.policy;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;

/**
 * 隐私政策 / 用户协议共用页面。
 * 通过 Intent extra {@link #EXTRA_TYPE} 区分内容：
 *   - {@link #TYPE_PRIVACY}：隐私政策
 *   - {@link #TYPE_AGREEMENT}：用户协议
 */
public class PolicyActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "policy_type";
    public static final int TYPE_PRIVACY = 1;
    public static final int TYPE_AGREEMENT = 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            int type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_PRIVACY);

            // 动态构建一个全屏 ScrollView + TextView，避免创建多余的 layout xml
            TextView tv = new TextView(this);
            tv.setTextSize(15f);
            tv.setLineSpacing(0f, 1.4f);
            tv.setTextColor(ContextCompat.getColor(this, R.color.ios_label));
            tv.setPadding(40, 120, 40, 60);
            tv.setTextIsSelectable(true);
            tv.setText(type == TYPE_AGREEMENT ? R.string.user_agreement_body : R.string.privacy_policy_body);

            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.ios_background));
            scroll.addView(tv);
            setContentView(scroll);

            // 自定义顶部标题栏
            android.widget.LinearLayout titleBar = new android.widget.LinearLayout(this);
            titleBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            titleBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            titleBar.setBackgroundColor(ContextCompat.getColor(this, R.color.ios_background));
            titleBar.setPadding(24, 48, 24, 16);

            TextView title = new TextView(this);
            title.setText(type == TYPE_AGREEMENT ? R.string.user_agreement_title : R.string.privacy_policy_title);
            title.setTextSize(20f);
            title.setTextColor(ContextCompat.getColor(this, R.color.ios_label));
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleBar.addView(title, titleLp);

            TextView close = new TextView(this);
            close.setText("✕");
            close.setTextSize(20f);
            close.setTextColor(ContextCompat.getColor(this, R.color.ios_blue));
            close.setOnClickListener(v -> finish());
            titleBar.addView(close);

            // 把 titleBar 浮在 ScrollView 顶部
            android.widget.FrameLayout root = new android.widget.FrameLayout(this);
            root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.ios_background));
            android.widget.FrameLayout.LayoutParams scrollLp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            root.addView(scroll, scrollLp);
            android.widget.FrameLayout.LayoutParams titleLp2 = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            titleLp2.gravity = android.view.Gravity.TOP;
            root.addView(titleBar, titleLp2);
            setContentView(root);
        } catch (Exception e) {
            android.util.Log.e("PolicyActivity", "onCreate failed: " + e.getMessage(), e);
            finish();
        }
    }
}
