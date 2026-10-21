package com.echotimer.core.settings

import com.echotimer.core.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun themeModeRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, SettingsCodec.decodeThemeMode(SettingsCodec.encodeThemeMode(mode)))
        }
        assertEquals(ThemeMode.SYSTEM, SettingsCodec.decodeThemeMode("bogus"))
        assertEquals(ThemeMode.SYSTEM, SettingsCodec.decodeThemeMode(null))
    }

    @Test
    fun sanitizeRejectsUnknownPalette() {
        val settings = AppSettings(paletteId = "not-a-palette").sanitized()
        assertEquals(Palettes.DEFAULT_ID, settings.paletteId)
    }

    @Test
    fun sanitizeClampsIntervalIntoRange() {
        assertEquals(
            AppSettings.MIN_INTERVAL_SECONDS,
            AppSettings(announcementIntervalSeconds = 0).sanitized().announcementIntervalSeconds,
        )
        assertEquals(
            AppSettings.MIN_INTERVAL_SECONDS,
            AppSettings(announcementIntervalSeconds = -30).sanitized().announcementIntervalSeconds,
        )
        assertEquals(
            AppSettings.MAX_INTERVAL_SECONDS,
            AppSettings(announcementIntervalSeconds = 99_999).sanitized().announcementIntervalSeconds,
        )
    }

    @Test
    fun sanitizeRestoresBlankLanguage() {
        assertEquals("zh-CN", AppSettings(recognitionLanguage = "  ").sanitized().recognitionLanguage)
    }

    @Test
    fun defaultsAreSane() {
        val defaults = AppSettings()
        assertEquals(Palettes.DEFAULT_ID, defaults.paletteId)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertTrue("默认应开启定时播报", defaults.announcementsEnabled)
        assertEquals(AppSettings.DEFAULT_INTERVAL_SECONDS, defaults.announcementIntervalSeconds)
        assertTrue(
            "默认间隔必须出现在设置界面的可选项里",
            defaults.announcementIntervalSeconds in AppSettings.SELECTABLE_INTERVALS,
        )
        assertTrue(
            "所有可选项都在合法区间内",
            AppSettings.SELECTABLE_INTERVALS.all {
                it in AppSettings.MIN_INTERVAL_SECONDS..AppSettings.MAX_INTERVAL_SECONDS
            },
        )
    }
}
