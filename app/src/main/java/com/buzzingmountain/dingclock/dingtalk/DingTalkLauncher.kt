package com.buzzingmountain.dingclock.dingtalk

import android.content.Context
import android.content.Intent
import com.buzzingmountain.dingclock.accessibility.AccessibilityBridge
import com.buzzingmountain.dingclock.core.Constants
import com.buzzingmountain.dingclock.core.StepResult
import kotlinx.coroutines.delay
import timber.log.Timber

/** Launches DingTalk and waits until its foreground window is observed. */
class DingTalkLauncher(private val context: Context) {

    suspend fun launchAndAwaitForeground(timeoutMs: Long = 20_000): StepResult {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(Constants.DINGTALK_PACKAGE)
            ?: return StepResult.Failure("钉钉未安装")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
            .onFailure { return StepResult.Failure("启动钉钉失败：${it.message}", it) }

        if (!AccessibilityBridge.isReady()) {
            Timber.w("AccessibilityService not bound; cannot confirm DingTalk in foreground")
            // Fall back to a fixed wait; the rest of the state machine will fail later if needed.
            delay(2_000)
            return StepResult.Success
        }

        val ok = AccessibilityBridge.awaitNode(
            timeoutMs = timeoutMs,
            tag = "dingtalk-foreground",
            predicate = { node ->
                node.packageName?.toString() == Constants.DINGTALK_PACKAGE
            },
        )
        return if (ok != null) StepResult.Success
        else StepResult.Failure("钉钉未进入前台（${timeoutMs / 1000}s 内）")
    }
}
