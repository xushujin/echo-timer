package com.echotimer.core.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardRulesTest {

    @Test
    fun normalizeNameTrimsAndCollapsesSpaces() {
        assertEquals("张三", LeaderboardRules.normalizeName("  张三 "))
        assertEquals("Li Ming", LeaderboardRules.normalizeName("Li   Ming"))
        assertEquals("", LeaderboardRules.normalizeName("   "))
    }

    @Test
    fun validateNameRejectsEmpty() {
        assertEquals(NameProblem.EMPTY, LeaderboardRules.validateName(""))
        assertEquals(NameProblem.EMPTY, LeaderboardRules.validateName("   "))
    }

    @Test
    fun validateNameRejectsTooLong() {
        val max = "a".repeat(LeaderboardRules.MAX_NAME_LENGTH)
        assertNull(LeaderboardRules.validateName(max))
        assertEquals(
            NameProblem.TOO_LONG,
            LeaderboardRules.validateName("a".repeat(LeaderboardRules.MAX_NAME_LENGTH + 1)),
        )
    }

    @Test
    fun firstScoreCreatesEntry() {
        assertEquals(ScoreOutcome.NEW_ENTRY, LeaderboardRules.decide(previousBestMs = null, submittedMs = 1_000L))
    }

    @Test
    fun onlyStrictlyLargerScoreImproves() {
        assertEquals(ScoreOutcome.IMPROVED, LeaderboardRules.decide(previousBestMs = 1_000L, submittedMs = 1_001L))
        assertEquals(ScoreOutcome.KEPT, LeaderboardRules.decide(previousBestMs = 1_000L, submittedMs = 1_000L))
        assertEquals(ScoreOutcome.KEPT, LeaderboardRules.decide(previousBestMs = 1_000L, submittedMs = 999L))
    }
}
