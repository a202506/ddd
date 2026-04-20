package com.buzzingmountain.dingclock.scheduler

import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.HolidayMode
import java.time.DayOfWeek
import java.time.LocalDate

object HolidayChecker {

    /** True if the punch should run on [date] given the current [config]. */
    fun isWorkday(date: LocalDate, config: AppConfig): Boolean {
        val isoDate = date.toString()
        return when (config.holidayMode) {
            HolidayMode.WEEKENDS_ONLY ->
                date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            HolidayMode.CUSTOM_LIST -> {
                if (config.customHolidays.contains(isoDate)) false
                else date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            }
        }
    }
}
