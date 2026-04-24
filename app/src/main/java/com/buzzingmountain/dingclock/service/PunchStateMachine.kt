package com.buzzingmountain.dingclock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import com.buzzingmountain.dingclock.accessibility.AccessibilityBridge
import com.buzzingmountain.dingclock.accessibility.NodeDumper
import com.buzzingmountain.dingclock.accessibility.screens.DingActions
import com.buzzingmountain.dingclock.accessibility.screens.DingLoginFlow
import com.buzzingmountain.dingclock.accessibility.screens.DingScreen
import com.buzzingmountain.dingclock.core.StepResult
import com.buzzingmountain.dingclock.dingtalk.DingTalkLauncher
import com.buzzingmountain.dingclock.net.NetworkProbe
import com.buzzingmountain.dingclock.notify.Notifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Drives the full punch flow as a sequential state machine. Designed to be invoked from a
 * foreground service so that screen-off / Doze-mode wake-ups don't kill it.
 *
 * Assumes the device is already on Wi-Fi. `stepPreCheck` runs an HTTP reachability probe
 * against dingtalk.com before launching DingTalk so a dead Wi-Fi (AP without DNS / captive
 * portal / outage) fails fast instead of hanging inside the login flow.
 *
 * The terminal states are [State.Success] and [State.Failed]. Anything in between is an
 * intermediate step with its own timeout and recovery.
 */
class PunchStateMachine(
    private val appContext: Context,
    private val context: PunchContext,
    private val dingLauncher: DingTalkLauncher = DingTalkLauncher(appContext),
    private val notifier: Notifier? = null,
) {

    sealed class State(val title: String) {
        data object Idle : State("待机")
        data object PreCheck : State("环境检查")
        data object OpenDingTalk : State("启动钉钉")
        data object Classify : State("识别页面")
        data object Login : State("自动登录")
        data object NavigateToAttendance : State("进入考勤页")
        data object WaitForPunch : State("等待打卡")
        data object DoPunch : State("点击打卡")
        data object VerifyPunch : State("校验结果")
        data object BackToHome : State("回首页")
        data object Success : State("成功")
        data class Failed(val reason: String) : State("失败")
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state = _state.asStateFlow()

    suspend fun run(): TerminalResult = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val deadline = SystemClock.elapsedRealtime() + HARD_DEADLINE_MS
        var current: State = State.PreCheck
        // Track whether we already attempted login so we don't loop forever on a bad password.
        var loginAttempts = 0

        try {
            while (current !is State.Success && current !is State.Failed) {
                if (SystemClock.elapsedRealtime() > deadline) {
                    current = State.Failed("整体超时（${HARD_DEADLINE_MS / 1000}s）")
                    break
                }
                _state.value = current
                Timber.i("[FSM] → %s", current.title)

                current = when (current) {
                    State.PreCheck -> stepPreCheck()
                    State.OpenDingTalk -> stepOpenDingTalk()
                    State.Classify -> stepClassify()
                    State.Login -> {
                        if (loginAttempts >= 1) State.Failed("登录已尝试 1 次仍未进入主界面，放弃")
                        else { loginAttempts++; stepLogin() }
                    }
                    State.NavigateToAttendance -> stepNavigateToAttendance()
                    State.WaitForPunch -> stepWaitForPunch()
                    State.DoPunch -> stepDoPunch()
                    State.VerifyPunch -> stepVerifyPunch()
                    State.BackToHome -> stepBackToHome()
                    else -> State.Failed("unhandled state: $current")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "FSM crashed")
            current = State.Failed("异常：${e.message ?: e::class.java.simpleName}")
        }

        _state.value = current

        // On terminal failures, fire-and-forget a webhook alert.
        if (current is State.Failed) {
            launch { notifyFailure(current as State.Failed) }
        }

        TerminalResult(
            state = current,
            startedAt = startedAt,
            finishedAt = System.currentTimeMillis(),
        )
    }

    // -- Steps -------------------------------------------------------------------------------

    private suspend fun stepPreCheck(): State {
        if (!AccessibilityBridge.isReady()) return State.Failed("无障碍服务未启用")
        return when (val r = NetworkProbe.check()) {
            StepResult.Success -> State.OpenDingTalk
            is StepResult.Failure -> State.Failed(r.reason)
        }
    }

    private suspend fun stepOpenDingTalk(): State =
        when (val r = dingLauncher.launchAndAwaitForeground(timeoutMs = 20_000)) {
            StepResult.Success -> { delay(2_000); State.Classify }
            is StepResult.Failure -> State.Failed(r.reason)
        }

    private suspend fun stepClassify(): State {
        // Poll up to 12s; classify once per second.
        repeat(12) {
            val screen = DingScreen.classify(AccessibilityBridge.currentRoot())
            Timber.d("[FSM] classify → %s", screen)
            when (screen) {
                DingScreen.PunchSuccess -> return State.BackToHome
                DingScreen.Attendance -> return State.WaitForPunch
                DingScreen.Login -> return State.Login
                DingScreen.Home -> return State.NavigateToAttendance
                DingScreen.Splash, DingScreen.Unknown -> Unit
                DingScreen.NotDingTalk -> {
                    // DingTalk slipped to background. Try to bring it back once.
                    Timber.w("DingTalk no longer foreground; relaunching")
                    return State.OpenDingTalk
                }
            }
            delay(1_000)
        }
        // Last-ditch: dump the tree so we can fix the recognizer next time.
        Timber.w("Classify timed out; dumping current root for diagnosis")
        Timber.i("=== UNCLASSIFIED DUMP ===\n%s=== END DUMP ===", NodeDumper.dump(AccessibilityBridge.currentRoot()))
        return State.Failed("钉钉界面无法识别")
    }

    /**
     * Walk the four-screen re-login sequence that appears when DingTalk has been kicked out:
     *   S0 "欢迎使用钉钉" with 182****68 + 下一步  → click 下一步 (leave consent unchecked)
     *   S1 bottom sheet "服务协议及隐私保护" + 同意并登录 → click 同意并登录
     *   S2 密码登录 entry button → click 密码登录
     *   S3 "请输入密码" + password field + 登录 → setText + click 登录
     * Phone number is already remembered by DingTalk; we never type it.
     */
    private suspend fun stepLogin(): State {
        return when (val result = DingLoginFlow.completeSavedPasswordLogin(context.passwordProvider)) {
            StepResult.Success -> State.Classify
            is StepResult.Failure -> State.Failed(result.reason)
        }
    }

    private suspend fun stepNavigateToAttendance(): State {
        // Try clicking "考勤打卡" tile on the workbench. If "工作" tab is visible, click it first.
        DingActions.clickByText("工作", "Work", timeoutMs = 2_000, tag = "tab-work")
        delay(800)
        val opened = DingActions.clickByText("考勤打卡", "外勤打卡", timeoutMs = 5_000, tag = "open-attendance")
        if (!opened) {
            Timber.w("Could not find 考勤打卡 tile; falling back to passive wait")
        }
        delay(2_000)
        return State.Classify
    }

    private suspend fun stepWaitForPunch(): State {
        // 钉钉极速打卡通常自动打。等待最多 60 秒。
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val screen = DingScreen.classify(AccessibilityBridge.currentRoot())
            if (screen == DingScreen.PunchSuccess) return State.BackToHome
            // Active fallback: if a 极速打卡 / 打卡 button is visible after 8s of waiting, click it.
            if (SystemClock.elapsedRealtime() - (deadline - 60_000) > 8_000) {
                if (DingActions.clickByText("极速打卡", "上班打卡", "下班打卡", timeoutMs = 1_000, tag = "punch-button")) {
                    return State.VerifyPunch
                }
            }
            delay(1_000)
        }
        return State.Failed("等待极速打卡超时（60s）")
    }

    private suspend fun stepDoPunch(): State {
        if (DingActions.clickByText("极速打卡", "上班打卡", "下班打卡", timeoutMs = 5_000, tag = "punch")) {
            return State.VerifyPunch
        }
        return State.Failed("找不到打卡按钮")
    }

    private suspend fun stepVerifyPunch(): State {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val screen = DingScreen.classify(AccessibilityBridge.currentRoot())
            if (screen == DingScreen.PunchSuccess) return State.BackToHome
            delay(1_000)
        }
        return State.Failed("打卡结果未确认（20s 内未见成功标记）")
    }

    private suspend fun stepBackToHome(): State {
        repeat(3) { AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); delay(300) }
        AccessibilityBridge.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        delay(500)
        return State.Success
    }

    private suspend fun notifyFailure(failed: State.Failed) {
        val n = notifier ?: return
        val sent = n.send(
            title = "钉钉打卡失败",
            markdown = "### 钉钉打卡失败\n" +
                "- 同事：${context.config.colleagueName.ifBlank { "(未设置)" }}\n" +
                "- 类型：${context.type.zh}\n" +
                "- 状态：${state.value.title}\n" +
                "- 原因：${failed.reason}\n",
        )
        Timber.i("notifier.send result=%s", sent)
    }

    data class TerminalResult(
        val state: State,
        val startedAt: Long,
        val finishedAt: Long,
    ) {
        val success: Boolean get() = state is State.Success
        val reason: String get() = (state as? State.Failed)?.reason.orEmpty()
    }

    companion object {
        const val HARD_DEADLINE_MS = 5L * 60 * 1000
    }
}
