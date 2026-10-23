package com.echotimer.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardPermissionTest {

    @Test
    fun leaderboardIsReadOnlyByDefault() {
        assertFalse("默认关闭修改权限（只读）", AppSettings().leaderboardEditable)
    }

    @Test
    fun sanitizeKeepsEditPermission() {
        val settings = AppSettings(leaderboardEditable = true).sanitized()
        assertTrue(settings.leaderboardEditable)
    }
}
