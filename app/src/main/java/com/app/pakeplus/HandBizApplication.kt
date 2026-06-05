package com.app.pakeplus

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

/**
 * 掌上商客 V2.0 Application
 * 负责全局初始化、配置加载、错误监控等
 */
class HandBizApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 启用矢量图支持
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // 初始化全局配置
        initConfig()

        Log.i(TAG, "掌上商客 V2.0 启动完成")
    }

    /**
     * 加载 app.json 全局配置
     */
    private fun initConfig() {
        try {
            val jsonString = assets.open("app.json").bufferedReader().use { it.readText() }
            val jsonObject = org.json.JSONObject(jsonString)
            // 这里可以缓存到全局
            AppConfig.apply {
                name = jsonObject.optString("name", "掌上商客")
                version = jsonObject.optString("version", "2.0.0")
                webUrl = jsonObject.optString("webUrl", "")
                debug = jsonObject.optBoolean("debug", false)
                fullScreen = jsonObject.optBoolean("fullScreen", true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 app.json 失败", e)
        }
    }

    companion object {
        private const val TAG = "HandBizApp"
    }
}

/**
 * 全局应用配置
 */
object AppConfig {
    var name: String = "掌上商客"
    var version: String = "2.0.0"
    var webUrl: String = ""
    var debug: Boolean = false
    var fullScreen: Boolean = true
}
