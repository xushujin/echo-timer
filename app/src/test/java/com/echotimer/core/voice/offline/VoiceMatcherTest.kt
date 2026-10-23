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
    private val startAgain = template(VoiceCommand.START, 2f)
    private val stop = template(VoiceCommand.STOP, 8f)

    @Test
    fun interClassDistanceUsesDifferentCommandsOnly() {
        val sameCommandTwice = listOf(start, template(VoiceCommand.START, 1.2f))
        assertNull("同一命令不参与类间距离", VoiceMatcher.interClassDistance(sameCommandTwice))
        assertNotNull(VoiceMatcher.interClassDistance(listOf(start, stop)))
    }

    @Test
    fun sameClassDistanceUsesSameCommandOnly() {
        assertNull("没有同命令的两遍录音时无类内距离", VoiceMatcher.sameClassDistance(listOf(start, stop)))
        val same = VoiceMatcher.sameClassDistance(listOf(start, startAgain))
        requireNotNull(same)
        assertTrue(same > 0f)
    }

    @Test
    fun twoTakesTightenThresholdBetweenClasses() {
        val single = listOf(start, stop)
        val doubled = listOf(start, startAgain, stop)

        val same = VoiceMatcher.sameClassDistance(doubled)
        val inter = VoiceMatcher.interClassDistance(doubled)
        requireNotNull(same)
        requireNotNull(inter)

        val threshold = VoiceMatcher.threshold(doubled)
        assertTrue("门限要能容忍同一句话的两遍差异", threshold > same)
        assertTrue("门限不能跨到别的口令上", threshold < inter)
        // 单遍模板走"类间 × 系数"的兜底逻辑，与两遍的数据驱动结果应当不同
        assertTrue(VoiceMatcher.threshold(single) != threshold)
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
    fun ambiguousInputIsRejected() {
        // 探针正好落在两条口令中间：两个距离几乎相等，应当判为"太接近、不敢认"。
        val a = template(VoiceCommand.START, 1.0f)
        val b = template(VoiceCommand.STOP, 1.2f)
        val probe = template(VoiceCommand.START, 1.1f).frames

        val decision = VoiceMatcher.decide(probe, listOf(a, b))
        assertTrue(decision is MatchDecision.Rejected)
        assertEquals(MatchRejection.AMBIGUOUS, (decision as MatchDecision.Rejected).reason)
        assertNull(VoiceMatcher.match(probe, listOf(a, b)))
    }

    @Test
    fun decideReportsTooFarForUnrelatedSound() {
        val decision = VoiceMatcher.decide(template(VoiceCommand.LAP, 60f).frames, listOf(start, stop))
        assertTrue(decision is MatchDecision.Rejected)
        assertEquals(MatchRejection.TOO_FAR, (decision as MatchDecision.Rejected).reason)
    }

    @Test
    fun decideReportsMissingTemplates() {
        val decision = VoiceMatcher.decide(start.frames, emptyList())
        assertTrue(decision is MatchDecision.Rejected)
        assertEquals(MatchRejection.NO_TEMPLATES, (decision as MatchDecision.Rejected).reason)
    }

    @Test
    fun exactMatchWins() {
        val match = VoiceMatcher.match(stop.frames, listOf(start, stop))
        requireNotNull(match)
        assertEquals(VoiceCommand.STOP, match.command)
        assertEquals(0f, match.distance, 1e-4f)
    }

    @Test
    fun unrelatedSoundIsRejected() {
        val noise = List(12) { floatArrayOf(50f, -60f, 70f) }
        assertNull(VoiceMatcher.match(noise, listOf(start, stop)))
    }

    @Test
    fun statsExposeNumbersForDiagnostics() {
        val stats = VoiceMatcher.stats(stop.frames, listOf(start, startAgain, stop))
        assertEquals(VoiceCommand.STOP, stats.bestCommand)
        assertNotNull(stats.interClass)
        assertNotNull(stats.sameClass)
        assertTrue(stats.threshold > 0f)
    }

    @Test
    fun rankingSortsByDistance() {
        val ranking = VoiceMatcher.ranking(stop.frames, listOf(start, stop))
        assertEquals(VoiceCommand.STOP, ranking.first().first)
        assertTrue(ranking.first().second <= ranking.last().second)
    }
}
