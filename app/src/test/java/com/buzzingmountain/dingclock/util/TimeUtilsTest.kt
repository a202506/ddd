package com.buzzingmountain.dingclock.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TimeUtilsTest {

    @Test
    fun `parses normal HH mm`() {
        assertEquals(LocalTime.of(9, 0), TimeUtils.parseHHmm("09:00"))
        assertEquals(LocalTime.of(18, 30), TimeUtils.parseHHmm("18:30"))
    }

    @Test
    fun `falls back on garbage input`() {
        val fallback = LocalTime.of(7, 7)
        assertEquals(fallback, TimeUtils.parseHHmm("not-a-time", fallback))
        assertEquals(fallback, TimeUtils.parseHHmm("25:99", fallback))
        assertEquals(fallback, TimeUtils.parseHHmm("", fallback))
    }

    @Test
    fun `formats with zero padding`() {
        assertEquals("07:05", TimeUtils.formatHHmm(LocalTime.of(7, 5)))
        assertEquals("23:59", TimeUtils.formatHHmm(LocalTime.of(23, 59)))
    }
}
