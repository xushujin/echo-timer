package com.echotimer.core.voice.offline

import com.echotimer.core.settings.VoiceEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceEngineResolverTest {

    @Test
    fun autoPrefersSystemWhenAvailable() {
        assertEquals(VoiceBackend.SYSTEM, VoiceEngineResolver.resolve(VoiceEngine.AUTO, true, 0))
        assertEquals(VoiceBackend.SYSTEM, VoiceEngineResolver.resolve(VoiceEngine.AUTO, true, 5))
    }

    @Test
    fun autoFallsBackToOfflineTemplates() {
        assertEquals(VoiceBackend.OFFLINE, VoiceEngineResolver.resolve(VoiceEngine.AUTO, false, 3))
    }

    @Test
    fun autoReportsNoneWhenNothingIsReady() {
        assertEquals(VoiceBackend.NONE, VoiceEngineResolver.resolve(VoiceEngine.AUTO, false, 0))
    }

    @Test
    fun forcingSystemIgnoresOfflineTemplates() {
        assertEquals(VoiceBackend.NONE, VoiceEngineResolver.resolve(VoiceEngine.SYSTEM, false, 5))
        assertEquals(VoiceBackend.SYSTEM, VoiceEngineResolver.resolve(VoiceEngine.SYSTEM, true, 0))
    }

    @Test
    fun forcingOfflineIgnoresSystem() {
        assertEquals(VoiceBackend.OFFLINE, VoiceEngineResolver.resolve(VoiceEngine.OFFLINE, true, 1))
        assertEquals(VoiceBackend.NONE, VoiceEngineResolver.resolve(VoiceEngine.OFFLINE, true, 0))
    }
}
