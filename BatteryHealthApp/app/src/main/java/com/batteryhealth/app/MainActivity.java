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
import android.view.MenuItem;
import android.view.View;
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
import com.batteryhealth.app.utils.DeviceInfoManager;
import com.batteryhealth.app.utils.PermissionManager;
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
        setContentView(R.layout.activity_main);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化管理器
        batteryDataManager = new BatteryDataManager(this);
        deviceInfoManager = new DeviceInfoManager(this);
        
        // 初始化视图
        initViews();
        
        // 检查权限
        checkPermissions();
        
        // 注册电池广播
        registerBatteryReceiver();
        
        // 启动监测服务
        startMonitorServices();
        
        // 加载初始数据
        loadInitialData();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(batteryReceiver);
    }
    
    /**
     * 初始化视图
     */
    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
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
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(batteryReceiver, filter);
    }
    
    /**
     * 启动监测服务
     */
    private void startMonitorServices() {
        // 启动电池监测服务
        Intent batteryServiceIntent = new Intent(this, BatteryMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(batteryServiceIntent);
        } else {
            startService(batteryServiceIntent);
        }
        
        // 启动充电监测服务
        Intent chargingServiceIntent = new Intent(this, ChargingMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(chargingServiceIntent);
        } else {
            startService(chargingServiceIntent);
        }
    }
    
    /**
     * 加载初始数据
     */
    private void loadInitialData() {
        // 在后台线程加载数据
        new Thread(() -> {
            // 获取电池信息
            BatteryInfo batteryInfo = batteryDataManager.getCurrentBatteryInfo();
            
            // 获取设备配置
            DeviceConfig deviceConfig = deviceInfoManager.getDeviceConfig();
            
            // 在主线程更新UI
            mainHandler.post(() -> {
                // 数据已加载，Fragment会自行获取
            });
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
        return batteryDataManager;
    }
    
    /**
     * 获取设备信息管理器
     */
    public DeviceInfoManager getDeviceInfoManager() {
        return deviceInfoManager;
    }
}