package com.batteryhealth.app.ui.performance;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.os.Looper;
import android.os.Process;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.batteryhealth.app.R;
import com.batteryhealth.app.ui.viewmodel.PerformanceViewModel;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.FragmentErrorViewHelper;
import com.batteryhealth.app.utils.PerformanceAnalyzer;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.batteryhealth.app.utils.UiAnimationHelper;

import java.util.Locale;

public class PerformanceFragment extends Fragment {

    private static final String TAG = "PerformanceFragment";

    private TextView tvCpuUsage, tvMemoryUsage, tvPerformanceScore, tvStorageUsage;
    private ProgressBar progressCpu, progressMemory, progressScore, progressStorage;
    private TextView tvAppCpu, tvAppMemory, tvRuntime, tvForegroundService;
    private TextView tvGpuRenderer, tvOpenglVersion, tvVulkanVersion;
    private TextView tvAnrCount, tvAnrSeverity, tvAnrMessage, tvPerformanceTips;
    private TextView tvPerformanceGrade;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private DeviceInfoManager deviceInfoManager;

    // FPS 监控
    private Choreographer.FrameCallback fpsFrameCallback;
    private long lastFrameTimeNanos = 0;
    private int frameCount = 0;
    private float currentFps = 0;
    private long fpsCalculationStartNanos = 0;

    // 应用启动时间
    private final long appStartTimeMs = System.currentTimeMillis();

    private PerformanceViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_performance, container, false);
            initViews(view);
            initViewModel();
            animateEntry(view);
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view", e);
            Context ctx = getContext();
            if (ctx == null && container != null) ctx = container.getContext();
            return FragmentErrorViewHelper.createErrorView(ctx, e);
        }
    }

    private void initViews(View view) {
        tvCpuUsage = view.findViewById(R.id.tv_cpu_usage);
        tvMemoryUsage = view.findViewById(R.id.tv_memory_usage);
        tvPerformanceScore = view.findViewById(R.id.tv_performance_score);
        tvStorageUsage = view.findViewById(R.id.tv_storage_usage);
        progressCpu = view.findViewById(R.id.progress_cpu);
        progressMemory = view.findViewById(R.id.progress_memory);
        progressScore = view.findViewById(R.id.progress_score);
        progressStorage = view.findViewById(R.id.progress_storage);

        tvAppCpu = view.findViewById(R.id.tv_app_cpu);
        tvAppMemory = view.findViewById(R.id.tv_app_memory);
        tvRuntime = view.findViewById(R.id.tv_runtime);
        tvForegroundService = view.findViewById(R.id.tv_foreground_service);

        tvGpuRenderer = view.findViewById(R.id.tv_gpu_renderer);
        tvOpenglVersion = view.findViewById(R.id.tv_opengl_version);
        tvVulkanVersion = view.findViewById(R.id.tv_vulkan_version);

        tvAnrCount = view.findViewById(R.id.tv_anr_count);
        tvAnrSeverity = view.findViewById(R.id.tv_anr_severity);
        tvAnrMessage = view.findViewById(R.id.tv_anr_message);
        tvPerformanceTips = view.findViewById(R.id.tv_performance_tips);

        // 性能评分等级（复用 tvPerformanceScore 所在行的左侧空间）
        tvPerformanceGrade = new TextView(requireContext());
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(PerformanceViewModel.class);

        // 系统级指标
        viewModel.getCpuUsage().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Integer>() {
            @Override
            public void onChanged(Integer cpu) {
                if (cpu != null && cpu >= 0) {
                    tvCpuUsage.setText(String.format(Locale.getDefault(), "%d%%", cpu));
                    UiAnimationHelper.animateProgressBar(progressCpu, cpu);
                }
            }
        });

        viewModel.getMemoryUsage().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Integer>() {
            @Override
            public void onChanged(Integer memory) {
                if (memory != null && memory >= 0) {
                    tvMemoryUsage.setText(String.format(Locale.getDefault(), "%d%%", memory));
                    UiAnimationHelper.animateProgressBar(progressMemory, memory);
                }
            }
        });

        viewModel.getStorageUsage().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Integer>() {
            @Override
            public void onChanged(Integer storage) {
                if (storage != null && storage >= 0) {
                    tvStorageUsage.setText(String.format(Locale.getDefault(), "%d%%", storage));
                    UiAnimationHelper.animateProgressBar(progressStorage, storage);
                }
            }
        });

        // 多维加权性能评分
        viewModel.getPerformanceScore().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<PerformanceAnalyzer.PerformanceScoreResult>() {
            @Override
            public void onChanged(PerformanceAnalyzer.PerformanceScoreResult scoreResult) {
                if (scoreResult != null) {
                    tvPerformanceScore.setText(String.format(Locale.getDefault(), "%d %s",
                            scoreResult.totalScore, scoreResult.grade));
                    UiAnimationHelper.animateProgressBar(progressScore, scoreResult.totalScore);
                }
            }
        });

        // 应用级指标
        viewModel.getAppCpuUsage().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Float>() {
            @Override
            public void onChanged(Float appCpu) {
                if (appCpu != null) {
                    tvAppCpu.setText(String.format(Locale.getDefault(), "%.1f%%", appCpu));
                }
            }
        });

        viewModel.getAppMemoryUsage().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Long>() {
            @Override
            public void onChanged(Long appMem) {
                if (appMem != null) {
                    tvAppMemory.setText(formatSize(appMem));
                }
            }
        });

        viewModel.getForegroundServiceRunning().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean running) {
                if (running != null) {
                    tvForegroundService.setText(running
                            ? getString(R.string.status_running)
                            : getString(R.string.status_not_running));
                }
            }
        });

        // ANR 分析
        viewModel.getAnrResult().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<PerformanceAnalyzer.AnrAnalysisResult>() {
            @Override
            public void onChanged(PerformanceAnalyzer.AnrAnalysisResult anrResult) {
                if (anrResult != null) {
                    tvAnrCount.setText(String.valueOf(anrResult.ourAppAnrs));
                    tvAnrSeverity.setText(anrResult.severity);
                    tvAnrMessage.setText(anrResult.message);
                }
            }
        });

        // 动态性能建议
        viewModel.getPerformanceSuggestions().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<List<String>>() {
            @Override
            public void onChanged(List<String> suggestions) {
                if (suggestions != null && !suggestions.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    for (String tip : suggestions) {
                        builder.append(tip).append("\n");
                    }
                    tvPerformanceTips.setText(builder.toString().trim());
                }
            }
        });
    }

    private void animateEntry(View view) {
        Animation fadeUp = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_up);
        view.startAnimation(fadeUp);
    }

    @Override
    public void onResume() {
        super.onResume();
        deviceInfoManager = new DeviceInfoManager(requireContext());
        startPeriodicUpdate();
        loadGpuInfo();
        startFpsMonitor();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicUpdate();
        stopFpsMonitor();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理 Handler 待执行回调，避免内存泄漏
        handler.removeCallbacksAndMessages(null);
        // 清理 Choreographer 帧回调，避免内存泄漏
        if (fpsFrameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(fpsFrameCallback);
            fpsFrameCallback = null;
        }
        // 清理 DeviceInfoManager 避免持有 Activity 引用
        if (deviceInfoManager != null) {
            deviceInfoManager.shutdown();
            deviceInfoManager = null;
        }
    }

    private void startPeriodicUpdate() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                viewModel.refreshData();
                // 应用运行时间（从应用启动开始计算）
                long runtimeMs = System.currentTimeMillis() - appStartTimeMs;
                tvRuntime.setText(formatDuration(runtimeMs));
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    // ========== FPS 帧率监控 ==========

    private void startFpsMonitor() {
        fpsCalculationStartNanos = 0;
        frameCount = 0;
        currentFps = 0;

        fpsFrameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (fpsCalculationStartNanos == 0) {
                    fpsCalculationStartNanos = frameTimeNanos;
                }
                frameCount++;
                long elapsedNanos = frameTimeNanos - fpsCalculationStartNanos;
                // 每 500ms 计算一次 FPS
                if (elapsedNanos >= 500_000_000L) {
                    currentFps = frameCount * 1_000_000_000f / elapsedNanos;
                    frameCount = 0;
                    fpsCalculationStartNanos = frameTimeNanos;
                }
                if (isAdded()) {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };
        Choreographer.getInstance().postFrameCallback(fpsFrameCallback);
    }

    private void stopFpsMonitor() {
        if (fpsFrameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(fpsFrameCallback);
            fpsFrameCallback = null;
        }
    }

    // ========== GPU 信息 ==========

    private void loadGpuInfo() {
        // EGL 检测与文件 IO 移到后台线程，避免阻塞主线程
        ThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String gpuInfo = (deviceInfoManager != null) ? deviceInfoManager.getGpuInfo() : null;
                final String openglVersion = detectOpenglVersion();
                final String vulkanVersion = detectVulkanVersion();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        tvGpuRenderer.setText(gpuInfo != null && !gpuInfo.isEmpty() ? gpuInfo : "Unknown");
                        tvOpenglVersion.setText(openglVersion);
                        tvVulkanVersion.setText(vulkanVersion);
                    }
                });
            }
        });
    }

    /**
     * OpenGL 版本检测（带错误处理），需在后台线程执行以避免阻塞主线程。
     */
    private String detectOpenglVersion() {
        try {
            javax.microedition.khronos.egl.EGL10 egl =
                    (javax.microedition.khronos.egl.EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
            javax.microedition.khronos.egl.EGLDisplay display =
                    egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY);
            if (display == javax.microedition.khronos.egl.EGL10.EGL_NO_DISPLAY) {
                return "Unknown";
            } else {
                int[] version = new int[2];
                boolean initialized = egl.eglInitialize(display, version);
                if (!initialized) {
                    return "Unknown";
                } else {
                    try {
                        String glVersion = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);
                        return glVersion != null ? glVersion : "Unknown";
                    } catch (Exception e) {
                        return "Unknown";
                    } finally {
                        egl.eglTerminate(display);
                    }
                }
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String detectVulkanVersion() {
        try {
            String vulkanProp = getSystemProperty("ro.hardware.vulkan");
            if (vulkanProp != null && !vulkanProp.isEmpty()) {
                return "Vulkan " + vulkanProp;
            }

            String[] vulkanPaths = {
                    "/system/lib64/libvulkan.so",
                    "/system/lib/libvulkan.so",
                    "/vendor/lib64/libvulkan.so",
                    "/vendor/lib/libvulkan.so"
            };
            for (String path : vulkanPaths) {
                if (new java.io.File(path).exists()) {
                    String vulkan13 = getSystemProperty("ro.hardware.vulkan.version");
                    if (vulkan13 != null && !vulkan13.isEmpty()) {
                        try {
                            int version = Integer.parseInt(vulkan13.trim(), 16);
                            int major = (version >> 22) & 0x3FF;
                            int minor = (version >> 12) & 0x3FF;
                            return "Vulkan " + major + "." + minor;
                        } catch (Exception ignored) {}
                    }
                    return "Vulkan 1.0+";
                }
            }

            String glVersion = getSystemProperty("ro.opengles.version");
            if (glVersion != null && !glVersion.isEmpty()) {
                try {
                    int version = Integer.parseInt(glVersion.trim());
                    if (version >= 196610) return "Vulkan 1.1+";
                    if (version >= 196609) return "Vulkan 1.0+";
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.status_not_supported);
    }

    private String getSystemProperty(String propertyName) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, propertyName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 工具方法 ==========

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format(Locale.getDefault(), "%.1f %s",
                bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDuration(long ms) {
        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms % (1000 * 60 * 60)) / (1000 * 60);
        return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
    }
}
