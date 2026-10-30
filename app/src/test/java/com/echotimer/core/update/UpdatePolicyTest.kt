package com.echotimer.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val current = AppVersion(1, 6, 0)

    @Test
    fun offersOnlyNewerVersions() {
        assertTrue(UpdatePolicy.shouldOffer(AppVersion(1, 6, 1), current))
        assertTrue(UpdatePolicy.shouldOffer(AppVersion(2, 0, 0), current))
        assertFalse(UpdatePolicy.shouldOffer(current, current))
        assertFalse(UpdatePolicy.shouldOffer(AppVersion(1, 5, 9), current))
    }

    @Test
    fun autoCheckHonoursSwitch() {
        assertFalse(UpdatePolicy.shouldAutoCheck(enabled = false, lastCheckAt = 0L, nowMs = 1_000L))
    }

    @Test
    fun autoCheckRunsWhenNeverChecked() {
        assertTrue(UpdatePolicy.shouldAutoCheck(enabled = true, lastCheckAt = 0L, nowMs = 1_000L))
    }

    @Test
    fun autoCheckWaitsOneDay() {
        val now = 1_800_000_000_000L
        assertFalse(
            UpdatePolicy.shouldAutoCheck(
                enabled = true,
                lastCheckAt = now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS + 1_000,
                nowMs = now,
            ),
        )
        assertTrue(
            UpdatePolicy.shouldAutoCheck(
                enabled = true,
                lastCheckAt = now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS,
                nowMs = now,
            ),
        )
    }

    @Test
    fun autoCheckStillRunsWhenClockRolledBack() {
        // 用户把系统时间调早：差值为负，不能因此永远不检查。
        assertTrue(
            UpdatePolicy.shouldAutoCheck(
                enabled = true,
                lastCheckAt = 2_000_000L,
                nowMs = 1_000_000L,
            ),
        )
    }

    @Test
    fun skippedVersionIsNotPromptedAutomatically() {
        assertFalse(
            UpdatePolicy.shouldPrompt(
                version = AppVersion(1, 7, 0),
                skippedVersion = "1.7.0",
                auto = true,
            ),
        )
        // 跳过的是旧版本，新版本仍然要提醒。
        assertTrue(
            UpdatePolicy.shouldPrompt(
                version = AppVersion(1, 8, 0),
                skippedVersion = "1.7.0",
                auto = true,
            ),
        )
    }

    @Test
    fun manualCheckAlwaysPrompts() {
        assertTrue(
            UpdatePolicy.shouldPrompt(
                version = AppVersion(1, 7, 0),
                skippedVersion = "1.7.0",
                auto = false,
            ),
        )
        assertTrue(UpdatePolicy.shouldPrompt(AppVersion(1, 7, 0), skippedVersion = null, auto = true))
        assertTrue(UpdatePolicy.shouldPrompt(AppVersion(1, 7, 0), skippedVersion = "", auto = true))
    }
}
