package com.echotimer.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parsesPlainVersion() {
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("1.6.0"))
        assertEquals(AppVersion(0, 9, 12), AppVersion.parse("0.9.12"))
    }

    @Test
    fun parsesTagWithPrefix() {
        // GitHub 的 tag 是 v1.6.0，构建脚本里的 versionName 是 1.6.0。
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("v1.6.0"))
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("V1.6.0"))
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("  v1.6.0  "))
    }

    @Test
    fun ignoresBuildSuffixes() {
        // debug 包的 VERSION_NAME 是 1.5.1-debug，不能因此解析失败。
        assertEquals(AppVersion(1, 5, 1), AppVersion.parse("1.5.1-debug"))
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("1.6.0+20261030"))
    }

    @Test
    fun padsMissingSegments() {
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
        assertEquals(AppVersion(2, 3, 0), AppVersion.parse("2.3"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1.x.0"))
    }

    @Test
    fun comparesNumerically() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 9))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertTrue(AppVersion(1, 6, 1) > AppVersion(1, 6, 0))
        assertEquals(AppVersion(1, 6, 0), AppVersion.parse("v1.6.0"))
        assertTrue(AppVersion(1, 6, 0) >= AppVersion(1, 6, 0))
    }

    @Test
    fun formatsAsDottedText() {
        assertEquals("1.6.0", AppVersion(1, 6, 0).toString())
    }
}
