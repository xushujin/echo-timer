package com.echotimer.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementSchedulerTest {

    private fun scheduler(intervalSeconds: Int) = AnnouncementScheduler().apply {
        setInterval(intervalSeconds)
        reset()
    }

    @Test
    fun oneSecondCadenceAnnouncesEverySecond() {
        val scheduler = scheduler(1)
        assertNull(scheduler.advanceTo(0L))
        assertNull(scheduler.advanceTo(999L))
        assertEquals(Announcement(1_000L, 1), scheduler.advanceTo(1_000L))
        assertEquals(Announcement(2_000L, 1), scheduler.advanceTo(2_000L))
        assertEquals(Announcement(3_000L, 1), scheduler.advanceTo(3_000L))
    }

    @Test
    fun fiveSecondCadenceAnnouncesOnlyOnMultiples() {
        val scheduler = scheduler(5)
        assertNull(scheduler.advanceTo(4_999L))
        assertEquals(Announcement(5_000L, 5), scheduler.advanceTo(5_000L))
        assertNull("5s 与 10s 之间不应播报", scheduler.advanceTo(9_999L))
        assertEquals(Announcement(10_000L, 5), scheduler.advanceTo(10_000L))
        assertEquals(Announcement(15_000L, 5), scheduler.advanceTo(15_000L))
    }

    @Test
    fun doesNotRepeatTheSameMark() {
        val scheduler = scheduler(1)
        assertEquals(Announcement(1_000L, 1), scheduler.advanceTo(1_000L))
        assertNull("同一时间点不应重复播报", scheduler.advanceTo(1_000L))
        assertNull(scheduler.advanceTo(1_050L))
        assertEquals(Announcement(2_000L, 1), scheduler.advanceTo(2_000L))
    }

    @Test
    fun largeJumpProducesSingleLatestAnnouncement() {
        val scheduler = scheduler(1)
        assertEquals(Announcement(25_000L, 1), scheduler.advanceTo(25_000L))
    }

    @Test
    fun zeroIntervalMeansNoAnnouncements() {
        val scheduler = scheduler(0)
        assertNull(scheduler.advanceTo(10_000L))
        assertEquals(0, scheduler.interval())
    }

    @Test
    fun resetAllowsSameMarkToBeAnnouncedAgain() {
        val scheduler = scheduler(5)
        assertEquals(Announcement(5_000L, 5), scheduler.advanceTo(5_000L))
        scheduler.reset()
        assertEquals(Announcement(5_000L, 5), scheduler.advanceTo(5_000L))
    }

    @Test
    fun timeGoingBackwardsResynchronises() {
        val scheduler = scheduler(1)
        assertEquals(Announcement(1_000L, 1), scheduler.advanceTo(1_000L))
        assertNull(scheduler.advanceTo(200L))
        assertEquals(Announcement(1_000L, 1), scheduler.advanceTo(1_000L))
    }

    @Test
    fun changingIntervalMidRunUsesNewCadence() {
        val scheduler = scheduler(10)
        assertNull(scheduler.advanceTo(9_000L))
        scheduler.setInterval(5)
        assertEquals(Announcement(10_000L, 5), scheduler.advanceTo(10_000L))
        assertEquals(Announcement(15_000L, 5), scheduler.advanceTo(15_000L))
    }

    @Test
    fun negativeIntervalIsTreatedAsOff() {
        val scheduler = AnnouncementScheduler()
        scheduler.setInterval(-5)
        scheduler.reset()
        assertEquals(0, scheduler.interval())
        assertNull(scheduler.advanceTo(10_000L))
    }
}
