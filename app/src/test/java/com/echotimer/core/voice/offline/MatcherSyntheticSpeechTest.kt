package com.echotimer.core.voice.offline

import com.echotimer.core.voice.VoiceCommand
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用合成语音验证"该认的认、不该认的不认"。
 *
 * 没有真机也能覆盖的关键性质：
 * - 同一口令换一次录音（音高/时长抖动）仍能认出来；
 * - 白噪声、没录过的"生词"必须被拒绝（"乱说也能匹配"的回归防线）。
 *
 * 合成方式：用两段共振峰频率近似一个"字"，每个口令 3 个音节的频率走向不同。
 */
class MatcherSyntheticSpeechTest {

    private val sampleRate = 16_000

    private val words = mapOf(
        VoiceCommand.START to listOf(300.0 to 1200.0, 500.0 to 900.0, 700.0 to 1300.0),
        VoiceCommand.PAUSE to listOf(500.0 to 800.0, 350.0 to 1100.0, 300.0 to 1500.0),
        VoiceCommand.RESUME to listOf(250.0 to 2000.0, 400.0 to 1600.0, 450.0 to 1200.0),
        VoiceCommand.STOP to listOf(700.0 to 1000.0, 600.0 to 800.0, 400.0 to 700.0),
        VoiceCommand.LAP to listOf(350.0 to 1600.0, 350.0 to 1600.0, 500.0 to 1900.0),
    )

    private val templates: List<VoiceTemplate> = words.entries.flatMap { (command, segments) ->
        listOf(
            VoiceTemplate(command, frames(word(segments, seed = 1)), 0L),
            VoiceTemplate(command, frames(word(segments, seed = 2, jitter = 0.08)), 0L),
        )
    }

    private fun word(
        segments: List<Pair<Double, Double>>,
        seed: Long,
        jitter: Double = 0.0,
        amplitude: Double = 9_000.0,
    ): ShortArray {
        val random = Random(seed)
        val out = ArrayList<Short>(sampleRate)
        for ((f1, f2) in segments) {
            val length = (sampleRate * 0.14 * (1.0 + jitter * (random.nextDouble() * 2 - 1))).toInt()
            for (i in 0 until length) {
                val t = i.toDouble() / sampleRate
                val envelope = sin(PI * i / length)
                val value = (
                    sin(2 * PI * f1 * t) * 0.6 +
                        sin(2 * PI * f2 * t) * 0.4
                    ) * amplitude * envelope
                out.add(value.toInt().toShort())
            }
        }
        for (i in out.indices) {
            val noisy = out[i] + (random.nextDouble() * 2 - 1) * 250
            out[i] = noisy.toInt().toShort()
        }
        return out.toShortArray()
    }

    private fun noise(seed: Long, lengthMs: Int = 500, amplitude: Double = 3_000.0): ShortArray {
        val random = Random(seed)
        return ShortArray(sampleRate * lengthMs / 1000) {
            ((random.nextDouble() * 2 - 1) * amplitude).toInt().toShort()
        }
    }

    private fun frames(samples: ShortArray): List<FloatArray> =
        Mfcc.extract(Mfcc.trimSilence(samples, threshold = 300))

    @Test
    fun syntheticWordsAreRecognized() {
        for (command in words.keys) {
            val probe = frames(word(words.getValue(command), seed = 42, jitter = 0.05))
            val decision = VoiceMatcher.decide(probe, templates)
            assertTrue("$command 应被识别，实际：$decision", decision is MatchDecision.Accepted)
            assertEquals(command, (decision as MatchDecision.Accepted).command)
        }
    }

    @Test
    fun noiseIsRejected() {
        repeat(5) { index ->
            val decision = VoiceMatcher.decide(frames(noise(seed = 200L + index)), templates)
            assertTrue("噪声 #$index 不应命中，实际：$decision", decision is MatchDecision.Rejected)
        }
    }

    @Test
    fun unknownWordIsRejected() {
        // 没录过的"生词"：频率走向与 5 条口令都不同。
        val unknown = listOf(950.0 to 2500.0, 850.0 to 2200.0, 150.0 to 1750.0)
        val decision = VoiceMatcher.decide(frames(word(unknown, seed = 7, jitter = 0.05)), templates)
        assertTrue("生词不应命中，实际：$decision", decision is MatchDecision.Rejected)
    }
}
