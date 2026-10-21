package com.echotimer.core.leaderboard

/** 名称校验结果。 */
enum class NameProblem {
    EMPTY,
    TOO_LONG,
}

/**
 * 天梯榜的业务规则（纯函数，便于单测）：
 * 1. 名称去首尾空格、连续空白折叠成一个空格；
 * 2. 榜单名唯一（大小写不敏感，由数据库唯一索引兜底，这里只做长度/空值校验）；
 * 3. 成绩"只有比原记录更大才更新"，否则保持原记录；没有记录则新增。
 */
object LeaderboardRules {

    const val MAX_NAME_LENGTH = 20

    private val WHITESPACE = Regex("\\s+")

    fun normalizeName(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /** 返回 null 表示合法。 */
    fun validateName(raw: String): NameProblem? {
        val name = normalizeName(raw)
        return when {
            name.isEmpty() -> NameProblem.EMPTY
            name.length > MAX_NAME_LENGTH -> NameProblem.TOO_LONG
            else -> null
        }
    }

    /** 秒数越大越好：只有严格大于原记录才更新。 */
    fun decide(previousBestMs: Long?, submittedMs: Long): ScoreOutcome = when {
        previousBestMs == null -> ScoreOutcome.NEW_ENTRY
        submittedMs > previousBestMs -> ScoreOutcome.IMPROVED
        else -> ScoreOutcome.KEPT
    }
}
