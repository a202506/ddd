package com.buzzingmountain.dingclock.service

import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.PunchType

/** Snapshot of inputs for a single state-machine run. */
data class PunchContext(
    val type: PunchType,
    val config: AppConfig,
    /** Provider executed at the moment we need to type the password into DingTalk. */
    val passwordProvider: () -> String?,
)
