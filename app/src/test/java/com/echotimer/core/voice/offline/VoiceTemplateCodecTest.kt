package com.echotimer.core.voice.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateCodecTest {

    private val firstTake = listOf(
        floatArrayOf(1.234f, -5.678f, 0f, 12.5f),
        floatArrayOf(-0.001f, 3.3f, -7.25f, 0.5f),
    )

    private val secondTake = listOf(
        floatArrayOf(2.5f, -1.25f, 0.75f, -3.5f),
        floatArrayOf(0.125f, 9.5f, -2f, 1f),
        floatArrayOf(-4f, 4f, 0f, 0.25f),
    )

    @Test
    fun singleTakeRoundTrip() {
        val decoded = VoiceTemplateCodec.decodeTakes(VoiceTemplateCodec.encode(listOf(firstTake)))
        requireNotNull(decoded)
        assertEquals(1, decoded.size)
        assertFramesEqual(firstTake, decoded[0])
    }

    @Test
    fun multipleTakesRoundTrip() {
        val decoded = VoiceTemplateCodec.decodeTakes(
            VoiceTemplateCodec.encode(listOf(firstTake, secondTake)),
        )
        requireNotNull(decoded)
        assertEquals(2, decoded.size)
        assertFramesEqual(firstTake, decoded[0])
        assertFramesEqual(secondTake, decoded[1])
    }

    @Test
    fun legacySingleTakeBlobStillDecodes() {
        // 手工拼一个 v1 格式（老版本只存一遍），确认升级后不用重录。
        val frames = firstTake
        val dim = frames[0].size
        val bytes = ByteArray(4 + frames.size * dim * 2)
        bytes[0] = 1
        bytes[1] = dim.toByte()
        bytes[2] = ((frames.size shr 8) and 0xFF).toByte()
        bytes[3] = (frames.size and 0xFF).toByte()
        var offset = 4
        for (frame in frames) {
            for (i in 0 until dim) {
                val value = (frame[i] * 1000f).toInt()
                bytes[offset++] = (value shr 8).toByte()
                bytes[offset++] = value.toByte()
            }
        }
        val raw = java.util.Base64.getEncoder().encodeToString(bytes)
        val decoded = VoiceTemplateCodec.decodeTakes(raw)
        requireNotNull(decoded)
        assertEquals(1, decoded.size)
        assertFramesEqual(frames, decoded[0])
    }

    @Test
    fun emptyInputReturnsEmptyString() {
        assertEquals("", VoiceTemplateCodec.encode(emptyList()))
        assertEquals("", VoiceTemplateCodec.encode(listOf(emptyList())))
        assertNull(VoiceTemplateCodec.decodeTakes(""))
        assertNull(VoiceTemplateCodec.decodeTakes(null))
        assertNull(VoiceTemplateCodec.decodeTakes("   "))
    }

    @Test
    fun corruptInputReturnsNull() {
        assertNull(VoiceTemplateCodec.decodeTakes("not-base64!!"))
        assertNull(VoiceTemplateCodec.decodeTakes("AAAA"))
    }

    @Test
    fun wrongVersionReturnsNull() {
        val bytes = java.util.Base64.getDecoder().decode(VoiceTemplateCodec.encode(listOf(firstTake)))
        bytes[0] = 99
        assertNull(VoiceTemplateCodec.decodeTakes(java.util.Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun truncatedPayloadReturnsNull() {
        val bytes = java.util.Base64.getDecoder().decode(VoiceTemplateCodec.encode(listOf(firstTake)))
        val truncated = bytes.copyOfRange(0, bytes.size - 2)
        assertNull(VoiceTemplateCodec.decodeTakes(java.util.Base64.getEncoder().encodeToString(truncated)))
    }

    @Test
    fun encodedSizeStaysSmall() {
        // 2 遍 × 100 帧 × 13 维，编码后应远小于 20KB（SharedPreferences 放 5 条毫无压力）。
        val take = List(100) { index -> FloatArray(13) { index * 0.1f } }
        assertTrue(VoiceTemplateCodec.encode(listOf(take, take)).length < 20_000)
    }

    private fun assertFramesEqual(expected: List<FloatArray>, actual: List<FloatArray>) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i].size, actual[i].size)
            for (j in expected[i].indices) {
                assertEquals(expected[i][j], actual[i][j], 0.001f)
            }
        }
    }
}
