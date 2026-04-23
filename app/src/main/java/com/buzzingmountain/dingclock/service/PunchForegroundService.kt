package com.buzzingmountain.dingclock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.buzzingmountain.dingclock.R
import com.buzzingmountain.dingclock.core.Constants
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.PunchLogRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.notify.DingRobotNotifier
import com.buzzingmountain.dingclock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hosts a single [PunchStateMachine] run. Started via [startWith]; self-stops on completion.
 *
 * specialUse foreground service so vivo / Android 14 can keep us alive while the user's
 * screen is off and the punch flow is automating Settings + DingTalk.
 */
class PunchForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var runningJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("PunchForegroundService onCreate")
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val typeName = intent?.getStringExtra(EXTRA_TYPE) ?: PunchType.DRY_RUN.name
        val type = runCatching { PunchType.valueOf(typeName) }.getOrDefault(PunchType.DRY_RUN)
        Timber.i("Start punch flow type=%s", type)

        startForegroundCompat(buildNotification(getString(R.string.notif_punch_starting)))
        acquireWakeLock()

        if (runningJob?.isActive == true) {
            Timber.w("Another punch is already running; ignoring new request")
            return START_NOT_STICKY
        }

        runningJob = scope.launch {
            val cfg = ConfigRepository(this@PunchForegroundService).load()
            if (cfg == null || !cfg.isComplete()) {
                Timber.e("No config; aborting")
                stopAll()
                return@launch
            }

            // Skip if today's slot is already marked successful in our DB — avoids opening
            // DingTalk redundantly (which would kick the same account off the second phone).
            // DRY_RUN is intentional testing and never blocked.
            if (type != PunchType.DRY_RUN &&
                PunchLogRepository(this@PunchForegroundService).hasSuccessfulPunchToday(type)
            ) {
                Timber.i("Skip %s — already recorded a success today", type)
                stopAll()
                return@launch
            }

            val repo = ConfigRepository(this@PunchForegroundService)
            val passwordProvider: () -> String? = { repo.decryptPassword(cfg) }

            val notifier = if (cfg.webhookUrl.isNotBlank()) {
                DingRobotNotifier(cfg.webhookUrl, cfg.webhookSecret.takeIf { it.isNotBlank() })
            } else null

            val context = PunchContext(
                type = type,
                config = cfg,
                passwordProvider = passwordProvider,
            )
            val machine = PunchStateMachine(applicationContext, context, notifier = notifier)

            // Mirror state into the foreground notification.
            launch {
                machine.state.collectLatest { st ->
                    updateNotification(st.title)
                }
            }

            val result = machine.run()
            Timber.i("Punch finished success=%s state=%s", result.success, result.state)

            runCatching {
                PunchLogRepository(this@PunchForegroundService).record(
                    startedAt = result.startedAt,
                    finishedAt = result.finishedAt,
                    type = type,
                    success = result.success,
                    finalState = result.state.title,
                    reason = result.reason,
                )
            }

            stopAll()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Timber.i("PunchForegroundService onDestroy")
        runningJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // FGS type required since Q; specialUse permission gated since UPSIDE_DOWN_CAKE.
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openMain = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_punch_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openMain)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
                ?.notify(NOTIF_ID, buildNotification(text))
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DingClock::Punch").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.runCatching { release() }
        wakeLock = null
    }

    private fun stopAll() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val NOTIF_ID = 2001
        private const val EXTRA_TYPE = "type"

        fun startWith(context: Context, type: PunchType) {
            val intent = Intent(context, PunchForegroundService::class.java).apply {
                putExtra(EXTRA_TYPE, type.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
