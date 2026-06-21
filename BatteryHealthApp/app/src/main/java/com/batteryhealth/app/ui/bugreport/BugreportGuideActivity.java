package com.batteryhealth.app.ui.bugreport;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BugreportAnalyzer;
import com.batteryhealth.app.utils.BugreportParser;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 安装成功首页 / Bugreport 引导页
 *
 * 功能：
 * 1. 首次启动时展示各品牌抓取 bugreports 的指南
 * 2. 提供文件选择入口上传 bugreports ZIP 文件
 * 3. 端侧本地解析 bugreports，提取电池、性能等关键数据
 * 4. 将解析结果持久化，供其他模块使用
 * 5. 支持跳过此步骤直接进入主界面
 */
public class BugreportGuideActivity extends AppCompatActivity {

    private static final String TAG = "BugreportGuide";
    private static final String PREFS_NAME = "bugreport_prefs";
    private static final String KEY_GUIDE_SHOWN = "guide_shown";
    private static final String KEY_BUGREPORT_ANALYZED = "bugreport_analyzed";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ViewGroup guideContainer;
    private ViewGroup uploadSection;
    private ViewGroup analyzingSection;
    private ProgressBar progressAnalyzing;
    private TextView tvAnalyzingStatus;
    private Button btnSelectFile;
    private Button btnSkip;
    private Button btnGoMain;
    private TextView tvBrandName;

    private JSONObject guidesJson;
    private String detectedBrand;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    showPermissionRationale();
                }
            }
    );

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startBugreportAnalysis(uri);
                    }
                }
            }
    );

    /**
     * 判断是否显示引导页（首次启动或尚未分析过 bugreport）
     */
    public static boolean shouldShow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean guideShown = prefs.getBoolean(KEY_GUIDE_SHOWN, false);
        boolean analyzed = prefs.getBoolean(KEY_BUGREPORT_ANALYZED, false);
        // 如果已经分析过 bugreport，不再显示
        return !analyzed;
    }

    /**
     * 标记引导页已展示过（但尚未分析 bugreport）
     */
    public static void markGuideShown(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_GUIDE_SHOWN, true).apply();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_bugreport_guide);

            initViews();
            loadGuides();
            detectBrandAndShowGuide();
            setupListeners();
            requestPermissionsIfNeeded();

            // 入场动画
            View scrollContent = findViewById(R.id.scroll_content);
            if (scrollContent != null) {
                UiAnimationHelper.animateCardsEntry(scrollContent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate", e);
            goToMainActivity();
        }
    }

    private void initViews() {
        guideContainer = findViewById(R.id.guide_container);
        uploadSection = findViewById(R.id.upload_section);
        analyzingSection = findViewById(R.id.analyzing_section);
        progressAnalyzing = findViewById(R.id.progress_analyzing);
        tvAnalyzingStatus = findViewById(R.id.tv_analyzing_status);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnSkip = findViewById(R.id.btn_skip);
        btnGoMain = findViewById(R.id.btn_go_main);
        tvBrandName = findViewById(R.id.tv_brand_name);

        if (analyzingSection != null) analyzingSection.setVisibility(View.GONE);
        if (btnGoMain != null) btnGoMain.setVisibility(View.GONE);
    }

    private void loadGuides() {
        try (InputStream is = getAssets().open("bugreport_guides.json");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            String json = baos.toString(StandardCharsets.UTF_8.name());
            guidesJson = new JSONObject(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load bugreport guides", e);
            guidesJson = new JSONObject();
        }
    }

    private void detectBrandAndShowGuide() {
        detectedBrand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "generic";
        String displayBrand = Build.BRAND != null ? Build.BRAND : getString(R.string.status_unknown);

        // 品牌映射
        String guideBrand = mapToGuideBrand(detectedBrand);

        if (tvBrandName != null) {
            tvBrandName.setText(getString(R.string.bugreport_detected_brand, displayBrand));
        }

        try {
            JSONArray guides = guidesJson.optJSONArray("guides");
            if (guides != null) {
                for (int i = 0; i < guides.length(); i++) {
                    JSONObject brandGuide = guides.getJSONObject(i);
                    if (guideBrand.equals(brandGuide.optString("brand", ""))) {
                        renderBrandGuide(brandGuide);
                        return;
                    }
                }
                // 未匹配到特定品牌，显示通用指南
                for (int i = 0; i < guides.length(); i++) {
                    JSONObject brandGuide = guides.getJSONObject(i);
                    if ("generic".equals(brandGuide.optString("brand", ""))) {
                        renderBrandGuide(brandGuide);
                        return;
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing guides", e);
        }

        // 兜底：显示简单提示
        showFallbackGuide();
    }

    private String mapToGuideBrand(String brand) {
        if (brand == null) return "generic";
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) return "xiaomi";
        if (brand.contains("oppo") || brand.contains("realme")) return "oppo";
        if (brand.contains("oneplus")) return "oppo"; // 一加归类到OPPO
        if (brand.contains("vivo") || brand.contains("iqoo")) return "vivo";
        if (brand.contains("huawei") || brand.contains("honor")) return "huawei";
        if (brand.contains("samsung")) return "samsung";
        if (brand.contains("meizu")) return "meizu";
        if (brand.contains("google")) return "google";
        return "generic";
    }

    private void renderBrandGuide(JSONObject brandGuide) throws JSONException {
        guideContainer.removeAllViews();

        String brandName = brandGuide.optString("brandName", getString(R.string.status_unknown));
        JSONArray methods = brandGuide.getJSONArray("methods");

        for (int m = 0; m < methods.length(); m++) {
            JSONObject method = methods.getJSONObject(m);
            addMethodCard(method, m + 1);
        }
    }

    private void addMethodCard(JSONObject method, int index) throws JSONException {
        String methodName = method.optString("name", "方法 " + index);
        JSONArray steps = method.getJSONArray("steps");
        String note = method.optString("note", "");

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_solid));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setRadius(getResources().getDimension(R.dimen.radius_card));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(cardLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                (int) (22 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (22 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density)
        );

        // 方法标题
        TextView tvMethodTitle = new TextView(this);
        tvMethodTitle.setText(methodName);
        tvMethodTitle.setTextSize(16);
        tvMethodTitle.setTextColor(ContextCompat.getColor(this, R.color.label));
        tvMethodTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(tvMethodTitle);

        // 步骤列表
        for (int i = 0; i < steps.length(); i++) {
            TextView tvStep = new TextView(this);
            tvStep.setText((i + 1) + ". " + steps.getString(i));
            tvStep.setTextSize(14);
            tvStep.setTextColor(ContextCompat.getColor(this, R.color.label_2));
            tvStep.setLineSpacing(4, 1.2f);
            LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stepLp.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            tvStep.setLayoutParams(stepLp);
            content.addView(tvStep);
        }

        // 备注
        if (!note.isEmpty()) {
            TextView tvNote = new TextView(this);
            tvNote.setText("提示: " + note);
            tvNote.setTextSize(12);
            tvNote.setTextColor(ContextCompat.getColor(this, R.color.ios_orange));
            tvNote.setLineSpacing(2, 1.1f);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noteLp.setMargins(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
            tvNote.setLayoutParams(noteLp);
            content.addView(tvNote);
        }

        card.addView(content);
        guideContainer.addView(card);
    }

    private void showFallbackGuide() {
        guideContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(R.string.bugreport_fallback_guide);
        tv.setTextSize(14);
        tv.setTextColor(ContextCompat.getColor(this, R.color.label_2));
        tv.setLineSpacing(6, 1.2f);
        guideContainer.addView(tv);
    }

    private void setupListeners() {
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnSkip.setOnClickListener(v -> showSkipConfirmDialog());
        btnGoMain.setOnClickListener(v -> goToMainActivity());
    }

    private void requestPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_permission_title)
                .setMessage(R.string.bugreport_permission_message)
                .setPositiveButton(R.string.action_go_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.dialog_permission_cancel, null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // 允许选择 ZIP 和 txt 文件
        String[] mimeTypes = {"application/zip", "application/x-zip-compressed", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        filePickerLauncher.launch(intent);
    }

    private void startBugreportAnalysis(Uri uri) {
        uploadSection.setVisibility(View.GONE);
        analyzingSection.setVisibility(View.VISIBLE);
        progressAnalyzing.setIndeterminate(true);
        tvAnalyzingStatus.setText(R.string.bugreport_analyzing_start);

        executor.execute(() -> {
            try {
                BugreportParser parser = new BugreportParser(this);
                BugreportParser.ParsedResult parsed = parser.parse(uri);

                mainHandler.post(() -> {
                    tvAnalyzingStatus.setText(R.string.bugreport_analyzing_extracting);
                });

                // 进一步分析提取的数据
                BugreportAnalyzer analyzer = new BugreportAnalyzer();
                BugreportAnalyzer.AnalysisResult analysis = analyzer.analyze(parsed);

                // 保存分析结果到 SharedPreferences 供其他模块使用
                saveAnalysisResult(analysis);

                mainHandler.post(() -> {
                    progressAnalyzing.setIndeterminate(false);
                    progressAnalyzing.setProgress(100);
                    tvAnalyzingStatus.setText(getString(R.string.bugreport_analyze_complete,
                            analysis.batteryHealth > 0 ? analysis.batteryHealth + "%" : "--",
                            analysis.cycleCount >= 0 ? String.valueOf(analysis.cycleCount) : "--"));
                    btnGoMain.setVisibility(View.VISIBLE);
                    btnGoMain.setText(R.string.bugreport_enter_app);

                    Toast.makeText(this, R.string.bugreport_analyze_success, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "Bugreport analysis failed", e);
                mainHandler.post(() -> {
                    progressAnalyzing.setIndeterminate(false);
                    tvAnalyzingStatus.setText(R.string.bugreport_analyze_failed);
                    btnGoMain.setVisibility(View.VISIBLE);
                    btnGoMain.setText(R.string.bugreport_enter_app_skip);

                    new AlertDialog.Builder(this)
                            .setTitle(R.string.bugreport_analyze_failed_title)
                            .setMessage(getString(R.string.bugreport_analyze_failed_message, e.getMessage()))
                            .setPositiveButton(R.string.action_retry, (d, w) -> {
                                uploadSection.setVisibility(View.VISIBLE);
                                analyzingSection.setVisibility(View.GONE);
                                btnGoMain.setVisibility(View.GONE);
                            })
                            .setNegativeButton(R.string.action_skip, (d, w) -> goToMainActivity())
                            .show();
                });
            }
        });
    }

    private void saveAnalysisResult(BugreportAnalyzer.AnalysisResult result) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_BUGREPORT_ANALYZED, true);
        editor.putInt("bugreport_battery_health", result.batteryHealth);
        editor.putInt("bugreport_cycle_count", result.cycleCount);
        editor.putInt("bugreport_design_capacity", result.designCapacity);
        editor.putInt("bugreport_full_capacity", result.fullCapacity);
        editor.putFloat("bugreport_battery_voltage", (float) result.voltage);
        editor.putFloat("bugreport_battery_temp", (float) result.temperature);
        editor.putString("bugreport_battery_technology", result.technology);
        editor.putString("bugreport_charging_policy", result.chargingPolicy);
        editor.putLong("bugreport_analyzed_at", System.currentTimeMillis());
        editor.apply();
    }

    private void showSkipConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bugreport_skip_title)
                .setMessage(R.string.bugreport_skip_message)
                .setPositiveButton(R.string.action_skip_anyway, (dialog, which) -> {
                    markGuideShown(this);
                    goToMainActivity();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void goToMainActivity() {
        markGuideShown(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
