package com.buzzingmountain.dingclock.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "punch_log")
data class PunchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** epoch millis when the attempt began. */
    val startedAt: Long,
    /** epoch millis when it finished (success or failure). */
    val finishedAt: Long,
    /** [com.buzzingmountain.dingclock.data.PunchType] name. */
    val type: String,
    val success: Boolean,
    /** Final state name on failure, "Success" on success. */
    val finalState: String,
    /** Reason text on failure, empty on success. */
    val reason: String,
)
