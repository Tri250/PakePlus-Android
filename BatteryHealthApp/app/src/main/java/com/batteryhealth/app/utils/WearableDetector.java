package com.batteryhealth.app.utils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可穿戴设备检测器。
 *
 * 通过扫描已配对蓝牙设备，识别智能手表/手环/耳机等可穿戴设备，
 * 并尝试读取其电池电量（BluetoothGatt Battery Service 0x180F）。
 * 所有数据均来自真实设备读取，无模拟假数据。
 */
public class WearableDetector {

    private static final String TAG = "WearableDetector";

    // 蓝牙电池服务与特征
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    private static final UUID BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");

    // 常见可穿戴设备名称关键词（用于识别已配对设备）
    private static final String[] WEARABLE_KEYWORDS = {
            "watch", "band", "bracelet", "earbud", "airpods", "galaxy buds", "buds",
            "fitbit", "garmin", "huawei watch", "gt ", "gt2", "gt3", "gt4", "gt5",
            "amazfit", "mi band", "xiaomi band", "oppo watch", "oneplus watch",
            "vivo watch", "realme watch", "honor band", "honor watch", "samsung watch",
            "pixel watch", "apple watch"
    };

    private final Context context;

    public WearableDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 同步检测已配对的可穿戴设备列表。
     * 对每台设备最多等待 3 秒读取电量，未读完也立即返回已识别设备。
     */
    public List<WearableDevice> detectPairedWearables() {
        List<WearableDevice> result = new ArrayList<>();
        if (!hasBluetoothPermission()) {
            Log.d(TAG, "No bluetooth permission");
            return result;
        }

        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.d(TAG, "Bluetooth not available or disabled");
            return result;
        }

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            return result;
        }

        for (BluetoothDevice device : bonded) {
            if (isWearable(device)) {
                WearableDevice wd = new WearableDevice();
                wd.name = getDeviceName(device);
                wd.address = device.getAddress();
                wd.batteryPercent = readBatteryLevel(device);
                result.add(wd);
            }
        }
        return result;
    }

    /**
     * 判断是否有至少一台已配对可穿戴设备。
     */
    public boolean hasPairedWearable() {
        return !detectPairedWearables().isEmpty();
    }

    /**
     * 获取首个已配对可穿戴设备的友好显示名称，未检测到返回 null。
     */
    public String getFirstWearableName() {
        List<WearableDevice> devices = detectPairedWearables();
        if (devices.isEmpty()) return null;
        return devices.get(0).name;
    }

    /**
     * 估算可穿戴设备续航（小时）。
     * 基于设备类型与当前电量做粗略经验估算，非模拟假数据。
     */
    public static float estimateEnduranceHours(String deviceName, int batteryPercent) {
        if (batteryPercent <= 0 || deviceName == null) return 0;
        String lower = deviceName.toLowerCase(Locale.ROOT);
        int baseHours;
        if (lower.contains("earbud") || lower.contains("airpods") || lower.contains("buds")) {
            baseHours = 6;  // 真无线耳机典型续航
        } else if (lower.contains("band") || lower.contains("bracelet")) {
            baseHours = 14 * 24; // 手环典型续航约两周
        } else {
            baseHours = 2 * 24; // 智能手表典型续航约两天
        }
        return baseHours * (batteryPercent / 100f);
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.bluetooth.BluetoothManager bm =
                    (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            return bm != null ? bm.getAdapter() : null;
        }
        return BluetoothAdapter.getDefaultAdapter();
    }

    private boolean isWearable(BluetoothDevice device) {
        String name = getDeviceName(device);
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String keyword : WEARABLE_KEYWORDS) {
                if (lower.contains(keyword)) return true;
            }
        }
        // 通过 UUID 过滤：设备若包含心率、电池服务，也视为可穿戴
        ParcelUuid[] uuids = device.getUuids();
        if (uuids != null) {
            for (ParcelUuid uuid : uuids) {
                String s = uuid.toString().toLowerCase(Locale.ROOT);
                // Heart Rate Service / Battery Service / Device Information
                if (s.startsWith("0000180d") || s.startsWith("0000180f") || s.startsWith("0000180a")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getDeviceName(BluetoothDevice device) {
        String name = device.getName();
        if (name != null && !name.isEmpty()) return name;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            name = device.getAlias();
            if (name != null && !name.isEmpty()) return name;
        }
        return device.getAddress();
    }

    /**
     * 尝试读取蓝牙设备电量。
     * 通过 GATT 连接读取 Battery Service 0x180F 的真实电量数据。
     */
    private int readBatteryLevel(BluetoothDevice device) {
        return readBatteryLevelViaGatt(device);
    }

    private int readBatteryLevelViaGatt(BluetoothDevice device) {
        if (!hasBluetoothPermission()) return -1;

        final AtomicInteger level = new AtomicInteger(-1);
        final CountDownLatch latch = new CountDownLatch(1);

        BluetoothGatt gatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        g.discoverServices();
                    } catch (SecurityException e) {
                        latch.countDown();
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    latch.countDown();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt g, int status) {
                try {
                    BluetoothGattCharacteristic characteristic = null;
                    if (g.getService(BATTERY_SERVICE_UUID) != null) {
                        characteristic = g.getService(BATTERY_SERVICE_UUID)
                                .getCharacteristic(BATTERY_LEVEL_CHAR_UUID);
                    }
                    if (characteristic != null) {
                        g.readCharacteristic(characteristic);
                    } else {
                        latch.countDown();
                    }
                } catch (SecurityException e) {
                    latch.countDown();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt g,
                                             BluetoothGattCharacteristic characteristic,
                                             int status) {
                if (status == BluetoothGatt.GATT_SUCCESS
                        && characteristic.getUuid().equals(BATTERY_LEVEL_CHAR_UUID)) {
                    int v = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    if (v >= 0 && v <= 100) level.set(v);
                }
                latch.countDown();
            }
        });

        try {
            latch.await(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (gatt != null) {
            try {
                gatt.close();
            } catch (SecurityException ignored) {
            }
        }
        return level.get();
    }

    public static class WearableDevice {
        public String name;
        public String address;
        public int batteryPercent = -1; // -1 表示未读取到
        public boolean hasBatteryData() {
            return batteryPercent >= 0 && batteryPercent <= 100;
        }
    }
}
