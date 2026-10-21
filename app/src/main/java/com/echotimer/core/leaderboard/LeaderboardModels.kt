package com.echotimer.core.leaderboard

/** 一个榜单。 */
data class Leaderboard(
    val id: Long,
    val name: String,
    /** 榜上人数。 */
    val entryCount: Int,
    /** 榜首成绩；还没有成绩时为 null。 */
    val bestMs: Long?,
    val createdAt: Long,
)

/** 榜单里的一条成绩（每个姓名只保留最好的一次）。 */
data class LeaderboardEntry(
    val id: Long,
    val boardId: Long,
    val player: String,
    val bestMs: Long,
    val updatedAt: Long,
)

/** 一次成绩提交的结果。 */
enum class ScoreOutcome {
    /** 该姓名第一次上榜。 */
    NEW_ENTRY,

    /** 超过原记录，已更新。 */
    IMPROVED,

    /** 没有超过原记录，保持原样。 */
    KEPT,
}

data class SubmitResult(
    val outcome: ScoreOutcome,
    val submittedMs: Long,
    /** 提交后该姓名的最好成绩。 */
    val bestMs: Long,
    /** 提交前的成绩；首次上榜为 null。 */
    val previousBestMs: Long?,
)

/** 新建榜单的结果。 */
sealed interface CreateBoardResult {
    data class Created(val boardId: Long) : CreateBoardResult

    /** 已存在同名榜单（不区分大小写）。 */
    data object NameTaken : CreateBoardResult

    /** 名称为空或超长。 */
    data object InvalidName : CreateBoardResult
}
