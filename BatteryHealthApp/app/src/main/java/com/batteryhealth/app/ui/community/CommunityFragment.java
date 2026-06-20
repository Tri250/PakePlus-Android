package com.batteryhealth.app.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;

/**
 * 电池江湖社区 Fragment
 * 展示社区入口与电池养护小贴士。
 */
public class CommunityFragment extends Fragment {
    private static final String TAG = "CommunityFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_community, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        TextView errorView = new TextView(requireContext());
        String message = "界面加载失败\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            View btnShareExperience = view.findViewById(R.id.btn_share_experience);
            if (btnShareExperience != null) {
                btnShareExperience.setOnClickListener(v -> shareBatteryTips());
            }
            View btnShareCareTips = view.findViewById(R.id.btn_share_care_tips);
            if (btnShareCareTips != null) {
                btnShareCareTips.setOnClickListener(v -> shareBatteryTips());
            }
            animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    private void shareBatteryTips() {
        try {
            String shareText = "电池养护小贴士：\n"
                    + "1. 避免过度充电，电量保持 20%-80%；\n"
                    + "2. 避免深度放电，低于 10% 会加速老化；\n"
                    + "3. 避免高温环境，充电时保持通风；\n"
                    + "4. 使用原装充电器，兼容快充更安全高效。\n\n"
                    + "来自「电池健康」应用";
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(intent, "分享电池养护贴士"));
        } catch (Exception e) {
            Log.e(TAG, "分享失败", e);
            Toast.makeText(requireContext(), "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void animateCardsEntry(View view) {
        try {
            if (!(view instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup root = (android.view.ViewGroup) view;
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getId() == R.id.view_pager) continue;
                child.setAlpha(0f);
                child.setTranslationY(60f);
                child.setScaleX(0.94f);
                child.setScaleY(0.94f);
                child.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(650)
                        .setStartDelay(i * 100L)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                        .start();
            }
        } catch (Exception e) {
            Log.d(TAG, "Liquid glass card animation skipped: " + e.getMessage());
        }
    }
}
