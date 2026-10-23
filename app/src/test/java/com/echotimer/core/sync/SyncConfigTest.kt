package com.echotimer.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncConfigTest {

    @Test
    fun normalizesFullUrl() {
        assertEquals(
            "xushujin/echo-timer",
            SyncConfig.normalizeRepo("https://github.com/xushujin/echo-timer"),
        )
        assertEquals(
            "xushujin/echo-timer",
            SyncConfig.normalizeRepo("http://www.github.com/xushujin/echo-timer/"),
        )
    }

    @Test
    fun normalizesGitSuffixAndPlainPair() {
        assertEquals("owner/repo", SyncConfig.normalizeRepo("owner/repo.git"))
        assertEquals("owner/repo", SyncConfig.normalizeRepo("  owner/repo  "))
        assertEquals("owner/repo", SyncConfig.normalizeRepo("git@github.com:owner/repo"))
    }

    @Test
    fun rejectsIncompleteInput() {
        assertEquals("", SyncConfig.normalizeRepo(""))
        assertEquals("", SyncConfig.normalizeRepo("justname"))
        assertEquals("", SyncConfig.normalizeRepo("https://github.com/"))
        assertEquals("", SyncConfig.normalizeRepo("a/b/c"))
    }

    @Test
    fun blankBranchAndPathFallBackToDefaults() {
        val config = SyncConfig(repo = "owner/repo", branch = "  ", path = "//")
        assertEquals("main", config.normalizedBranch)
        assertEquals(SyncConfig.DEFAULT_PATH, config.normalizedPath)
    }

    @Test
    fun problemReportsWhatIsMissing() {
        assertNotNull(SyncConfig(repo = "", token = "t").problem())
        assertNotNull(SyncConfig(repo = "owner/repo", token = " ").problem())
        assertNull(SyncConfig(repo = "owner/repo", token = "t").problem())
    }

    @Test
    fun fullUrlIsAcceptedAsRepo() {
        val config = SyncConfig(repo = "https://github.com/xushujin/echo-timer", token = "t")
        assertEquals("xushujin/echo-timer", config.normalizedRepo)
        assertNull(config.problem())
    }
}
