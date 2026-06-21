package com.batteryhealth.app.ui.error;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.batteryhealth.app.BuildConfig;
import com.batteryhealth.app.MainActivity;
import com.batteryhealth.app.R;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 全局错误兜底页
 * 用于展示未捕获异常或 Fragment/页面加载失败的详情，并提供重启入口。
 */
public class ErrorActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "error_title";
    public static final String EXTRA_MESSAGE = "error_message";
    public static final String EXTRA_THROWABLE = "error_throwable";

    public static Intent createIntent(Context context, String title, String message, Throwable throwable) {
        Intent intent = new Intent(context, ErrorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message);
        if (throwable != null) intent.putExtra(EXTRA_THROWABLE, throwableToString(throwable));
        return intent;
    }

    private static String throwableToString(Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return throwable.getClass().getName() + ": " + throwable.getMessage();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        String throwableStr = getIntent().getStringExtra(EXTRA_THROWABLE);

        TextView tvTitle = findViewById(R.id.tv_error_title);
        TextView tvMessage = findViewById(R.id.tv_error_message);
        TextView tvDetails = findViewById(R.id.tv_error_details);
        ScrollView scrollDetails = findViewById(R.id.scroll_error_details);
        Button btnRestart = findViewById(R.id.btn_error_restart);
        Button btnDetails = findViewById(R.id.btn_error_details);

        if (tvTitle != null) tvTitle.setText(title != null ? title : getString(R.string.error_title));
        if (tvMessage != null) tvMessage.setText(message != null ? message : getString(R.string.error_unknown));

        String details = buildDetails(throwableStr);
        if (tvDetails != null) tvDetails.setText(details);

        if (btnDetails != null && scrollDetails != null) {
            btnDetails.setOnClickListener(v -> {
                if (scrollDetails.getVisibility() == ScrollView.GONE) {
                    scrollDetails.setVisibility(ScrollView.VISIBLE);
                    btnDetails.setText(R.string.error_hide_details);
                } else {
                    scrollDetails.setVisibility(ScrollView.GONE);
                    btnDetails.setText(R.string.error_view_details);
                }
            });
        }

        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private String buildDetails(String throwableStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("App Version: ").append(BuildConfig.VERSION_NAME).append("\n");
        sb.append("Version Code: ").append(BuildConfig.VERSION_CODE).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Board: ").append(Build.BOARD).append("\n");
        sb.append("\n");

        if (throwableStr != null && !throwableStr.isEmpty()) {
            sb.append(throwableStr);
        } else {
            sb.append("No stack trace available.");
        }
        return sb.toString();
    }
}
