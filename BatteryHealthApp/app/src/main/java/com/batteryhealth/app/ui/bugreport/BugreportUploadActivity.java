package com.batteryhealth.app.ui.bugreport;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.batteryhealth.app.R;
import com.batteryhealth.app.data.model.BatteryHealthReport;
import com.batteryhealth.app.data.model.BugreportUploadResponse;
import com.batteryhealth.app.data.repository.BugreportRepository;
import com.batteryhealth.app.utils.BugreportParser;
import com.batteryhealth.app.utils.NetworkConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Bugreport 上传与分析页面
 * 支持从系统文件选择器选择 bugreport ZIP/TXT 文件，先进行本地解析预览，再上传到后端深度分析。
 */
public class BugreportUploadActivity extends AppCompatActivity {
    private static final String TAG = "BugreportUploadActivity";

    private TextView tvSelectedFile;
    private TextView tvResultHealth;
    private TextView tvResultCapacity;
    private TextView tvResultCycle;
    private TextView tvResultSource;
    private TextView tvResultTemp;
    private LinearLayout layoutRecommendations;
    private LinearLayout layoutAppConsumption;
    private View cardResult;
    private ProgressBar progressUpload;
    private View btnSelectFile;
    private View btnUpload;
    private View btnLocalParse;

    private BugreportRepository repository;
    private Uri selectedUri;
    private File selectedFile;
    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            result -> {
                if (result != null) {
                    selectedUri = result;
                    tvSelectedFile.setText(getFileName(selectedUri));
                    selectedFile = null;
                    cardResult.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bugreport_upload);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bugreport 分析");
        }

        repository = new BugreportRepository(this);
        bindViews();
        setupListeners();
    }

    private void bindViews() {
        tvSelectedFile = findViewById(R.id.tv_selected_file);
        tvResultHealth = findViewById(R.id.tv_result_health);
        tvResultCapacity = findViewById(R.id.tv_result_capacity);
        tvResultCycle = findViewById(R.id.tv_result_cycle);
        tvResultSource = findViewById(R.id.tv_result_source);
        tvResultTemp = findViewById(R.id.tv_result_temp);
        layoutRecommendations = findViewById(R.id.layout_recommendations);
        layoutAppConsumption = findViewById(R.id.layout_app_consumption);
        cardResult = findViewById(R.id.card_result);
        progressUpload = findViewById(R.id.progress_upload);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnUpload = findViewById(R.id.btn_upload);
        btnLocalParse = findViewById(R.id.btn_local_parse);
    }

    private void setupListeners() {
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnUpload.setOnClickListener(v -> {
            if (selectedUri == null) {
                Toast.makeText(this, "请先选择 bugreport 文件", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!NetworkConfig.isBaseUrlConfigured(this)) {
                Toast.makeText(this, "后端服务地址未配置，请先前往配置页面设置", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "网络不可用，请连接网络后上传，或尝试本地上传解析", Toast.LENGTH_LONG).show();
                return;
            }
            uploadFile();
        });
        if (btnLocalParse != null) {
            btnLocalParse.setOnClickListener(v -> {
                if (selectedUri == null) {
                    Toast.makeText(this, "请先选择 bugreport 文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                parseLocal();
            });
        }
    }

    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/gzip",
                "application/x-gzip",
                "text/plain",
                "application/octet-stream"
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {
                Log.e(TAG, "get file name failed", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "未知文件";
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态失败", e);
            return false;
        }
    }

    private void uploadFile() {
        selectedFile = uriToFile(selectedUri);
        if (selectedFile == null) {
            Toast.makeText(this, "文件读取失败", Toast.LENGTH_SHORT).show();
            return;
        }

        progressUpload.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);
        if (btnLocalParse != null) btnLocalParse.setEnabled(false);

        String brand = Build.BRAND != null ? Build.BRAND : "";
        String model = Build.MODEL != null ? Build.MODEL : "";

        repository.uploadBugreport(selectedFile, brand, model, new Callback<BugreportUploadResponse>() {
            @Override
            public void onResponse(Call<BugreportUploadResponse> call, Response<BugreportUploadResponse> response) {
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        showResult(response.body());
                    } else {
                        Toast.makeText(BugreportUploadActivity.this,
                                "分析失败：" + response.code() + "，可尝试本地解析", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<BugreportUploadResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    Toast.makeText(BugreportUploadActivity.this,
                            "上传失败：" + t.getMessage() + "，可尝试本地解析", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void parseLocal() {
        selectedFile = uriToFile(selectedUri);
        if (selectedFile == null) {
            Toast.makeText(this, "文件读取失败", Toast.LENGTH_SHORT).show();
            return;
        }

        progressUpload.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);
        if (btnLocalParse != null) btnLocalParse.setEnabled(false);

        new Thread(() -> {
            try {
                BatteryHealthReport report = BugreportParser.parse(selectedFile);
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    if (report != null) {
                        showReport(report);
                        Toast.makeText(this, "本地解析完成", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "本地解析失败，未识别有效电池数据", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "本地解析异常", e);
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    Toast.makeText(this, "本地解析异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private File uriToFile(Uri uri) {
        if (uri == null) return null;
        try {
            ContentResolver resolver = getContentResolver();
            String name = getFileName(uri);
            File outFile = new File(getCacheDir(), name);
            try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
                 FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
            return outFile;
        } catch (Exception e) {
            Log.e(TAG, "uri to file failed", e);
            return null;
        }
    }

    private void showResult(BugreportUploadResponse response) {
        if (!response.isSuccess() || response.getData() == null) {
            Toast.makeText(this, response.getMessage() != null ? response.getMessage() : "分析结果为空", Toast.LENGTH_LONG).show();
            return;
        }
        showReport(response.getData());
    }

    private void showReport(BatteryHealthReport report) {
        if (report == null) {
            Toast.makeText(this, "分析结果为空", Toast.LENGTH_LONG).show();
            return;
        }
        cardResult.setVisibility(View.VISIBLE);

        tvResultHealth.setText(String.format(Locale.getDefault(), "健康度：%.1f%% (%s)",
                report.getBatteryHealthPercentage(), report.getBatteryHealthLevel()));
        tvResultCapacity.setText(String.format(Locale.getDefault(), "容量：%d / %d mAh",
                report.getCurrentCapacityMah(), report.getDesignCapacityMah()));
        tvResultCycle.setText(String.format(Locale.getDefault(), "循环次数：%d 次", report.getCycleCount()));
        tvResultSource.setText(String.format(Locale.getDefault(), "电池来源：%s",
                report.getBatterySource() != null ? report.getBatterySource() : "未知"));
        tvResultTemp.setText(String.format(Locale.getDefault(), "当前温度：%.1f°C", report.getTemperatureNowCelsius()));

        layoutRecommendations.removeAllViews();
        List<BatteryHealthReport.Recommendation> recommendations = report.getRecommendations();
        if (recommendations.isEmpty()) {
            addText(layoutRecommendations, "暂无建议", true);
        } else {
            for (BatteryHealthReport.Recommendation rec : recommendations) {
                String text = (rec.getTitle() != null ? rec.getTitle() + "\n" : "")
                        + (rec.getContent() != null ? rec.getContent() : "");
                addText(layoutRecommendations, text, false);
            }
        }

        layoutAppConsumption.removeAllViews();
        List<BatteryHealthReport.AppConsumption> apps = report.getAppConsumption();
        if (apps.isEmpty()) {
            addText(layoutAppConsumption, "暂无耗电数据", true);
        } else {
            for (BatteryHealthReport.AppConsumption app : apps) {
                String name = app.getAppName() != null ? app.getAppName() : app.getPackageName();
                addText(layoutAppConsumption, String.format(Locale.getDefault(), "%s：%.1f%%（%d 分钟）",
                        name != null ? name : "未知", app.getConsumptionPercent(), app.getUsageMinutes()), false);
            }
        }
    }

    private void addText(LinearLayout parent, String text, boolean secondary) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(ContextCompat.getColor(this, secondary ? R.color.ios_secondary_label : R.color.ios_label));
        tv.setLineSpacing(0, 1.2f);
        tv.setPadding(0, 8, 0, 8);
        parent.addView(tv);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
