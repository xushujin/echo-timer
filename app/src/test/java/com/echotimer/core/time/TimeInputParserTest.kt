package com.echotimer.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeInputParserTest {

    @Test
    fun parsesPlainSeconds() {
        assertEquals(12_000L, TimeInputParser.parse("12"))
        assertEquals(12_500L, TimeInputParser.parse("12.5"))
        assertEquals(12_340L, TimeInputParser.parse("12.34"))
        assertEquals(5_000L, TimeInputParser.parse(" 5 "))
    }

    @Test
    fun parsesMinutesAndSeconds() {
        assertEquals(83_450L, TimeInputParser.parse("1:23.45"))
        assertEquals(60_000L, TimeInputParser.parse("1:00"))
        assertEquals(135_000L, TimeInputParser.parse("1:75"))
    }

    @Test
    fun parsesHoursMinutesSeconds() {
        assertEquals(3_723_500L, TimeInputParser.parse("1:02:03.5"))
        assertEquals(3_600_000L, TimeInputParser.parse("1:00:00"))
    }

    @Test
    fun normalisesFullWidthInput() {
        assertEquals(12_340L, TimeInputParser.parse("１２．３４"))
        assertEquals(83_450L, TimeInputParser.parse("１：２３．４５"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(TimeInputParser.parse(""))
        assertNull(TimeInputParser.parse("   "))
        assertNull(TimeInputParser.parse("abc"))
        assertNull(TimeInputParser.parse("1:2:3:4"))
        assertNull(TimeInputParser.parse("1::2"))
        assertNull(TimeInputParser.parse("-5"))
        assertNull(TimeInputParser.parse("0"))
        assertNull(TimeInputParser.parse("25:00:00"))
    }
}
