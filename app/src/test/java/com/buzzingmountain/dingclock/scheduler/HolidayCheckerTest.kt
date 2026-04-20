package com.buzzingmountain.dingclock.scheduler

import com.buzzingmountain.dingclock.data.AppConfig
import com.buzzingmountain.dingclock.data.HolidayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayCheckerTest {

    private val baseCfg = AppConfig(
        phoneNumber = "13812345678",
        passwordCipher = "x",
        wifiSsid = "Office",
    )

    @Test
    fun `weekends_only treats Sat and Sun as off`() {
        val cfg = baseCfg.copy(holidayMode = HolidayMode.WEEKENDS_ONLY)
        // 2026-04-18 is a Saturday, 19 Sunday, 20 Monday
        assertFalse(HolidayChecker.isWorkday(LocalDate.of(2026, 4, 18), cfg))
        assertFalse(HolidayChecker.isWorkday(LocalDate.of(2026, 4, 19), cfg))
        assertTrue(HolidayChecker.isWorkday(LocalDate.of(2026, 4, 20), cfg))
    }

    @Test
    fun `custom_list excludes weekdays explicitly listed`() {
        val cfg = baseCfg.copy(
            holidayMode = HolidayMode.CUSTOM_LIST,
            customHolidays = setOf("2026-05-01"),
        )
        // May 1 2026 is a Friday — would normally be a workday
        assertFalse(HolidayChecker.isWorkday(LocalDate.of(2026, 5, 1), cfg))
        assertTrue(HolidayChecker.isWorkday(LocalDate.of(2026, 5, 4), cfg)) // Monday
        assertFalse(HolidayChecker.isWorkday(LocalDate.of(2026, 5, 2), cfg)) // Saturday
    }
}
