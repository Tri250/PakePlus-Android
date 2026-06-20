package com.batteryhealth.app.ui.bugreport;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
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
import com.batteryhealth.app.utils.BugreportParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Bugreport 本地分析页面
 * 支持从系统文件选择器选择 bugreport ZIP/TXT 文件，仅进行本地解析（已移除上传功能）。
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
    private View btnLocalParse;

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
            getSupportActionBar().setTitle(R.string.title_bugreport_upload);
        }

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
        btnLocalParse = findViewById(R.id.btn_local_parse);
    }

    private void setupListeners() {
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        if (btnLocalParse != null) {
            btnLocalParse.setOnClickListener(v -> {
                if (selectedUri == null) {
                    Toast.makeText(this, R.string.please_select_bugreport, Toast.LENGTH_SHORT).show();
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
        return result != null ? result : getString(R.string.unknown_file);
    }

    private void parseLocal() {
        selectedFile = uriToFile(selectedUri);
        if (selectedFile == null) {
            Toast.makeText(this, R.string.file_read_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        progressUpload.setVisibility(View.VISIBLE);
        btnSelectFile.setEnabled(false);
        if (btnLocalParse != null) btnLocalParse.setEnabled(false);

        new Thread(() -> {
            try {
                BatteryHealthReport report = BugreportParser.parse(selectedFile);
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnSelectFile.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    if (report != null) {
                        showReport(report);
                        Toast.makeText(this, R.string.local_parse_done, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.local_parse_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "本地解析异常", e);
                runOnUiThread(() -> {
                    progressUpload.setVisibility(View.GONE);
                    btnSelectFile.setEnabled(true);
                    if (btnLocalParse != null) btnLocalParse.setEnabled(true);
                    Toast.makeText(this, getString(R.string.local_parse_error, e.getMessage()), Toast.LENGTH_LONG).show();
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

    private void showReport(BatteryHealthReport report) {
        if (report == null) {
            Toast.makeText(this, R.string.analysis_result_empty, Toast.LENGTH_LONG).show();
            return;
        }
        cardResult.setVisibility(View.VISIBLE);

        tvResultHealth.setText(String.format(Locale.getDefault(), "%s：%.1f%% (%s)",
                getString(R.string.label_health), report.getBatteryHealthPercentage(), report.getBatteryHealthLevel()));
        tvResultCapacity.setText(String.format(Locale.getDefault(), "%s：%d / %d mAh",
                getString(R.string.label_capacity), report.getCurrentCapacityMah(), report.getDesignCapacityMah()));
        tvResultCycle.setText(String.format(Locale.getDefault(), "%s：%d %s",
                getString(R.string.label_cycle_count), report.getCycleCount(), getString(R.string.unit_times)));
        tvResultSource.setText(String.format(Locale.getDefault(), "%s：%s",
                getString(R.string.label_battery_source), report.getBatterySource() != null ? report.getBatterySource() : getString(R.string.unknown)));
        tvResultTemp.setText(String.format(Locale.getDefault(), "%s：%.1f°C",
                getString(R.string.label_temperature), report.getTemperatureNowCelsius()));

        layoutRecommendations.removeAllViews();
        List<BatteryHealthReport.Recommendation> recommendations = report.getRecommendations();
        if (recommendations == null || recommendations.isEmpty()) {
            addText(layoutRecommendations, getString(R.string.no_recommendations), true);
        } else {
            for (BatteryHealthReport.Recommendation rec : recommendations) {
                String text = (rec.getTitle() != null ? rec.getTitle() + "\n" : "")
                        + (rec.getContent() != null ? rec.getContent() : "");
                addText(layoutRecommendations, text, false);
            }
        }

        layoutAppConsumption.removeAllViews();
        List<BatteryHealthReport.AppConsumption> apps = report.getAppConsumption();
        if (apps == null || apps.isEmpty()) {
            addText(layoutAppConsumption, getString(R.string.no_power_data), true);
        } else {
            for (BatteryHealthReport.AppConsumption app : apps) {
                String name = app.getAppName() != null ? app.getAppName() : app.getPackageName();
                addText(layoutAppConsumption, String.format(Locale.getDefault(), "%s：%.1f%%（%d %s）",
                        name != null ? name : getString(R.string.unknown), app.getConsumptionPercent(), app.getUsageMinutes(), getString(R.string.unit_minutes)), false);
            }
        }
    }

    private void addText(LinearLayout parent, String text, boolean secondary) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(ContextCompat.getColor(this, secondary ? R.color.text_secondary : R.color.text_primary));
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
