package com.digiguide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.digiguide.core.NativeLib
import com.digiguide.db.AppDatabase
import com.digiguide.service.ApiClient

/**
 * 数码指南应用入口
 */
class DigiGuideApp : Application() {

    companion object {
        const val CHANNEL_ID_ANALYSIS = "battery_analysis"
        const val CHANNEL_ID_NOTIFICATION = "general_notification"

        lateinit var instance: DigiGuideApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化Native库
        NativeLib.init()

        // 初始化数据库
        AppDatabase.init(this)

        // 初始化API客户端
        ApiClient.init()

        // 创建通知渠道
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 电池分析通知渠道
            val analysisChannel = NotificationChannel(
                CHANNEL_ID_ANALYSIS,
                "电池分析",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "电池健康度分析进度通知"
            }

            // 一般通知渠道
            val notificationChannel = NotificationChannel(
                CHANNEL_ID_NOTIFICATION,
                "一般通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "应用一般通知"
            }

            notificationManager.createNotificationChannels(
                listOf(analysisChannel, notificationChannel)
            )
        }
    }
}