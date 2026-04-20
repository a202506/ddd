package com.buzzingmountain.dingclock.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.buzzingmountain.dingclock.core.StepResult
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Waits for a Wi-Fi network with internet to come up after airplane mode is toggled off.
 *
 * SSID matching is best-effort: reading the connected SSID requires location permission on
 * Android 8+, which we don't request automatically. If the SSID can't be read, we fall back
 * to "any Wi-Fi with internet" — the user's flow guarantees the phone auto-reconnects to
 * the right office network anyway.
 */
class WifiWatcher(private val context: Context) {

    suspend fun awaitConnected(
        targetSsid: String,
        timeoutMs: Long = 90_000,
    ): StepResult = suspendCancellableCoroutine { cont ->
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            cont.resume(StepResult.Failure("ConnectivityManager 不可用"))
            return@suspendCancellableCoroutine
        }
        val expected = targetSsid.trim().trim('"')

        // Already on the right Wi-Fi? Resolve immediately.
        currentMatchedSsid(cm, expected)?.let { ssid ->
            Timber.i("WifiWatcher: already on %s", ssid)
            cont.resume(StepResult.Success)
            return@suspendCancellableCoroutine
        }

        val handler = Handler(Looper.getMainLooper())
        var settled = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (settled) return
                val caps = cm.getNetworkCapabilities(network) ?: return
                check(caps, network)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (settled) return
                check(caps, network)
            }
            private fun check(caps: NetworkCapabilities, network: Network) {
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                val ssid = readSsid(caps)
                when {
                    ssid == null -> {
                        Timber.w("WifiWatcher: SSID unreadable (no location perm?), accepting any Wi-Fi with internet")
                        settle(StepResult.Success)
                    }
                    ssid.equals(expected, ignoreCase = true) -> {
                        Timber.i("WifiWatcher: connected to target %s", ssid)
                        settle(StepResult.Success)
                    }
                    else -> {
                        Timber.d("WifiWatcher: on %s, waiting for %s", ssid, expected)
                    }
                }
            }
            private fun settle(result: StepResult) {
                if (settled) return
                settled = true
                handler.removeCallbacksAndMessages(null)
                runCatching { cm.unregisterNetworkCallback(this) }
                if (cont.isActive) cont.resume(result)
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure {
                Timber.e(it, "registerNetworkCallback failed")
                cont.resume(StepResult.Failure("注册 Wi-Fi 回调失败：${it.message}"))
                return@suspendCancellableCoroutine
            }

        handler.postDelayed({
            if (settled) return@postDelayed
            settled = true
            runCatching { cm.unregisterNetworkCallback(callback) }
            if (cont.isActive) {
                cont.resume(StepResult.Failure("等 Wi-Fi 超时（${timeoutMs / 1000}s，目标 SSID=$expected）"))
            }
        }, timeoutMs)

        cont.invokeOnCancellation {
            handler.removeCallbacksAndMessages(null)
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    private fun currentMatchedSsid(cm: ConnectivityManager, expected: String): String? {
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
        val ssid = readSsid(caps) ?: return null
        return if (ssid.equals(expected, ignoreCase = true)) ssid else null
    }

    private fun readSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val info = caps.transportInfo as? WifiInfo ?: return null
            return cleanupSsid(info.ssid)
        }
        val wm = context.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        val info = wm?.connectionInfo ?: return null
        return cleanupSsid(info.ssid)
    }

    private fun cleanupSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val unquoted = raw.trim().removePrefix("\"").removeSuffix("\"")
        if (unquoted.isBlank() || unquoted == "<unknown ssid>") return null
        return unquoted
    }
}
