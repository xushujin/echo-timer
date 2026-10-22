package com.echotimer.core.voice.offline

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MfccTest {

    private val config = MfccConfig()

    private fun sine(frequency: Double, samples: Int, amplitude: Double = 8_000.0): ShortArray =
        ShortArray(samples) { i ->
            (amplitude * sin(2.0 * PI * frequency * i / config.sampleRate)).toInt().toShort()
        }

    @Test
    fun frameCountMatchesHopLength() {
        val samples = sine(440.0, 16_000)
        val frames = Mfcc.extract(samples, config)
        // 1 + (16000 - 400) / 160 = 98
        assertEquals(98, frames.size)
        assertEquals(config.cepstra, frames.first().size)
    }

    @Test
    fun tooShortInputProducesNoFrames() {
        assertEquals(0, Mfcc.extract(ShortArray(100), config).size)
        assertEquals(0, Mfcc.extract(ShortArray(0), config).size)
    }

    @Test
    fun sameInputGivesSameFeatures() {
        val samples = sine(600.0, 8_000)
        val a = Mfcc.extract(samples, config)
        val b = Mfcc.extract(samples, config)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertTrue(a[i].contentEquals(b[i]))
        }
    }

    @Test
    fun differentSoundSequenceGivesDifferentFeatures() {
        // 注意：CMN 会把"稳态"信号归一化成零，所以这里比较的是时间结构不同的两段，
        // 而不是两个不同频率的稳态单音（那样两者都会被归一化掉）。
        val rising = sine(300.0, 4_000) + sine(2_400.0, 4_000)
        val falling = sine(2_400.0, 4_000) + sine(300.0, 4_000)
        val a = Mfcc.extract(rising, config)
        val b = Mfcc.extract(falling, config)
        assertTrue("不同时序的特征应该明显不同", Dtw.distance(a, b) > 0.5f)
    }

    @Test
    fun silenceIsTrimmedAway() {
        val samples = ShortArray(2_000)
        for (i in 800 until 1_200) samples[i] = 1_500
        val trimmed = Mfcc.trimSilence(samples, threshold = 600)
        assertTrue("裁剪后应保留有声段", trimmed.size in 390..410)
        assertTrue(trimmed.first() != 0.toShort())
        assertTrue(trimmed.last() != 0.toShort())
    }

    @Test
    fun pureSilenceTrimsToEmpty() {
        assertEquals(0, Mfcc.trimSilence(ShortArray(4_000), threshold = 600).size)
    }
}
