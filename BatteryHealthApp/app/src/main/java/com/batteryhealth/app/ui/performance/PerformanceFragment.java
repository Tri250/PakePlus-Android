package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.batteryhealth.app.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 性能分析Fragment
 */
public class PerformanceFragment extends Fragment {
    
    private static final String TAG = "PerformanceFragment";
    
    private TextView tvCpuUsage;
    private TextView tvMemoryUsage;
    private ProgressBar progressCpu;
    private ProgressBar progressMemory;
    
    private Handler handler;
    private Runnable updateTask;
    private boolean isRunning = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_performance, container, false);
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            View errorView = new View(requireContext());
            return errorView;
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
            tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
            progressCpu = view.findViewById(R.id.progress_cpu);
            progressMemory = view.findViewById(R.id.progress_memory);
            
            // 设置默认值
            setDefaultValues();
            
            handler = new Handler(Looper.getMainLooper());
            isRunning = true;
            
            updateTask = new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;
                    updatePerformanceData();
                    if (handler != null) {
                        handler.postDelayed(this, 2000);
                    }
                }
            };
            
            handler.post(updateTask);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }
    
    private void setDefaultValues() {
        if (tvCpuUsage != null) tvCpuUsage.setText("0%");
        if (tvMemoryUsage != null) tvMemoryUsage.setText("0%");
        if (progressCpu != null) progressCpu.setProgress(0);
        if (progressMemory != null) progressMemory.setProgress(0);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRunning = false;
        if (handler != null) {
            handler.removeCallbacks(updateTask);
        }
    }
    
    private void updatePerformanceData() {
        try {
            // 获取CPU使用率
            float cpuUsage = readCpuUsage();
            if (tvCpuUsage != null) {
                tvCpuUsage.setText(String.format("%.1f%%", cpuUsage));
            }
            if (progressCpu != null) {
                progressCpu.setProgress((int) Math.min(cpuUsage, 100));
            }
            
            // 获取内存使用率
            float memoryUsage = readMemoryUsage();
            if (tvMemoryUsage != null) {
                tvMemoryUsage.setText(String.format("%.1f%%", memoryUsage));
            }
            if (progressMemory != null) {
                progressMemory.setProgress((int) Math.min(memoryUsage, 100));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating performance data: " + e.getMessage());
        }
    }
    
    private float readCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                
                long total = user + nice + system + idle;
                long used = user + nice + system;
                
                return (used * 100.0f) / total;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    private float readMemoryUsage() {
        try {
            if (getContext() == null) return 0;
            
            ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return 0;
            
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            long totalMemory = memoryInfo.totalMem;
            long availableMemory = memoryInfo.availMem;
            long usedMemory = totalMemory - availableMemory;
            
            if (totalMemory <= 0) return 0;
            
            return (usedMemory * 100.0f) / totalMemory;
        } catch (Exception e) {
            Log.e(TAG, "Error reading memory usage: " + e.getMessage());
        }
        return 0;
    }
}