package com.batteryhealth.app.ui.bugreport;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.BugReportParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BugReportFragment extends Fragment {

    private static final String TAG = "BugReportFragment";

    private static final String PREFS_NAME = "battery_health_prefs";
    private static final String PREF_PREFIX = "bugreport_";

    // SharedPreferences keys
    private static final String KEY_DESIGN_CAPACITY = PREF_PREFIX + "design_capacity";
    private static final String KEY_CURRENT_CAPACITY = PREF_PREFIX + "current_capacity";
    private static final String KEY_CYCLE_COUNT = PREF_PREFIX + "cycle_count";
    private static final String KEY_MANUFACTURING_DATE = PREF_PREFIX + "manufacturing_date";
    private static final String KEY_BRAND = PREF_PREFIX + "brand";
    private static final String KEY_MODEL = PREF_PREFIX + "model";
    private static final String KEY_TEMPERATURE = PREF_PREFIX + "temperature";
    private static final String KEY_SCREEN_ON_TIME = PREF_PREFIX + "screen_on_time";
    private static final String KEY_CHARGE_COUNT = PREF_PREFIX + "charge_count";
    private static final String KEY_SN = PREF_PREFIX + "sn";
    private static final String KEY_HEALTH_PERCENT = PREF_PREFIX + "health_percent";

    // Views
    private MaterialButton btnSelectFile;
    private LinearLayout llProgress;
    private ProgressBar progressBar;
    private TextView tvProgressStatus;
    private TextView eyebrowResults;
    private MaterialCardView cardSummary;
    private TextView tvSummaryDesignCap;
    private TextView tvSummaryCurrentCap;
    private TextView tvSummaryHealth;
    private TextView tvSummaryCycle;
    private MaterialCardView cardResults;
    private LinearLayout llResults;
    private MaterialButton btnViewHealth;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean parsingCancelled = false;
    private Thread parsingThread;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFileSelected);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bug_report, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        btnSelectFile = view.findViewById(R.id.btn_select_file);
        llProgress = view.findViewById(R.id.ll_progress);
        progressBar = view.findViewById(R.id.progress_bar);
        tvProgressStatus = view.findViewById(R.id.tv_progress_status);
        eyebrowResults = view.findViewById(R.id.eyebrow_results);
        cardSummary = view.findViewById(R.id.card_summary);
        tvSummaryDesignCap = view.findViewById(R.id.tv_summary_design_cap);
        tvSummaryCurrentCap = view.findViewById(R.id.tv_summary_current_cap);
        tvSummaryHealth = view.findViewById(R.id.tv_summary_health);
        tvSummaryCycle = view.findViewById(R.id.tv_summary_cycle);
        cardResults = view.findViewById(R.id.card_results);
        llResults = view.findViewById(R.id.ll_results);
        btnViewHealth = view.findViewById(R.id.btn_view_health);

        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnViewHealth.setOnClickListener(v -> navigateToHealthPage());
    }

    private void openFilePicker() {
        try {
            filePickerLauncher.launch(new String[]{
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch file picker", e);
        }
    }

    private void onFileSelected(@Nullable Uri uri) {
        if (uri == null) {
            Log.d(TAG, "File picker cancelled");
            return;
        }

        Log.d(TAG, "File selected: " + uri);
        showProgress(true);
        updateProgress(10, "正在复制文件...");

        parsingCancelled = false;

        parsingThread = new Thread(() -> {
            try {
                // Copy file to cache directory (needed for ZipFile which requires a file path)
                File cachedFile = copyToCache(uri);
                if (cachedFile == null) {
                    mainHandler.post(() -> onParseFailed("无法读取文件"));
                    return;
                }

                if (parsingCancelled) return;

                updateProgress(40, "正在解析 BugReport...");

                BugReportParser parser = new BugReportParser();
                BugReportParser.BugReportData data = parser.parseFromZip(cachedFile.getAbsolutePath());

                if (parsingCancelled) return;

                updateProgress(80, "正在提取数据...");

                // Save to SharedPreferences
                saveToSharedPreferences(data);

                // Feed data to BatteryDataManager
                mainHandler.post(() -> feedToBatteryDataManager(data));

                updateProgress(100, "解析完成");

                if (parsingCancelled) return;

                mainHandler.post(() -> onParseComplete(data));

                // Clean up cached file
                cachedFile.delete();

            } catch (Exception e) {
                Log.e(TAG, "Error parsing bugreport", e);
                mainHandler.post(() -> onParseFailed("解析失败: " + e.getMessage()));
            }
        });

        parsingThread.start();
    }

    private File copyToCache(Uri uri) {
        try {
            Context ctx = requireContext();
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            File cacheDir = ctx.getCacheDir();
            File cachedFile = new File(cacheDir, "bugreport_" + System.currentTimeMillis() + ".zip");

            try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            } finally {
                is.close();
            }

            Log.d(TAG, "Cached bugreport file: " + cachedFile.getAbsolutePath()
                    + " size=" + cachedFile.length());
            return cachedFile;

        } catch (Exception e) {
            Log.e(TAG, "Failed to copy file to cache", e);
            return null;
        }
    }

    private void saveToSharedPreferences(BugReportParser.BugReportData data) {
        Context ctx = getContext();
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (data.designCapacityMah.isPresent()) {
            editor.putInt(KEY_DESIGN_CAPACITY, data.designCapacityMah.getAsInt());
        }
        if (data.currentCapacityMah.isPresent()) {
            editor.putInt(KEY_CURRENT_CAPACITY, data.currentCapacityMah.getAsInt());
        }
        if (data.cycleCount.isPresent()) {
            editor.putInt(KEY_CYCLE_COUNT, data.cycleCount.getAsInt());
        }
        if (data.manufacturingDate.isPresent()) {
            editor.putString(KEY_MANUFACTURING_DATE, data.manufacturingDate.get());
        }
        if (data.brand.isPresent()) {
            editor.putString(KEY_BRAND, data.brand.get());
        }
        if (data.model.isPresent()) {
            editor.putString(KEY_MODEL, data.model.get());
        }
        if (data.temperatureCelsius.isPresent()) {
            editor.putFloat(KEY_TEMPERATURE, (float) data.temperatureCelsius.getAsDouble());
        }
        if (data.screenOnTimeHours.isPresent()) {
            editor.putInt(KEY_SCREEN_ON_TIME, data.screenOnTimeHours.getAsInt());
        }
        if (data.chargeCount.isPresent()) {
            editor.putInt(KEY_CHARGE_COUNT, data.chargeCount.getAsInt());
        }
        if (data.sn.isPresent()) {
            editor.putString(KEY_SN, data.sn.get());
        }

        // Calculate and save health percent
        if (data.designCapacityMah.isPresent() && data.currentCapacityMah.isPresent()) {
            int design = data.designCapacityMah.getAsInt();
            int current = data.currentCapacityMah.getAsInt();
            if (design > 0) {
                float health = Math.min(100f, (current / (float) design) * 100f);
                editor.putFloat(KEY_HEALTH_PERCENT, health);
            }
        }

        editor.apply();
        Log.d(TAG, "BugReport data saved to SharedPreferences, "
                + data.getAvailableDataCount() + " fields extracted");
    }

    private void feedToBatteryDataManager(BugReportParser.BugReportData data) {
        if (!isAdded()) return;
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        BatteryDataManager bdm = activity.getBatteryDataManager();
        if (bdm == null) return;

        // Save design capacity as calibrated capacity if not already set
        if (data.designCapacityMah.isPresent()) {
            Context ctx = getContext();
            if (ctx == null) return;
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int existing = prefs.getInt(BatteryDataManager.PREF_CALIBRATED_CAPACITY, -1);
            if (existing <= 0) {
                prefs.edit()
                        .putInt(BatteryDataManager.PREF_CALIBRATED_CAPACITY,
                                data.designCapacityMah.getAsInt())
                        .apply();
                Log.d(TAG, "Saved bugreport design capacity as calibrated capacity: "
                        + data.designCapacityMah.getAsInt());
            }
        }

        // Refresh data so other fragments pick up the changes
        bdm.refreshAllDataAsync();
        Log.d(TAG, "Fed bugreport data to BatteryDataManager");
    }

    private void onParseComplete(BugReportParser.BugReportData data) {
        showProgress(false);

        // Show results sections
        eyebrowResults.setVisibility(View.VISIBLE);
        cardSummary.setVisibility(View.VISIBLE);
        cardResults.setVisibility(View.VISIBLE);
        btnViewHealth.setVisibility(View.VISIBLE);

        // Populate summary card
        populateSummary(data);

        // Populate detailed results
        populateDetailedResults(data);
    }

    private void onParseFailed(String message) {
        showProgress(false);
        Log.e(TAG, "Parse failed: " + message);
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), message,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void populateSummary(BugReportParser.BugReportData data) {
        // Design capacity
        if (data.designCapacityMah.isPresent()) {
            tvSummaryDesignCap.setText(data.designCapacityMah.getAsInt() + " mAh");
        } else {
            tvSummaryDesignCap.setText("--");
        }

        // Current capacity
        if (data.currentCapacityMah.isPresent()) {
            tvSummaryCurrentCap.setText(data.currentCapacityMah.getAsInt() + " mAh");
        } else {
            tvSummaryCurrentCap.setText("--");
        }

        // Health %
        if (data.designCapacityMah.isPresent() && data.currentCapacityMah.isPresent()) {
            int design = data.designCapacityMah.getAsInt();
            int current = data.currentCapacityMah.getAsInt();
            if (design > 0) {
                float health = Math.min(100f, (current / (float) design) * 100f);
                tvSummaryHealth.setText(String.format(Locale.getDefault(), "%.0f%%", health));
                // Color based on health
                if (health >= 85) {
                    tvSummaryHealth.setTextColor(
                            getResources().getColor(R.color.green, null));
                } else if (health >= 70) {
                    tvSummaryHealth.setTextColor(
                            getResources().getColor(R.color.orange, null));
                } else {
                    tvSummaryHealth.setTextColor(
                            getResources().getColor(R.color.red, null));
                }
            }
        } else {
            tvSummaryHealth.setText("--");
        }

        // Cycle count
        if (data.cycleCount.isPresent()) {
            tvSummaryCycle.setText(String.valueOf(data.cycleCount.getAsInt()));
        } else {
            tvSummaryCycle.setText("--");
        }
    }

    private void populateDetailedResults(BugReportParser.BugReportData data) {
        if (!isAdded() || getContext() == null) return;
        llResults.removeAllViews();

        List<ResultRow> rows = new ArrayList<>();
        rows.add(new ResultRow("品牌", data.brand.orElse(null)));
        rows.add(new ResultRow("型号", data.model.orElse(null)));
        rows.add(new ResultRow("序列号", data.sn.orElse(null)));
        rows.add(new ResultRow("设计容量",
                data.designCapacityMah.isPresent()
                        ? data.designCapacityMah.getAsInt() + " mAh" : null));
        rows.add(new ResultRow("当前容量",
                data.currentCapacityMah.isPresent()
                        ? data.currentCapacityMah.getAsInt() + " mAh" : null));
        rows.add(new ResultRow("循环次数",
                data.cycleCount.isPresent()
                        ? String.valueOf(data.cycleCount.getAsInt()) : null));
        rows.add(new ResultRow("制造日期", data.manufacturingDate.orElse(null)));
        rows.add(new ResultRow("温度",
                data.temperatureCelsius.isPresent()
                        ? String.format(Locale.getDefault(), "%.1f°C",
                        data.temperatureCelsius.getAsDouble()) : null));
        rows.add(new ResultRow("屏幕开启时间",
                data.screenOnTimeHours.isPresent()
                        ? data.screenOnTimeHours.getAsInt() + " h" : null));
        rows.add(new ResultRow("充电次数",
                data.chargeCount.isPresent()
                        ? String.valueOf(data.chargeCount.getAsInt()) : null));

        int colorGreen = getResources().getColor(R.color.green, null);
        int colorGray = getResources().getColor(R.color.label_3, null);

        for (int i = 0; i < rows.size(); i++) {
            ResultRow row = rows.get(i);
            View rowView = createResultRow(row, colorGreen, colorGray);
            llResults.addView(rowView);

            // Add separator between rows (not after the last one)
            if (i < rows.size() - 1) {
                View separator = new View(requireContext());
                separator.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundResource(R.color.separator);
                float density = getResources().getDisplayMetrics().density;
                ((LinearLayout.LayoutParams) separator.getLayoutParams())
                        .setMarginStart((int) (18 * density));
                llResults.addView(separator);
            }
        }
    }

    private View createResultRow(ResultRow row, int colorGreen, int colorGray) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pxH = (int) (18 * getResources().getDisplayMetrics().density);
        int pxV = (int) (13 * getResources().getDisplayMetrics().density);
        container.setPadding(pxH, pxV, pxH, pxV);
        container.setMinimumHeight((int) (48 * getResources().getDisplayMetrics().density));

        // Label
        TextView label = new TextView(requireContext());
        label.setText(row.label);
        label.setTextSize(15.5f);
        label.setTextColor(getResources().getColor(R.color.label_2, null));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelParams);
        container.addView(label);

        // Value
        TextView value = new TextView(requireContext());
        boolean found = row.value != null;
        value.setText(found ? row.value : "未找到");
        value.setTextSize(15.5f);
        value.setTextColor(found ? colorGreen : colorGray);
        if (found) {
            value.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        value.setLayoutParams(valueParams);
        container.addView(value);

        return container;
    }

    private void showProgress(boolean show) {
        llProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSelectFile.setEnabled(!show);
    }

    private void updateProgress(int percent, String status) {
        mainHandler.post(() -> {
            progressBar.setProgress(percent);
            tvProgressStatus.setText(status);
        });
    }

    private void navigateToHealthPage() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            // Navigate to the BatteryHealthFragment (position 0 in ViewPager)
            androidx.viewpager2.widget.ViewPager2 viewPager =
                    activity.findViewById(R.id.view_pager);
            if (viewPager != null) {
                viewPager.setCurrentItem(0, true);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        parsingCancelled = true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        parsingCancelled = true;
        if (parsingThread != null && parsingThread.isAlive()) {
            parsingThread.interrupt();
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static class ResultRow {
        final String label;
        final String value;

        ResultRow(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}
