package com.batteryhealth.app.ui.bugreport;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Bugreport 日志抓取引导页
 * 按国内主流品牌展示抓取方法，并提供跳转到本地分析页面的入口。
 */
public class BugreportGuideActivity extends AppCompatActivity {

    private LinearLayout layoutBrands;
    private MaterialButton btnSelectFile;

    private final List<BrandMethod> brandMethods = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bugreport_guide);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_bugreport_guide);
        }

        initData();
        bindViews();
        renderBrands();
        animateCardsEntry();
    }

    private void initData() {
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_xiaomi),
                getString(R.string.method_xiaomi),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_huawei_honor),
                getString(R.string.method_huawei_honor),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_oppo_oneplus_realme),
                getString(R.string.method_oppo_oneplus_realme),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_vivo_iqoo),
                getString(R.string.method_vivo_iqoo),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_meizu),
                getString(R.string.method_meizu),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_nubia_redmagic),
                getString(R.string.method_nubia_redmagic),
                R.drawable.ic_device));
        brandMethods.add(new BrandMethod(
                getString(R.string.brand_generic),
                getString(R.string.method_generic),
                R.drawable.ic_device));
    }

    private void bindViews() {
        layoutBrands = findViewById(R.id.layout_brands);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnSelectFile.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, BugreportUploadActivity.class));
            } catch (Exception e) {
                // ignore
            }
        });
    }

    private void renderBrands() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < brandMethods.size(); i++) {
            BrandMethod item = brandMethods.get(i);
            View card = inflater.inflate(R.layout.item_expandable_card, layoutBrands, false);

            TextView tvTitle = card.findViewById(R.id.tv_card_title);
            TextView tvContent = card.findViewById(R.id.tv_card_content);
            ImageView ivArrow = card.findViewById(R.id.iv_arrow);
            View header = card.findViewById(R.id.card_header);

            tvTitle.setText(item.title);
            tvContent.setText(item.method);
            ivArrow.setImageResource(R.drawable.ic_trend);
            ivArrow.setColorFilter(ContextCompat.getColor(this, R.color.primary_green));

            header.setOnClickListener(v -> {
                boolean expanded = tvContent.getVisibility() == View.VISIBLE;
                tvContent.setVisibility(expanded ? View.GONE : View.VISIBLE);
                ivArrow.setRotation(expanded ? 0f : 180f);
            });

            tvContent.setVisibility(View.GONE);
            layoutBrands.addView(card);
        }
    }

    private void animateCardsEntry() {
        View root = findViewById(R.id.scroll_root);
        if (!(root instanceof LinearLayout)) return;
        LinearLayout container = (LinearLayout) root;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(60f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay(i * 80L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.7f))
                    .start();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class BrandMethod {
        final String title;
        final String method;
        final int iconRes;

        BrandMethod(String title, String method, int iconRes) {
            this.title = title;
            this.method = method;
            this.iconRes = iconRes;
        }
    }
}
