package com.buzzingmountain.dingclock.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.buzzingmountain.dingclock.util.VivoUtils

/**
 * One row in the [PermissionsFragment]. The [intentFor] callback returns the best Intent for
 * this device, or null if no system path exists (in which case the user must navigate by hand).
 */
data class PermissionItem(
    val key: String,
    val title: String,
    val description: String,
    val intentFor: (Context) -> Intent?,
) {
    companion object {

        fun all(): List<PermissionItem> = listOf(
            PermissionItem(
                key = "accessibility",
                title = "无障碍服务",
                description = "用于点系统设置的飞行模式 Switch、操作钉钉打卡。开启后通知栏会出现 Dump 按钮。",
                intentFor = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            ),
            PermissionItem(
                key = "notifications",
                title = "通知权限",
                description = "前台服务通知 + 失败本地告警 + 通知栏 Dump 入口都需要。",
                intentFor = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${ctx.packageName}"))
                    }
                },
            ),
            PermissionItem(
                key = "battery_optimization",
                title = "忽略电池优化",
                description = "防止系统在 Doze / 待机时延迟我们的精确闹钟。",
                intentFor = { ctx ->
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                },
            ),
            PermissionItem(
                key = "exact_alarm",
                title = "精确闹钟",
                description = "Android 12+ 上要求显式开启，否则定时打卡会被推迟。",
                intentFor = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    } else null
                },
            ),
            PermissionItem(
                key = "auto_start",
                title = "自启动（vivo i 管家 → 权限管理）",
                description = "开机/锁屏后允许本应用自启动，避免错过打卡时间。",
                intentFor = { ctx -> firstAvailable(ctx, autoStartCandidates(ctx)) },
            ),
            PermissionItem(
                key = "background_popup",
                title = "后台弹出界面（vivo i 管家 → 权限管理）",
                description = "无障碍触发的设置/钉钉跳转必须有这个权限，否则会被拦截。",
                intentFor = { ctx -> firstAvailable(ctx, bgPopupCandidates(ctx)) },
            ),
            PermissionItem(
                key = "lock_screen_show",
                title = "锁屏显示",
                description = "息屏 + 锁屏时也允许自动唤醒并显示设置/钉钉界面。",
                intentFor = { ctx -> firstAvailable(ctx, lockScreenCandidates(ctx)) },
            ),
            PermissionItem(
                key = "background_high_drain",
                title = "后台高耗电（vivo i 管家）",
                description = "在「i 管家 → 耗电管理 → 后台高耗电」里把本应用改为「允许」，否则息屏时会被强制冻结。",
                intentFor = { ctx -> firstAvailable(ctx, hiDrainCandidates(ctx)) },
            ),
            PermissionItem(
                key = "memory_no_clean",
                title = "内存允许常驻（vivo 设置）",
                description = "「设置 → 应用管理 → 钉钉打卡助手 → 内存」关掉「允许被清理」。无直达 intent，请手动打开。",
                intentFor = { ctx ->
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                },
            ),
        )

        // ---- vivo intent candidates ---------------------------------------------------------

        private fun autoStartCandidates(ctx: Context): List<Intent> = listOf(
            cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            Intent("com.iqoo.secure.MAIN_SETTINGS"),
            appDetails(ctx),
        )

        private fun bgPopupCandidates(ctx: Context): List<Intent> = listOf(
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"),
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
            appDetails(ctx),
        )

        private fun lockScreenCandidates(ctx: Context): List<Intent> = listOf(
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
            appDetails(ctx),
        )

        private fun hiDrainCandidates(ctx: Context): List<Intent> = listOf(
            Intent("com.iqoo.secure.MAIN_SETTINGS"),
            cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.PowerSavingManagerActivity"),
            appDetails(ctx),
        )

        private fun cmp(pkg: String, cls: String): Intent =
            Intent().apply { component = ComponentName(pkg, cls) }

        private fun appDetails(ctx: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${ctx.packageName}"))

        private fun firstAvailable(ctx: Context, candidates: List<Intent>): Intent? =
            VivoUtils.resolveFirstAvailable(ctx, candidates) ?: appDetails(ctx)
    }
}
