package com.batteryhealth.app.ui.onboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.batteryhealth.app.R;
import com.batteryhealth.app.ui.MainActivity;

/**
 * 首次启动引导页（3 步 + 上传 bugreport 入口）。
 *
 * <p>完成引导后保存状态，下次启动不再展示。使用 ViewPager2 + 底部进度指示器，
 * 整个流程不依赖任何外部数据，离线可用。</p>
 */
public class OnboardActivity extends AppCompatActivity {

    private static final String PREF_NAME = "onboard_prefs";
    private static final String KEY_DONE = "onboard_done";

    private ViewPager2 viewPager;
    private LinearLayout indicatorContainer;
    private Button btnNext;
    private Button btnSkip;
    private TextView tvBrandBadge;

    private final int[] titles = {
        R.string.onboard_title_1,
        R.string.onboard_title_2,
        R.string.onboard_title_3
    };
    private final int[] subtitles = {
        R.string.onboard_subtitle_1,
        R.string.onboard_subtitle_2,
        R.string.onboard_subtitle_3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 已完成引导则直接跳转主页
        if (isDone(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_onboard);
        applyInsets();
        initViews();
        setupPager();
        setupActions();
    }

    private void applyInsets() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initViews() {
        viewPager = findViewById(R.id.onboard_pager);
        indicatorContainer = findViewById(R.id.onboard_indicator);
        btnNext = findViewById(R.id.onboard_btn_next);
        btnSkip = findViewById(R.id.onboard_btn_skip);
        tvBrandBadge = findViewById(R.id.onboard_brand_badge);
    }

    private void setupPager() {
        viewPager.setAdapter(new OnboardPagerAdapter(titles, subtitles));
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
                updateButtonForPosition(position);
            }
        });
        buildIndicators(titles.length);
        updateIndicator(0);
    }

    private void setupActions() {
        btnNext.setOnClickListener(v -> {
            int cur = viewPager.getCurrentItem();
            if (cur < titles.length - 1) {
                viewPager.setCurrentItem(cur + 1, true);
            } else {
                goToUpload();
            }
        });
        btnSkip.setOnClickListener(v -> finishOnboard());
    }

    private void updateButtonForPosition(int pos) {
        if (pos == titles.length - 1) {
            btnNext.setText(R.string.onboard_action_upload);
        } else {
            btnNext.setText(R.string.onboard_action_next);
        }
    }

    private void buildIndicators(int count) {
        indicatorContainer.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size * 2, size);
            lp.leftMargin = (int) (4 * getResources().getDisplayMetrics().density);
            lp.rightMargin = (int) (4 * getResources().getDisplayMetrics().density);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_indicator_dot);
            indicatorContainer.addView(dot);
        }
    }

    private void updateIndicator(int active) {
        for (int i = 0; i < indicatorContainer.getChildCount(); i++) {
            View dot = indicatorContainer.getChildAt(i);
            int color = (i == active)
                    ? ContextCompat.getColor(this, R.color.green_primary)
                    : ContextCompat.getColor(this, R.color.label_3);
            dot.getBackground().setTint(color);
        }
    }

    private void goToUpload() {
        // 第二步进入上传页
        Intent i = new Intent(this, BugreportUploadActivity.class);
        i.putExtra(BugreportUploadActivity.EXTRA_FROM_ONBOARD, true);
        startActivity(i);
    }

    private void finishOnboard() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_DONE, true).apply();
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() > 0) {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
        } else {
            super.onBackPressed();
        }
    }

    public static boolean isDone(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE, false);
    }

    public static void reset(@NonNull android.content.Context ctx) {
        ctx.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, false).apply();
    }
}
