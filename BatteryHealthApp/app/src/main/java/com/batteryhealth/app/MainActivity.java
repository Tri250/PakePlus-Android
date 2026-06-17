package com.batteryhealth.app;

import android.Manifest;
import android.content.pm.PackageManager;
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

import com.batteryhealth.app.service.BatteryMonitorService;
import com.batteryhealth.app.service.ChargingMonitorService;
import com.batteryhealth.app.ui.battery.BatteryHealthFragment;
import com.batteryhealth.app.ui.config.DeviceConfigFragment;
import com.batteryhealth.app.ui.performance.PerformanceFragment;
import com.batteryhealth.app.ui.endurance.EnduranceFragment;
import com.batteryhealth.app.ui.trend.TrendFragment;
import com.batteryhealth.app.ui.power.PowerFragment;
import com.batteryhealth.app.utils.BatteryDataManager;
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
 * 
 * 注意：电池广播由BatteryMonitorService统一处理，避免重复监听
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            
            mainHandler = new Handler(Looper.getMainLooper());
            
            // 初始化视图 - 必须先于其他操作
            initViews();
            
            // 检查视图是否成功初始化
            if (viewPager == null || bottomNavigation == null) {
                Log.e(TAG, "Critical views not initialized");
                Toast.makeText(this, "界面初始化失败", Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.d(TAG, "Views initialized successfully");
            
            // 初始化管理器（带异常处理）
            try {
                batteryDataManager = new BatteryDataManager(this);
                Log.d(TAG, "BatteryDataManager created");
            } catch (Exception e) {
                Log.e(TAG, "Error creating BatteryDataManager: " + e.getMessage(), e);
                batteryDataManager = null;
            }
            
            try {
                deviceInfoManager = new DeviceInfoManager(this);
                Log.d(TAG, "DeviceInfoManager created");
            } catch (Exception e) {
                Log.e(TAG, "Error creating DeviceInfoManager: " + e.getMessage(), e);
                deviceInfoManager = null;
            }
            
            // 检查权限
            checkPermissions();
            
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
        // 不再需要注销广播接收器，因为已经移除了
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
                        return new DeviceConfigFragment();
                    case 2:
                        return new PerformanceFragment();
                    case 3:
                        return new EnduranceFragment();
                    case 4:
                        return new TrendFragment();
                    case 5:
                        return new PowerFragment();
                    default:
                        return new BatteryHealthFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 6;
            }
        });

        viewPager.setOffscreenPageLimit(5);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "Page selected: " + position);
                updateBottomNavigation(position);
            }
        });

        // 显式设置初始页面为第0页（健康）
        viewPager.setCurrentItem(0, false);
        updateBottomNavigation(0);

        Log.d(TAG, "ViewPager setup completed");
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
        } else if (menuId == R.id.nav_endurance) {
            return 3;
        } else if (menuId == R.id.nav_trend) {
            return 4;
        } else if (menuId == R.id.nav_power) {
            return 5;
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
                menuId = R.id.nav_endurance;
                break;
            case 4:
                menuId = R.id.nav_trend;
                break;
            case 5:
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
     * 启动监测服务
     */
    private void startMonitorServices() {
        if (servicesStarted) return;
        
        try {
            // 启动电池监测服务
            android.content.Intent batteryServiceIntent = new android.content.Intent(this, BatteryMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(batteryServiceIntent);
            } else {
                startService(batteryServiceIntent);
            }
            
            // 延迟启动充电监测服务，避免同时启动两个前台服务导致超时
            mainHandler.postDelayed(() -> {
                try {
                    android.content.Intent chargingServiceIntent = new android.content.Intent(this, ChargingMonitorService.class);
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
                if (batteryDataManager != null) {
                    batteryDataManager.refreshAllDataAsync();
                }
                
                // 在主线程更新UI
                mainHandler.post(() -> {
                    // 数据已加载，Fragment会自行获取
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading initial data: " + e.getMessage());
            }
        }).start();
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