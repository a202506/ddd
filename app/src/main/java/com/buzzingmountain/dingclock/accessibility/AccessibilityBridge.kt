package com.buzzingmountain.dingclock.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Façade between the rest of the app (controllers, state machine) and the live
 * AccessibilityService instance. All methods are no-ops if the service isn't bound.
 *
 * Stateless: the actual node tree is owned by the framework / service.
 */
object AccessibilityBridge {

    fun isReady(): Boolean = DingAccessibilityService.instance != null

    /** Returns the current foreground app's package name, or null if unavailable. */
    fun currentPackage(): String? {
        val root = currentRoot() ?: return null
        return root.packageName?.toString()
    }

    /** Latest root NodeInfo from the active window. The framework returns a fresh tree per call. */
    fun currentRoot(): AccessibilityNodeInfo? =
        runCatching { DingAccessibilityService.instance?.rootInActiveWindow }.getOrNull()

    fun performGlobalAction(action: Int): Boolean {
        val svc = DingAccessibilityService.instance ?: return false
        return runCatching { svc.performGlobalAction(action) }.getOrDefault(false)
    }

    /**
     * Polls [currentRoot] every [pollMs] until [predicate] returns a non-null match
     * or [timeoutMs] elapses. Returns the matching node or null.
     *
     * The returned node is a snapshot of a transient framework object; do not hold it
     * across long operations — re-find if needed after a UI change.
     */
    suspend fun awaitNode(
        timeoutMs: Long = 8_000,
        pollMs: Long = 200,
        tag: String = "node",
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var attempts = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            attempts++
            val root = currentRoot()
            if (root != null) {
                NodeFinder.findFirst(root, predicate)?.let {
                    Timber.d("awaitNode[%s] hit after %d attempts", tag, attempts)
                    return it
                }
            }
            delay(pollMs)
        }
        Timber.w("awaitNode[%s] timed out after %d ms (%d polls)", tag, timeoutMs, attempts)
        return null
    }

    /** Same as [awaitNode] but waits for the predicate to start returning null (e.g. node disappeared). */
    suspend fun awaitGone(
        timeoutMs: Long = 5_000,
        pollMs: Long = 200,
        tag: String = "gone",
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = currentRoot()
            val match = if (root == null) null else NodeFinder.findFirst(root, predicate)
            if (match == null) return true
            delay(pollMs)
        }
        Timber.w("awaitGone[%s] still present after %d ms", tag, timeoutMs)
        return false
    }
}
