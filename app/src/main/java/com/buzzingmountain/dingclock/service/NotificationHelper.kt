package com.buzzingmountain.dingclock.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buzzingmountain.dingclock.core.Constants

object NotificationHelper {

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_STATUS,
                "运行状态",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "无障碍服务运行中、调试 dump 入口等"
                setShowBadge(false)
            },
            NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID_ALERT,
                "失败告警",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "打卡失败、需要短信验证等需要人工干预的事件"
            },
        ).forEach(nm::createNotificationChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
