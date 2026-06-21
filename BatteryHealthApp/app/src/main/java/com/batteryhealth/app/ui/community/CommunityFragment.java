package com.batteryhealth.app.ui.community;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.UiAnimationHelper;

public class CommunityFragment extends Fragment {
    private static final String TAG = "CommunityFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached, returning empty view");
            return new View(requireContext());
        }
        try {
            return inflater.inflate(R.layout.fragment_community, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private View createErrorView(Exception e) {
        Context context = getContext();
        if (context == null) {
            context = requireContext();
        }
        TextView errorView = new TextView(context);
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "Unknown error");
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(context, R.color.ios_label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(context, R.color.ios_background));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "Fragment not attached in onViewCreated, skipping animation");
            return;
        }
        try {
            UiAnimationHelper.animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage(), e);
        }
    }
}
