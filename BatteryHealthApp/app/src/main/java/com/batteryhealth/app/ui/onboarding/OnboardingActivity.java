package com.batteryhealth.app.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

        btnStart.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_completed", true)
                .apply();
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