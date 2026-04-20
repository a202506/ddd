package com.buzzingmountain.dingclock.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

object VivoUtils {

    /** True if the running ROM looks like vivo / iQOO Funtouch / OriginOS. */
    fun isVivoRom(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("vivo") || brand.contains("iqoo") ||
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
            sysProp("ro.vivo.os.name").isNotBlank() ||
            sysProp("ro.vivo.os.version").isNotBlank()
    }

    fun romDescription(): String = buildString {
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        val osName = sysProp("ro.vivo.os.name")
        val osVer = sysProp("ro.vivo.os.version")
        if (osName.isNotBlank() || osVer.isNotBlank()) {
            append(" / ").append(osName).append(' ').append(osVer)
        }
        append(" (Android ").append(Build.VERSION.RELEASE).append(")")
    }

    /**
     * Tries each known component path for a given vivo "advanced" settings page; returns the
     * first one that resolves on this device. Falls back to [Intent.ACTION_APPLICATION_DETAILS_SETTINGS]
     * for our own app.
     */
    fun resolveFirstAvailable(context: Context, candidates: List<Intent>): Intent? {
        val pm = context.packageManager
        return candidates.firstOrNull { pm.resolveActivity(it, 0) != null }
    }

    private fun sysProp(key: String): String =
        runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String).orEmpty()
        }.onFailure { Timber.v(it, "sysProp %s failed", key) }.getOrDefault("")
}
