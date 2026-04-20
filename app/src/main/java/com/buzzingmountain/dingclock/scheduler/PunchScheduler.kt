package com.buzzingmountain.dingclock.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.util.TimeUtils
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Recomputes today/tomorrow's punch alarms and schedules them via AlarmManager (primary)
 * plus a 15-minute periodic [HeartbeatWorker] (fallback to catch missed alarms).
 *
 * Re-runs itself daily at 00:01 to bump the schedule one day forward.
 */
class PunchScheduler(private val context: Context) {

    private val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val configRepo = ConfigRepository(context)

    fun rescheduleAll() {
        val cfg = configRepo.load()
        if (cfg == null || !cfg.isComplete() || !cfg.enabled) {
            Timber.i("rescheduleAll: no config or disabled — clearing existing alarms")
            cancelAllPunches()
            return
        }

        val now = LocalDateTime.now(ZONE)
        val today = now.toLocalDate()

        // Schedule each of today's punches that's still in the future.
        scheduleIfFuture(cfg, today, PunchType.MORNING, TimeUtils.parseHHmm(cfg.morningPunchAt), now)
        scheduleIfFuture(cfg, today, PunchType.EVENING, TimeUtils.parseHHmm(cfg.eveningPunchAt), now)

        // Always schedule tomorrow's punches (so we have at least the next-day pair).
        val tomorrow = today.plusDays(1)
        scheduleIfFuture(cfg, tomorrow, PunchType.MORNING, TimeUtils.parseHHmm(cfg.morningPunchAt), now)
        scheduleIfFuture(cfg, tomorrow, PunchType.EVENING, TimeUtils.parseHHmm(cfg.eveningPunchAt), now)

        // Daily reschedule alarm at 00:01 to keep rolling forward.
        scheduleReschedule(now)

        // Periodic heartbeat as fallback.
        enqueueHeartbeat()

        Timber.i("rescheduleAll done")
    }

    fun cancelAllPunches() {
        listOf(PunchType.MORNING, PunchType.EVENING).forEach { type ->
            for (dayOffset in 0..2) {
                runCatching { alarmMgr.cancel(pendingIntent(type.name, dayOffset)) }
            }
        }
        runCatching { alarmMgr.cancel(pendingIntent(PunchAlarmReceiver.ACTION_RESCHEDULE, 0)) }
    }

    private fun scheduleIfFuture(
        cfg: AppConfig,
        date: LocalDate,
        type: PunchType,
        time: LocalTime,
        now: LocalDateTime,
    ) {
        if (!HolidayChecker.isWorkday(date, cfg)) {
            Timber.i("Skip %s on %s (not workday)", type, date)
            return
        }
        val baseTrigger = LocalDateTime.of(date, time)
        val jitterMs = nextJitter(cfg.randomJitterSeconds)
        val triggerInstant = baseTrigger.atZone(ZONE).toInstant().toEpochMilli() + jitterMs
        if (triggerInstant <= System.currentTimeMillis()) {
            Timber.d("Skip %s on %s — already past", type, date)
            return
        }
        val pi = pendingIntent(type.name, dayOffset = (date.toEpochDay() - LocalDate.now(ZONE).toEpochDay()).toInt())
        scheduleExact(triggerInstant, pi)
        Timber.i(
            "Scheduled %s @ %s (jitter %+dms, trigger=%s)",
            type, baseTrigger, jitterMs, java.util.Date(triggerInstant),
        )
    }

    private fun scheduleReschedule(now: LocalDateTime) {
        // Next 00:01.
        val next = now.toLocalDate().plusDays(1).atTime(0, 1)
        val triggerMs = next.atZone(ZONE).toInstant().toEpochMilli()
        val pi = pendingIntent(PunchAlarmReceiver.ACTION_RESCHEDULE, dayOffset = 0)
        scheduleExact(triggerMs, pi)
        Timber.i("Reschedule alarm @ %s", next)
    }

    private fun scheduleExact(triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmMgr.canScheduleExactAlarms()) {
                Timber.w("Exact alarms denied; falling back to inexact set()")
                alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                return
            }
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (e: SecurityException) {
            Timber.e(e, "scheduleExact failed; falling back to inexact")
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(typeName: String, dayOffset: Int): PendingIntent {
        val intent = Intent(context, PunchAlarmReceiver::class.java).apply {
            putExtra(PunchAlarmReceiver.EXTRA_TYPE, typeName)
        }
        // requestCode must be unique per (type, dayOffset) so cancel/replace works.
        val rc = (typeName.hashCode() * 31) + dayOffset
        return PendingIntent.getBroadcast(
            context, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun enqueueHeartbeat() {
        val req = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HEARTBEAT_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    private fun nextJitter(jitterSeconds: Int): Long {
        if (jitterSeconds <= 0) return 0
        // Symmetric in [-jitter, +jitter] in milliseconds.
        return (Random().nextInt(jitterSeconds * 2) - jitterSeconds) * 1000L
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.systemDefault()
        const val HEARTBEAT_NAME = "dingclock.heartbeat"
    }
}
