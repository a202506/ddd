package com.buzzingmountain.dingclock.data

import android.content.Context
import com.buzzingmountain.dingclock.db.AppDatabase
import com.buzzingmountain.dingclock.db.PunchLogEntity
import java.time.LocalDate
import java.time.ZoneId

class PunchLogRepository(context: Context) {

    private val dao = AppDatabase.get(context).punchLogDao()

    suspend fun record(
        startedAt: Long,
        finishedAt: Long,
        type: PunchType,
        success: Boolean,
        finalState: String,
        reason: String,
    ): Long {
        // Best-effort retention: prune anything older than 90 days every time we insert.
        runCatching { dao.deleteOlderThan(System.currentTimeMillis() - 90L * 24 * 3600_000L) }
        return dao.insert(
            PunchLogEntity(
                startedAt = startedAt,
                finishedAt = finishedAt,
                type = type.name,
                success = success,
                finalState = finalState,
                reason = reason,
            ),
        )
    }

    suspend fun recent(limit: Int = 20): List<PunchLogEntity> = dao.recent(limit)

    suspend fun hasSuccessfulPunchToday(type: PunchType, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.successCountInRange(type.name, start, end) > 0
    }
}
