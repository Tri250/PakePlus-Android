package com.app.pakeplus.utils

import android.content.Context
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient

/**
 * WebView 定位权限回调
 * 用于统一处理 WebView 的定位权限请求
 */
class WebViewLocationCallback(
    private val context: Context,
    private val origin: String?,
    private val callback: GeolocationPermissions.Callback?
) {
    /**
     * 处理定位权限请求
     * @return true 表示已处理，false 表示未处理
     */
    fun handle(): Boolean {
        if (origin == null || callback == null) return false

        val manager = (context as? android.app.Activity)?.let {
            LocationPermissionManager(it)
        } ?: return false

        if (manager.hasLocationPermission()) {
            // 已经有权限，直接授予
            callback.invoke(origin, true, false)
            return true
        }

        return false
    }

    companion object {
        private const val TAG = "WebViewLocation"
    }
}
