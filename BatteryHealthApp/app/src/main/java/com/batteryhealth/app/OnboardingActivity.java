package com.batteryhealth.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private static final String TAG = "OnboardingActivity";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    private ViewPager2 viewPager;
    private TextView btnSkip;
    private TextView btnNext;
    private View indicator0;
    private View indicator1;
    private View indicator2;

    private final int[] pageIcons = {
            R.drawable.ic_battery_health,
            R.drawable.ic_charging,
            R.drawable.ic_battery
    };

    private final int[] pageTitles = {
            R.string.onboarding_title_1,
            R.string.onboarding_title_2,
            R.string.onboarding_title_3
    };

    private final int[] pageDescs = {
            R.string.onboarding_desc_1,
            R.string.onboarding_desc_2,
            R.string.onboarding_desc_3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_onboarding);

            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            applyInsets();

            initViews();
            setupViewPager();
            setupClickListeners();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private void applyInsets() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager_onboarding);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);
        indicator0 = findViewById(R.id.indicator_0);
        indicator1 = findViewById(R.id.indicator_1);
        indicator2 = findViewById(R.id.indicator_2);
    }

    private void setupViewPager() {
        List<OnboardingPage> pages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pages.add(new OnboardingPage(pageIcons[i], pageTitles[i], pageDescs[i]));
        }

        OnboardingAdapter adapter = new OnboardingAdapter(pages);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicators(position);
                updateButtonText(position);
            }
        });
    }

    private void setupClickListeners() {
        btnSkip.setOnClickListener(v -> completeOnboarding());

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < 2) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                completeOnboarding();
            }
        });
    }

    private void updateIndicators(int position) {
        indicator0.setBackgroundResource(position == 0 ? R.drawable.bg_onboarding_dot_active : R.drawable.bg_onboarding_dot_inactive);
        indicator1.setBackgroundResource(position == 1 ? R.drawable.bg_onboarding_dot_active : R.drawable.bg_onboarding_dot_inactive);
        indicator2.setBackgroundResource(position == 2 ? R.drawable.bg_onboarding_dot_active : R.drawable.bg_onboarding_dot_inactive);
    }

    private void updateButtonText(int position) {
        if (position == 2) {
            btnNext.setText(R.string.onboarding_start);
        } else {
            btnNext.setText(R.string.onboarding_next);
        }
    }

    private void completeOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private static class OnboardingPage {
        final int iconRes;
        final int titleRes;
        final int descRes;

        OnboardingPage(int iconRes, int titleRes, int descRes) {
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.descRes = descRes;
        }
    }

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageViewHolder> {

        private final List<OnboardingPage> pages;

        OnboardingAdapter(List<OnboardingPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new PageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            OnboardingPage page = pages.get(position);
            holder.ivIcon.setImageResource(page.iconRes);
            holder.tvTitle.setText(page.titleRes);
            holder.tvDesc.setText(page.descRes);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle;
            TextView tvDesc;

            PageViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvDesc = itemView.findViewById(R.id.tv_desc);
            }
        }
    }
}
