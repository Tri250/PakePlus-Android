package com.batteryhealth.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.ui.battery.BatteryHealthFragment;
import com.batteryhealth.app.ui.community.CommunityFragment;
import com.batteryhealth.app.ui.config.DeviceConfigFragment;
import com.batteryhealth.app.ui.endurance.EnduranceFragment;
import com.batteryhealth.app.ui.guide.GuideFragment;
import com.batteryhealth.app.ui.healthcheck.HealthCheckFragment;
import com.batteryhealth.app.ui.origin.BatteryOriginFragment;
import com.batteryhealth.app.ui.performance.PerformanceFragment;
import com.batteryhealth.app.ui.power.ChargingHistoryFragment;
import com.batteryhealth.app.ui.power.PowerFragment;
import com.batteryhealth.app.ui.trend.TrendFragment;
import com.batteryhealth.app.ui.view.CustomBottomNavigationView;
import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.PermissionManager;
import com.batteryhealth.app.utils.ThreadExecutor;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主Activity
 * 
 * 功能：
 * 1. 管理底部导航
 * 2. 协调各Fragment
 * 3. 启动监测服务
 * 4. 处理权限请求
 * 
 * 注意：电池广播由BatteryMonitorService统一处理，避免重复监听
 */
public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";

    private ViewPager2 viewPager;
    private CustomBottomNavigationView bottomNavigation;

    // PendingIntent 请求码使用固定值，避免进程重启后 hashCode 变化导致 PendingIntent 无法取消
    private static final int PENDING_INTENT_REQUEST_BATTERY_MONITOR = 0xB4A7_0001;
    private static final int PENDING_INTENT_REQUEST_CHARGING_MONITOR = 0xB4A7_0002;

    private BatteryDataManager batteryDataManager;
    private DeviceInfoManager deviceInfoManager;
    
    private Handler mainHandler;
    private final AtomicBoolean servicesStarted = new AtomicBoolean(false);

    // 跟踪前台状态（替代废弃的 getRunningAppProcesses API）
    private static final AtomicBoolean isAppInForeground = new AtomicBoolean(false);
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            // Android 15+ 强制 edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            applyEdgeToEdgeInsets();

            mainHandler = new Handler(Looper.getMainLooper());

            // 初始化视图 - 必须先于其他操作
            initViews();
            
            // 检查视图是否成功初始化
            if (viewPager == null || bottomNavigation == null) {
                Log.e(TAG, "Critical views not initialized");
                Toast.makeText(this, getString(R.string.status_init_failed), Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.d(TAG, "Views initialized successfully");
            
            // 初始化管理器（带异常处理）
            try {
                deviceInfoManager = new DeviceInfoManager(this);
                // 注入到 Application，以便 onTerminate 时关闭 executor
                ((BatteryHealthApplication) getApplication()).setDeviceInfoManager(deviceInfoManager);
                Log.d(TAG, "DeviceInfoManager created");
            } catch (Exception e) {
                Log.e(TAG, "Error creating DeviceInfoManager: " + e.getMessage(), e);
                deviceInfoManager = null;
            }

            try {
                batteryDataManager = new BatteryDataManager(this);
                // 将设备使用天数与激活信息同步给电池管理器，用于健康度物理估算
                if (deviceInfoManager != null) {
                    batteryDataManager.setUsageDays(deviceInfoManager.getUsageDays());
                    batteryDataManager.setActivationInfo(deviceInfoManager.getActivationInfo());
                }
                Log.d(TAG, "BatteryDataManager created");
            } catch (Exception e) {
                Log.e(TAG, "Error creating BatteryDataManager: " + e.getMessage(), e);
                batteryDataManager = null;
            }

            // 首次启动主动展示隐私政策与用户协议（GDPR / 个人信息保护法合规要求）
            showPrivacyPolicyIfNeeded();

            // 检查权限（统一使用 PermissionManager）
            String[] requiredPermissions = getRequiredPermissions();
            if (requiredPermissions.length > 0) {
                PermissionManager.checkAndRequestPermissions(this, requiredPermissions);
            }

            // 检查通知权限并提示
            checkNotificationPermissionAndPrompt();

            // 引导用户关闭电池优化
            promptBatteryOptimizationIfNeeded();

            // 注意：电池广播由BatteryMonitorService统一处理
            // 不再在MainActivity中注册电池广播接收器，避免重复监听

            // 延迟启动服务，避免启动时闪退
            mainHandler.postDelayed(() -> {
                try {
                    if (!isFinishing() && !isDestroyed()) {
                        startMonitorServices();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error starting services: " + e.getMessage());
                }
            }, 2000);
            
            // 加载初始数据
            loadInitialData();
            
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.status_app_init_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            // 记录详细错误信息
            StringBuilder errorDetail = new StringBuilder();
            errorDetail.append("Error: ").append(e.getMessage()).append("\n");
            for (StackTraceElement element : e.getStackTrace()) {
                errorDetail.append(element.toString()).append("\n");
            }
            Log.e(TAG, "Stack trace:\n" + errorDetail.toString());
        }
    }
    
    // 电池广播由 BatteryMonitorService 统一处理，MainActivity 无需在 onDestroy 中注销
    
    /**
     * Android 15+ 强制 edge-to-edge：
     * 1. 根视图仅保留状态栏/左右内边距，底部不设置 padding，保证底部导航可贴底。
     * 2. 自定义底部导航栏高度 = 64dp 内容高度 + 系统导航栏/手势条高度，避免被遮挡或压缩。
     * 3. ViewPager2 底部 margin 跟随底部导航栏总高度，确保内容不被导航栏覆盖。
     */
    private void applyEdgeToEdgeInsets() {
        try {
            View root = findViewById(android.R.id.content);
            if (root == null) return;
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomInset = Math.max(bars.bottom, ime.bottom);

                // 状态栏顶边距 + 左右边距；底部不设置，由导航栏自身处理
                v.setPadding(bars.left, bars.top, bars.right, 0);

                // 底部导航栏动态增高并设置底部 padding，内容保持在手势条上方
                CustomBottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setPadding(bottomNav.getPaddingLeft(), bottomNav.getPaddingTop(),
                            bottomNav.getPaddingRight(), bottomInset);
                    bottomNav.applySystemBottomInset(bottomInset);

                    // 等导航栏 layout 完成后，更新 ViewPager 底部 margin
                    bottomNav.post(() -> updateViewPagerBottomMargin(bottomNav));
                }
                return WindowInsetsCompat.CONSUMED;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error applying edge-to-edge insets: " + e.getMessage());
        }
    }

    private void updateViewPagerBottomMargin(CustomBottomNavigationView bottomNav) {
        if (viewPager == null || bottomNav == null) return;
        int totalNavHeight = bottomNav.getMeasuredHeight();
        if (totalNavHeight <= 0) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
        if (lp.bottomMargin != totalNavHeight) {
            lp.bottomMargin = totalNavHeight;
            viewPager.setLayoutParams(lp);
        }
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        
        if (viewPager == null || bottomNavigation == null) {
            Log.e(TAG, "Required views not found in layout");
            return;
        }
        
        // 设置ViewPager
        setupViewPager();
        
        // 设置底部导航
        setupBottomNavigation();
    }
    
    /**
     * 设置ViewPager
     */
    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                Log.d(TAG, "Creating fragment at position: " + position);
                switch (position) {
                    case 0:
                        return new BatteryHealthFragment();
                    case 1:
                        return new PowerFragment();
                    case 2:
                        return new EnduranceFragment();
                    case 3:
                        return new TrendFragment();
                    case 4:
                        return new BatteryOriginFragment();
                    case 5:
                        return new HealthCheckFragment();
                    case 6:
                        return new DeviceConfigFragment();
                    default:
                        return new BatteryHealthFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 7;
            }
        });

        viewPager.setOffscreenPageLimit(3);
        // 设置页面切换动画
        viewPager.setPageTransformer((page, position) -> {
            float absPosition = Math.abs(position);
            if (absPosition >= 1f) {
                page.setAlpha(1f);
                page.setTranslationX(0f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else {
                page.setAlpha(1f - absPosition * 0.3f);
                page.setTranslationX(-position * page.getWidth() * 0.08f);
                page.setScaleX(1f - absPosition * 0.03f);
                page.setScaleY(1f - absPosition * 0.03f);
            }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "Page selected: " + position);
                updateBottomNavigation(position);
                // 进入自检页时清除红点
                if (position == 5) {
                    bottomNavigation.hideBadge(5);
                }
            }
        });

        // 显式设置初始页面为第0页（健康）
        viewPager.setCurrentItem(0, false);
        updateBottomNavigation(0);

        Log.d(TAG, "ViewPager setup completed");
    }
    
    /**
     * 设置底部导航（7项：健康 / 充电 / 续航 / 趋势 / 溯源 / 自检 / 配置）
     * 社区和指南合并到配置页二级入口，减少Tab数量提升触控体验
     */
    private void setupBottomNavigation() {
        List<CustomBottomNavigationView.NavItem> navItems = new ArrayList<>();
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_health), R.drawable.ic_battery_health));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_power), R.drawable.ic_power));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_endurance), R.drawable.ic_endurance));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_trend), R.drawable.ic_trend));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_origin), R.drawable.ic_battery));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_health_check), R.drawable.ic_battery_alert));
        navItems.add(new CustomBottomNavigationView.NavItem(getString(R.string.nav_config), R.drawable.ic_device));

        bottomNavigation.setItems(navItems);
        bottomNavigation.setOnItemSelectedListener(position -> {
            viewPager.setCurrentItem(position, true);
        });
    }

    /**
     * 更新底部导航状态
     */
    private void updateBottomNavigation(int position) {
        bottomNavigation.setSelectedPosition(position);
    }
    
    /**
     * 返回应用运行所需权限列表（按 Android 版本区分）
     */
    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Android 13+ 通知权限（前台服务必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        // 设备信息权限 - Android 10+ 无需此权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        return permissions.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, getString(R.string.status_permission_needed), Toast.LENGTH_LONG).show();
            }
            PermissionManager.handlePermissionResult(this, permissions, grantResults);
        }
    }
    
    /**
     * 启动监测服务
     */
    private void startMonitorServices() {
        if (!servicesStarted.compareAndSet(false, true)) return;

        ThreadExecutor.execute(() -> {
            try {
                startServiceSafely(BatteryMonitorService.class);

                // 延迟启动充电监测服务，避免同时启动两个前台服务导致超时
                mainHandler.postDelayed(() -> {
                    try {
                        startServiceSafely(ChargingMonitorService.class);
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting charging service: " + e.getMessage());
                    }
                }, 1000);
            } catch (Exception e) {
                Log.e(TAG, "Error starting services: " + e.getMessage());
            }
        });
    }

    /**
     * 安全启动服务：若应用在前台直接启动；若在后台（Android 14+）使用 AlarmManager 延迟启动。
     * Android 12+ 使用精确闹钟前必须声明 SCHEDULE_EXACT_ALARM 并检查 canScheduleExactAlarms()，
     * 否则 setExactAndAllowWhileIdle 会抛出 SecurityException。
     */
    private void startServiceSafely(Class<?> serviceClass) {
        Intent intent = new Intent(this, serviceClass);
        boolean isAppInForeground = isAppInForeground();
        // 根据服务类型选择固定请求码，避免进程重启后 hashCode 变化
        int requestCode = (serviceClass == BatteryMonitorService.class)
                ? PENDING_INTENT_REQUEST_BATTERY_MONITOR
                : PENDING_INTENT_REQUEST_CHARGING_MONITOR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isAppInForeground || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForegroundService(intent);
            } else {
                // Android 14+ 且不在前台：使用 AlarmManager 延迟 5 秒启动
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    PendingIntent pendingIntent = PendingIntent.getForegroundService(
                            this, requestCode, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    long triggerAt = System.currentTimeMillis() + 5000;
                    scheduleAlarmCompat(alarmManager, triggerAt, pendingIntent);
                    Log.d(TAG, "Scheduled delayed start for " + serviceClass.getSimpleName());
                }
            }
        } else {
            startService(intent);
        }
    }

    /**
     * 兼容性调度闹钟：优先精确闹钟，无权限时回退到非精确闹钟，避免 SecurityException 导致服务无法重启。
     */
    private void scheduleAlarmCompat(AlarmManager alarmManager, long triggerAt, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    /**
     * 判断应用是否处于前台（使用静态标志，避免废弃的 getRunningAppProcesses API）。
     */
    private boolean isAppInForeground() {
        return isAppInForeground.get();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground.set(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground.set(false);
    }

    /**
     * 检查通知权限，未授予时显示 Snackbar 提示
     */
    private void checkNotificationPermissionAndPrompt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                View rootView = findViewById(android.R.id.content);
                if (rootView != null) {
                    Snackbar.make(rootView, getString(R.string.status_notification_permission_needed), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.action_go_settings), v -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            })
                            .show();
                }
            }
        }
    }

    /**
     * 引导用户关闭电池优化
     */
    private void promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_battery_optimization_title))
                        .setMessage(getString(R.string.dialog_battery_optimization_message))
                        .setPositiveButton(getString(R.string.action_go_settings), (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            try {
                                startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to open battery optimization settings", e);
                            }
                        })
                        .setNegativeButton(getString(R.string.action_later), null)
                        .show();
            }
        }
    }

    private void showPrivacyPolicyIfNeeded() {
        final String PREFS_PRIVACY = "privacy_policy_prefs";
        final String PREF_POLICY_ACCEPTED = "policy_accepted";
        final String PREF_ACCEPTED_VERSION = "accepted_version";
        final String CURRENT_VERSION = "20260625";

        try {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_PRIVACY, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_POLICY_ACCEPTED, false)
                    && CURRENT_VERSION.equals(prefs.getString(PREF_ACCEPTED_VERSION, ""))) {
                // 已同意过当前版本，无需重复展示
                return;
            }

            // 展示隐私政策与用户协议摘要弹窗，引导用户查看完整内容
            String summary = getString(R.string.privacy_policy_summary);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.privacy_policy_title))
                    .setMessage(summary)
                    .setPositiveButton(getString(R.string.privacy_policy_agree), (dialog, which) -> {
                        prefs.edit()
                                .putBoolean(PREF_POLICY_ACCEPTED, true)
                                .putString(PREF_ACCEPTED_VERSION, CURRENT_VERSION)
                                .apply();
                    })
                    .setNeutralButton(getString(R.string.privacy_policy_view), (dialog, which) -> {
                        prefs.edit()
                                .putBoolean(PREF_POLICY_ACCEPTED, true)
                                .putString(PREF_ACCEPTED_VERSION, CURRENT_VERSION)
                                .apply();
                        try {
                            Intent intent = new Intent(this, com.batteryhealth.app.ui.policy.PolicyActivity.class);
                            intent.putExtra(com.batteryhealth.app.ui.policy.PolicyActivity.EXTRA_TYPE,
                                    com.batteryhealth.app.ui.policy.PolicyActivity.TYPE_PRIVACY);
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton(getString(R.string.privacy_policy_disagree), (dialog, which) -> {
                        // 用户不同意仍可使用基础功能，但部分功能受限
                        Snackbar.make(findViewById(android.R.id.content),
                                getString(R.string.privacy_policy_limited), Snackbar.LENGTH_LONG).show();
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "showPrivacyPolicyIfNeeded failed: " + e.getMessage());
        }
    }
    
    /**
     * 加载初始数据
     */
    private void loadInitialData() {
        if (batteryDataManager != null) {
            batteryDataManager.refreshAllDataAsync();
        }
        // 延迟检查电池健康度，若低于80%则在自检Tab显示红点提醒
        mainHandler.postDelayed(() -> {
            if (batteryDataManager != null && !isFinishing() && !isDestroyed()) {
                try {
                    BatteryInfo info = batteryDataManager.getCurrentBatteryInfo();
                    if (info != null && info.hasValidHealthData() && info.getHealthPercentage() < 80f) {
                        bottomNavigation.showBadge(5);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Health check badge skipped: " + e.getMessage());
                }
            }
        }, 3000);
    }
    
    /**
     * 获取电池数据管理器
     */
    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager;
    }
    
    /**
     * 获取设备信息管理器
     */
    public DeviceInfoManager getDeviceInfoManager() {
        return deviceInfoManager;
    }
}