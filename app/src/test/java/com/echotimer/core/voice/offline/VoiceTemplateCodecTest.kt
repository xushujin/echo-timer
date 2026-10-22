package com.echotimer.core.voice.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTemplateCodecTest {

    private val frames = listOf(
        floatArrayOf(1.234f, -5.678f, 0f, 12.5f),
        floatArrayOf(-0.001f, 3.3f, -7.25f, 0.5f),
        floatArrayOf(20f, -20f, 0.123f, -0.456f),
    )

    @Test
    fun roundTripKeepsValues() {
        val decoded = VoiceTemplateCodec.decode(VoiceTemplateCodec.encode(frames))
        requireNotNull(decoded)
        assertEquals(frames.size, decoded.size)
        for (i in frames.indices) {
            assertEquals(frames[i].size, decoded[i].size)
            for (j in frames[i].indices) {
                assertEquals(frames[i][j], decoded[i][j], 0.001f)
            }
        }
    }

    @Test
    fun emptyFramesEncodeToEmptyString() {
        assertEquals("", VoiceTemplateCodec.encode(emptyList()))
        assertNull(VoiceTemplateCodec.decode(""))
        assertNull(VoiceTemplateCodec.decode(null))
        assertNull(VoiceTemplateCodec.decode("   "))
    }

    @Test
    fun corruptInputReturnsNull() {
        assertNull(VoiceTemplateCodec.decode("not-base64!!"))
        assertNull(VoiceTemplateCodec.decode("AAAA"))
    }

    @Test
    fun wrongVersionReturnsNull() {
        val encoded = VoiceTemplateCodec.encode(frames)
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        bytes[0] = 99
        assertNull(VoiceTemplateCodec.decode(java.util.Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun truncatedPayloadReturnsNull() {
        val encoded = VoiceTemplateCodec.encode(frames)
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        val truncated = bytes.copyOfRange(0, bytes.size - 2)
        assertNull(VoiceTemplateCodec.decode(java.util.Base64.getEncoder().encodeToString(truncated)))
    }

    @Test
    fun encodedSizeStaysSmall() {
        // 100 帧 × 13 维，编码后应远小于 10KB（SharedPreferences 里放 5 条毫无压力）。
        val big = List(100) { index -> FloatArray(13) { index * 0.1f } }
        assertTrue(VoiceTemplateCodec.encode(big).length < 10_000)
    }
}
