package com.buzzingmountain.dingclock.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * Long-running AccessibilityService: logs every TYPE_WINDOW_STATE_CHANGED so we can see which
 * activity is in front, and exposes a [ACTION_DUMP_ROOT] broadcast (wired to a notification
 * action) that dumps the current window's accessibility node tree into the log file. The
 * punch state machine reads from [AccessibilityBridge] to classify and drive DingTalk.
 */
class DingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("AccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("AccessibilityService unbinding")
        instance = null
        return super.onUnbind(intent)
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
