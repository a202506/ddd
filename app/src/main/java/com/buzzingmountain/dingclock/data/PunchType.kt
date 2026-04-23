package com.buzzingmountain.dingclock.data

/** Which slot the current punch attempt belongs to. */
enum class PunchType(val zh: String) {
    MORNING("上班"),
    EVENING("下班"),
}
