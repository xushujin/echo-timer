package com.echotimer.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedFormatterTest {

    @Test
    fun clock_padsMinutesSecondsAndCentiseconds() {
        assertEquals("00:00.00", ElapsedFormatter.clock(0L))
        assertEquals("00:00.00", ElapsedFormatter.clock(1L))
        assertEquals("00:01.23", ElapsedFormatter.clock(1_234L))
        assertEquals("00:59.99", ElapsedFormatter.clock(59_999L))
        assertEquals("01:00.00", ElapsedFormatter.clock(60_000L))
    }

    @Test
    fun clock_switchesToHoursWhenNeeded() {
        assertEquals("1:00:00.00", ElapsedFormatter.clock(3_600_000L))
        assertEquals("1:01:01.23", ElapsedFormatter.clock(3_661_234L))
        assertEquals("10:00:00.00", ElapsedFormatter.clock(36_000_000L))
    }

    @Test
    fun clock_neverShowsNegativeTime() {
        assertEquals("00:00.00", ElapsedFormatter.clock(-1L))
        assertEquals("00:00.00", ElapsedFormatter.clock(-99_999L))
    }

    @Test
    fun clockShort_dropsCentiseconds() {
        assertEquals("00:00", ElapsedFormatter.clockShort(999L))
        assertEquals("01:05", ElapsedFormatter.clockShort(65_400L))
        assertEquals("1:01:01", ElapsedFormatter.clockShort(3_661_000L))
    }

    @Test
    fun spokenSeconds_isBareNumber() {
        assertEquals("0", ElapsedFormatter.spokenSeconds(0L))
        assertEquals("5", ElapsedFormatter.spokenSeconds(5_000L))
        assertEquals("5", ElapsedFormatter.spokenSeconds(5_999L))
        assertEquals("90", ElapsedFormatter.spokenSeconds(90_000L))
        assertEquals("3600", ElapsedFormatter.spokenSeconds(3_600_000L))
        assertEquals("0", ElapsedFormatter.spokenSeconds(-1L))
    }

    @Test
    fun spoken_omitsZeroUnits() {
        assertEquals("0 秒", ElapsedFormatter.spoken(0L))
        assertEquals("1 秒", ElapsedFormatter.spoken(1_000L))
        assertEquals("59 秒", ElapsedFormatter.spoken(59_400L))
        assertEquals("1 分", ElapsedFormatter.spoken(60_000L))
        assertEquals("1 分", ElapsedFormatter.spoken(90_000L))
        assertEquals("1 小时", ElapsedFormatter.spoken(3_600_000L))
        assertEquals("1 小时 1 分", ElapsedFormatter.spoken(3_661_000L))
        assertEquals("2 小时 5 分", ElapsedFormatter.spoken(7_500_000L))
    }
}
