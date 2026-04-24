package com.buzzingmountain.dingclock.accessibility.screens

import android.view.accessibility.AccessibilityNodeInfo
import com.buzzingmountain.dingclock.core.Constants

/**
 * Pure heuristics that map an [AccessibilityNodeInfo] root to a coarse "what page is this?"
 * label. Layout details vary across DingTalk versions, so we rely on visible Chinese / English
 * fragments rather than viewIds.
 */
sealed class DingScreen {
    data object Home : DingScreen()
    data object Attendance : DingScreen()
    data object PunchSuccess : DingScreen()
    data object Login : DingScreen()
    data object Splash : DingScreen()
    data object Unknown : DingScreen()
    data object NotDingTalk : DingScreen()

    companion object {

        private const val MAX_TEXT_NODES = 200

        fun classify(root: AccessibilityNodeInfo?): DingScreen {
            if (root == null) return Unknown
            val pkg = root.packageName?.toString()
            if (pkg != Constants.DINGTALK_PACKAGE) return NotDingTalk

            val texts = mutableListOf<String>()
            collectTexts(root, texts)
            val joined = texts.joinToString("")

            return when {
                hasAny(joined, "打卡成功", "已打卡", "上班打卡成功", "下班打卡成功", "外勤打卡成功") -> PunchSuccess
                isLoginScreen(joined) -> Login
                isAttendanceScreen(joined) -> Attendance
                hasAny(joined, "工作", "消息", "通讯录", "我的") -> Home
                hasAny(joined, "钉钉", "正在加载", "loading") -> Splash
                else -> Unknown
            }
        }

        private fun isLoginScreen(joined: String): Boolean {
            // Kicked-out flow anchors, any of:
            //   S0 "欢迎使用钉钉" — logged-out entry with remembered phone
            //   S1 "同意并登录"   — consent bottom sheet
            //   S2 "密码登录"     — password-login entry button
            //   S3 "请输入密码"   — password input page
            if (hasAny(joined, "欢迎使用钉钉", "同意并登录", "密码登录", "请输入密码")) return true
            // Fallback: generic login layout mentioning both a submit verb and an account field.
            val mentionsLogin = hasAny(joined, "登录", "Sign in")
            val mentionsAccount = hasAny(joined, "请输入手机号", "手机号", "工作号")
            return mentionsLogin && mentionsAccount
        }

        private fun isAttendanceScreen(joined: String): Boolean {
            if (hasAny(joined, "极速打卡", "上班打卡", "下班打卡", "外勤打卡")) return true
            return hasAny(joined, "考勤打卡") &&
                hasAny(joined, "打卡记录", "打卡规则", "考勤统计", "月历", "班次")
        }

        private fun hasAny(joined: String, vararg fragments: String): Boolean =
            fragments.any { joined.contains(it, ignoreCase = true) }

        private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
            if (node == null || out.size >= MAX_TEXT_NODES) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
            for (i in 0 until node.childCount) collectTexts(node.getChild(i), out)
        }
    }
}
