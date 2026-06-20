package com.batteryhealth.app.ui.onboard;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.batteryhealth.app.R;
import com.batteryhealth.app.bugreport.BatteryHealthCalculator;
import com.batteryhealth.app.bugreport.BatteryRawData;
import com.batteryhealth.app.bugreport.BugReportDataBus;
import com.batteryhealth.app.bugreport.BugreportGuides;
import com.batteryhealth.app.bugreport.BugreportParser;
import com.batteryhealth.app.bugreport.ParseDetail;
import com.batteryhealth.app.bugreport.SNDecoder;
import com.batteryhealth.app.ui.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * BugReport 上传与解析页。
 *
 * <p>支持 .zip / .txt 格式；解析过程在后台线程完成，解析结果通过 {@link BugReportDataBus}
 * 推送给电池健康、配置查询、性能分析、续航等所有模块。</p>
 */
public class BugreportUploadActivity extends AppCompatActivity {

    public static final String EXTRA_FROM_ONBOARD = "from_onboard";

    private TextView tvFileName;
    private TextView tvFileSize;
    private TextView tvParsingStatus;
    private TextView tvExtractedFields;
    private TextView tvMissingFields;
    private TextView tvHealthScore;
    private TextView tvHealthGrade;
    private TextView tvSnInfo;
    private TextView tvBrandGuide;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private ProgressBar progressBar;
    private LinearLayout resultContainer;
    private LinearLayout brandListContainer;
    private Button btnPickFile;
    private Button btnViewGuide;
    private Button btnDone;
    private Button btnPickBrand;
    private View cardResult;

    private Uri pickedFile;
    private String currentBrand = "generic";

    private ActivityResultLauncher<String[]> filePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_upload_bugreport);
        applyInsets();
        initViews();
        setupFilePicker();
        setupActions();
        // 初始化品牌选择
        renderBrandList();
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
        tvFileName = findViewById(R.id.upload_file_name);
        tvFileSize = findViewById(R.id.upload_file_size);
        tvParsingStatus = findViewById(R.id.upload_status);
        tvExtractedFields = findViewById(R.id.upload_extracted);
        tvMissingFields = findViewById(R.id.upload_missing);
        tvHealthScore = findViewById(R.id.upload_health_score);
        tvHealthGrade = findViewById(R.id.upload_health_grade);
        tvSnInfo = findViewById(R.id.upload_sn_info);
        tvBrandGuide = findViewById(R.id.upload_brand_guide);
        tvTitle = findViewById(R.id.upload_title);
        tvSubtitle = findViewById(R.id.upload_subtitle);
        progressBar = findViewById(R.id.upload_progress);
        resultContainer = findViewById(R.id.upload_result);
        brandListContainer = findViewById(R.id.upload_brand_list);
        btnPickFile = findViewById(R.id.upload_btn_pick);
        btnViewGuide = findViewById(R.id.upload_btn_guide);
        btnDone = findViewById(R.id.upload_btn_done);
        btnPickBrand = findViewById(R.id.upload_btn_brand);
        cardResult = findViewById(R.id.upload_card_result);
    }

    private void setupFilePicker() {
        filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        pickedFile = uri;
                        showPickedFile(uri);
                        parseInBackground(uri);
                    }
                });
    }

    private void setupActions() {
        btnPickFile.setOnClickListener(v -> {
            try {
                filePicker.launch(new String[]{"*/*"});
            } catch (Exception e) {
                Toast.makeText(this, R.string.upload_no_file, Toast.LENGTH_SHORT).show();
            }
        });
        btnViewGuide.setOnClickListener(v -> openGuide());
        btnDone.setOnClickListener(v -> {
            // 标记引导完成
            OnboardActivity.reset(this);
            OnboardActivity.isDone(this);
            getSharedPreferences("onboard_prefs", MODE_PRIVATE).edit()
                    .putBoolean("onboard_done", true).apply();
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
        btnPickBrand.setOnClickListener(v -> toggleBrandList());
    }

    private void showPickedFile(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        tvFileName.setText(name != null ? name : "bugreport");
        tvParsingStatus.setText(R.string.upload_parsing);
        progressBar.setVisibility(View.VISIBLE);
        resultContainer.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
    }

    private void parseInBackground(Uri uri) {
        AsyncTask.execute(() -> {
            try {
                // 拷贝到 cache 目录便于解析
                File cache = new File(getCacheDir(), "bugreport_upload_" + System.currentTimeMillis() + ".zip");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(cache)) {
                    if (in == null) throw new IllegalStateException("无法读取文件");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }

                // 解析
                BatteryRawData data;
                String name = cache.getName().toLowerCase();
                if (name.endsWith(".zip")) {
                    data = BugreportParser.parseFromZip(cache);
                } else {
                    java.io.FileInputStream fis = new java.io.FileInputStream(cache);
                    byte[] bytes = new byte[(int) cache.length()];
                    fis.read(bytes);
                    fis.close();
                    data = BugreportParser.parseFromText(new String(bytes, "UTF-8"));
                }

                // 推断品牌
                String brand = data.getBrand();
                if (brand == null || brand.isEmpty()) {
                    brand = "generic";
                }
                SNDecoder.Result snResult = null;
                if (data.getSn() != null) {
                    snResult = SNDecoder.decode(data.getSn());
                }

                BatteryHealthCalculator.Result health = BatteryHealthCalculator.calculate(data);
                ParseDetail detail = BugreportParser.getParseDetail(data);

                // 写总线
                BugReportDataBus.get().publish(data, health, detail, brand);
                BugReportDataBus.get().persistToPrefs(this);

                final BatteryRawData dataFinal = data;
                final BatteryHealthCalculator.Result healthFinal = health;
                final ParseDetail detailFinal = detail;
                final String brandFinal = brand;
                final SNDecoder.Result snFinal = snResult;

                runOnUiThread(() -> showResult(dataFinal, healthFinal, detailFinal, brandFinal, snFinal));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvParsingStatus.setText(getString(R.string.upload_failure, e.getMessage()));
                });
            }
        });
    }

    private void showResult(BatteryRawData data, BatteryHealthCalculator.Result health,
                            ParseDetail detail, String brand, SNDecoder.Result snResult) {
        progressBar.setVisibility(View.GONE);
        tvParsingStatus.setText(R.string.upload_success);
        cardResult.setVisibility(View.VISIBLE);
        resultContainer.setVisibility(View.VISIBLE);

        tvExtractedFields.setText(getString(R.string.upload_field_extracted, detail.extractedFields.size()));
        tvMissingFields.setText(getString(R.string.upload_field_missing, detail.missingFields.size()));
        if (health.healthPercentage >= 0) {
            tvHealthScore.setText(getString(R.string.upload_health_score, health.healthPercentage));
            tvHealthGrade.setText(getString(R.string.upload_health_grade, health.grade));
        } else {
            tvHealthScore.setText("—");
            tvHealthGrade.setText("—");
        }

        if (snResult != null && snResult.factoryYear != null) {
            String line = snResult.brand + " · " + snResult.getProductionDateEstimate();
            tvSnInfo.setText(line);
        } else {
            tvSnInfo.setText("—");
        }

        BugreportGuides.Guide guide = BugreportGuides.forBrand(brand);
        StringBuilder sb = new StringBuilder();
        sb.append(guide.brand).append(" · ").append(guide.adbCommand);
        tvBrandGuide.setText(sb.toString());

        // 写入到本地 SharedPreferences 用于其他模块
        getSharedPreferences("bugreport_summary", MODE_PRIVATE).edit()
                .putString("brand", brand)
                .putLong("ts", System.currentTimeMillis())
                .apply();
    }

    private void openGuide() {
        BugreportGuides.Guide g = BugreportGuides.forBrand(currentBrand);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.guide_title, g.brand)).append("\n\n");
        for (BugreportGuides.Step step : g.steps) {
            sb.append(getString(R.string.guide_step_title, step.order)).append(" ")
                    .append(step.title).append("\n").append(step.detail).append("\n\n");
        }
        sb.append(getString(R.string.guide_adb_label)).append("：\n").append(g.adbCommand).append("\n\n");
        sb.append(getString(R.string.guide_filename_label)).append("：").append(g.filenamePattern).append("\n");
        sb.append(getString(R.string.guide_notes_label)).append("：").append(g.notes);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.guide_title)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void toggleBrandList() {
        boolean visible = brandListContainer.getVisibility() == View.VISIBLE;
        brandListContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void renderBrandList() {
        brandListContainer.removeAllViews();
        String[] brands = {"xiaomi", "huawei", "oppo", "vivo", "honor", "samsung", "oneplus", "realme", "redmi", "generic"};
        for (String b : brands) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            tv.setLayoutParams(lp);
            tv.setText(b.substring(0, 1).toUpperCase() + b.substring(1));
            tv.setTextColor(ContextCompat.getColor(this, R.color.label));
            tv.setTextSize(16);
            tv.setBackgroundResource(R.drawable.bg_card_health);
            tv.setOnClickListener(v -> {
                currentBrand = b;
                brandListContainer.setVisibility(View.GONE);
                tvBrandGuide.setText(b + " · " + BugreportGuides.forBrand(b).adbCommand);
            });
            LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            wrap.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            brandListContainer.addView(tv, wrap);
        }
    }
}
