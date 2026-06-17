package com.batteryhealth.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.batteryhealth.app.data.model.BatteryInfo;
import com.batteryhealth.app.data.model.DeviceConfig;
import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.ui.battery.BatteryHealthFragment;
import com.batteryhealth.app.ui.config.DeviceConfigFragment;
import com.batteryhealth.app.ui.performance.PerformanceFragment;
import com.batteryhealth.app.ui.trend.TrendFragment;
import com.batteryhealth.app.ui.power.PowerFragment;
import com.batteryhealth.app.utils.BatteryDataManager;
import com.batteryhealth.app.utils.AppManager;
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主Activity
 * 
 * 功能：
 * 1. 管理底部导航
 * 2. 协调各Fragment
 * 3. 启动监测服务
 * 4. 处理权限请求
 */
public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;
    
    private BatteryDataManager batteryDataManager;
    private DeviceInfoManager deviceInfoManager;
    
    private Handler mainHandler;
    private boolean servicesStarted = false;
    
    // 电池广播接收器
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryData(intent);
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            
            mainHandler = new Handler(Looper.getMainLooper());
            
            // 初始化全局管理器 - 使用单例，Fragment可以随时访问
            AppManager.getInstance().init(this);
            batteryDataManager = AppManager.getInstance().getBatteryDataManager();
            deviceInfoManager = AppManager.getInstance().getDeviceInfoManager();
            
            // 初始化视图
            initViews();
            
            // 检查视图是否成功初始化
            if (viewPager == null || bottomNavigation == null) {
                Log.e(TAG, "Critical views not initialized");
                Toast.makeText(this, "界面初始化失败", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 检查权限
            checkPermissions();
            
            // 注册电池广播
            registerBatteryReceiver();
            
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
            Toast.makeText(this, "应用初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            // 记录详细错误信息
            StringBuilder errorDetail = new StringBuilder();
            errorDetail.append("Error: ").append(e.getMessage()).append("\n");
            for (StackTraceElement element : e.getStackTrace()) {
                errorDetail.append(element.toString()).append("\n");
            }
            Log.e(TAG, "Stack trace:\n" + errorDetail.toString());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            // 接收器可能未注册
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
        List<Fragment> fragments = new ArrayList<>();
        fragments.add(new BatteryHealthFragment());
        fragments.add(new DeviceConfigFragment());
        fragments.add(new PerformanceFragment());
        fragments.add(new TrendFragment());
        fragments.add(new PowerFragment());
        
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return fragments.get(position);
            }
            
            @Override
            public int getItemCount() {
                return fragments.size();
            }
        });
        
        viewPager.setOffscreenPageLimit(4);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateBottomNavigation(position);
            }
        });
    }
    
    /**
     * 设置底部导航
     */
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int position = getPositionByMenuId(item.getItemId());
            if (position != -1) {
                viewPager.setCurrentItem(position, false);
                return true;
            }
            return false;
        });
    }
    
    /**
     * 根据菜单ID获取位置
     */
    private int getPositionByMenuId(int menuId) {
        if (menuId == R.id.nav_battery) {
            return 0;
        } else if (menuId == R.id.nav_config) {
            return 1;
        } else if (menuId == R.id.nav_performance) {
            return 2;
        } else if (menuId == R.id.nav_trend) {
            return 3;
        } else if (menuId == R.id.nav_power) {
            return 4;
        }
        return -1;
    }
    
    /**
     * 更新底部导航状态
     */
    private void updateBottomNavigation(int position) {
        int menuId;
        switch (position) {
            case 0:
                menuId = R.id.nav_battery;
                break;
            case 1:
                menuId = R.id.nav_config;
                break;
            case 2:
                menuId = R.id.nav_performance;
                break;
            case 3:
                menuId = R.id.nav_trend;
                break;
            case 4:
                menuId = R.id.nav_power;
                break;
            default:
                return;
        }
        bottomNavigation.setSelectedItemId(menuId);
    }
    
    /**
     * 检查权限
     */
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // Android 13+ 通知权限（前台服务必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        // Android 13+ 使用新的媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        // 设备信息权限 - Android 10+ 需要特殊处理
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                    permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "部分功能需要权限才能正常使用", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * 注册电池广播接收器
     */
    private void registerBatteryReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            
            // Android 14+ 需要指定导出标志
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, filter);
            }
            
            // 立即获取sticky intent更新数据，确保Fragment初始化时数据已就绪
            Intent stickyIntent = registerReceiver(null, filter);
            if (stickyIntent != null && batteryDataManager != null) {
                batteryDataManager.updateFromIntent(stickyIntent);
                updateBatteryData(stickyIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering battery receiver: " + e.getMessage());
        }
    }
    
    /**
     * 启动监测服务
     */
    private void startMonitorServices() {
        if (servicesStarted) return;
        
        try {
            // 启动电池监测服务
            Intent batteryServiceIntent = new Intent(this, BatteryMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(batteryServiceIntent);
            } else {
                startService(batteryServiceIntent);
            }
            
            // 延迟启动充电监测服务，避免同时启动两个前台服务导致超时
            mainHandler.postDelayed(() -> {
                try {
                    Intent chargingServiceIntent = new Intent(this, ChargingMonitorService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(chargingServiceIntent);
                    } else {
                        startService(chargingServiceIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error starting charging service: " + e.getMessage());
                }
            }, 1000);
            
            servicesStarted = true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting services: " + e.getMessage());
        }
    }
    
    /**
     * 加载初始数据
     */
    private void loadInitialData() {
        // 在后台线程加载数据
        new Thread(() -> {
            try {
                // 获取电池信息
                BatteryInfo batteryInfo = batteryDataManager != null ? batteryDataManager.getCurrentBatteryInfo() : null;
                if (batteryInfo != null) {
                    Log.d(TAG, "Initial battery level: " + batteryInfo.getLevel() + "%");
                }
                
                // 获取设备配置
                DeviceConfig deviceConfig = deviceInfoManager != null ? deviceInfoManager.getDeviceConfig() : null;
                if (deviceConfig != null) {
                    Log.d(TAG, "Initial device: " + deviceConfig.getFullModelName());
                }
                
                // 在主线程触发一次全局刷新，确保Fragment都能拿到数据
                mainHandler.post(() -> AppManager.getInstance().refreshAll());
            } catch (Exception e) {
                Log.e(TAG, "Error loading initial data: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 更新电池数据
     */
    private void updateBatteryData(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            // 更新电池数据管理器
            batteryDataManager.updateFromIntent(intent);
        }
    }
    
    /**
     * 获取电池数据管理器
     */
    public BatteryDataManager getBatteryDataManager() {
        return batteryDataManager != null ? batteryDataManager : AppManager.getInstance().getBatteryDataManager();
    }
    
    /**
     * 获取设备信息管理器
     */
    public DeviceInfoManager getDeviceInfoManager() {
        return deviceInfoManager != null ? deviceInfoManager : AppManager.getInstance().getDeviceInfoManager();
    }
}