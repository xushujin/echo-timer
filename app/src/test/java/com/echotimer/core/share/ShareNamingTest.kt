package com.echotimer.core.share

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareNamingTest {

    @Test
    fun safeNameReplacesIllegalCharacters() {
        assertEquals("A-B-C", ShareNaming.safeName("A/B\\C"))
        assertEquals("1-2-3", ShareNaming.safeName("1:2*3"))
        assertEquals("单杠-悬垂", ShareNaming.safeName("单杠 悬垂"))
        assertEquals("a-b", ShareNaming.safeName("a<>b"))
    }

    @Test
    fun safeNameFallsBackWhenBlank() {
        assertEquals("leaderboard", ShareNaming.safeName(""))
        assertEquals("leaderboard", ShareNaming.safeName("   "))
        assertEquals("leaderboard", ShareNaming.safeName("///"))
        assertEquals("自定义", ShareNaming.safeName("  ", fallback = "自定义"))
    }

    @Test
    fun safeNameIsTruncated() {
        val longName = "名".repeat(60)
        assertEquals(40, ShareNaming.safeName(longName).length)
    }

    @Test
    fun fileNameCarriesBoardNameAndTimestamp() {
        val name = ShareNaming.fileNameFor("单杠悬垂", 0L, ZoneId.of("UTC"))
        assertEquals("EchoTimer-单杠悬垂-19700101-000000.png", name)
    }

    @Test
    fun fileNameIsAlwaysSafeForFileSystem() {
        val name = ShareNaming.fileNameFor("a/b:c", 1_700_000_000_000L, ZoneId.of("UTC"))
        assertTrue(name.endsWith(".png"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
    }
}
