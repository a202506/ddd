package com.buzzingmountain.dingclock.accessibility.screens

import com.buzzingmountain.dingclock.core.StepResult
import kotlinx.coroutines.delay

/**
 * Drives the remembered-account password login flow shown by DingTalk after a logout/kick-out.
 */
object DingLoginFlow {

    suspend fun completeSavedPasswordLogin(passwordProvider: () -> String?): StepResult {
        if (!DingActions.clickByText("下一步", "Next", timeoutMs = 8_000, tag = "login-s0-next")) {
            return StepResult.Failure("登录(S0)：找不到「下一步」")
        }
        delay(1_000)

        if (!DingActions.clickByText("同意并登录", timeoutMs = 6_000, tag = "login-s1-agree")) {
            return StepResult.Failure("登录(S1)：找不到「同意并登录」")
        }
        delay(1_200)

        if (!DingActions.clickByText("密码登录", timeoutMs = 6_000, tag = "login-s2-pw-entry")) {
            return StepResult.Failure("登录(S2)：找不到「密码登录」")
        }
        delay(1_200)

        val plain = passwordProvider()
        if (plain.isNullOrEmpty()) {
            return StepResult.Failure("无可用密码：未配置或解密失败")
        }
        val typed = DingActions.setPasswordField(value = plain, timeoutMs = 8_000)
        @Suppress("UNUSED_VALUE") var passwordRef: String? = plain
        passwordRef = null
        if (!typed) return StepResult.Failure("登录(S3)：找不到密码输入框")
        delay(500)

        if (!DingActions.clickByTextExact("登录", timeoutMs = 5_000, tag = "login-s3-submit")) {
            return StepResult.Failure("登录(S3)：找不到「登录」按钮")
        }
        delay(3_500)
        return StepResult.Success
    }
}
