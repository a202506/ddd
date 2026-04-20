package com.buzzingmountain.dingclock.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtils {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseHHmm(value: String, default: LocalTime = LocalTime.of(9, 0)): LocalTime =
        try {
            LocalTime.parse(value, FMT)
        } catch (_: DateTimeParseException) {
            default
        }

    fun formatHHmm(time: LocalTime): String = time.format(FMT)
}
