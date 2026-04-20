package com.buzzingmountain.dingclock.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buzzingmountain.dingclock.data.ConfigRepository
import com.buzzingmountain.dingclock.data.PunchLogRepository
import com.buzzingmountain.dingclock.data.PunchType
import com.buzzingmountain.dingclock.service.PunchForegroundService
import com.buzzingmountain.dingclock.util.TimeUtils
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Belt-and-suspenders fallback for the AlarmManager schedule. Runs every 15 minutes;
 * if today's punch slot is in the past and no successful PunchLog exists for it yet,
 * fires the punch immediately.
 */
class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val cfg = ConfigRepository(ctx).load()
        if (cfg == null || !cfg.isComplete() || !cfg.enabled) {
            Timber.d("HeartbeatWorker: no config or disabled — nothing to do")
            return Result.success()
        }
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        if (!HolidayChecker.isWorkday(today, cfg)) return Result.success()

        val morning = TimeUtils.parseHHmm(cfg.morningPunchAt)
        val evening = TimeUtils.parseHHmm(cfg.eveningPunchAt)
        val logRepo = PunchLogRepository(ctx)

        catchUpIfDue(
            ctx, now.toLocalTime(), morning, PunchType.MORNING,
            cfg.randomJitterSeconds, logRepo,
        )
        catchUpIfDue(
            ctx, now.toLocalTime(), evening, PunchType.EVENING,
            cfg.randomJitterSeconds, logRepo,
        )
        return Result.success()
    }

    private suspend fun catchUpIfDue(
        ctx: Context,
        nowTime: LocalTime,
        scheduled: LocalTime,
        type: PunchType,
        jitterSec: Int,
        logRepo: PunchLogRepository,
    ) {
        // Skip if we haven't reached the slot yet (allow up to jitter past the time).
        val tolerance = (jitterSec + 30) // seconds
        if (nowTime.isBefore(scheduled.minusSeconds(tolerance.toLong()))) return
        // Don't fire if it's been more than 4 hours past — assume the user no longer wants it.
        if (nowTime.isAfter(scheduled.plusHours(4))) return
        if (logRepo.hasSuccessfulPunchToday(type)) return
        Timber.w("Heartbeat: %s slot %s missed; firing catch-up", type, scheduled)
        PunchForegroundService.startWith(ctx, type)
    }
}
