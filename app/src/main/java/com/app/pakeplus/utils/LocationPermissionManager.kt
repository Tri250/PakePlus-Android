package com.app.pakeplus.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * 定位权限管理器
 * 统一管理 Android 定位权限申请和位置获取
 */
class LocationPermissionManager(private val activity: Activity) {

    /**
     * 检查定位权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取所需的定位权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return buildList {
            if (ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
    }

    /**
     * 请求定位权限
     */
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        val permissions = getRequiredPermissions()
        if (permissions.isNotEmpty()) {
            launcher.launch(permissions)
        }
    }

    /**
     * 检查定位服务是否启用
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = activity.getSystemService<LocationManager>() ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * 获取最后已知位置
     */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val locationManager = activity.getSystemService<LocationManager>() ?: return null

        val providers = locationManager.allProviders
        var bestLocation: Location? = null

        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            } catch (e: SecurityException) {
                // 权限不足
                return null
            }
        }

        return bestLocation
    }

    companion object {
        /**
         * 判断是否为 Android 10 及以上版本
         */
        fun isAndroid10OrAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /**
         * 判断是否为 Android 12 及以上版本
         */
        fun isAndroid12OrAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        /**
         * 判断是否为 Android 13 及以上版本
         */
        fun isAndroid13OrAbove(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
