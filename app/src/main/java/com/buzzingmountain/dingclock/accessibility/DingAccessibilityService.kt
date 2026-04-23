package com.buzzingmountain.dingclock.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.core.Constants
import com.buzzingmountain.dingclock.service.NotificationHelper
import timber.log.Timber

/**
 * Long-running AccessibilityService: logs every TYPE_WINDOW_STATE_CHANGED so we can see which
 * activity is in front, and exposes a [ACTION_DUMP_ROOT] broadcast (wired to a notification
 * action) that dumps the current window's accessibility node tree into the log file. The
 * punch state machine reads from [AccessibilityBridge] to classify and drive DingTalk.
 */
class DingAccessibilityService : AccessibilityService() {

    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.i("Dump action received from notification")
            dumpCurrentRoot()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("AccessibilityService connected")

        NotificationHelper.ensureChannels(this)
        registerDumpReceiver()
        showStatusNotification()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("AccessibilityService unbinding")
        runCatching { unregisterReceiver(dumpReceiver) }
        cancelStatusNotification()
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

    private fun dumpCurrentRoot() {
        val root: AccessibilityNodeInfo? = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null) {
            Timber.w("dump: rootInActiveWindow is null (no foreground app or service still warming up)")
            return
        }
        val pkg = root.packageName
        val dump = NodeDumper.dump(root)
        Timber.i("=== NODE DUMP START pkg=%s ===\n%s=== NODE DUMP END ===", pkg, dump)
        @Suppress("DEPRECATION")
        runCatching { root.recycle() }
    }

    private fun registerDumpReceiver() {
        val filter = IntentFilter(ACTION_DUMP_ROOT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dumpReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dumpReceiver, filter)
        }
    }

    private fun showStatusNotification() {
        if (!NotificationHelper.canPostNotifications(this)) {
            Timber.w("POST_NOTIFICATIONS not granted; status notification skipped")
            return
        }
        val dumpIntent = Intent(ACTION_DUMP_ROOT).setPackage(packageName)
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val dumpPi = PendingIntent.getBroadcast(this, REQ_DUMP, dumpIntent, piFlags)

        val notif = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_status_title))
            .setContentText(getString(R.string.notif_status_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notif_action_dump), dumpPi)
            .build()

        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID_STATUS, notif) }
            .onFailure { Timber.e(it, "post status notification failed") }
    }

    private fun cancelStatusNotification() {
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_ID_STATUS) }
    }

    companion object {
        const val ACTION_DUMP_ROOT = "com.buzzingmountain.dingclock.action.DUMP_ROOT"
        private const val NOTIF_ID_STATUS = 1001
        private const val REQ_DUMP = 0xD0_01

        @Volatile
        var instance: DingAccessibilityService? = null
            private set

        /**
         * Trigger a node-tree dump from inside the app process. No-op if the service hasn't
         * been bound by the system yet (user hasn't enabled accessibility for us).
         */
        fun dumpRootIfActive(): Boolean {
            val svc = instance ?: return false
            svc.dumpCurrentRoot()
            return true
        }
    }
}
