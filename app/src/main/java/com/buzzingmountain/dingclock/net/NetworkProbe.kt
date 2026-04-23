package com.buzzingmountain.dingclock.net

import com.buzzingmountain.dingclock.core.StepResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies real internet reachability by issuing a lightweight HEAD request to the DingTalk
 * landing page. `ConnectivityManager` only reports link-layer state — a Wi-Fi AP without DNS
 * or an outage can still pass `NET_CAPABILITY_INTERNET`, so we probe an external host before
 * launching DingTalk to avoid entering login/punch flow on a dead connection.
 */
object NetworkProbe {

    private const val DEFAULT_URL = "https://www.dingtalk.com/"
    private const val DEFAULT_TIMEOUT_MS = 5_000

    suspend fun check(
        url: String = DEFAULT_URL,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): StepResult = withContext(Dispatchers.IO) {
        val deadlineGuard = (timeoutMs * 2L).coerceAtLeast(3_000L)
        val result = withTimeoutOrNull(deadlineGuard) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    requestMethod = "HEAD"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "DingClock/NetworkProbe")
                }
                try {
                    val code = conn.responseCode
                    Timber.i("NetworkProbe: %s → HTTP %d", url, code)
                    if (code in 200..399) StepResult.Success
                    else StepResult.Failure("网络探测失败：HTTP $code")
                } finally {
                    runCatching { conn.disconnect() }
                }
            }.getOrElse { e ->
                Timber.w(e, "NetworkProbe: %s failed", url)
                StepResult.Failure("网络探测失败：${e.javaClass.simpleName}${e.message?.let { "：$it" } ?: ""}")
            }
        }
        result ?: StepResult.Failure("网络探测超时（${deadlineGuard / 1000}s）")
    }
}
