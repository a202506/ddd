package com.buzzingmountain.dingclock.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/** Accessibility bridge for manual DingTalk launch and password-assisted login. */
class DingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("AccessibilityService connected")
    }

    override fun onDestroy() {
        Timber.i("AccessibilityService destroyed")
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Timber.d("Window: pkg=%s cls=%s", event.packageName, event.className)
        }
    }

    override fun onInterrupt() {
        Timber.w("AccessibilityService interrupted")
    }

    companion object {
        @Volatile
        var instance: DingAccessibilityService? = null
            private set
    }
}
