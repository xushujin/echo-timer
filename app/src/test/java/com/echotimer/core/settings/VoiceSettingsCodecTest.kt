package com.echotimer.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSettingsCodecTest {

    @Test
    fun voiceEngineRoundTrip() {
        VoiceEngine.entries.forEach { engine ->
            assertEquals(engine, SettingsCodec.decodeVoiceEngine(SettingsCodec.encodeVoiceEngine(engine)))
        }
    }

    @Test
    fun unknownVoiceEngineFallsBackToAuto() {
        assertEquals(VoiceEngine.AUTO, SettingsCodec.decodeVoiceEngine(null))
        assertEquals(VoiceEngine.AUTO, SettingsCodec.decodeVoiceEngine(""))
        assertEquals(VoiceEngine.AUTO, SettingsCodec.decodeVoiceEngine("TURBO"))
    }

    @Test
    fun sensitivityRoundTrip() {
        OfflineSensitivity.entries.forEach { level ->
            assertEquals(level, SettingsCodec.decodeSensitivity(SettingsCodec.encodeSensitivity(level)))
        }
    }

    @Test
    fun unknownSensitivityFallsBackToMedium() {
        assertEquals(OfflineSensitivity.MEDIUM, SettingsCodec.decodeSensitivity(null))
        assertEquals(OfflineSensitivity.MEDIUM, SettingsCodec.decodeSensitivity("EXTREME"))
    }

    @Test
    fun sanitizeKeepsVoiceSettings() {
        val settings = AppSettings(
            voiceEngine = VoiceEngine.OFFLINE,
            offlineSensitivity = OfflineSensitivity.HIGH,
        ).sanitized()
        assertEquals(VoiceEngine.OFFLINE, settings.voiceEngine)
        assertEquals(OfflineSensitivity.HIGH, settings.offlineSensitivity)
    }

    @Test
    fun defaultEngineIsAutoAndSensitivityMedium() {
        val defaults = AppSettings()
        assertEquals(VoiceEngine.AUTO, defaults.voiceEngine)
        assertEquals(OfflineSensitivity.MEDIUM, defaults.offlineSensitivity)
    }
}
