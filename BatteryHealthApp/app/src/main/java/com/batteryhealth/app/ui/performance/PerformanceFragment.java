package com.batteryhealth.app.ui.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    
    private TextView tvCpuUsage;
    private TextView tvMemoryUsage;
    private ProgressBar progressCpu;
    private ProgressBar progressMemory;
    
    private Handler handler;
    private Runnable updateTask;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_performance, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
        tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
        progressCpu = view.findViewById(R.id.progress_cpu);
        progressMemory = view.findViewById(R.id.progress_memory);
        
        handler = new Handler(Looper.getMainLooper());
        
        updateTask = new Runnable() {
            @Override
            public void run() {
                updatePerformanceData();
                handler.postDelayed(this, 2000);
            }
        };
        
        handler.post(updateTask);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(updateTask);
    }
    
    private void updatePerformanceData() {
        // 获取CPU使用率
        float cpuUsage = readCpuUsage();
        tvCpuUsage.setText(String.format("%.1f%%", cpuUsage));
        progressCpu.setProgress((int) cpuUsage);
        
        // 获取内存使用率
        float memoryUsage = readMemoryUsage();
        tvMemoryUsage.setText(String.format("%.1f%%", memoryUsage));
        progressMemory.setProgress((int) memoryUsage);
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
            ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            long totalMemory = memoryInfo.totalMem;
            long availableMemory = memoryInfo.availMem;
            long usedMemory = totalMemory - availableMemory;
            
            return (usedMemory * 100.0f) / totalMemory;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}