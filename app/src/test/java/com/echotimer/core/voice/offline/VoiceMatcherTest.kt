package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMatcherTest {

    /** 造一条"口令"：给定基准值，生成一串只在该值附近波动的特征帧。 */
    private fun template(command: VoiceCommand, base: Float): VoiceTemplate =
        VoiceTemplate(
            command = command,
            frames = List(12) { index ->
                floatArrayOf(base + index * 0.05f, base * 0.5f, -base + index * 0.02f)
            },
            updatedAt = 0L,
        )

    private val start = template(VoiceCommand.START, 1f)
    private val stop = template(VoiceCommand.STOP, 4f)
    private val lap = template(VoiceCommand.LAP, 8f)

    @Test
    fun interClassDistanceUsesDifferentCommandsOnly() {
        val sameCommandTwice = listOf(start, template(VoiceCommand.START, 1.2f))
        assertNull("同一命令不参与类间距离", VoiceMatcher.interClassDistance(sameCommandTwice))
        assertNotNull(VoiceMatcher.interClassDistance(listOf(start, stop)))
    }

    @Test
    fun thresholdIsClampedIntoUsableRange() {
        val tiny = listOf(template(VoiceCommand.START, 1f), template(VoiceCommand.STOP, 1.001f))
        assertTrue(VoiceMatcher.threshold(tiny) >= VoiceMatcher.MIN_THRESHOLD)

        val huge = listOf(template(VoiceCommand.START, 0f), template(VoiceCommand.STOP, 1_000f))
        assertTrue(VoiceMatcher.threshold(huge) <= VoiceMatcher.MAX_THRESHOLD)
    }

    @Test
    fun singleTemplateFallsBackToFixedThreshold() {
        assertEquals(VoiceMatcher.FALLBACK_THRESHOLD, VoiceMatcher.threshold(listOf(start)), 1e-5f)
    }

    @Test
    fun exactMatchWins() {
        val match = VoiceMatcher.match(stop.frames, listOf(start, stop, lap))
        requireNotNull(match)
        assertEquals(VoiceCommand.STOP, match.command)
        assertEquals(0f, match.distance, 1e-4f)
    }

    @Test
    fun unrelatedSoundIsRejected() {
        val noise = List(12) { floatArrayOf(50f, -60f, 70f) }
        assertNull(VoiceMatcher.match(noise, listOf(start, stop, lap)))
    }

    @Test
    fun rankingSortsByDistance() {
        val ranking = VoiceMatcher.ranking(stop.frames, listOf(start, stop, lap))
        assertEquals(VoiceCommand.STOP, ranking.first().first)
        assertTrue(ranking.first().second <= ranking.last().second)
    }
}
