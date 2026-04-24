package com.buzzingmountain.dingclock.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
                description = "用于识别钉钉界面并在你手动启动后辅助完成密码登录。",
                intentFor = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            ),
            PermissionItem(
                key = "battery_optimization",
                title = "忽略电池优化",
                description = "可选。部分系统会频繁回收无障碍服务，关闭优化后稳定性更好。",
                intentFor = { ctx ->
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                },
            ),
            PermissionItem(
                key = "vendor_permissions",
                title = "厂商权限页",
                description = "可选。若系统拦截页面切换，可在这里检查自启动、后台弹出界面等厂商限制。",
                intentFor = { ctx ->
                    firstAvailable(ctx, vendorPermissionCandidates(ctx))
                },
            ),
        )

        private fun vendorPermissionCandidates(ctx: Context): List<Intent> = listOf(
            cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"),
            cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
            Intent("com.iqoo.secure.MAIN_SETTINGS"),
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
