package com.echotimer.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalMathTest {

    @Test
    fun nextMarkIsStrictlyInTheFuture() {
        assertEquals(NextMark(1_000L, 1), IntervalMath.nextMark(0L, 1))
        assertEquals(NextMark(1_000L, 1), IntervalMath.nextMark(999L, 1))
        assertEquals(NextMark(2_000L, 1), IntervalMath.nextMark(1_000L, 1))
    }

    @Test
    fun nextMarkFollowsTheChosenInterval() {
        assertEquals(NextMark(5_000L, 5), IntervalMath.nextMark(0L, 5))
        assertEquals(NextMark(10_000L, 5), IntervalMath.nextMark(9_000L, 5))
        assertEquals(NextMark(60_000L, 30), IntervalMath.nextMark(45_000L, 30))
    }

    @Test
    fun nextMarkReturnsNullWithoutInterval() {
        assertNull(IntervalMath.nextMark(5_000L, 0))
        assertNull(IntervalMath.nextMark(5_000L, -1))
    }

    @Test
    fun progressIsFractionOfCurrentInterval() {
        assertEquals(0.5f, IntervalMath.progressToNextMark(5_000L, 10), 0.0001f)
        assertEquals(0f, IntervalMath.progressToNextMark(0L, 10), 0.0001f)
        assertEquals(0.9f, IntervalMath.progressToNextMark(9_000L, 10), 0.0001f)
    }

    @Test
    fun progressWithoutIntervalIsZero() {
        assertEquals(0f, IntervalMath.progressToNextMark(1_234L, 0), 0.0001f)
    }
}
