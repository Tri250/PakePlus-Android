package com.batteryhealth.app.ui.onboarding;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 首次引导页：3页引导 + 点指示器 + 开始使用按钮
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout dotsContainer;
    private Button btnStart;
    private Button btnNext;
    private Button btnSkip;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.onboarding_pager);
        dotsContainer = findViewById(R.id.dots_container);
        btnStart = findViewById(R.id.btn_start);
        btnNext = findViewById(R.id.btn_next);
        btnSkip = findViewById(R.id.btn_skip);

        List<OnboardingPage> pages = createPages();
        OnboardingAdapter adapter = new OnboardingAdapter(this, pages);
        viewPager.setAdapter(adapter);

        setupDots(pages.size());
        updateDots(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots(position);
                if (position == pages.size() - 1) {
                    btnStart.setVisibility(View.VISIBLE);
                    btnNext.setVisibility(View.GONE);
                } else {
                    btnStart.setVisibility(View.GONE);
                    btnNext.setVisibility(View.VISIBLE);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        btnStart.setOnClickListener(v -> {
            // 在最后一页请求通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                }
            }
            finishOnboarding();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 无论权限是否授予，都继续进入主页面
        finishOnboarding();
    }

    private void finishOnboarding() {
        getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_completed", true)
                .apply();
        showPrivacyConsent();
    }

    private void showPrivacyConsent() {
        SharedPreferences prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("privacy_consented", false)) {
            goToMain();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("隐私政策与用户协议")
                .setMessage("欢迎使用电池健康！\n\n" +
                        "我们重视您的隐私：\n" +
                        "• 本应用仅在本地设备上分析电池数据\n" +
                        "• 所有数据存储在您的设备上，不会上传到任何服务器\n" +
                        "• 我们不会收集您的个人信息、位置或使用习惯\n" +
                        "• 您可以随时在设置中管理通知权限\n\n" +
                        "点击「同意」表示您已阅读并理解以上内容。")
                .setPositiveButton("同意", (d, which) -> {
                    prefs.edit().putBoolean("privacy_consented", true).apply();
                    d.dismiss();
                    goToMain();
                })
                .setNegativeButton("退出", (d, which) -> {
                    Toast.makeText(this, "需要同意隐私政策才能使用", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private List<OnboardingPage> createPages() {
        List<OnboardingPage> pages = new ArrayList<>();
        pages.add(new OnboardingPage(
                "电池健康监控",
                "实时监测电池健康度、容量衰减、循环次数\n了解电池真实状态，科学养护",
                R.drawable.ic_battery_health));
        pages.add(new OnboardingPage(
                "充电智能保护",
                "设置目标电量，充满自动提醒\n合理充电区间20%-80%，延长电池寿命",
                R.drawable.ic_charging));
        pages.add(new OnboardingPage(
                "全面性能分析",
                "CPU/GPU/内存实时监控\n续航预测 + 趋势追踪 + 一键自检",
                R.drawable.ic_performance));
        return pages;
    }

    private void setupDots(int count) {
        dotsContainer.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(10, 10);
            params.setMargins(6, 0, 6, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_dot_inactive);
            dotsContainer.addView(dot);
        }
    }

    private void updateDots(int position) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            dot.setBackgroundResource(i == position ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        }
    }

    public static boolean shouldShow(Context context) {
        return !context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                .getBoolean("onboarding_completed", false);
    }
}