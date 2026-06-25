package com.batteryhealth.app.utils.healthcheck;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import com.batteryhealth.app.data.model.HealthCheckResult;

/**
 * 蓝牙检测
 * 检测蓝牙是否开启，未使用时开启会增加耗电
 */
public class BluetoothChecker implements IHealthChecker {

    private static final String NAME = "蓝牙状态";
    private static final String CATEGORY = HealthCheckResult.CATEGORY_SYSTEM;
    private static final int PRIORITY = 65;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public HealthCheckResult check(Context context) {
        HealthCheckResult.Builder builder = new HealthCheckResult.Builder()
                .setId("bluetooth_status")
                .setTitle(NAME)
                .setCategory(CATEGORY);

        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                builder.setStatus("不支持");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(100);
                builder.setDescription("设备不支持蓝牙功能。");
                builder.setAdvice("无需处理。");
                return builder.build();
            }

            boolean isEnabled = bluetoothAdapter.isEnabled();

            if (isEnabled) {
                builder.setStatus("已开启");
                builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
                builder.setItemScore(70);
                builder.setDescription("蓝牙当前处于开启状态。如果您不使用蓝牙设备（如蓝牙耳机、智能手表等），关闭蓝牙可以节省电量。");
                builder.setAdvice("如果暂时不使用蓝牙设备，建议关闭蓝牙以节省电量。");
                builder.setValue("开启");
                builder.setRepairable(true);
                builder.setFixAction(HealthCheckResult.FIX_ACTION_BLUETOOTH_SETTINGS);
            } else {
                builder.setStatus("已关闭");
                builder.setSeverity(HealthCheckResult.SEVERITY_GOOD);
                builder.setItemScore(100);
                builder.setDescription("蓝牙当前处于关闭状态，有助于节省电量。");
                builder.setAdvice("保持蓝牙关闭可延长续航时间，需要时再开启。");
                builder.setValue("关闭");
            }
        } catch (Exception e) {
            builder.setStatus("无法检测");
            builder.setSeverity(HealthCheckResult.SEVERITY_INFO);
            builder.setItemScore(70);
            builder.setDescription("无法检测蓝牙状态。");
            builder.setAdvice("您可以在系统设置中手动检查蓝牙状态。");
        }

        return builder.build();
    }
}
