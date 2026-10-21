package com.echotimer.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 可控单调时钟，用于确定性地测试秒表。 */
private class FakeClock(var now: Long = 0L) : MonotonicClock {
    override fun nowMs(): Long = now

    fun advance(ms: Long) {
        now += ms
    }
}

class StopwatchEngineTest {

    @Test
    fun initialStateIsIdleAtZero() {
        val engine = StopwatchEngine(FakeClock())
        assertEquals(StopwatchState.IDLE, engine.state)
        assertEquals(0L, engine.elapsedMs())
        assertTrue(engine.laps().isEmpty())
    }

    @Test
    fun startCountsUpWithClock() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        assertEquals(StopwatchState.RUNNING, engine.state)
        clock.advance(1_500L)
        assertEquals(1_500L, engine.elapsedMs())
    }

    @Test
    fun pauseFreezesElapsedAndResumeContinues() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        clock.advance(1_500L)
        engine.pause()
        assertEquals(StopwatchState.PAUSED, engine.state)

        clock.advance(10_000L)
        assertEquals("暂停期间时间不应累计", 1_500L, engine.elapsedMs())

        engine.start()
        assertEquals(StopwatchState.RUNNING, engine.state)
        clock.advance(500L)
        assertEquals(2_000L, engine.elapsedMs())
    }

    @Test
    fun finishKeepsTotalAndFreezesClock() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        clock.advance(2_000L)
        val total = engine.finish()
        assertEquals(2_000L, total)
        assertEquals(StopwatchState.FINISHED, engine.state)

        clock.advance(5_000L)
        assertEquals("结束后时长不应继续增长", 2_000L, engine.elapsedMs())
    }

    @Test
    fun startingFromFinishedBeginsNewSessionAndClearsLaps() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        clock.advance(1_000L)
        engine.addLap()
        engine.finish()
        assertEquals(1, engine.lapCount())

        engine.start()
        assertEquals(StopwatchState.RUNNING, engine.state)
        assertEquals(0L, engine.elapsedMs())
        assertEquals(0, engine.lapCount())
    }

    @Test
    fun lapsRecordSplitAndTotal() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()

        clock.advance(1_000L)
        val first = engine.addLap()
        assertEquals(LapSplit(index = 1, lapMs = 1_000L, totalMs = 1_000L), first)

        clock.advance(500L)
        val second = engine.addLap()
        assertEquals(LapSplit(index = 2, lapMs = 500L, totalMs = 1_500L), second)

        assertEquals(2, engine.lapCount())
    }

    @Test
    fun lapIsRejectedWhenNotRunning() {
        val engine = StopwatchEngine(FakeClock())
        assertNull(engine.addLap())

        val clock = FakeClock()
        val finished = StopwatchEngine(clock)
        finished.start()
        clock.advance(100L)
        finished.finish()
        assertNull(finished.addLap())
    }

    @Test
    fun resetClearsEverything() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        clock.advance(900L)
        engine.addLap()
        engine.reset()

        assertEquals(StopwatchState.IDLE, engine.state)
        assertEquals(0L, engine.elapsedMs())
        assertEquals(0, engine.lapCount())
    }

    @Test
    fun repeatedStartWhileRunningIsNoOp() {
        val clock = FakeClock()
        val engine = StopwatchEngine(clock)
        engine.start()
        clock.advance(1_000L)
        engine.start()
        assertEquals("运行中重复 start 不应清零", 1_000L, engine.elapsedMs())
    }

    @Test
    fun pauseWhenIdleIsNoOp() {
        val engine = StopwatchEngine(FakeClock())
        engine.pause()
        assertEquals(StopwatchState.IDLE, engine.state)
    }
}
