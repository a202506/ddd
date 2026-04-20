package com.buzzingmountain.dingclock.airplane

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.buzzingmountain.dingclock.accessibility.AccessibilityBridge
import com.buzzingmountain.dingclock.accessibility.NodeFinder
import com.buzzingmountain.dingclock.core.StepResult
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Drives the system "Airplane mode" toggle through AccessibilityService clicks.
 *
 * Strategy (in order, first match wins):
 *   1. Find a Switch in the same row as the text "飞行模式" / "Airplane mode" / "Flight mode".
 *   2. If exactly one Switch exists in the whole window, use it.
 *
 * State changes are validated by re-reading the Switch's `isChecked` after the click.
 */
class AirplaneModeController(private val appContext: Context) {

    /** Sets airplane mode to [target]. No-op if already in [target] state. */
    suspend fun setAirplaneMode(target: Boolean): StepResult {
        if (!AccessibilityBridge.isReady()) {
            return StepResult.Failure("无障碍服务未启用")
        }
        Timber.i("AirplaneModeController.set target=%s", target)

        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            return StepResult.Failure("无法打开飞行模式设置页", it)
        }

        // Wait for the settings UI to settle and the Switch to be discoverable.
        val switchNode = AccessibilityBridge.awaitNode(
            timeoutMs = 8_000,
            tag = "airplane-switch",
            predicate = ::isAirplaneSwitch,
        ) ?: return StepResult.Failure("未找到飞行模式开关")

        val current = switchNode.isChecked
        Timber.i("Airplane current=%s, target=%s", current, target)
        if (current == target) {
            backToHome()
            return StepResult.Success
        }

        val clicked = NodeFinder.clickEffectively(switchNode)
        if (!clicked) {
            return StepResult.Failure("点击飞行模式开关失败")
        }

        // Verify the state actually flipped by polling the Switch again.
        val verified = AccessibilityBridge.awaitNode(
            timeoutMs = 5_000,
            tag = "airplane-switch-verify",
            predicate = { node -> isAirplaneSwitch(node) && node.isChecked == target },
        )
        backToHome()
        return if (verified != null) {
            Timber.i("Airplane mode now %s", target)
            StepResult.Success
        } else {
            StepResult.Failure("点击后开关状态未翻转")
        }
    }

    private fun isAirplaneSwitch(node: AccessibilityNodeInfo): Boolean {
        if (!NodeFinder.isToggleable().invoke(node)) return false
        // Strategy 1: same row as the airplane label.
        val sameRowText = NodeFinder.nearestRelative(node) { sibling ->
            AIRPLANE_LABELS.any { label ->
                sibling.text?.toString().equals(label, ignoreCase = true) ||
                    sibling.contentDescription?.toString().equals(label, ignoreCase = true)
            }
        }
        if (sameRowText != null) return true

        // Strategy 2: window only contains a single Switch — that's it.
        val root = AccessibilityBridge.currentRoot() ?: return false
        val allSwitches = NodeFinder.findAll(root, NodeFinder.isToggleable())
        return allSwitches.size == 1 && allSwitches[0] === node
    }

    private suspend fun backToHome() {
        // Brief settle, then back out of Settings; falls through silently on failure.
        delay(400)
        AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    companion object {
        private val AIRPLANE_LABELS = listOf(
            "飞行模式",
            "飛航模式",
            "Airplane mode",
            "Flight mode",
        )
    }
}
