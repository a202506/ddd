package com.buzzingmountain.dingclock.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityHelper {

    fun isServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expected = ComponentName(context, serviceClass).flattenToString()
        val enabledRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        if (enabledRaw.isBlank()) return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledRaw) }
        for (token in splitter) {
            if (token.equals(expected, ignoreCase = true)) return true
            // Also accept short form "package/.ClassName"
            if (token.equals(ComponentName(context, serviceClass).flattenToShortString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
