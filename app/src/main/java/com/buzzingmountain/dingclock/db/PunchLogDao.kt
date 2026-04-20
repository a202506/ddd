package com.buzzingmountain.dingclock.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PunchLogDao {

    @Insert
    suspend fun insert(entity: PunchLogEntity): Long

    @Query("SELECT * FROM punch_log ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<PunchLogEntity>

    @Query(
        """
        SELECT COUNT(*) FROM punch_log
        WHERE success = 1
          AND type = :type
          AND startedAt >= :sinceMillis
          AND startedAt < :untilMillis
        """,
    )
    suspend fun successCountInRange(type: String, sinceMillis: Long, untilMillis: Long): Int

    @Query("DELETE FROM punch_log WHERE startedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
