package com.echotimer.core.voice.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceSegmenterTest {

    private fun segmenter() = UtteranceSegmenter(
        chunkMs = 20,
        triggerRatio = 3.5,
        minTriggerRms = 600.0,
        tailSilenceMs = 400,
        maxUtteranceMs = 2_500,
        initialNoiseFloor = 250.0,
    )

    @Test
    fun quietInputStaysIdle() {
        val segmenter = segmenter()
        repeat(50) {
            assertEquals(UtteranceSegmenter.Event.Idle, segmenter.accept(100.0))
        }
        assertFalse(segmenter.isSpeaking)
    }

    @Test
    fun speechThenSilenceProducesOneUtterance() {
        val segmenter = segmenter()
        repeat(5) { segmenter.accept(100.0) }

        assertEquals(UtteranceSegmenter.Event.Started, segmenter.accept(2_000.0))
        repeat(10) { assertEquals(UtteranceSegmenter.Event.Ongoing, segmenter.accept(2_000.0)) }

        var finished: UtteranceSegmenter.Event.Finished? = null
        repeat(25) {
            val event = segmenter.accept(100.0)
            if (event is UtteranceSegmenter.Event.Finished && finished == null) finished = event
        }
        requireNotNull(finished)
        assertFalse(segmenter.isSpeaking)
        // 开口 20ms + 10 块有声 + 20 块静音 = 620ms
        assertEquals(620, finished.durationMs)
    }

    @Test
    fun longSpeechIsForceFinished() {
        val segmenter = segmenter()
        segmenter.accept(2_000.0)
        var finished: UtteranceSegmenter.Event.Finished? = null
        repeat(200) {
            val event = segmenter.accept(2_000.0)
            if (event is UtteranceSegmenter.Event.Finished && finished == null) finished = event
        }
        requireNotNull(finished)
        assertEquals(2_500, finished.durationMs)
    }

    @Test
    fun noiseFloorAdaptsToQuietRoom() {
        val segmenter = segmenter()
        val initialTrigger = segmenter.triggerLevel
        repeat(100) { segmenter.accept(60.0) }
        assertTrue("噪声底应随环境下降", segmenter.triggerLevel < initialTrigger)
        // 但仍然不会低到被环境噪声触发
        assertTrue(segmenter.triggerLevel >= 600.0)
    }

    @Test
    fun resetClearsSpeakingState() {
        val segmenter = segmenter()
        segmenter.accept(2_000.0)
        assertTrue(segmenter.isSpeaking)
        segmenter.reset()
        assertFalse(segmenter.isSpeaking)
    }
}
